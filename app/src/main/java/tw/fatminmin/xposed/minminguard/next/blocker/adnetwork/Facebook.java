package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Facebook Audience Network ({@code com.facebook.ads}).
 * <p>
 * Target hook diperiksa langsung ke APK sungguhan. Tiga hal berbeda dari
 * versi sebelumnya:
 * <ul>
 *   <li>{@code AdView} tidak punya {@code setAdListener}; listener dipasang
 *       lewat builder yang seluruh overload-nya abstrak. Karena itu listener
 *       dicari dari field objek iklan saat pemuatan diblokir.</li>
 *   <li>{@code NativeAd} tidak mendeklarasikan {@code loadAd()} sendiri; itu
 *       warisan {@code NativeAdBase}.</li>
 *   <li>{@code loadAdFromBid} sudah tidak ada di SDK modern.</li>
 * </ul>
 */
public class Facebook extends Blocker
{
    private static final String BANNER = "com.facebook.ads.AdView";
    private static final String BANNER_PREFIX = "com.facebook.ads";

    private static final String INTER = "com.facebook.ads.InterstitialAd";
    private static final String REWARDED_VIDEO = "com.facebook.ads.RewardedVideoAd";
    private static final String REWARDED_INTER = "com.facebook.ads.RewardedInterstitialAd";

    /** {@code loadAd()} native dideklarasikan di kelas dasar ini. */
    private static final String NATIVE_AD_BASE = "com.facebook.ads.NativeAdBase";
    private static final String NATIVE_ADS_MGR = "com.facebook.ads.NativeAdsManager";

    public boolean handleLoadPackage(final String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        result |= ApiBlocking.removeBanner(packageName, BANNER, "loadAd", lpparam);

        // Listener dipasang lewat builder (AdView/InterstitialAd/NativeAd
        // masing-masing punya builder-nya sendiri, semuanya abstrak), jadi
        // listener dicari dari field objek iklan saat pemuatan diblokir.
        // Parameter kelima true = aktifkan penelusuran field; hanya Facebook
        // yang membutuhkannya.
        result |= ApiBlocking.blockAdLoad(packageName, INTER, "loadAd", lpparam, true);
        result |= ApiBlocking.blockAdLoad(packageName, REWARDED_VIDEO, "loadAd", lpparam, true);
        result |= ApiBlocking.blockAdLoad(packageName, REWARDED_INTER, "loadAd", lpparam, true);

        // NativeAd modern tidak punya loadAd sendiri; method itu hidup di
        // NativeAdBase dan diwarisi. Cukup satu pendaftaran di kelas dasar —
        // menyebut NativeAd juga hanya mengulang penelusuran yang sama dan
        // ditemukan sebagai duplikat saat diperiksa ke APK.
        result |= ApiBlocking.blockAdLoad(packageName, NATIVE_AD_BASE, "loadAd", lpparam, true);

        result |= ApiBlocking.blockAdLoad(packageName, NATIVE_ADS_MGR, "loadAds", lpparam);

        // Jalur tampil: bila ada iklan yang telanjur termuat sebelum hook
        // terpasang, show() tetap dicegah dan callback tutup dikirim supaya
        // layar reward tidak berputar.
        result |= ApiBlocking.blockAdFunction(packageName, INTER, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED_VIDEO, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, REWARDED_INTER, "show", lpparam);

        // Listener native di pasang lewat NativeAdsManager#setListener dan
        // dipakai ulang oleh nextNativeAd(); tangkap supaya pemuatan yang
        // diblokir bisa dilaporkan.
        result |= ApiBlocking.captureAdListener(packageName, NATIVE_ADS_MGR, lpparam);

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
