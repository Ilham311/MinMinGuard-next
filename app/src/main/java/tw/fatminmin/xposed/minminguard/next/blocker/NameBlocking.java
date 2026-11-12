package tw.fatminmin.xposed.minminguard.next.blocker;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.Main;
import tw.fatminmin.xposed.minminguard.next.core.name.AdNameMatcher;
import tw.fatminmin.xposed.minminguard.next.core.name.NameCache;

/**
 * Pemblokiran berbasis nama kelas view.
 * <p>
 * Dipakai untuk SDK yang memakai nama kelas tetap (tidak di-obfuscate), dan
 * sebagai jaring pengaman ketika hook API tidak menemukan signature yang
 * cocok. Tiga titik pemasangan dipilih karena setiap UI Android melewati
 * salah satunya: {@code ViewGroup.addView}, {@code Activity.setContentView},
 * dan {@code LayoutInflater.inflate}.
 */
public final class NameBlocking
{
    /** Batas kedalaman penelusuran pohon view. */
    private static final int MAX_DEPTH = 64;

    /** Akhiran nama kelas yang hampir selalu berarti container iklan. */
    private static final String[] HEURISTIC_SUFFIXES = {
            "AdView", "AdViewGroup", "AdContainer", "AdLayout", "AdWrapper",
            "BannerView", "BannerLayout", "NativeAdView", "NativeAdLayout",
            "InterstitialAdView", "AdFrameLayout", "AdRelativeLayout",
            "AdWebView", "RewardedAdView", "AppOpenAdView",
    };

    /** Prefix yang bukan iklan meskipun mengandung kata "ad". */
    private static final String[] IGNORED_PREFIXES = {
            "android.", "androidx.", "com.android.", "java.", "kotlin.",
            "com.google.android.material.",
    };

    /**
     * Hasil klasifikasi per kelas.
     * <p>
     * Ini yang membuat lapisan nama layak dipakai: tanpa cache, view biasa
     * menempuh 61 pemblokir × 2 panggilan virtual pada SETIAP addView.
     * Lihat {@link NameCache} untuk rinciannya.
     */
    private static final NameCache sCache = new NameCache();

    private NameBlocking() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    private static boolean matchBannerName(String className, String banner, String bannerPrefix)
    {
        if (banner != null && banner.equals(className))
        {
            return true;
        }
        return bannerPrefix != null && className.startsWith(bannerPrefix);
    }

    /**
     * Klasifikasi nama kelas <em>tanpa</em> efek samping.
     * <p>
     * Versi sebelumnya mencatat statistik di dalam {@link #isAdView}, sehingga
     * satu banner yang lewat {@code inflate} lalu {@code addView} terhitung
     * dua kali. Pencatatan sekarang dilakukan pemanggil, sekali, setelah
     * {@link ViewBlocking} menyatakan view itu memang baru.
     *
     * @return nama jaringan iklan, atau {@code null} bila view bukan iklan.
     */
    public static String classifyOnly(String className)
    {
        if (className == null)
        {
            return null;
        }

        for (String ignored : IGNORED_PREFIXES)
        {
            if (className.startsWith(ignored))
            {
                return null;
            }
        }

        // Kasus khusus: SDK lama Google. Pemeriksaan ini HARUS melewati IMA
        // lebih dulu, karena prefix-nya berada di dalam "com.google.ads" dan
        // tanpa pengecualian ini setiap kelas iklan video IMA dilaporkan
        // sebagai AdMob.
        if (className.startsWith("com.google.ads.")
                && !className.startsWith("com.google.ads.interactivemedia"))
        {
            return "GoogleAdmob";
        }

        // Prefix tambahan dari pengguna.
        for (String prefix : BlockPrefs.get().getUserPrefixes())
        {
            if (className.startsWith(prefix))
            {
                return "Custom";
            }
        }

        for (Blocker blocker : Main.blockers)
        {
            // Prefix menangkap kelas yang di-obfuscate selama awalan paket tetap.
            if (matchBannerName(className, blocker.getBanner(), blocker.getBannerPrefix()))
            {
                return blocker.getName();
            }
        }

        if (BlockPrefs.get().isHeuristicEnabled())
        {
            String simple = AdNameMatcher.simpleName(className);
            if (AdNameMatcher.matchesAnySuffix(simple, HEURISTIC_SUFFIXES))
            {
                return "Heuristic";
            }
        }

        return null;
    }

