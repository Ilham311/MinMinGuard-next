package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

public class Ironsource extends Blocker
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
    private static final String BANNER = "com.ironsource.mediationsdk.ads.banner.LevelPlayBannerAdView";
    private static final String BANNER_PREFIX = "com.ironsource";
    /**
     * Kelas lama tidak dipakai lagi: {@code SupersonicAdsAdapter} tidak ada di
     * SDK ironSource modern, sehingga seluruh hook pada kelas itu tidak pernah
     * terpasang dan iklan ironSource lolos tanpa terlihat.
     * <p>
     * Kelas adapter abstrak di bawah ini punya method konkret yang dipanggil
     * untuk setiap iklan ironSource, apa pun jaringan yang menang di mediasi.
     */
    private static final String ABSTRACT_ADAPTER = "com.ironsource.mediationsdk.AbstractAdapter";
    private static final String INTERSTITIAL_ADAPTER = "com.ironsource.mediationsdk.adapter.AbstractInterstitialAdapter";
    private static final String REWARDED_ADAPTER = "com.ironsource.mediationsdk.adapter.AbstractRewardedVideoAdapter";

    private static final String IRONSOURCE = "com.ironsource.mediationsdk.IronSource";

    /**
     * Adapter ironSource sendiri ({@code IronSourceAdapter}) menimpa hampir
     * semua method pemuatan dan penayangan milik {@code AbstractAdapter} dengan
     * deklarasinya sendiri. Diperiksa pada APK sungguhan: dari 11 method yang
     * di-hook di kelas dasar, 11-nya ditimpa. Tanpa kelas ini, iklan ironSource
     * yang dimediasi lewat jaringannya sendiri tetap dimuat dan ditampilkan
     * walaupun semua hook di {@code AbstractAdapter} terpasang.
     * <p>
     * Adapter jaringan lain (AppLovin, Mintegral, dsb. di dalam mediasi
     * ironSource) berada di dex yang berbeda dan tidak menimpa method ini,
     * jadi hook di kelas dasar tetap berlaku untuk mereka.
     */
    private static final String IRONSOURCE_ADAPTER = "com.ironsource.adapters.ironsource.IronSourceAdapter";

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        // Demand-only memakai listener statis yang didaftarkan sekali di muka;
        // ditangkap lebih dulu supaya pemuatan yang diblokir bisa dilaporkan
        // lewat onISDemandOnlyInterstitialAdFailedToLoad.
        result |= ApiBlocking.captureAdListener(packageName, IRONSOURCE, lpparam);

        // Jalur adapter: dipakai semua iklan yang dimediasi ironSource.
        // initInterstitial/initRewardedVideoWithCallback/initBanners adalah
        // nama method pemuatan yang sebenarnya pada SDK modern; loadInterstitial
        // dan kawan-kawan hanya ada di subclass adapter tertentu. Tanpa
        // keduanya, iklan yang dimediasi ironSource tetap dimuat.
        result |= ApiBlocking.blockAdFunction(packageName, ABSTRACT_ADAPTER, "showRewardedVideo", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, ABSTRACT_ADAPTER, "showInterstitial", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, ABSTRACT_ADAPTER, "loadInterstitial", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, ABSTRACT_ADAPTER, "initInterstitial", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, ABSTRACT_ADAPTER, "initInterstitialForBidding", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, ABSTRACT_ADAPTER, "initRewardedVideoWithCallback", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, ABSTRACT_ADAPTER, "initRewardedVideoForDemandOnly", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, ABSTRACT_ADAPTER, "initAndLoadRewardedVideo", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, ABSTRACT_ADAPTER, "initBanners", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, ABSTRACT_ADAPTER, "initBannerForBidding", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, ABSTRACT_ADAPTER, "initNativeAds", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, ABSTRACT_ADAPTER, "initNativeAdForBidding", lpparam);
        // Pertanyaan kesiapan tidak dihitung sebagai blokir (Util menanganinya),
        // tetapi nilainya tetap dipaksa false supaya aplikasi tidak menampilkan
        // tombol yang tidak bisa dijalankan.
        result |= ApiBlocking.blockAdFunctionWithResult(packageName, ABSTRACT_ADAPTER, "isRewardedVideoAvailable", false, lpparam);
        result |= ApiBlocking.blockAdFunctionWithResult(packageName, ABSTRACT_ADAPTER, "isInterstitialReady", false, lpparam);

        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL_ADAPTER, "showInterstitial", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, INTERSTITIAL_ADAPTER, "loadInterstitial", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, INTERSTITIAL_ADAPTER, "loadInterstitialForBidding", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED_ADAPTER, "showRewardedVideo", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, REWARDED_ADAPTER, "loadRewardedVideo", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, REWARDED_ADAPTER, "loadRewardedVideoForBidding", lpparam);

        // Adapter ironSource sendiri menimpa 11 method di atas (lihat
        // IRONSOURCE_ADAPTER). Ini jalur yang benar-benar dipakai iklan
        // ironSource sendiri, jadi tanpa bagian ini seluruh blokir ironSource
        // hanya berlaku pada adapter jaringan pihak ketiga.
        //
        // captureAdListener di sini tidak menemukan setter apa pun: kelas ini
        // tidak punya method set*, listener-nya datang sebagai argumen
        // (InterstitialSmashListener/RewardedVideoSmashListener pada init*).
        // Jalur argumen sudah ditangani notifyBlocked, jadi baris ini hanya
        // menjaga bila versi SDK lain menambahkan setter.
        result |= ApiBlocking.captureAdListener(packageName, IRONSOURCE_ADAPTER, lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, IRONSOURCE_ADAPTER, "showRewardedVideo", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, IRONSOURCE_ADAPTER, "showInterstitial", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, IRONSOURCE_ADAPTER, "loadInterstitial", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, IRONSOURCE_ADAPTER, "initInterstitial", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, IRONSOURCE_ADAPTER, "initInterstitialForBidding", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, IRONSOURCE_ADAPTER, "initRewardedVideoWithCallback", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, IRONSOURCE_ADAPTER, "initRewardedVideoForDemandOnly", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, IRONSOURCE_ADAPTER, "initAndLoadRewardedVideo", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, IRONSOURCE_ADAPTER, "initBannerForBidding", lpparam);
        result |= ApiBlocking.blockAdFunctionWithResult(packageName, IRONSOURCE_ADAPTER, "isRewardedVideoAvailable", false, lpparam);
        result |= ApiBlocking.blockAdFunctionWithResult(packageName, IRONSOURCE_ADAPTER, "isInterstitialReady", false, lpparam);

        // Jalur demand-only: dipakai saat aplikasi memuat per instance.
        result |= ApiBlocking.blockAdFunction(packageName, IRONSOURCE, "showISDemandOnlyInterstitial", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, IRONSOURCE, "showISDemandOnlyRewardedVideo", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, IRONSOURCE, "loadISDemandOnlyInterstitial", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, IRONSOURCE, "loadISDemandOnlyRewardedVideo", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, IRONSOURCE, "loadISDemandOnlyBanner", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, IRONSOURCE, "createBannerForDemandOnly", lpparam);
        result |= ApiBlocking.blockAdFunctionWithResult(packageName, IRONSOURCE, "isISDemandOnlyInterstitialReady", false, lpparam);
        result |= ApiBlocking.blockAdFunctionWithResult(packageName, IRONSOURCE, "isISDemandOnlyRewardedVideoAvailable", false, lpparam);

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
