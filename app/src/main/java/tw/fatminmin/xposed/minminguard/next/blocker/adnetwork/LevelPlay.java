package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * LevelPlay — API mediasi ironSource yang lebih baru
 * ({@code com.unity3d.mediation}).
 * <p>
 * Diperiksa pada APK sungguhan: MiChat memuat seluruh kelas LevelPlay, dan
 * tidak satu pun dari mereka merupakan turunan kelas ironSource lama. Artinya
 * hook di {@code Ironsource.java} tidak berlaku untuk jalur ini — iklan yang
 * dimuat dan ditayangkan lewat LevelPlay lolos sepenuhnya, dan blokir yang
 * "tidak terjadi" itulah yang membuat layar reward berputar tanpa ujung:
 * aplikasi menunggu {@code onAdLoaded}/{@code onAdLoadFailed} yang tidak pernah
 * datang.
 * <p>
 * Ketiga kelas iklan ini berdiri sendiri (superclass-nya {@code Object}, kecuali
 * banner yang merupakan {@code FrameLayout}), jadi tidak ada method warisan yang
 * perlu ditelusuri. Listener dipasang lewat {@code setListener} /
 * {@code setBannerListener} sebelum pemuatan, sehingga ditangkap lebih dulu.
 * <p>
 * Nama callback-nya sudah tercakup daftar di {@code AdCallbacks}:
 * {@code onAdLoadFailed} (gagal muat), {@code onAdClosed} (tutup), dan
 * {@code onAdDisplayFailed} (gagal tampil).
 */
public class LevelPlay extends Blocker
{
    private static final String PREFIX = "com.unity3d.mediation";

    private static final String REWARDED_AD = "com.unity3d.mediation.rewarded.LevelPlayRewardedAd";
    private static final String INTERSTITIAL_AD = "com.unity3d.mediation.interstitial.LevelPlayInterstitialAd";
    private static final String BANNER_VIEW = "com.unity3d.mediation.banner.LevelPlayBannerAdView";

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        // Listener ditangkap lebih dulu. Tanpa ini, loadAd() yang diblokir tidak
        // memberi kabar apa pun kepada aplikasi.
        result |= ApiBlocking.captureAdListener(packageName, REWARDED_AD, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, INTERSTITIAL_AD, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, BANNER_VIEW, lpparam);

        // Muat: gagal-muat dilaporkan lewat onAdLoadFailed.
        result |= ApiBlocking.blockAdLoad(packageName, REWARDED_AD, "loadAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, INTERSTITIAL_AD, "loadAd", lpparam);

        // Banner adalah View; removeBanner menghapusnya dari hierarki sekaligus
        // melaporkan gagal-muat ke LevelPlayBannerAdViewListener.
        result |= ApiBlocking.removeBanner(packageName, BANNER_VIEW, "loadAd", lpparam);

        // Tampil: pemanggilan showAd() setelah iklan tidak pernah termuat akan
        // menggantung. Blokir dan jawab lewat callback tutup (onAdClosed), bukan
        // callback gagal-tampil, karena kegagalan tampil justru memicu
        // percobaan ulang pada banyak alur reward.
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED_AD, "showAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL_AD, "showAd", lpparam);

        // LevelPlay.init(...) sengaja TIDAK diblokir: aplikasi menunggu
        // LevelPlayInitListener dan akan mengulang terus bila callback itu tidak
        // pernah datang. Pemuatan iklan sudah diblokir di atas.

        return result;
    }

    @Override
    public String getBanner()
    {
        return BANNER_VIEW;
    }

    @Override
    public String getBannerPrefix()
    {
        return PREFIX;
    }
}