    /**
     * Seperti {@link #classifyOnly} tetapi memakai cache per kelas.
     *
     * @return nama jaringan, string kosong bila sudah diperiksa dan bukan
     *         iklan, atau {@code null} bila belum diketahui
     */
    private static String classifyCached(Class<?> type)
    {
        String cached = sCache.lookup(type);
        if (cached != null)
        {
            return cached;
        }
        String network = classifyOnly(type.getName());
        sCache.put(type, network);
        return network;
    }

    /**
     * Memeriksa apakah view ini container iklan, <em>tanpa</em> mengubah
     * statistik.
     */
    public static boolean isAdView(View view)
    {
        if (view == null)
        {
            return false;
        }
        String network = classifyCached(view.getClass());
        return network != null && !network.isEmpty();
    }

    /**
     * Memeriksa dan mencatat bila view ini iklan.
     * <p>
     * Pencatatan hanya terjadi di sini, dan pemanggil memanggilnya sekali per
     * view berkat {@link ViewBlocking#markSeen}.
     */
    public static boolean isAdView(String pkgName, View view)
    {
        if (view == null)
        {
            return false;
        }
        String network = classifyCached(view.getClass());
        if (network == null || network.isEmpty())
        {
            return false;
        }
        if (ViewBlocking.markSeen(view))
        {
            Util.countBlockedAd(view.getContext(), pkgName, network);
        }
        return true;
    }

    public static void clearAdViewInLayout(String packageName, View view)
    {
        clearAdViewInLayout(packageName, view, 0);
    }

    /**
     * @param depth kedalaman rekursi, dibatasi agar pohon view yang sangat
     *              dalam tidak menghabiskan waktu pada setiap inflate.
     */
    private static void clearAdViewInLayout(String packageName, View view, int depth)
    {
        if (view == null || depth > MAX_DEPTH)
        {
            return;
        }

        // isAdView(String, View) hanya mencatat bila view belum pernah dilihat,
        // jadi penelusuran berulang pada pohon yang sama tidak menggelembungkan
        // statistik.
        if (isAdView(packageName, view))
        {
            ViewBlocking.removeAdView(packageName, view);
            Util.log(packageName, "clearAdViewInLayout: " + view.getClass().getName());
            // Subtree iklan dibuang seluruhnya; tidak perlu ditelusuri lagi.
            return;
        }

        if (view instanceof ViewGroup)
        {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
            {
                clearAdViewInLayout(packageName, group.getChildAt(i), depth + 1);
            }
        }
    }

