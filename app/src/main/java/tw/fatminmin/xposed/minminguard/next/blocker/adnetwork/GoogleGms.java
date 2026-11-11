package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

public class GoogleGms extends Blocker
{

    private static final String BANNER = "com.google.android.gms.ads.AdView";
    private static final String BANNER_PREFIX = "com.google.android.gms.ads";

    /**
     * {@code AdView.loadAd()} tidak dideklarasikan di kelas {@code AdView}
     * sendiri melainkan di {@code BaseAdView} (diperiksa pada APK sungguhan).
     * Hook lama pada {@code AdView#loadAd} selalu "method not found", sehingga
     * banner AdMob tidak pernah diblokir.
     */
    private static final String BASE_AD_VIEW = "com.google.android.gms.ads.BaseAdView";

    private static final String SEARCH_BANNER = "com.google.android.gms.ads.search.SearchAdView";
    private static final String INTER_ADS = "com.google.android.gms.ads.InterstitialAd";

    private static final String AD_LOADER = "com.google.android.gms.ads.AdLoader";
    private static final String AD_LOADER_BUILDER = "com.google.android.gms.ads.AdLoader$Builder";

    // API modern (Play services 20+): mayoritas aplikasi memakai kelas ini,
    // bukan lagi InterstitialAd/AdLoader lama.
    private static final String REWARDED_AD = "com.google.android.gms.ads.rewarded.RewardedAd";
    private static final String REWARDED_INTERSTITIAL =
            "com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd";
    private static final String INTERSTITIAL_AD = "com.google.android.gms.ads.interstitial.InterstitialAd";
    private static final String APP_OPEN_AD = "com.google.android.gms.ads.appopen.AppOpenAd";
    private static final String AD_MANAGER_BANNER = "com.google.android.gms.ads.admanager.AdManagerAdView";

    /**
     * {@code AdManagerInterstitialAd} menimpa {@code load()} milik
     * {@code InterstitialAd} dengan deklarasi statisnya sendiri, sehingga hook di
     * kelas dasar tidak mengenainya. Diperiksa pada APK sungguhan.
     */
    private static final String AD_MANAGER_INTERSTITIAL =
            "com.google.android.gms.ads.admanager.AdManagerInterstitialAd";

    public boolean handleLoadPackage(final String packageName, LoadPackageParam lpparam)
    {
        boolean result = false;

        // AdListener dipasang aplikasi lebih dulu (setAdListener /
        // withAdListener), jadi harus ditangkap sebelum pemuatan diblokir.
        //
        // setAdListener dideklarasikan sekali di BaseAdView dan diwarisi oleh
        // AdView maupun AdManagerAdView. Menangkapnya per subkelas akan
        // memasang hook yang sama dua kali untuk satu objek; cukup satu kali di
        // kelas dasar karena listener disimpan atas objek iklan itu sendiri.
        result |= ApiBlocking.captureAdListener(packageName, BASE_AD_VIEW, lpparam);

        // AdLoader menerima listener lewat Builder, bukan lewat objek AdLoader.
        // Listener itu didaftarkan atas nama AdLoader supaya saat loadAd()
        // diblokir, AdListener milik aplikasi menerima onAdFailedToLoad.
        result |= ApiBlocking.captureAdListenerFor(packageName, AD_LOADER_BUILDER, AD_LOADER, lpparam);

        result |= ApiBlocking.removeBanner(packageName, BASE_AD_VIEW, "loadAd", lpparam);
        result |= ApiBlocking.removeBanner(packageName, SEARCH_BANNER, "loadAd", lpparam);

        // InterstitialAd lama sudah dihapus dari Play services modern; hook-nya
        // tetap dipertahankan untuk aplikasi yang masih memakai versi tua.
        result |= ApiBlocking.blockAdLoad(packageName, INTER_ADS, "loadAd", lpparam);

        result |= ApiBlocking.blockAdLoad(packageName, AD_LOADER, "loadAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, AD_LOADER, "loadAds", lpparam);

        // Banner Ad Manager memakai BaseAdView juga; hook ganda aman karena
        // Util menolak pemasangan kedua untuk kelas#method yang sama.
        result |= ApiBlocking.removeBanner(packageName, AD_MANAGER_BANNER, "loadAd", lpparam);

        // Full screen modern: load() diblokir lewat blockAdLoad supaya callback
        // aplikasi tetap dipanggil. Tanpa itu RewardedAdLoadCallback tidak
        // pernah menerima onAdFailedToLoad dan UI reward berputar selamanya.
        //
        // show() tidak lagi di-hook: pada kelas ini show() abstrak
        // ("is abstract: it has no body to hook"), jadi hook-nya selalu gagal
        // dan hanya memenuhi log. Overload konkretnya ada di subclass internal
        // GMS yang namanya berubah tiap versi; memblokir load() sudah cukup
        // karena tanpa iklan yang termuat show() tidak punya apa pun untuk
        // ditampilkan.
        result |= ApiBlocking.blockAdLoad(packageName, INTERSTITIAL_AD, "load", lpparam);
        // AdManagerInterstitialAd menimpa load() milik InterstitialAd, jadi
        // hook di kelas dasar tidak mengenainya.
        result |= ApiBlocking.blockAdLoad(packageName, AD_MANAGER_INTERSTITIAL, "load", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, REWARDED_AD, "load", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, REWARDED_INTERSTITIAL, "load", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, APP_OPEN_AD, "load", lpparam);

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
