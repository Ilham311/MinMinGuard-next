package tw.fatminmin.xposed.minminguard.next;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import tw.fatminmin.xposed.minminguard.next.blocker.ActivityBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.BlockPrefs;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocklist;
import tw.fatminmin.xposed.minminguard.next.blocker.HostBlock;
import tw.fatminmin.xposed.minminguard.next.blocker.NameBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.StatsReporter;
import tw.fatminmin.xposed.minminguard.next.blocker.UrlFiltering;
import tw.fatminmin.xposed.minminguard.next.blocker.Util;
import tw.fatminmin.xposed.minminguard.next.blocker.ViewBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Ad2iction;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.AdMarvel;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Adbert;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Adcolony;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Adfurikun;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Adtech;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Amazon;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Amobee;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Aotter;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.AppBrain;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Applovin;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Appnext;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.AppodealMRAID;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Avocarrot;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Bonzai;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Chartboost;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Clickforce;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Domob;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Facebook;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Flurry;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Freewheel;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Fyber;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.GoogleAdmob;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.GoogleGms;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.GoogleGmsDoubleClick;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Hodo;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Inmobi;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Intowow;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Ironsource;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.KuAd;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Madvertise;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.MasAd;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.MdotM;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Millennial;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Mintegral;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.MoPub;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.MobFox;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Mobclix;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Nend;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Og;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Onelouder;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.OpenX;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Pangle;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Smaato;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.SmartAdserver;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.SourcekitMRAID;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Startapp;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.TWMads;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Tapfortap;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Tapjoy;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.TencentGDT;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.TopOn;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.UnityAds;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Vpadn;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Vpon;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Vungle;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Waystorm;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.Yandex;
import tw.fatminmin.xposed.minminguard.next.blocker.adnetwork.mAdserve;
import tw.fatminmin.xposed.minminguard.next.blocker.custom_mod.NextMedia;
import tw.fatminmin.xposed.minminguard.next.blocker.custom_mod.OneWeather;
import tw.fatminmin.xposed.minminguard.next.blocker.custom_mod.PeriodCalendar;
import tw.fatminmin.xposed.minminguard.next.blocker.custom_mod.Viafree;
import tw.fatminmin.xposed.minminguard.next.blocker.custom_mod._2chMate;

/**
 * Titik masuk modul. Dimuat oleh framework Xposed melalui
 * {@code assets/xposed_init}.
 */
public class Main implements IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookInitPackageResources
{
    private static final String MY_PACKAGE_NAME = Main.class.getPackage().getName();
    private static final String QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES";

    private static String MODULE_PATH;
    public static Integer xposedVersionCode;

    /** Proses yang sudah dipasangi hook, agar tidak dobel bila onCreate berulang. */
    private static final Set<String> sHookedProcesses = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    public static Blocker[] blockers = {
            /* Popular adnetwork */
            new Ad2iction(), new Adbert(), new Adcolony(), new Adfurikun(), new AdMarvel(), new AppodealMRAID(), new GoogleAdmob(), new GoogleGms(), new Adtech(), new Amazon(), new Amobee(),
            new Aotter(), new AppBrain(), new Applovin(), new Appnext(), new Avocarrot(), new Bonzai(), new Chartboost(), new Clickforce(), new Domob(), new Facebook(),
            new Freewheel(), new Flurry(), new Fyber(), new GoogleGmsDoubleClick(), new Hodo(), new Inmobi(), new Intowow(), new Ironsource(), new KuAd(), new mAdserve(), new Madvertise(),
            new MasAd(), new MdotM(), new Millennial(), new Mobclix(), new MobFox(), new MoPub(), new Nend(), new Og(), new Onelouder(), new OpenX(), new SmartAdserver(), new SourcekitMRAID(),
            new Startapp(), new Tapfortap(), new TWMads(), new UnityAds(), new Vpadn(), new Vpon(), new Vungle(), new Waystorm(), new Yandex(),
            /* Jaringan modern: API-based bila signature dikenali, selalu prefix-based */
            new Pangle(), new Mintegral(), new Tapjoy(), new TencentGDT(), new Smaato(), new TopOn(),
    };

