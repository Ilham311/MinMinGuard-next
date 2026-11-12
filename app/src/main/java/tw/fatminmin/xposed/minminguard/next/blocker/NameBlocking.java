package tw.fatminmin.xposed.minminguard.next.blocker;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.Main;
import tw.fatminmin.xposed.minminguard.next.core.name.AdClassification;
import tw.fatminmin.xposed.minminguard.next.core.name.AdNameMatcher;
import tw.fatminmin.xposed.minminguard.next.core.name.FirstPartyApps;
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

    /**
     * Nama jaringan untuk hasil yang hanya berasal dari heuristik.
     * <p>
     * Bedanya penting: kelas SDK punya bukti (ada di daftar pemblokir yang
     * memang khusus iklan), sedangkan hasil heuristik hanya tebakan dari nama.
     * Lihat {@link AdClassification} untuk akibatnya pada penanganan view.
     */
    static final String HEURISTIC_NETWORK = AdClassification.HEURISTIC;

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
                return HEURISTIC_NETWORK;
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
     * Klasifikasi lengkap untuk satu view: jaringan iklan, atau {@code null}
     * bila view ini bukan iklan atau milik aplikasi target sendiri.
     * <p>
     * Mengembalikan nama jaringan — bukan sekadar boolean — karena pemanggil
     * harus bisa membedakan dua tingkat keyakinan yang sangat berbeda:
     * <ul>
     *   <li>{@link #HEURISTIC_NETWORK}: tebakan dari nama kelas saja. Cocok
     *       untuk banner SDK yang namanya tidak di-obfuscate, tetapi juga
     *       cocok untuk komponen UI biasa aplikasi.</li>
     *   <li>Nama jaringan lain: kelas itu memang ada di daftar pemblokir SDK
     *       yang didukung modul.</li>
     * </ul>
     * Perbedaan ini menentukan boleh atau tidaknya view <em>dibuang</em> dari
     * pohon view (lihat {@link #nameBasedBlocking}).
     *
     * <p>
     * Pencatatan statistik dilakukan di sini, sekali per view, memakai
     * {@link ViewBlocking#markSeen}.
     */
    static String networkFor(String pkgName, View view)
    {
        if (view == null)
        {
            return null;
        }
        String network = classifyCached(view.getClass());
        if (network == null || network.isEmpty())
        {
            return null;
        }
        if (belongsToApp(view.getClass().getName(), pkgName))
        {
            return null;
        }
        if (ViewBlocking.markSeen(view))
        {
            Util.countBlockedAd(view.getContext(), pkgName, network);
        }
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
     * Seperti {@link #isAdView(View)} tetapi mengecualikan kelas milik aplikasi
     * target sendiri.
     * <p>
     * Tanpa pengecualian ini, heuristik nama menghapus komponen UI biasa yang
     * kebetulan berakhiran {@code BannerView}. Kasus nyata yang ditemukan dari
     * bytecode Instagram 454: {@code OngoingMetaAiVoiceBannerView} — banner
     * panggilan Meta AI di DM, bukan iklan. Ia lolos sebagai "iklan" hanya
     * karena namanya, lalu dihapus dari pohon view.
     * <p>
     * Yang dikecualikan hanya kelas yang tinggal di dalam paket aplikasi itu
     * sendiri. Kelas SDK tetap berada di paket SDK dan tetap disaring, jadi
     * pemblokiran iklan tidak berkurang.
     *
     * @param pkgName paket aplikasi yang prosesnya sedang di-hook
     */
    public static boolean isAdView(String pkgName, View view)
    {
        return networkFor(pkgName, view) != null;
    }

    /**
     * True bila kelas milik namespace aplikasi target sendiri.
     * <p>
     * Perbandingan memakai batas titik, bukan {@code startsWith} mentah, agar
     * {@code com.instagram.androidx.Foo} tidak dianggap bagian dari
     * {@code com.instagram.android}.
     */
    static boolean belongsToApp(String className, String pkgName)
    {
        return FirstPartyApps.ownsClass(pkgName, className);
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

        // networkFor() hanya mencatat bila view belum pernah dilihat, jadi
        // penelusuran berulang pada pohon yang sama tidak menggelembungkan
        // statistik.
        String network = networkFor(packageName, view);
        if (network != null)
        {
            Util.log(packageName, "clearAdViewInLayout: " + view.getClass().getName());
            if (AdClassification.isHeuristic(network))
            {
                // Tebakan nama: susutkan saja, jangan lepas dari pohon. View
                // aplikasi yang kebetulan bernama mirip iklan masih dipakai
                // ulang lewat findViewById (lihat catatan di addView).
                ViewBlocking.shrinkAdView(view);
                // Subtree tetap ditelusuri: anak-anaknya bisa jadi iklan SDK
                // sungguhan yang layak dibuang.
            }
            else
            {
                ViewBlocking.removeAdView(packageName, view);
                // Subtree iklan SDK dibuang seluruhnya.
                return;
            }
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
                String network = networkFor(pkgName, view);
                if (network == null)
                {
                    return;
                }
                Util.log(pkgName, "addView: " + view.getClass().getName());

                if (AdClassification.isHeuristic(network))
                {
                    // Hasil heuristik hanya tebakan dari nama kelas. Membuang
                    // view seperti ini dari pohon terbukti mematikan aplikasi:
                    //
                    //   CapCut  com.vega.ui.banner.BannerView -> dihapus, lalu
                    //           BannerCardViewHolder.t0 melempar
                    //           "NullPointerException: must not be null".
                    //   MiChat  com.zenmen.palmchat.ui.widget.FeedBottomBannerView
                    //           dan com.michatapp.ad.view.MeTabBannerAdView ->
                    //           dihapus, lalu PhotoViewActivity.D1:139 dan
                    //           SettingsFragment:312 NPE pada findViewById.
                    //
                    // Keduanya memakai view-nya sendiri lewat findViewById;
                    // setResult(null) membuat pemanggil menerima null.
                    // Karena itu view dibiarkan terpasang lalu dikecilkan
                    // (tinggi 0 + GONE) — iklan tetap tidak terlihat, dan
                    // findViewById tetap mengembalikan objeknya.
                    ViewBlocking.shrinkAdView(view);
                    return;
                }

                // Kelas SDK yang memang ada di daftar pemblokir: aman dibuang
                // sama sekali, sehingga parent tidak perlu menata letak untuk
                // view yang tidak akan pernah tampil.
                //
                // setResult(null) dipakai apa pun status parent-nya:
                // ViewGroup.addView menambahkan view ke parent-nya sendiri, jadi
                // view yang masih punya parent lain akan tetap ditambahkan ke
                // parent yang sedang diproses — iklan tetap muncul. Memanggil
                // removeAdView() di sini juga memicu requestLayout saat addView
                // sedang berjalan, yang pada beberapa aplikasi berujung layout
                // berulang tanpa henti.
                param.setResult(null);
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
                    String network = networkFor(pkgName, root);
                    if (network != null)
                    {
                        if (AdClassification.isHeuristic(network))
                        {
                            // Hasil heuristik: susutkan, jangan lepas.
                            ViewBlocking.shrinkAdView(root);
                        }
                        else
                        {
                            ViewBlocking.removeAdView(pkgName, root);
                        }
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
