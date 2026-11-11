package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * TopOn / AnyThink ({@code com.anythink} + alias lama {@code com.thinkup}).
 */
public class TopOn extends Blocker
{
    public static final String BANNER_PREFIX = "com.anythink";

    private static final String BANNER_VIEW = "com.anythink.banner.api.ATBannerView";
    private static final String INTERSTITIAL = "com.anythink.interstitial.api.ATInterstitial";
    private static final String REWARDED = "com.anythink.rewardvideo.api.ATRewardVideoAd";
    private static final String SPLASH = "com.anythink.splashad.api.ATSplashAd";
    private static final String NATIVE = "com.anythink.nativead.api.ATNativeAdView";

    @Override
    public String getBanner()
    {
        return BANNER_VIEW;
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

        result |= ApiBlocking.removeBanner(packageName, BANNER_VIEW, "loadAd", lpparam);

        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, SPLASH, "loadAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, NATIVE, "renderAdContainer", lpparam);

        return result;
    }
}
