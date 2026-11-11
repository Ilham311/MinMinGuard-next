package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Mintegral (Mobvista) - kelas {@code com.mintegral.msdk} dan
 * {@code com.mbridge.msdk} (rebranding 2022).
 */
public class Mintegral extends Blocker
{
    public static final String BANNER = "com.mintegral.msdk.out.BannerView";
    public static final String BANNER_PREFIX = "com.mintegral.msdk";

    private static final String MBANNER_PREFIX = "com.mbridge.msdk";

    private static final String INTERSTITIAL = "com.mintegral.msdk.interstitial.out.MBridgeInterstitial";
    private static final String REWARD_VIDEO = "com.mintegral.msdk.reward.out.MBridgeReward";
    private static final String NATIVE_AD = "com.mintegral.msdk.nativead.out.MBridgeNativeAd";

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
        result |= ApiBlocking.removeBanner(packageName, BANNER, "loadFromBid", lpparam);

        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARD_VIDEO, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARD_VIDEO, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, NATIVE_AD, "load", lpparam);

        // MBridge (nama baru) - signature sama, kelas berbeda.
        result |= ApiBlocking.blockAdFunction(packageName, MBANNER_PREFIX + ".interstitial.out.MBridgeInterstitial", "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, MBANNER_PREFIX + ".reward.out.MBridgeReward", "show", lpparam);

        return result;
    }
}
