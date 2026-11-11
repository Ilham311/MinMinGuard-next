package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;
import tw.fatminmin.xposed.minminguard.next.blocker.Util;

public class mAdserve extends Blocker
{

    private static final String BANNER = "com.adsdk.sdk.banner.InAppWebView";
    private static final String BANNER_PREFIX = "com.adsdk.sdk.banner";

    @Override
    public boolean handleLoadPackage(final String packageName, LoadPackageParam lpparam)
    {
        Class<?> adView = Util.findClassOrNull(BANNER, lpparam.classLoader);
        if (adView == null)
        {
            return false;
        }

        // Konstruktor tidak dapat dibatalkan lewat setResult (nilainya selalu
        // objek baru); menyembunyikan view setelah dibuat lebih aman.
        XposedBridge.hookAllConstructors(adView, new XC_MethodHook()
        {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
            {
                Util.log(packageName, "Detect mAdserve InAppWebView constructor in " + packageName);
                Util.notifyRemoveAdView(null, packageName, 1);
                Util.notifyAdNetwork(null, packageName, "mAdserve");
                if (param.thisObject instanceof android.view.View)
                {
                    tw.fatminmin.xposed.minminguard.next.blocker.ViewBlocking
                            .removeAdView(packageName, (android.view.View) param.thisObject);
                }
            }
        });

        Util.log(packageName, packageName + " uses mAdserve");
        return true;
    }

    @Override
    public String getBannerPrefix()
    {
        return BANNER_PREFIX;
    }

    @Override
    public String getBanner()
    {
        return BANNER;
    }
}
