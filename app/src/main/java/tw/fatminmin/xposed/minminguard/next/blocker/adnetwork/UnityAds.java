package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Unity Ads ({@code com.unity3d.ads.UnityAds}).
 * <p>
 * Semua method di kelas ini statis. {@code canShow}/{@code canShowAds} tidak
 * ada lagi di SDK modern (diperiksa pada APK sungguhan), jadi hook lama ke dua
 * method itu tidak pernah terpasang dan hanya memenuhi log.
 */
public class UnityAds extends Blocker
{
    private static final String UnityAds = "com.unity3d.ads.UnityAds";

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        // load(String, IUnityAdsLoadListener) menerima listener langsung sebagai
        // argumen, jadi blockAdLoad bisa mengirim onUnityAdsFailedToLoad tanpa
        // menangkap apa pun lebih dulu.
        result |= ApiBlocking.blockAdLoad(packageName, UnityAds, "load", lpparam);

        // show(Activity, String, IUnityAdsShowListener) juga menerima listener
        // sebagai argumen; blockAdFunction mengirim callback tutup lewat
        // notifyBlocked -> jalur isShowPath.
        result |= ApiBlocking.blockAdFunction(packageName, UnityAds, "show", lpparam);

        // isInitialized()/isSupported() sengaja TIDAK dipaksa false. Aplikasi
        // yang menunggu inisialisasi berhasil akan mengulang terus bila nilai
        // itu palsu, dan pemuatan iklan sudah diblokir di atas.

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

