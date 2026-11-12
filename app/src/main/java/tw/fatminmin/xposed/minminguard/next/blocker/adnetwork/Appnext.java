package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

public class Appnext extends Blocker
{

    /**
     * Kelas banner dan prefix paketnya untuk lapisan pemblokiran berbasis nama.
     * <p>
     * Sebelumnya keduanya bernilai null, sehingga classifier nama tidak
     * pernah mengenali view dari SDK ini sama sekali: jaring pengaman yang
     * seharusnya menangkap iklan ketika signature API bergeser antar versi
     * SDK tidak pernah berfungsi. Nama kelas diambil dari SDK yang dipakai
     * aplikasi.
     */
    private static final String BANNER = "com.appnext.banners.BannerAd";
    private static final String BANNER_PREFIX = "com.appnext";
    private static final String INTER_ADS = "com.appnext.ads.interstitial.Interstitial";

    private static final String VIDEO_ADS = "com.appnext.ads.fullscreen.";

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        result |= ApiBlocking.blockAdFunction(packageName, INTER_ADS, "loadAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTER_ADS, "showAd", lpparam);

        result |= ApiBlocking.blockAdFunction(packageName, VIDEO_ADS, "loadAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, VIDEO_ADS, "showAd", lpparam);

        return result;
    }

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
}
