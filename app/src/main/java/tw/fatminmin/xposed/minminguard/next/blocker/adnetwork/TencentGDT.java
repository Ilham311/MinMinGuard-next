package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Tencent GDT (Guangdian Tong) - blocked by class name prefixes.
 */
public class TencentGDT extends Blocker
{
    public static final String BANNER = "com.qq.e.ads.banner2.UnifiedBannerView";
    public static final String BANNER_PREFIX = "com.qq.e";

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
