package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Vungle / Liftoff ({@code com.vungle.ads}).
 * <p>
 * Versi sebelumnya menargetkan {@code com.vungle.warren.Vungle},
 * {@code com.vungle.warren.Storage}, dan {@code com.vungle.publisher.VunglePub}.
 * Ketiga kelas itu sudah tidak ada di SDK modern; diperiksa langsung ke APK,
 * yang ada adalah {@code com.vungle.ads.*}. Akibatnya Vungle tidak pernah
 * diblokir sama sekali — dan {@code load()} serta {@code play()} yang tidak
 * pernah dihentikan berarti layar reward yang memakai Vungle berputar sampai
 * timeout SDK.
 * <p>
 * API-nya berlapis seperti AdMob:
 * <ul>
 *   <li>{@code Ad.load()} dan {@code Ad.canPlayAd()} abstrak;</li>
 *   <li>implementasinya di {@code BaseAd} (konkret), yang juga mendeklarasikan
 *       {@code setAdListener} — jadi listener tertangkap dari sini untuk
 *       semua jenis iklan;</li>
 *   <li>{@code BaseFullscreenAd.play(Context)} konkret dan diwarisi
 *       {@code RewardedAd} serta {@code InterstitialAd};</li>
 *   <li>{@code VungleBannerView.load()} konkret dengan overload sendiri.</li>
 * </ul>
 */
public class Vungle extends Blocker
{
    private static final String BASE_AD = "com.vungle.ads.BaseAd";
    private static final String BASE_FULLSCREEN = "com.vungle.ads.BaseFullscreenAd";
    private static final String BANNER_VIEW = "com.vungle.ads.VungleBannerView";

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        // setAdListener dideklarasikan sekali di BaseAd dan diwarisi semua jenis
        // iklan, jadi menangkapnya di sana sudah mencakup reward, interstitial,
        // banner, dan native.
        result |= ApiBlocking.captureAdListener(packageName, BASE_AD, lpparam);

        // load() hidup di BaseAd. Menelusuri hierarki membuat BannerAd dan
        // NativeAd ikut ter-hook karena method itu diwarisi.
        result |= ApiBlocking.blockAdLoad(packageName, BASE_AD, "load", lpparam);

        // BaseFullscreenAd MENIMPA load(String). Penelusuran hierarki berjalan
        // ke atas, bukan ke bawah, jadi hook di BaseAd tidak mengenai
        // RewardedAd.load("id") maupun InterstitialAd.load("id") — padahal itu
        // justru jalur yang dipakai aplikasi. Tanpa baris ini Vungle reward
        // tetap termuat dan pemutarannya menggantung.
        result |= ApiBlocking.blockAdLoad(packageName, BASE_FULLSCREEN, "load", lpparam);

        // Banner punya deklarasi load() sendiri, jadi BaseAd saja tidak cukup.
        result |= ApiBlocking.blockAdLoad(packageName, BANNER_VIEW, "load", lpparam);

        // play() adalah jalur tampil: iklan mungkin sudah termuat sebelum hook
        // terpasang. Callback onAdEnd dikirim supaya layar reward tertutup,
        // bukan menunggu penayangan yang tidak akan terjadi.
        //
        // Cukup di BaseFullscreenAd: RewardedAd dan InterstitialAd tidak
        // mendeklarasikan play() sendiri (diperiksa pada APK), jadi menyebut
        // ketiganya hanya memasang hook yang sama berulang kali.
        result |= ApiBlocking.blockAdFunction(packageName, BASE_FULLSCREEN, "play", lpparam);

        // canPlayAd() melaporkan status, jadi tidak dihitung sebagai blokir;
        // tetapi tanpa dihentikan aplikasi mendapat nilai true untuk iklan yang
        // tidak pernah dimuat lalu memanggil play() dan menunggu.
        result |= ApiBlocking.blockAdFunction(packageName, BASE_AD, "canPlayAd", lpparam);

        // NativeAd tidak memakai play(); ia ditampilkan dengan view apa adanya.
        // load()-nya sudah tertangkap lewat BaseAd karena method itu diwarisi,
        // jadi tidak perlu didaftarkan lagi di sini.

        return result;
    }

    @Override
    public String getBanner()
    {
        return null;
    }

    @Override
    public String getBannerPrefix()
    {
        return null;
    }
}
