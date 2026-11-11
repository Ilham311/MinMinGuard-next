package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Tencent GDT / Guangdiantong ({@code com.qq.e}).
 */
public class TencentGDT extends Blocker
{
    public static final String BANNER = "com.qq.e.ads.banner2.UnifiedBannerView";
    public static final String BANNER_PREFIX = "com.qq.e";

    private static final String NATIVE_AD_MANAGER = "com.qq.e.ads.nativ.NativeADUnifiedManager";
    private static final String INTERSTITIAL_AD = "com.qq.e.ads.interstitial2.UnifiedInterstitialAD";
    private static final String REWARD_VIDEO_AD = "com.qq.e.ads.rewardvideo.RewardVideoAD";
    private static final String SPLASH_AD = "com.qq.e.ads.splash.SplashAD";

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

        result |= ApiBlocking.removeBanner(packageName, BANNER, "loadAd", lpparam);

        // GDT memakai setter listener terpisah untuk interstitial/reward.
        result |= ApiBlocking.captureAdListener(packageName, INTERSTITIAL_AD, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, REWARD_VIDEO_AD, lpparam);

        result |= ApiBlocking.blockAdLoad(packageName, NATIVE_AD_MANAGER, "loadData", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, INTERSTITIAL_AD, "loadAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL_AD, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL_AD, "showFullScreenAD", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, REWARD_VIDEO_AD, "loadAD", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARD_VIDEO_AD, "showAD", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, SPLASH_AD, "fetchAdOnly", lpparam);

        return result;
    }
}
