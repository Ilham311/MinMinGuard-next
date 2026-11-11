package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Smaato ({@code com.smaato.sdk}).
 */
public class Smaato extends Blocker
{
    public static final String BANNER = "com.smaato.sdk.banner.widget.BannerView";
    public static final String BANNER_PREFIX = "com.smaato.sdk";

    private static final String INTERSTITIAL = "com.smaato.sdk.interstitial.Interstitial";
    private static final String REWARDED = "com.smaato.sdk.rewarded.RewardedAd";
    private static final String AD_REQUEST = "com.smaato.sdk.core.ad.AdLoader";

    @Override
    public String getBanner()
    {
        return BANNER;
    }

    @Override
    public String getBannerPrefix()
    {
        return BANNER_PREFIX;
    }

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        result |= ApiBlocking.removeBanner(packageName, BANNER, "load", lpparam);

        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, AD_REQUEST, "loadAd", lpparam);

        return result;
    }
}
