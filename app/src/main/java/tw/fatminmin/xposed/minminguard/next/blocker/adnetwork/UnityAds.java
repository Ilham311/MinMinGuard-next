package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Unity Ads ({@code com.unity3d.ads.UnityAds} dan {@code com.unity3d.ads.*}).
 * <p>
 * Semua method di kelas {@code UnityAds} statis. {@code canShow}/{@code canShowAds}
 * tidak ada lagi di SDK modern (diperiksa pada APK sungguhan), jadi hook lama
 * ke dua method itu tidak pernah terpasang dan hanya memenuhi log.
 * <p>
 * Versi 4.x (diperiksa langsung pada AAR resmi
 * {@code com.unity3d.ads:unity-ads:4.21.0}) menambahkan jalur iklan berbasis
 * objek: {@code InterstitialAd.load(...)}, {@code RewardedAd.load(...)},
 * {@code RewardedOffersAd.load(...)}, dan {@code BannerAd.load(...)} — semuanya
 * statis dan semuanya menerima {@code LoadListener}. Sebelumnya hanya
 * {@code UnityAds.load/show} yang diblokir, sehingga aplikasi yang memakai API
 * baru tetap memuat iklan dengan normal.
 * <p>
 * Nama kelas banner: {@code com.unity3d.services.banners.view.BannerView}
 * <em>tidak ada lagi</em> di 4.21.0 (direktori itu kini hanya berisi
 * {@code BannerPosition}). Kelas yang benar adalah
 * {@code com.unity3d.services.banners.BannerView} (turunan
 * {@code RelativeLayout} dengan {@code load()}/{@code load(UnityAdsLoadOptions)}).
 */
public class UnityAds extends Blocker
{

    /**
     * Kelas banner dan prefix paketnya untuk lapisan pemblokiran berbasis nama.
     * <p>
     * Sebelumnya keduanya bernilai null, sehingga classifier nama tidak
     * pernah mengenali view dari SDK ini sama sekali: jaring pengaman yang
     * seharusnya menangkap iklan ketika signature API bergeser antar versi
     * SDK tidak pernah berfungsi. Nama kelas diambil dari AAR 4.21.0.
     */
    private static final String BANNER = "com.unity3d.services.banners.BannerView";
    private static final String BANNER_PREFIX = "com.unity3d.services.banners";
    private static final String UNITY_ADS = "com.unity3d.ads.UnityAds";

    // API berbasis objek (Unity Ads 4.x). Semuanya statis dan menerima
    // LoadListener, sehingga blockAdLoad bisa mengirim onAdLoaded(err) atau
    // onUnityAdsFailedToLoad tanpa menangkap apa pun lebih dulu.
    private static final String BANNER_AD = "com.unity3d.ads.BannerAd";
    private static final String INTERSTITIAL_AD = "com.unity3d.ads.InterstitialAd";
    private static final String REWARDED_AD = "com.unity3d.ads.RewardedAd";
    private static final String REWARDED_OFFERS_AD = "com.unity3d.ads.RewardedOffersAd";

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        // load(String, IUnityAdsLoadListener) menerima listener langsung sebagai
        // argumen, jadi blockAdLoad bisa mengirim onUnityAdsFailedToLoad tanpa
        // menangkap apa pun lebih dulu.
        result |= ApiBlocking.blockAdLoad(packageName, UNITY_ADS, "load", lpparam);

        // show(Activity, String, IUnityAdsShowListener) juga menerima listener
        // sebagai argumen; blockAdFunction mengirim callback tutup lewat
        // notifyBlocked -> jalur isShowPath.
        result |= ApiBlocking.blockAdFunction(packageName, UNITY_ADS, "show", lpparam);

        // isInitialized()/isSupported() sengaja TIDAK dipaksa false. Aplikasi
        // yang menunggu inisialisasi berhasil akan mengulang terus bila nilai
        // itu palsu, dan pemuatan iklan sudah diblokir di atas.

        // Jalur 4.x: load statis yang menerima LoadListener. Tanpa ini,
        // aplikasi modern memuat iklan lewat API objek dan tidak tersentuh
        // sama sekali oleh hook UnityAds.load di atas.
        result |= ApiBlocking.blockAdLoad(packageName, BANNER_AD, "load", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, INTERSTITIAL_AD, "load", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, REWARDED_AD, "load", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, REWARDED_OFFERS_AD, "load", lpparam);

        // Banner legacy: BannerView.load() instance method. Kelasnya masih ada
        // pada 4.21.0 (hanya paket view/ yang hilang), jadi jalur ini tetap
        // relevan untuk aplikasi yang belum pindah ke BannerAd.
        result |= ApiBlocking.removeBanner(packageName, BANNER, "load", lpparam);

        // Jalur banner lama yang memakai banner placement statis.
        result |= ApiBlocking.blockAdLoad(packageName, "com.unity3d.services.banners.UnityBanners",
                "loadBanner", lpparam);

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
