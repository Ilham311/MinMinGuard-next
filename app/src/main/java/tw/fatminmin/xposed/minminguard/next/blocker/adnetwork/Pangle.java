package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Pangle (ByteDance) - blocked by class name prefixes.
 */
public class Pangle extends Blocker
{
    public static final String BANNER_PREFIX = "com.bytedance.sdk.openadsdk";

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
        return false;
    }
}
