package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Huawei Ads Kit / PPS ({@code com.huawei.hms.ads}).
 * <p>
 * Sebelum ini modul tidak punya blocker Huawei sama sekali, padahal SDK ini
 * dipakai luas pada perangkat Huawei/Honor dan ikut dibundel mediator
 * (TopOn, TradPlus, MAX) sebagai jaringan bawah.
 * <p>
 * Seluruh nama kelas di bawah diperiksa langsung pada AAR resmi
 * ({@code com.huawei.hms:ads-base / ads-banner / ads-reward / ads-interstitial
 * / ads-splash} versi 13.4.91.301), bukan dari dokumentasi atau tebakan:
 * <ul>
 *   <li>{@code banner.BannerView extends FrameLayout} dengan
 *       {@code loadAd(AdParam)} — kelas view, jadi banner bisa dibuang dari
 *       pohon view sekaligus;</li>
 *   <li>{@code InterstitialAd.loadAd(AdParam)} / {@code show()} /
 *       {@code show(Activity)};</li>
 *   <li>{@code reward.RewardAd.loadAd(AdParam, RewardAdLoadListener)} dan tiga
 *       overload {@code show};</li>
 *   <li>{@code splash.SplashView.load(String, int, AdParam, listener)} dan
 *       {@code splash.SplashAd.preloadAd(...)} statis.</li>
 * </ul>
 * <p>
 * Callback: {@code AdListener.onAdFailed(int)} dan
 * {@code RewardAdLoadListener.onRewardAdFailedToLoad(int)} untuk jalur muat,
 * sedangkan {@code RewardAdStatusListener.onRewardAdClosed()} untuk jalur
 * tampil. Ketiganya sudah ditambahkan ke daftar {@code AdCallbacks}.
 */
public class HuaweiAds extends Blocker
{

    /**
     * Prefix paket untuk lapisan pemblokiran berbasis nama.
     * <p>
     * Sadar bahwa {@code BannerView} saja tidak cukup sebagai pencocok nama:
     * kelas itu juga ada di SDK lain, dan awalan paket inilah yang membedakan
     * iklan Huawei dari view biasa bernama sama.
     */
    private static final String BANNER = "com.huawei.hms.ads.banner.BannerView";
    private static final String BANNER_PREFIX = "com.huawei.hms.ads";

    private static final String INTERSTITIAL = "com.huawei.hms.ads.InterstitialAd";
    private static final String REWARD = "com.huawei.hms.ads.reward.RewardAd";
    private static final String SPLASH_VIEW = "com.huawei.hms.ads.splash.SplashView";
    private static final String SPLASH_AD = "com.huawei.hms.ads.splash.SplashAd";

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        // Listener harus tertangkap sebelum pemuatan diblokir, kalau tidak
        // tidak ada objek yang bisa diberi kabar gagal.
        result |= ApiBlocking.captureAdListener(packageName, BANNER, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, INTERSTITIAL, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, REWARD, lpparam);

        // removeBanner, bukan blockAdLoad: BannerView adalah View konkret, jadi
        // view-nya bisa langsung dikeluarkan dari pohon dan ruang kosongnya
        // diciutkan. blockAdLoad hanya menghentikan pemuatan.
        result |= ApiBlocking.removeBanner(packageName, BANNER, "loadAd", lpparam);

        result |= ApiBlocking.blockAdLoad(packageName, INTERSTITIAL, "loadAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL, "show", lpparam);

        // RewardAd punya dua jalur muat (AdParam + listener, dan loadAd(String,
        // AdParam)); keduanya ditangkap karena helper menelusuri semua overload.
        result |= ApiBlocking.blockAdLoad(packageName, REWARD, "loadAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARD, "show", lpparam);

        // Splash: SplashView.load(...) adalah instance method, sedangkan
        // SplashAd.preloadAd(...) statis. Tanpa keduanya, splash penuh layar
        // tetap muncul sebelum Activity pertama.
        result |= ApiBlocking.blockAdLoad(packageName, SPLASH_VIEW, "load", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, SPLASH_AD, "preloadAd", lpparam);

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
