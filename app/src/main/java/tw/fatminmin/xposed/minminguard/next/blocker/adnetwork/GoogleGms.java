package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

public class GoogleGms extends Blocker
{

    private static final String BANNER = "com.google.android.gms.ads.AdView";
    private static final String BANNER_PREFIX = "com.google.android.gms.ads";

    private static final String SEARCH_BANNER = "com.google.android.gms.ads.search.SearchAdView";
    private static final String INTER_ADS = "com.google.android.gms.ads.InterstitialAd";

    private static final String AD_LOADER = "com.google.android.gms.ads.AdLoader";

    // API modern (Play services 20+): mayoritas aplikasi memakai kelas ini,
    // bukan lagi InterstitialAd/AdLoader lama.
    private static final String REWARDED_AD = "com.google.android.gms.ads.rewarded.RewardedAd";
    private static final String REWARDED_INTERSTITIAL =
            "com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd";
    private static final String INTERSTITIAL_AD = "com.google.android.gms.ads.interstitial.InterstitialAd";
    private static final String APP_OPEN_AD = "com.google.android.gms.ads.appopen.AppOpenAd";
    private static final String AD_MANAGER_BANNER = "com.google.android.gms.ads.admanager.AdManagerAdView";

    public boolean handleLoadPackage(final String packageName, LoadPackageParam lpparam)
    {
        boolean result = false;

        result |= ApiBlocking.removeBanner(packageName, BANNER, "loadAd", lpparam);
        result |= ApiBlocking.removeBanner(packageName, SEARCH_BANNER, "loadAd", lpparam);

        result |= ApiBlocking.blockAdFunction(packageName, INTER_ADS, "loadAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTER_ADS, "show", lpparam);

        result |= ApiBlocking.blockAdFunction(packageName, AD_LOADER, "loadAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, AD_LOADER, "loadAds", lpparam);

        // Banner Ad Manager.
        result |= ApiBlocking.removeBanner(packageName, AD_MANAGER_BANNER, "loadAd", lpparam);

        // Full screen modern: load() + show() pada masing-masing tipe.
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL_AD, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL_AD, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED_AD, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED_AD, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED_INTERSTITIAL, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED_INTERSTITIAL, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, APP_OPEN_AD, "load", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, APP_OPEN_AD, "show", lpparam);

        // Catatan: MobileAds.initialize() sengaja tidak diblokir. Banyak SDK
        // pihak ketiga menunggu callback inisialisasi ini dan akan macet bila
        // tidak pernah dipanggil; pemuatan iklan sudah diblokir di atas.

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
