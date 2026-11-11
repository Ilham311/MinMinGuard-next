package tw.fatminmin.xposed.minminguard.next.blocker;

import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.Main;

/**
 * Pemblokiran berbasis API: memasang hook ke method milik SDK iklan sehingga
 * iklan tidak pernah dimuat, apa pun UI-nya.
 * <p>
 * Semua primitive di sini mengembalikan {@code false} bila kelas/method tidak
 * ada (SDK beda versi atau di-obfuscate) alih-alih melaporkan error.
 */
public final class ApiBlocking
{
    private ApiBlocking() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    public static void handle(final String packageName, final XC_LoadPackage.LoadPackageParam lpparam)
    {
        for (Blocker blocker : Main.blockers)
        {
            try
            {
                if (blocker.handleLoadPackage(packageName, lpparam))
                {
                    Util.notifyAdNetwork(null, packageName, blocker.getName());
                }
            }
            catch (Throwable t)
            {
                Util.log(packageName, blocker.getName() + ": " + t);
            }
        }
    }

    /**
     * Menghapus banner dan mencegahnya dimuat kembali.
     *
     * @param bannerFunc nama method yang dipanggil SDK saat memasang iklan
     */
    public static boolean removeBanner(final String packageName, final String bannerClass,
                                       final String bannerFunc, final XC_LoadPackage.LoadPackageParam lpparam)
    {
        return removeBannerWithResult(packageName, bannerClass, bannerFunc, null, lpparam);
    }

    public static boolean removeBannerWithResult(final String packageName, final String bannerClass,
                                                 final String bannerFunc, final Object result,
                                                 final XC_LoadPackage.LoadPackageParam lpparam)
    {
        final String blockerName = blockerNameOf(bannerClass);

        return hookAll(packageName, bannerClass, bannerFunc, lpparam, new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                Util.log(packageName, "removeBanner: " + bannerClass + "#" + bannerFunc);

                if (result == null)
                {
                    Util.setDefaultResult(param);
                }
                else
                {
                    Util.setResultCompat(param, result);
                }

                if (param.thisObject instanceof View)
                {
                    ViewBlocking.removeAdView(packageName, (View) param.thisObject);
                    Util.notifyAdNetwork(null, packageName, blockerName);
                }
                else
                {
                    Util.notifyRemoveAdView(null, packageName, 1);
                    Util.notifyAdNetwork(null, packageName, blockerName);
                }
            }
        });
    }

    /** Memblokir method yang memuat iklan; seluruh overload ikut di-hook. */
    public static boolean blockAdFunction(final String packageName, final String adClass,
                                          final String adFunc, final XC_LoadPackage.LoadPackageParam lpparam)
    {
        return blockAdFunctionWithResult(packageName, adClass, adFunc, null, lpparam);
    }

    public static boolean blockAdFunctionExact(final String packageName, final String adClass, final String adFunc,
                                               final Object parameter, final XC_LoadPackage.LoadPackageParam lpparam)
    {
        return hookExact(packageName, adClass, adFunc, null, lpparam, parameter);
    }

    public static boolean blockAdFunctionExact(final String packageName, final String adClass, final String adFunc,
                                               final Object parameter1, final Object parameter2,
                                               final XC_LoadPackage.LoadPackageParam lpparam)
    {
        return hookExact(packageName, adClass, adFunc, null, lpparam, parameter1, parameter2);
    }

    public static boolean blockAdFunctionExact(final String packageName, final String adClass, final String adFunc,
                                               final Object parameter1, final Object parameter2, final Object parameter3,
                                               final XC_LoadPackage.LoadPackageParam lpparam)
    {
        return hookExact(packageName, adClass, adFunc, null, lpparam, parameter1, parameter2, parameter3);
    }

    public static boolean blockAdFunctionWithResult(final String packageName, final String adClass, final String adFunc,
                                                    final Object result, final XC_LoadPackage.LoadPackageParam lpparam)
    {
        final String blockerName = blockerNameOf(adClass);

        return hookAll(packageName, adClass, adFunc, lpparam, new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                Util.log(packageName, "blockAdFunction: " + adClass + "#" + adFunc);

                if (result == null)
                {
                    Util.setDefaultResult(param);
                }
                else
                {
                    Util.setResultCompat(param, result);
                }

                Util.notifyRemoveAdView(null, packageName, 1);
                Util.notifyAdNetwork(null, packageName, blockerName);
            }
        });
    }

    public static boolean blockAdFunctionWithResultExact(final String packageName, final String adClass, final String adFunc,
                                                         final Object parameter, final Object result,
                                                         final XC_LoadPackage.LoadPackageParam lpparam)
    {
        return hookExact(packageName, adClass, adFunc, result, lpparam, parameter);
    }

    public static boolean blockAdFunctionWithResultExact(final String packageName, final String adClass, final String adFunc,
                                                         final Object parameter1, final Object parameter2, final Object result,
                                                         final XC_LoadPackage.LoadPackageParam lpparam)
    {
        return hookExact(packageName, adClass, adFunc, result, lpparam, parameter1, parameter2);
    }

    private static boolean hookAll(String packageName, String className, String method,
                                   XC_LoadPackage.LoadPackageParam lpparam, XC_MethodHook callback)
    {
        try
        {
            Class<?> clazz = Util.findClassOrNull(className, lpparam.classLoader);
            if (clazz == null)
            {
                return false;
            }
            XposedBridge.hookAllMethods(clazz, method, callback);
            return true;
        }
        catch (Throwable t)
        {
            Util.log(packageName, "hook " + className + "#" + method + " gagal: " + t);
            return false;
        }
    }

    private static boolean hookExact(String packageName, String className, String method, final Object result,
                                     XC_LoadPackage.LoadPackageParam lpparam, Object... parameters)
    {
        final String blockerName = blockerNameOf(className);
        final Object[] args = new Object[parameters.length + 1];
        System.arraycopy(parameters, 0, args, 0, parameters.length);
        args[parameters.length] = new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                if (result == null)
                {
                    Util.setDefaultResult(param);
                }
                else
                {
                    Util.setResultCompat(param, result);
                }

                Util.notifyRemoveAdView(null, packageName, 1);
                Util.notifyAdNetwork(null, packageName, blockerName);
            }
        };

        try
        {
            // Cek kelas dulu: findAndHookMethod melempar NoSuchMethodError
            // untuk setiap overload yang tidak ada, dan itu terjadi pada
            // puluhan SDK yang tidak dipakai aplikasi.
            if (Util.findClassOrNull(className, lpparam.classLoader) == null)
            {
                return false;
            }
            de.robv.android.xposed.XposedHelpers.findAndHookMethod(className, lpparam.classLoader, method, args);
            return true;
        }
        catch (Throwable t)
        {
            Util.log(packageName, "hookExact " + className + "#" + method + " gagal: " + t);
            return false;
        }
    }

    /**
     * Nama jaringan dari nama kelas SDK. Dipakai sebagai label statistik bila
     * pemanggil tidak menyediakan nama eksplisit.
     */
    private static String blockerNameOf(String className)
    {
        for (Blocker blocker : Main.blockers)
        {
            String banner = blocker.getBanner();
            String prefix = blocker.getBannerPrefix();
            String name = blocker.getName();

            if (banner != null && className.equals(banner))
            {
                return name;
            }
            if (prefix != null && className.startsWith(prefix))
            {
                return name;
            }
        }
        // Kelas khusus per aplikasi tidak terdaftar di array blockers.
        return "Custom";
    }
}
