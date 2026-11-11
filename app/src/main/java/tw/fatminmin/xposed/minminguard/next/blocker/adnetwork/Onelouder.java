package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import android.view.View;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;
import tw.fatminmin.xposed.minminguard.next.blocker.Util;

public class Onelouder extends Blocker
{

    private static final String BANNER = "com.onelouder.adlib.AdView";
    private static final String BANNER_PREFIX = "com.onelouder.adlib";
    private static final String INTERSTITIAL = "com.onelouder.adlib.AdInterstitial";

    @Override
    public boolean handleLoadPackage(final String packageName, LoadPackageParam lpparam)
    {
        final Class<?> adView = Util.findClassOrNull(BANNER, lpparam.classLoader);
        if (adView == null)
        {
            return false;
        }

        XposedBridge.hookAllMethods(adView, "setVisibility", new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                Util.log(packageName, "Detect onelouder AdView setVisibility in " + packageName);
                if (param.args.length > 0)
                {
                    param.args[0] = View.GONE;
                }
            }
        });

        final Method setVisibility;
        try
        {
            setVisibility = XposedHelpers.findMethodBestMatch(adView, "setVisibility", Integer.class);
        }
        catch (Throwable t)
        {
            Util.log(packageName, "onelouder setVisibility tidak ditemukan: " + t);
            return false;
        }

        hookHideAdView(adView, "addProxiedAdView", packageName, setVisibility);
        hookHideAdView(adView, "resume", packageName, setVisibility);

        final Class<?> interstitial = Util.findClassOrNull(INTERSTITIAL, lpparam.classLoader);
        if (interstitial != null)
        {
            XposedBridge.hookAllMethods(interstitial, "displayInterstitial", new XC_MethodHook()
            {
                @Override
                protected void beforeHookedMethod(MethodHookParam param)
                {
                    Util.log(packageName, "Detect onelouder AdInterstitial displayInterstitial in " + packageName);
                    Util.setDefaultResult(param);
                    Util.notifyRemoveAdView(null, packageName, 1);
                    Util.countBlockedAd(null, packageName, "Onelouder");
                }
            });
        }

        Util.log(packageName, packageName + " uses onelouder");
        return true;
    }

    private static void hookHideAdView(Class<?> adView, String methodName, final String packageName,
                                       final Method setVisibility)
    {
        XposedBridge.hookAllMethods(adView, methodName, new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable
            {
                Util.log(packageName, "Detect onelouder " + methodName + " in " + packageName);
                setVisibility.invoke(param.thisObject, View.GONE);

                // Method ini umumnya void/int; setResult(Object) akan memicu
                // ClassCastException pada tipe primitif.
                Util.setDefaultResult(param);
                Util.notifyRemoveAdView(null, packageName, 1);
                Util.countBlockedAd(null, packageName, "Onelouder");
            }
        });
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
