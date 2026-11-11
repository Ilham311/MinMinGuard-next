package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Tapjoy - blocked by class name prefixes.
 */
public class Tapjoy extends Blocker
{
    public static final String BANNER = "com.tapjoy.TJAdView";
    public static final String BANNER_PREFIX = "com.tapjoy";

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
