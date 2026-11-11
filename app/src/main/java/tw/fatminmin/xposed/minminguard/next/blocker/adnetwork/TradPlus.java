package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * TradPlus ({@code com.tradplus.ads}) — mediator iklan.
 * <p>
 * Mediator memuat iklan dari banyak jaringan di belakang satu API. Tanpa
 * dukungan mediator, semua jaringan yang iklannya datang lewat TradPlus lolos
 * walaupun SDK-nya masing-masing sudah diblokir: yang dipanggil aplikasi adalah
 * {@code TPReward}/{@code TPInterstitial}, bukan kelas jaringan di bawahnya.
 * <p>
 * Nama kelas diperiksa langsung dari APK sungguhan ({@code TPReward},
 * {@code TPInterstitial}, {@code TPBanner}, {@code TPSplash}, {@code TPNative})
 * dan semuanya memakai {@code loadAd()} + setter listener terpisah, jadi
 * listener ditangkap lebih dulu agar aplikasi menerima kabar gagal alih-alih
 * menunggu selamanya.
 */
public class TradPlus extends Blocker
{
    public static final String BANNER_PREFIX = "com.tradplus.ads";

    private static final String REWARD = "com.tradplus.ads.open.reward.TPReward";
    private static final String INTERSTITIAL = "com.tradplus.ads.open.interstitial.TPInterstitial";
    private static final String BANNER = "com.tradplus.ads.open.banner.TPBanner";
    private static final String SPLASH = "com.tradplus.ads.open.splash.TPSplash";
    private static final String NATIVE = "com.tradplus.ads.open.nativead.TPNative";
    private static final String NATIVE_BANNER = "com.tradplus.ads.open.nativead.TPNativeBanner";
    private static final String MEDIA_VIDEO = "com.tradplus.ads.open.mediavideo.TPMediaVideo";
    private static final String INTERACTIVE = "com.tradplus.ads.open.interactive.TPInterActive";

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
        boolean result = false;

        String[] classes = {REWARD, INTERSTITIAL, BANNER, SPLASH, NATIVE, NATIVE_BANNER,
                MEDIA_VIDEO, INTERACTIVE};
        for (String adClass : classes)
        {
            result |= ApiBlocking.captureAdListener(packageName, adClass, lpparam);
            // Semua tipe TradPlus memuat lewat loadAd(), kecuali banner yang
            // juga menerima ID unit sebagai argumen.
            result |= ApiBlocking.blockAdLoad(packageName, adClass, "loadAd", lpparam);
            result |= ApiBlocking.blockAdFunction(packageName, adClass, "reloadAd", lpparam);
        }

        // Sisa jalur tampil: kalau ada iklan yang sudah telanjur termuat
        // sebelum hook terpasang, showAd() tetap dicegah.
        result |= ApiBlocking.blockAdFunction(packageName, REWARD, "showAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL, "showAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, BANNER, "showAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, NATIVE_BANNER, "showAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, SPLASH, "showAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERACTIVE, "showAd", lpparam);

        return result;
    }
}