    @SuppressWarnings("deprecation")
    @Override
    public void initZygote(StartupParam startupParam) throws Throwable
    {
        try
        {
            xposedVersionCode = XposedBridge.getXposedVersion();
        }
        catch (Throwable t)
        {
            xposedVersionCode = XposedBridge.XPOSED_BRIDGE_VERSION;
        }

        MODULE_PATH = startupParam.modulePath;

        // Resource dibaca langsung dari APK modul: XModuleResources tidak bisa
        // dipakai dari zygote pada Xposed versi baru.
        UnpackResources();

        BlockPrefs prefs = BlockPrefs.get();
        Util.DEBUG = prefs.isVerbose();

        Util.warn("loaded " + Blocklist.get().size() + " pola dari " + MODULE_PATH);
    }

    private void UnpackResources()
    {
        addPatternsFromAsset("host/output_file");
        addPatternsFromAsset("host/mmg_pattern");
    }

    private void addPatternsFromAsset(String assetName)
    {
        try
        {
            ZipFile zip = new ZipFile(new File(MODULE_PATH));
            try
            {
                ZipEntry entry = zip.getEntry("assets/" + assetName);
                if (entry == null)
                {
                    Util.warn("aset tidak ditemukan: " + assetName);
                    return;
                }

                InputStream in = zip.getInputStream(entry);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1)
                {
                    out.write(buffer, 0, read);
                }
                in.close();

                String[] lines = new String(out.toByteArray(), "UTF-8").split("\n");
                for (String line : lines)
                {
                    if ("host/mmg_pattern".equals(assetName))
                    {
                        Blocklist.get().addResourcePattern(line);
                    }
                    else
                    {
                        Blocklist.get().addLine(line);
                    }
                }

                Util.warn("memuat " + lines.length + " baris dari " + assetName);
            }
            finally
            {
                zip.close();
            }
        }
        catch (Throwable t)
        {
            Util.warn("gagal membaca " + assetName + ": " + t);
        }
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam)
    {
        if (lpparam.packageName.equals(MY_PACKAGE_NAME))
        {
            XposedHelpers.findAndHookMethod("tw.fatminmin.xposed.minminguard.next.blocker.Util",
                    lpparam.classLoader, "xposedEnabled", XC_MethodReplacement.returnConstant(true));
            if (xposedVersionCode != null && xposedVersionCode >= 93)
            {
                XposedHelpers.findAndHookMethod("tw.fatminmin.xposed.minminguard.next.Common",
                        lpparam.classLoader, "getPrefMode", XC_MethodReplacement.returnConstant(Context.MODE_WORLD_READABLE));
            }
        }

        /*
         * Sejak Android 11 aplikasi target tidak bisa melihat ContentProvider
         * modul (content://tw.fatminmin.xposed.minminguard.next) sehingga
         * statistik tidak bisa dilaporkan. Solusi resmi adalah menambahkan
         * <queries> atau izin QUERY_ALL_PACKAGES di manifest aplikasi target.
         * Karena aplikasi target tidak bisa diubah, izin itu disuntikkan saat
         * framework memulihkan status izin paket.
         *
         * Syarat: proses "android" harus ada dalam scope LSPosed dan perangkat
         * perlu reboot setelah mengaktifkan sebuah aplikasi.
         */
        if (Build.VERSION.SDK_INT > 29 && lpparam.packageName.equals("android"))
        {
            // Android 14 memindahkan kelas ini; coba kedua nama.
            hookRestorePermissionState(lpparam, "com.android.server.pm.permission.PermissionManagerServiceImpl");
            hookRestorePermissionState(lpparam, "com.android.server.pm.permission.PermissionManagerService");
        }

        /*
         * Tunggu sampai Application.onCreate supaya AndroidAppHelper
         * mengembalikan Context yang valid (dibutuhkan ContentResolver).
         */
        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate", new XC_MethodHook()
        {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
            {
                onApplicationCreated(lpparam);
            }
        });
    }

    private static void onApplicationCreated(LoadPackageParam lpparam)
    {
        final String packageName = lpparam.packageName;

        if (packageName.equals(MY_PACKAGE_NAME))
        {
            return;
        }

        if (Util.getCurrentApplication() == null)
        {
            Util.log(packageName, "Context belum tersedia, lewati");
            return;
        }

        // Satu proses hanya dipasangi hook sekali. Sebagian aplikasi memanggil
        // Application.onCreate lebih dari sekali (mis. activity recreate di
        // proses yang sama atau MultiDex), dan memasang hook ganda membuat
        // setiap iklan terhitung dua kali.
        String processKey = packageName + "/" + (lpparam.processName == null ? "" : lpparam.processName)
                + "@" + System.identityHashCode(lpparam.classLoader);
        if (!sHookedProcesses.add(processKey))
        {
            return;
        }

        BlockPrefs prefs = BlockPrefs.get();
        if (!prefs.isEnabledFor(packageName))
        {
            return;
        }

        Util.log(packageName, "diaktifkan untuk MinMinGuard");

        // WeakHashMap penanda view bisa menyimpan entri dari pemasangan hook
        // sebelumnya (aplikasi yang sama, proses baru). Bersihkan dulu agar
        // view baru tidak dianggap sudah pernah diblokir.
        ViewBlocking.reset();

        try
        {
            // 1. Pemblokiran berbasis API ad-SDK.
            ApiBlocking.handle(packageName, lpparam);

            // 2. Patch khusus per aplikasi.
            appSpecific(packageName, lpparam);

            // 3. Pemblokiran berbasis nama kelas view.
            NameBlocking.nameBasedBlocking(packageName, lpparam);

            // 4. Aktivitas interstitial / full screen.
            ActivityBlocking.blockInterstitialActivities(packageName, lpparam);

            // 5. Penyaringan URL WebView (opt-in per aplikasi).
            if (prefs.isUrlFilterEnabled(packageName))
            {
                UrlFiltering.removeWebViewAds(packageName, lpparam);
            }

            // 6. Pemblokiran DNS (opt-in per aplikasi).
            if (prefs.isDnsFilterEnabled(packageName))
            {
                HostBlock.install(packageName, lpparam);
            }
        }
        catch (Throwable t)
        {
            Util.warn("gagal memasang hook untuk " + packageName + ": " + t);
        }
        finally
        {
            StatsReporter.get().flushNow();
        }
    }

    private static void appSpecific(String packageName, LoadPackageParam lpparam)
    {
        _2chMate.handleLoadPackage(packageName, lpparam);
        OneWeather.handleLoadPackage(packageName, lpparam);
        NextMedia.handleLoadPackage(packageName, lpparam);
        Viafree.handleLoadPackage(packageName, lpparam);
    }

    /**
     * Patch berbasis resource (PeriodCalendar). Hook ini dipanggil framework
     * sebelum proses aplikasi berjalan, jadi tidak bisa dilakukan dari
     * {@link #handleLoadPackage}.
     */
    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam)
    {
        try
        {
            PeriodCalendar.handleInitPackageResources(resparam);
        }
        catch (Throwable t)
        {
            Util.warn("gagal mem-patch resource " + resparam.packageName + ": " + t);
        }
    }

    private static void hookRestorePermissionState(LoadPackageParam lpparam, String className)
    {
        try
        {
            XposedBridge.hookAllMethods(XposedHelpers.findClass(className, lpparam.classLoader), "restorePermissionState", new XC_MethodHook()
            {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable
                {
                    Object pkg = param.args[0];
                    if (pkg == null)
                    {
                        return;
                    }

                    String pkgName = null;
                    try
                    {
                        pkgName = (String) XposedHelpers.callMethod(pkg, "getPackageName");
                    }
                    catch (Throwable ignored)
                    {
                    }
                    if (pkgName == null)
                    {
                        try
                        {
                            pkgName = (String) XposedHelpers.getObjectField(pkg, "packageName");
                        }
                        catch (Throwable ignored)
                        {
                        }
                    }

                    if (pkgName == null || !BlockPrefs.get().isEnabledFor(pkgName))
                    {
                        return;
                    }

                    Object permissions = null;
                    try
                    {
                        permissions = XposedHelpers.getObjectField(pkg, "requestedPermissions");
                    }
                    catch (Throwable ignored)
                    {
                    }
                    if (permissions == null)
                    {
                        try
                        {
                            permissions = XposedHelpers.callMethod(pkg, "getRequestedPermissions");
                        }
                        catch (Throwable ignored)
                        {
                        }
                    }

                    if (!(permissions instanceof Collection))
                    {
                        return;
                    }

                    Collection collection = (Collection) permissions;
                    if (collection.contains(QUERY_ALL_PACKAGES))
                    {
                        return;
                    }
                    try
                    {
                        collection.add(QUERY_ALL_PACKAGES);
                        Util.warn("menambahkan " + QUERY_ALL_PACKAGES + " ke " + pkgName);
                    }
                    catch (ClassCastException | UnsupportedOperationException e)
                    {
                        Util.warn("tidak bisa menambahkan " + QUERY_ALL_PACKAGES + " ke " + pkgName + ": " + e);
                    }
                }
            });
        }
        catch (Throwable t)
        {
            Util.log(MY_PACKAGE_NAME, className + " -> " + t);
        }
    }
}
