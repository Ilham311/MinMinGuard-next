package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * AppLovin MAX ({@code com.applovin.mediation}).
 * <p>
 * MAX memakai {@code setListener()} (bukan {@code setAdListener}) dan
 * {@code showAd()} (bukan {@code show()}). Keduanya sudah diperiksa pada APK
 * sungguhan. Daftar {@code captureAdListener} di {@code ApiBlocking} memuat
 * {@code setListener}, jadi listener tertangkap dan pemuatan yang diblokir
 * bisa dilaporkan lewat {@code onAdLoadFailed}.
 */
public class Applovin extends Blocker
{
    private static final String MAX_ADVIEW = "com.applovin.mediation.ads.MaxAdView";
    private static final String MAX_INTER = "com.applovin.mediation.ads.MaxInterstitialAd";
    private static final String MAX_REWARDED = "com.applovin.mediation.ads.MaxRewardedAd";
    private static final String MAX_REWARDED_INTER = "com.applovin.mediation.ads.MaxRewardedInterstitialAd";
    private static final String MAX_APPOPEN = "com.applovin.mediation.ads.MaxAppOpenAd";
    private static final String MAX_NATIVE = "com.applovin.mediation.ads.MaxNativeAdLoader";

    private static final String NATIVE_ADS = "com.applovin.nativeAds.NativeAdService";
    private static final String APPLOVIN_ADS = "com.applovin.impl.sdk.AppLovinAdServiceImpl";

    public boolean handleLoadPackage(final String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        // Listener dipasang aplikasi lewat setListener() sebelum loadAd(),
        // jadi harus ditangkap lebih dulu agar pemuatan yang diblokir bisa
        // diberi kabar gagal.
        result |= ApiBlocking.captureAdListener(packageName, MAX_ADVIEW, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, MAX_INTER, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, MAX_REWARDED, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, MAX_REWARDED_INTER, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, MAX_APPOPEN, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, MAX_NATIVE, lpparam);

        result |= ApiBlocking.blockAdLoad(packageName, MAX_ADVIEW, "loadAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, MAX_INTER, "loadAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, MAX_REWARDED, "loadAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, MAX_REWARDED_INTER, "loadAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, MAX_APPOPEN, "loadAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, MAX_NATIVE, "loadAd", lpparam);

        // Jalur tampil: MAX memakai showAd(), bukan show(). Bila ada iklan
        // yang telanjur termuat, showAd() tetap dicegah dan callback tutup
        // dikirim supaya layar reward tidak berputar.
        result |= ApiBlocking.blockAdFunction(packageName, MAX_INTER, "showAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, MAX_REWARDED, "showAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, MAX_REWARDED_INTER, "showAd", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, MAX_APPOPEN, "showAd", lpparam);

        // AppLovinSdk.isEnabled sengaja TIDAK dipaksa false. Nilai itu dipakai
        // SDK untuk memutuskan apakah mediator boleh jalan; memaksanya false
        // membuat inisialisasi berhenti di tengah dan sebagian SDK yang
        // menunggu callback inisialisasi (termasuk mediation MAX) ikut macet.
        // Pemuatan iklan sudah diblokir di atas, jadi tidak perlu.

        // Jalur ad-received internal: {@code AppLovinAdServiceImpl$a} di APK
        // sungguhan ternyata kosong dan tidak punya {@code adReceived}, jadi
        // hook lama tidak pernah terpasang. Yang nyata adalah loadNextAd()
        // pada AppLovinAdServiceImpl.
        result |= ApiBlocking.blockAdLoad(packageName, APPLOVIN_ADS, "loadNextAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, APPLOVIN_ADS, "loadNextIncentivizedAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, APPLOVIN_ADS, "loadNextAdForZoneId", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, NATIVE_ADS, "loadNativeAds", lpparam);
        return result;
    }

    @Override
    public String getBannerPrefix()
    {
        return null;
    }

    @Override
    public String getBanner()
    {
        return null;
    }
}