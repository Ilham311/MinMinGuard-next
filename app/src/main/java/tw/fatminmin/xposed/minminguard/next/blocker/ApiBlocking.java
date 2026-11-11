package tw.fatminmin.xposed.minminguard.next.blocker;

import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
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
                    Util.countBlockedAd(null, packageName, blockerName);
                }
                else if (Util.countsAsBlock(bannerFunc))
                {
                    // setAdListener ikut di-hook pada sebagian SDK; itu bukan
                    // pemuatan iklan dan tidak boleh menambah hitungan.
                    Util.notifyRemoveAdView(null, packageName, 1);
                    Util.countBlockedAd(null, packageName, blockerName);
                }

                // Banner juga punya callback: aplikasi menunggu
                // onAdFailedToLoad setelah loadAd(). Tanpa kabar ini, banner
                // yang diblokir membuat layar yang bergantung padanya berputar.
                notifyBlocked(param, bannerClass, bannerFunc);
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

                // Sebelumnya setiap panggilan dihitung, termasuk pertanyaan
                // status seperti isReady()/canShow() dan setter listener.
                // Aplikasi alur reward menanyakan kesiapan berulang kali, jadi
                // angkanya membengkak tanpa satu iklan pun dicegah.
                if (!Util.countsAsBlock(adFunc))
                {
                    return;
                }

                Util.notifyRemoveAdView(null, packageName, 1);
                Util.countBlockedAd(null, packageName, blockerName);
                notifyBlocked(param, adClass, adFunc);
            }
        });
    }

    /**
     * Memblokir method pemuatan iklan <em>sekaligus</em> memberi tahu callback
     * yang dikirim aplikasi.
     * <p>
     * Ini perbaikan inti untuk iklan reward. Tanpa notifikasi, aplikasi
     * menunggu {@code onAdLoaded}/{@code onAdFailedToLoad} yang tidak pernah
     * datang dan layar loading-nya berputar selamanya. Callback dikirim lewat
     * {@link AdCallbacks} agar bentuknya tidak perlu diketahui di sini.
     */
    public static boolean blockAdLoad(final String packageName, final String adClass, final String adFunc,
                                      final XC_LoadPackage.LoadPackageParam lpparam)
    {
        return blockAdLoad(packageName, adClass, adFunc, lpparam, false);
    }

    /**
     * Seperti {@link #blockAdLoad}, dengan opsi menelusuri field objek iklan
     * untuk mencari listener.
     * <p>
     * Dipakai Facebook, satu-satunya SDK di sini yang memasang listener lewat
     * builder yang seluruh overload-nya abstrak sehingga tidak ada setter yang
     * bisa di-hook. Untuk SDK lain opsi ini HARUS {@code false}: menelusuri
     * field internal SDK akan menemukan listener milik SDK itu sendiri (bukan
     * milik aplikasi) dan memanggil callback gagal pada objek internal justru
     * memicu SDK mencoba ulang — persis putaran yang sedang diperbaiki.
     */
    public static boolean blockAdLoad(final String packageName, final String adClass, final String adFunc,
                                      final XC_LoadPackage.LoadPackageParam lpparam,
                                      final boolean scanFieldsForListener)
    {
        final String blockerName = blockerNameOf(adClass);

        return hookAll(packageName, adClass, adFunc, lpparam, new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                Util.log(packageName, "blockAdLoad: " + adClass + "#" + adFunc);

                Util.setDefaultResult(param);

                Util.notifyRemoveAdView(null, packageName, 1);
                Util.countBlockedAd(null, packageName, blockerName);

                // Setelah hasil method dihentikan, beri tahu callback supaya
                // aplikasi tidak menunggu selamanya. Jalur tampil memakai
                // callback tutup, bukan callback gagal-muat.
                notifyBlocked(param, adClass, adFunc, scanFieldsForListener);
            }
        });
    }

    /**
     * Memberi tahu callback milik aplikasi bahwa pemuatan/penayangan iklan
     * tidak akan terjadi.
     * <p>
     * Ini perbaikan inti layar "muter-muter". Method yang diblokir tanpa kabar
     * membuat aplikasi menunggu callback yang tidak akan pernah datang. Bentuk
     * callback berbeda menurut jalur:
     * <ul>
     *   <li>jalur muat → {@code onAdFailedToLoad}/{@code onAdLoadFailed};</li>
     *   <li>jalur tampil → {@code onAdClosed}/{@code onAdDismissed}, karena
     *       kegagalan tampil pada banyak SDK justru memicu percobaan ulang.
     *       Aplikasi alur reward menunggu layar kembali sebelum memberi hadiah,
     *       jadi tanpa callback ini spinner berputar terus.</li>
     * </ul>
     * {@link AdCallbacks} yang memilih nama method yang cocok per SDK.
     *
     * @param scanFields ikut menelusuri field objek iklan; hanya untuk SDK yang
     *                   tidak menyediakan setter yang bisa di-hook (Facebook)
     */
    private static void notifyBlocked(XC_MethodHook.MethodHookParam param, String adClass, String adFunc)
    {
        notifyBlocked(param, adClass, adFunc, false);
    }

    private static void notifyBlocked(XC_MethodHook.MethodHookParam param, String adClass, String adFunc,
                                      boolean scanFields)
    {
        String adUnitId = adUnitIdOf(param);

        if (Util.isLoadPath(adFunc))
        {
            AdCallbacks.notifyFromArgs(param, adUnitId);
            AdCallbacks.notifyCaptured(param.thisObject, adClass, adUnitId);
            if (scanFields)
            {
                AdCallbacks.notifyInFields(param.thisObject, adUnitId, false);
            }
        }
        else if (Util.isShowPath(adFunc))
        {
            AdCallbacks.notifyDismissedFromArgs(param, adUnitId);
            AdCallbacks.notifyDismissed(param.thisObject, adClass, adUnitId);
            if (scanFields)
            {
                AdCallbacks.notifyInFields(param.thisObject, adUnitId, true);
            }
        }
    }

    /**
     * Nama setter listener yang dikenal.
     * <p>
     * Daftar ini mencakup setter objek ({@code setAdListener},
     * {@code setListener}) dan setter bernama khusus per SDK
     * ({@code setInterstitialListener} pada Fyber/Mintegral,
     * {@code setRewardedListener}, {@code setLoadFailedListener} pada
     * TradPlus). Tanpa nama khusus itu, listener tidak pernah tertangkap pada
     * SDK yang tidak memakai nama generik — dan pemuatan yang diblokir membuat
     * aplikasi menunggu selamanya.
     */
    private static final String[] LISTENER_SETTERS = {
            "setAdListener",
            "setListener",
            // Jalur callback tutup layar penuh. Pada AdMob setter ini abstrak
            // di kelas publiknya (implementasinya di kelas internal GMS yang
            // namanya berubah tiap versi), jadi di sana hook-nya tidak terpasang
            // — aman karena load() sudah diblokir sehingga show() tidak punya
            // iklan untuk ditampilkan. Pada SDK yang menyediakan setter konkret
            // (sebagian mediator) nama ini ikut tertangkap.
            "setFullScreenContentCallback",
            "setRewardAdExListener",
            "setInterstitialListener",
            "setRewardedListener",
            "setRewardListener",
            "setNativeAdListener",
            "setSplashListener",
            "setRewardVideoListener",
            "setInterstitialVideoListener",
            "setBannerAdListener",
            // LevelPlay pada banner memakai nama ini, bukan setListener.
            "setBannerListener",
            "setLoadFailedListener",
            "setAllAdLoadListener",
            "setAdInteractionListener",
            "setAdInteractionCallback",
            "setISDemandOnlyInterstitialListener",
            "setISDemandOnlyRewardedVideoListener",
    };

    public static boolean captureAdListener(final String packageName, final String adClass,
                                            final XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean hooked = false;
        java.util.Set<String> present = methodNamesOf(packageName, adClass, lpparam);

        for (final String setter : LISTENER_SETTERS)
        {
            // Nama setter yang tidak ada di kelas ini dilewati tanpa menelusuri
            // hierarki: dari 14 nama di daftar, satu SDK biasanya hanya punya
            // dua atau tiga.
            if (!present.contains(setter))
            {
                continue;
            }
            hooked |= hookAll(packageName, adClass, setter, lpparam, new XC_MethodHook()
            {
                @Override
                protected void beforeHookedMethod(MethodHookParam param)
                {
                    for (Object arg : param.args)
                    {
                        if (arg == null)
                        {
                            continue;
                        }
                        // Setter bisa statis (IronSource demand-only) sehingga
                        // thisObject null; AdCallbacks menyimpannya per kelas.
                        AdCallbacks.captureListener(param.thisObject, adClass, arg);
                    }
                }
            });
        }

        // Setter bukan aksi iklan: jangan menambah statistik blokir.
        Util.log(packageName, "captureAdListener: " + adClass + " (" + blockerNameOf(adClass) + ")");
        return hooked;
    }

    /**
     * Nama method yang benar-benar ada di sebuah kelas SDK.
     *
     * @return daftar nama, atau daftar kosong bila kelas tidak ada
     */
    private static java.util.Set<String> methodNamesOf(String packageName, String className,
                                                       XC_LoadPackage.LoadPackageParam lpparam)
    {
        try
        {
            Class<?> clazz = Util.findClassOrNull(className, lpparam.classLoader);
            if (clazz == null)
            {
                return java.util.Collections.emptySet();
            }
            return Util.methodNamesInHierarchy(clazz);
        }
        catch (Throwable t)
        {
            Util.log(packageName, "methodNamesOf " + className + " gagal: " + t);
            return java.util.Collections.emptySet();
        }
    }

    /**
     * Setter gaya builder, mis. {@code AdLoader.Builder.withAdListener} dan
     * {@code forNativeAd}.
     * <p>
     * Pola builder tidak memakai nama setter biasa, dan listener-nya dipasang
     * pada builder sementara pemuatan terjadi pada objek hasil {@code build()}.
     * Karena itu listener disimpan atas nama kelas pemuat, bukan kelas builder.
     */
    private static final String[] BUILDER_SETTERS = {
            "withAdListener",
            "forNativeAd",
            "forCustomFormatAd",
            "forAdManagerAdView",
            "setAdListener",
            "setListener",
    };

    /**
     * Menangkap listener yang dipasang lewat builder.
     *
     * @param builderClass  kelas builder tempat listener dipasang
     * @param adClass       kelas objek iklan yang nanti memuat (kunci simpan)
     */
    public static boolean captureAdListenerFor(final String packageName, final String builderClass,
                                               final String adClass,
                                               final XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean hooked = false;

        for (final String setter : BUILDER_SETTERS)
        {
            hooked |= hookAll(packageName, builderClass, setter, lpparam, new XC_MethodHook()
            {
                @Override
                protected void beforeHookedMethod(MethodHookParam param)
                {
                    for (Object arg : param.args)
                    {
                        if (arg != null)
                        {
                            AdCallbacks.captureListener(null, adClass, arg);
                        }
                    }
                }
            });
        }
        return hooked;
    }

    /**
     * ID unit iklan dari argumen method, bila ada.
     * <p>
     * Dipakai mengisi parameter {@code String} pada callback kegagalan supaya
     * aplikasi mengenali unit mana yang gagal.
     */
    private static String adUnitIdOf(XC_MethodHook.MethodHookParam param)
    {
        if (param == null || param.args == null)
        {
            return null;
        }
        for (Object arg : param.args)
        {
            if (arg instanceof String)
            {
                String value = (String) arg;
                if (value.length() > 0)
                {
                    return value;
                }
            }
        }
        return null;
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
            // Saring sebelum memasang: nama method yang tidak ada di kelas ini
            // maupun kelas dasarnya tidak perlu ditelusuri. Modul mencoba
            // puluhan SDK pada setiap proses aplikasi, dan sebagian besar tidak
            // dipakai aplikasi itu.
            if (!Util.methodNamesInHierarchy(clazz).contains(method))
            {
                return false;
            }
            // Lewat Util: helper itu melewati method abstrak alih-alih
            // membatalkan seluruh pemasangan. RewardedAd/InterstitialAd/
            // AppOpenAd mendeklarasikan show() sebagai abstrak, dan
            // XposedBridge.hookAllMethods melempar untuk kelas seperti itu.
            return Util.hookAllMethods(className, lpparam.classLoader, method, callback);
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

                if (!Util.countsAsBlock(method))
                {
                    return;
                }

                Util.notifyRemoveAdView(null, packageName, 1);
                Util.countBlockedAd(null, packageName, blockerName);
                notifyBlocked(param, className, method);
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
