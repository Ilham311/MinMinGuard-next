package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Tapjoy - {@code com.tapjoy}.
 */
public class Tapjoy extends Blocker
{
    public static final String BANNER = "com.tapjoy.TJAdView";
    public static final String BANNER_PREFIX = "com.tapjoy";

    private static final String CONNECT = "com.tapjoy.Tapjoy";
    private static final String PLACEMENT = "com.tapjoy.TJPlacement";
    private static final String OFFERWALL = "com.tapjoy.TJOfferwallLookup";

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

        result |= ApiBlocking.blockAdFunction(packageName, PLACEMENT, "requestContent", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, CONNECT, "getSupport", lpparam);
        result |= ApiBlocking.blockAdFunctionWithResult(packageName, CONNECT, "isConnected", false, lpparam);
        result |= ApiBlocking.blockAdFunctionWithResult(packageName, OFFERWALL, "isCampaignAvailable", false, lpparam);

        return result;
    }
}
