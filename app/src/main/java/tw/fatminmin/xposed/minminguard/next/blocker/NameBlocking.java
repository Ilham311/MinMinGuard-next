package tw.fatminmin.xposed.minminguard.next.blocker;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.Main;

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
    };

    /** Prefix yang bukan iklan meskipun mengandung kata "ad". */
    private static final String[] IGNORED_PREFIXES = {
            "android.", "androidx.", "com.android.", "java.", "kotlin.",
            "com.google.android.material.",
    };

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
     * @return nama jaringan iklan, atau {@code null} bila view bukan iklan.
     */
    public static String classify(String className)
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

        // Kasus khusus: SDK lama Google.
        if (className.startsWith("com.google.ads."))
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
            String simple = simpleName(className);
            for (String suffix : HEURISTIC_SUFFIXES)
            {
                if (endsWithWord(simple, suffix))
                {
                    return "Heuristic";
                }
            }
        }

        return null;
    }

    /**
     * Pencocokan akhiran pada batas kata.
     * <p>
     * Tanpa ini {@code HeadView} ikut cocok dengan akhiran {@code AdView}
     * (huruf "ad" di tengah kata) dan view aplikasi biasa ikut dihapus.
     */
    private static boolean endsWithWord(String simple, String suffix)
    {
        if (simple.equals(suffix))
        {
            return true;
        }
        if (!simple.endsWith(suffix) || simple.length() <= suffix.length())
        {
            return false;
        }
        char before = simple.charAt(simple.length() - suffix.length() - 1);
        return Character.isUpperCase(before) || Character.isDigit(before);
    }

    private static String simpleName(String className)
    {
        int dot = className.lastIndexOf('.');
        String simple = dot >= 0 ? className.substring(dot + 1) : className;
        int dollar = simple.lastIndexOf('$');
        return dollar >= 0 ? simple.substring(dollar + 1) : simple;
    }

    public static boolean isAdView(String pkgName, View view)
    {
        if (view == null)
        {
            return false;
        }

        String network = classify(view.getClass().getName());
        if (network == null)
        {
            return false;
        }

        Util.notifyAdNetwork(view.getContext(), pkgName, network);
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
                    ViewBlocking.removeAdView(pkgName, view);
                    // Jangan tambahkan view iklan sama sekali, sehingga parent
                    // tidak perlu menata ulang tata letak untuknya.
                    if (view.getParent() == null)
                    {
                        param.setResult(null);
                    }
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
                clearAdViewInLayout(pkgName, root != null ? root : decor);
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
                    // Hanya root hasil inflate yang diperiksa: menelusuri
                    // seluruh subview di setiap inflate membuat startup lambat.
                    clearAdViewInLayout(pkgName, (View) result, 0);
                }
            }
        });
    }
}
