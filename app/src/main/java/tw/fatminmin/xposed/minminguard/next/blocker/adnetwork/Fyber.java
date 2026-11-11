package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Fyber / DT FairBid ({@code com.fyber}).
 */
public class Fyber extends Blocker
{
    public static final String BANNER_PREFIX = "com.fyber";

    private static final String MEDIATOR = "com.fyber.inneractive.sdk.external.InneractiveAdManager";
    private static final String INTERSTITIAL = "com.fyber.inneractive.sdk.external.InneractiveAdSpot";
    private static final String INTERSTITIAL_VIEW = "com.fyber.inneractive.sdk.external.InneractiveFullscreenUnitController";
    private static final String FAIRBID = "com.fyber.fairbid.ads.Interstitial";
    private static final String FAIRBID_REWARDED = "com.fyber.fairbid.ads.Rewarded";

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

        result |= ApiBlocking.blockAdFunctionWithResult(packageName, MEDIATOR, "wasInitialized", false, lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL, "requestAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL_VIEW, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, FAIRBID, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, FAIRBID_REWARDED, "show", lpparam);

        return result;
    }
}
