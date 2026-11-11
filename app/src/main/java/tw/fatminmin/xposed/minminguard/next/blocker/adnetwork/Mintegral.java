package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Mintegral (Mobvista) - blocked by class name prefixes.
 */
public class Mintegral extends Blocker
{
    public static final String BANNER = "com.mintegral.msdk.out.BannerView";
    public static final String BANNER_PREFIX = "com.mintegral.msdk";

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
        return false;
    }
}
