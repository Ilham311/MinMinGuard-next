package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Pangle / ByteDance (kelas SDK: {@code com.bytedance.sdk.openadsdk}).
 * <p>
 * APK sungguhan hanya memuat <em>API PAG</em> modern
 * ({@code com.bytedance.sdk.openadsdk.api.*}) dan tidak lagi memuat API lama
 * {@code TTAdNative}/{@code TTRewardVideoAd}/{@code TTInterstitialAd}. Hook
 * versi sebelumnya menargetkan kelas lama itu, sehingga tidak satu pun hook
 * terpasang dan blokir Pangle sepenuhnya tidak berfungsi.
 * <p>
 * {@code loadAd(String, Request, LoadCallback)} adalah method statis konkret
 * pada setiap kelas PAG, dan callback-nya diterima langsung sebagai argumen.
 * Itulah satu-satunya titik yang bisa di-hook: seluruh kelas PAG abstrak
 * ({@code flags=0x401}) sehingga {@code show()} dan setter listener-nya tidak
 * punya badan untuk dipasangi hook. Memblokir {@code loadAd} sudah cukup —
 * tanpa iklan termuat, {@code show()} tidak punya apa pun untuk ditampilkan.
 */
public class Pangle extends Blocker
{
    public static final String BANNER_PREFIX = "com.bytedance.sdk.openadsdk";

    private static final String PAG_BANNER = "com.bytedance.sdk.openadsdk.api.banner.PAGBannerAd";
    private static final String PAG_INTERSTITIAL = "com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialAd";
    private static final String PAG_REWARDED = "com.bytedance.sdk.openadsdk.api.reward.PAGRewardedAd";
    private static final String PAG_APP_OPEN = "com.bytedance.sdk.openadsdk.api.open.PAGAppOpenAd";
    private static final String PAG_NATIVE = "com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAd";

    @Override
    public String getBanner()
    {
        return null;
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

        // Callback muat diterima langsung sebagai argumen, jadi AdCallbacks
        // bisa mengirim PAGLoadCallback#onError tanpa penangkapan tambahan.
        result |= ApiBlocking.blockAdLoad(packageName, PAG_REWARDED, "loadAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, PAG_INTERSTITIAL, "loadAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, PAG_APP_OPEN, "loadAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, PAG_BANNER, "loadAd", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, PAG_NATIVE, "loadAd", lpparam);

        return result;
    }
}
