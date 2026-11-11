package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Pangle / ByteDance (kelas SDK: {@code com.bytedance.sdk.openadsdk}).
 * <p>
 * Nama kelas inti stabil sejak SDK 3.x, jadi pemblokiran berbasis API bisa
 * dipakai di samping pencocokan prefix untuk view yang di-obfuscate.
 */
public class Pangle extends Blocker
{
    public static final String BANNER_PREFIX = "com.bytedance.sdk.openadsdk";

    private static final String TT_AD_NATIVE = "com.bytedance.sdk.openadsdk.TTNativeAd";
    private static final String TT_AD = "com.bytedance.sdk.openadsdk.TTAdNative";
    private static final String TT_FULL_SCREEN = "com.bytedance.sdk.openadsdk.TTFullScreenVideoAd";
    private static final String TT_REWARD_VIDEO = "com.bytedance.sdk.openadsdk.TTRewardVideoAd";
    private static final String TT_INTERSTITIAL = "com.bytedance.sdk.openadsdk.TTInterstitialAd";

    @Override
    public String getBanner()
    {
        return null;
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

        // Semua iklan ByteDance dimuat lewat facade TTAdNative.
        result |= ApiBlocking.blockAdFunction(packageName, TT_AD, "loadFeedAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, TT_AD, "loadBannerExpressAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, TT_AD, "loadNativeExpressAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, TT_AD, "loadSplashAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, TT_AD, "loadFullScreenVideoAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, TT_AD, "loadRewardVideoAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, TT_AD, "loadInteractionExpressAd", lpparam);

        result |= ApiBlocking.blockAdFunction(packageName, TT_AD_NATIVE, "registerViewForInteraction", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, TT_FULL_SCREEN, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, TT_REWARD_VIDEO, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, TT_INTERSTITIAL, "show", lpparam);

        return result;
    }
}
