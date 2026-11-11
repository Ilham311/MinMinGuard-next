package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Inmobi ({@code com.inmobi.ads}).
 * <p>
 * Kelas banner yang dipakai APK modern bernama {@code InMobiBanner}, bukan
 * {@code IMBanner} — hook lama ke {@code IMBanner} tidak pernah terpasang
 * sehingga banner Inmobi lolos sepenuhnya.
 * <p>
 * Listener dipasang lewat {@code setListener(BannerAdEventListener)} dan
 * callback-nya bernama {@code onAdFetchFailed} / {@code onAdDismissed};
 * keduanya sudah ada di daftar {@code AdCallbacks}.
 */
public class Inmobi extends Blocker
{

    private static final String BANNER = "com.inmobi.ads.InMobiBanner";
    private static final String BANNER_PREFIX = "com.inmobi.ads";

    private static final String INTER_ADS = "com.inmobi.ads.InMobiInterstitial";

    private static final String NATIVE_ADS = "com.inmobi.ads.InMobiNative";

    public boolean handleLoadPackage(final String packageName, LoadPackageParam lpparam)
    {
        boolean result = false;

        result |= ApiBlocking.captureAdListener(packageName, BANNER, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, INTER_ADS, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, NATIVE_ADS, lpparam);

        result |= ApiBlocking.removeBanner(packageName, BANNER, "load", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, INTER_ADS, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTER_ADS, "show", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, NATIVE_ADS, "load", lpparam);

        return result;
    }

    @Override
    public String getBannerPrefix()
    {
        return BANNER_PREFIX;
    }

    @Override
    public String getBanner()
    {
        return BANNER;
    }
}
