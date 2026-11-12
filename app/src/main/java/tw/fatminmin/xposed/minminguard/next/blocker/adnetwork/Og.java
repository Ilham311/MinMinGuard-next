package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;
import tw.fatminmin.xposed.minminguard.next.blocker.Util;
import tw.fatminmin.xposed.minminguard.next.blocker.ViewBlocking;

public class Og extends Blocker
{

    private static final String BANNER = "com.og.wa.AdWebView";
    private static final String BANNER_PREFIX = "com.og.wa";

    @Override
    public boolean handleLoadPackage(final String packageName, LoadPackageParam lpparam)
    {
        final Class<?> adView = Util.findClassOrNull(BANNER, lpparam.classLoader);
        if (adView == null)
        {
            return false;
        }
        final Class<?> webView = Util.findClassOrNull("android.webkit.WebView", lpparam.classLoader);
        if (webView == null)
        {
            return false;
        }

        Util.hookAllMethods(webView, "loadUrl", new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                if (adView.isInstance(param.thisObject))
                {
                    Util.log(packageName, "Detect og AdWebView loadUrl in " + packageName);
                    Util.setDefaultResult(param);
                    Util.notifyRemoveAdView(null, packageName, 1);
                    Util.countBlockedAd(null, packageName, "Og");

                    if (param.thisObject instanceof View)
                    {
                        ViewBlocking.removeAdView(packageName, (View) param.thisObject);
                    }
                }
            }
        });

        Util.log(packageName, packageName + " uses Og AdWebView");
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