    public static void nameBasedBlocking(final String pkgName, final XC_LoadPackage.LoadPackageParam lpparam)
    {
        Util.hookAllMethods("android.view.ViewGroup", lpparam.classLoader, "addView", new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                if (param.args.length == 0 || !(param.args[0] instanceof View))
                {
                    return;
                }
                View view = (View) param.args[0];
                if (isAdView(pkgName, view))
                {
                    Util.log(pkgName, "addView: " + view.getClass().getName());
                    // Jangan tambahkan view iklan sama sekali, sehingga parent
                    // tidak perlu menata ulang tata letak untuknya.
                    //
                    // setResult(null) dipakai apa pun status parent-nya:
                    // ViewGroup.addView menambahkan view ke parent-nya sendiri,
                    // jadi view yang masih punya parent lain akan tetap
                    // ditambahkan ke parent yang sedang diproses — iklan tetap
                    // muncul. Memanggil removeAdView() di sini juga memicu
                    // requestLayout saat addView sedang berjalan, yang pada
                    // beberapa aplikasi berujung layout berulang tanpa henti.
                    param.setResult(null);
                }
            }
        });

        Util.hookAllMethods("android.app.Activity", lpparam.classLoader, "setContentView", new XC_MethodHook()
        {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
            {
                Activity activity = (Activity) param.thisObject;
                if (activity == null || activity.getWindow() == null)
                {
                    return;
                }
                View decor = activity.getWindow().getDecorView();
                View root = decor.findViewById(android.R.id.content);
                checkTreeOnce(pkgName, root != null ? root : decor);
            }
        });

        Util.hookAllMethods("android.view.LayoutInflater", lpparam.classLoader, "inflate", new XC_MethodHook()
        {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
            {
                // Semua overload mengembalikan root view (atau root yang
                // diberikan bila attachToRoot true), jadi selalu objek View.
                Object result = param.getResult();
                if (result instanceof View)
                {
                    // addView sudah memeriksa setiap view yang dipasang; di
                    // sini cukup view root-nya. Menelusuri seluruh subtree
                    // pada setiap inflate membuat daftar panjang seperti
                    // RecyclerView memicu ribuan pemeriksaan per frame —
                    // terasa sebagai UI yang berputar dan boros CPU.
                    View root = (View) result;
                    if (isAdView(pkgName, root))
                    {
                        ViewBlocking.removeAdView(pkgName, root);
                    }
                }
            }
        });
    }

    /**
     * Menelusuri pohon view satu kali per root, hanya setelah
     * {@code setContentView}.
     * <p>
     * Sebelumnya setiap {@code inflate} menelusuri seluruh subtree hingga 64
     * tingkat. Pada layar dengan banyak item itu menjadi pekerjaan berulang
     * untuk pohon yang sama, dan {@code removeAdView} yang dipanggil di
     * tengahnya memicu {@code requestLayout} lagi — sumber putaran yang
     * dilaporkan pada alur iklan reward.
     */
    private static void checkTreeOnce(String packageName, View root)
    {
        if (root == null)
        {
            return;
        }
        synchronized (sScannedTrees)
        {
            if (sScannedTrees.put(root, Boolean.TRUE) != null)
            {
                return;
            }
        }
        clearAdViewInLayout(packageName, root, 0);
    }

    /** Root yang sudah ditelusuri; WeakHashMap agar tidak menahan view. */
    private static final java.util.Map<View, Boolean> sScannedTrees =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<View, Boolean>());

    /** Membersihkan penanda saat hook dipasang untuk proses baru. */
    public static void reset()
    {
        sScannedTrees.clear();
        sCache.invalidate();
    }

    /**
     * Membuang cache klasifikasi karena preferensi berubah.
     * <p>
     * Prefix tambahan pengguna dan sakelar heuristik ikut memengaruhi hasil,
     * jadi cache harus kosong. Dipanggil dari dialog pengaturan setelah nilai
     * disimpan.
     */
    public static void invalidateCache()
    {
        sCache.invalidate();
    }

    /** Jumlah kelas yang sudah diklasifikasikan; dipakai tes dan diagnostik. */
    public static int cachedClassCount()
    {
        return sCache.size();
    }

    /** Nama jaringan untuk nama kelas apa pun, memakai cache. */
    public static String networkForClass(Class<?> type)
    {
        String value = classifyCached(type);
        return value == null || value.isEmpty() ? null : value;
    }

    /** Membuang satu kelas dari cache; dipakai tes. */
    static void evict(Class<?> type)
    {
        if (type != null)
        {
            sCache.put(type, null);
        }
    }

    /** Daftar akhiran heuristik; dipakai tes agar aset dan kode tidak menyimpang. */
    static List<String> heuristicSuffixes()
    {
        return java.util.Arrays.asList(HEURISTIC_SUFFIXES);
    }
}
