package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

public class Adcolony extends Blocker
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
    private static final String BANNER = "com.adcolony.sdk.AdColonyAdView";
    private static final String BANNER_PREFIX = "com.adcolony.sdk";
    private static final String Adcolony_Storage = "com.adcolony.sdk.ab";

    private static final String INTER_ADS = "com.adcolony.sdk.AdColonyInterstitial";

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        result |= ApiBlocking.blockAdFunctionWithResultExact(packageName, Adcolony_Storage, "a", String.class, 0.0d, lpparam);

        result |= ApiBlocking.blockAdFunctionWithResult(packageName, INTER_ADS, "isExpired", true, lpparam);
        result |= ApiBlocking.blockAdFunctionWithResult(packageName, INTER_ADS, "show", false, lpparam);

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
