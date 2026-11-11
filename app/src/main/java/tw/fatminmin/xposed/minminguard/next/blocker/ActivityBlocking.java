package tw.fatminmin.xposed.minminguard.next.blocker;

import android.app.Activity;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Menutup aktivitas interstitial / full screen milik SDK iklan sebelum
 * tampil. Ini satu-satunya lapisan yang bisa mencegah iklan layar penuh yang
 * dimuat oleh SDK tanpa view banner.
 */
public final class ActivityBlocking
{
    /**
     * Prefix nama aktivitas iklan yang dikenal. Dikelompokkan menurut SDK
     * agar mudah diperbarui saat SDK mengganti nama kelasnya.
     */
    private static final List<String> KNOWN_ACTIVITY_PREFIXES = new ArrayList<>(Arrays.asList(
            // Google AdMob / GMS
            "com.google.android.gms.ads.AdActivity",

            // ByteDance / Pangle.
            //
            // SDK 5.x+ menaruh aktivitas iklan di "...activity.single.*", bukan
            // "...activity.*". Prefix lama tidak pernah cocok, sehingga reward
            // video Pangle tetap tampil walau semua kelas iklan lain sudah
            // diblokir. Kedua bentuk dicantumkan agar versi lama pun tertangani.
            "com.bytedance.sdk.openadsdk.activity.single.TTInterstitialActivity",
            "com.bytedance.sdk.openadsdk.activity.single.TTFullScreenVideoActivity",
            "com.bytedance.sdk.openadsdk.activity.single.TTRewardVideoActivity",
            "com.bytedance.sdk.openadsdk.activity.single.TTAppOpenAdActivity",
            "com.bytedance.sdk.openadsdk.activity.single.TTRewardExpressVideoActivity",
            "com.bytedance.sdk.openadsdk.activity.single.TTFullScreenExpressVideoActivity",
            "com.bytedance.sdk.openadsdk.activity.TTInterstitialActivity",
            "com.bytedance.sdk.openadsdk.activity.TTFullScreenVideoActivity",
            "com.bytedance.sdk.openadsdk.activity.TTRewardVideoActivity",
            "com.bytedance.sdk.openadsdk.activity.TTAppOpenAdActivity",
            "com.bytedance.sdk.openadsdk.activity.TTRewardExpressVideoActivity",

            // Mintegral / Mobvista (nama kelas nyata pada SDK modern).
            "com.mbridge.msdk.interstitial.view.MBInterstitialActivity",
            "com.mbridge.msdk.reward.player.MBRewardVideoActivity",
            "com.mbridge.msdk.config.activity.MBRewardVideoActivity",
            "com.mintegral.msdk.activity.MTGInterstitialActivity",
            "com.mintegral.msdk.activity.MTGRewardActivity",
            "com.mintegral.msdk.activity.MTGAppOpenAdActivity",

            // Tapjoy
            "com.tapjoy.TJAdUnitActivity",
            "com.tapjoy.TJContentActivity",

            // Tencent GDT
            "com.qq.e.ads.ADActivity",
            "com.qq.e.ads.rewardvideo.RewardVideoADActivity",
            "com.qq.e.ads.PortraitADActivity",
            "com.qq.e.ads.LandscapeADActivity",

            // Unity Ads
            "com.unity3d.services.ads.adunit.AdUnitActivity",
            "com.unity3d.ads.adunit.AdUnitActivity",

            // AppLovin MAX
            "com.applovin.adview.AppLovinInterstitialActivity",
            "com.applovin.adview.AppLovinFullscreenActivity",
            "com.applovin.mediation.MaxAdActivity",
            "com.applovin.impl.adview.activity.FullscreenAdActivity",

            // ironSource
            "com.ironsource.sdk.controller.ControllerActivity",
            "com.ironsource.sdk.controller.InterstitialActivity",
            "com.ironsource.sdk.controller.OpenUrlActivity",

            // Vungle
            "com.vungle.warren.ui.VungleActivity",
            "com.vungle.ads.internal.ui.PresenterAdOpenActivity",

            // Chartboost / AdColony / Smaato / Fyber / MoPub / AppNext
            "com.chartboost.sdk.impl.CBActivity",
            "com.adcolony.sdk.AdColonyInterstitialActivity",
            "com.smaato.sdk.interstitial.InterstitialActivity",
            "com.fyber.inneractive.sdk.activities.FullscreenActivity",
            "com.fyber.inneractive.sdk.activities.InternalStoreActivity",
            "com.mopub.mobileads.MoPubActivity",
            "com.mopub.common.MoPubBrowser",
            "com.appnext.ads.interstitial.InterstitialActivity",

            // TopOn
            "com.anythink.interstitial.activity.ATInterstitialActivity",
            "com.anythink.rewardvideo.activity.ATRewardVideoActivity",
            "com.anythink.splashad.activity.ATSplashActivity",

            // TradPlus (mediator)
            "com.tradplus.ads.mgr.interstitial.views.InterNativeActivity",
            "com.tradplus.ads.mgr.interstitial.views.InterNativeAPI26Activity",

            // Facebook Audience Network
            "com.facebook.ads.AudienceNetworkActivity",

            // InMobi
            "com.inmobi.ads.rendering.InMobiAdActivity",

            // AppLovin fullscreen
            "com.applovin.adview.AppLovinFullscreenImmersiveActivity"
    ));

    private ActivityBlocking() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    public static void blockInterstitialActivities(final String packageName, final XC_LoadPackage.LoadPackageParam lpparam)
    {
        Util.hookMethod("android.app.Activity", lpparam.classLoader, "onCreate", new Object[]{
                Bundle.class,
                new XC_MethodHook()
                {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param)
                    {
                        Activity activity = (Activity) param.thisObject;
                        if (activity == null)
                        {
                            return;
                        }

                        String name = activity.getClass().getName();
                        String network = classify(name);
                        if (network == null)
                        {
                            return;
                        }

                        Util.log(packageName, "menutup aktivitas iklan: " + name);
                        Util.countBlockedAd(activity, packageName, network);
                        Util.notifyRemoveAdView(activity, packageName, 1);

                        // finish() sebelum konten dimuat menghindari animasi
                        // transisi dan membebaskan memori lebih cepat.
                        //
                        // JANGAN memakai param.setResult(null) di sini: itu
                        // melewati Activity.onCreate() sehingga mCalled tidak
                        // pernah diset, dan ActivityThread melempar
                        // SuperNotCalledException — crash di proses aplikasi
                        // induk, bukan sekadar menutup aktivitas iklan.
                        activity.finish();
                    }
                }
        });
    }

    /**
     * @return nama jaringan bila aktivitas ini milik SDK iklan, selain itu null.
     */
    public static String classify(String activityName)
    {
        if (activityName == null)
        {
            return null;
        }

        for (String prefix : KNOWN_ACTIVITY_PREFIXES)
        {
            if (activityName.startsWith(prefix))
            {
                return networkFor(prefix);
            }
        }

        // Aktivitas khusus yang ditambahkan pengguna lewat preferensi.
        for (String prefix : BlockPrefs.get().getUserPrefixes())
        {
            if (activityName.startsWith(prefix))
            {
                return "Custom";
            }
        }

        return null;
    }

    private static String networkFor(String prefix)
    {
        if (prefix.startsWith("com.google.") || prefix.startsWith("com.google.android.gms"))
        {
            return "GoogleAdmob";
        }
        if (prefix.startsWith("com.bytedance") || prefix.startsWith("com.pangle"))
        {
            return "Pangle";
        }
        if (prefix.startsWith("com.mintegral") || prefix.startsWith("com.mbridge"))
        {
            return "Mintegral";
        }
        if (prefix.startsWith("com.tapjoy"))
        {
            return "Tapjoy";
        }
        if (prefix.startsWith("com.qq.e"))
        {
            return "TencentGDT";
        }
        if (prefix.startsWith("com.unity3d"))
        {
            return "UnityAds";
        }
        if (prefix.startsWith("com.applovin"))
        {
            return "Applovin";
        }
        if (prefix.startsWith("com.ironsource"))
        {
            return "Ironsource";
        }
        if (prefix.startsWith("com.vungle"))
        {
            return "Vungle";
        }
        if (prefix.startsWith("com.chartboost"))
        {
            return "Chartboost";
        }
        if (prefix.startsWith("com.adcolony"))
        {
            return "Adcolony";
        }
        if (prefix.startsWith("com.smaato"))
        {
            return "Smaato";
        }
        if (prefix.startsWith("com.fyber"))
        {
            return "Fyber";
        }
        if (prefix.startsWith("com.mopub"))
        {
            return "MoPub";
        }
        if (prefix.startsWith("com.appnext"))
        {
            return "Appnext";
        }
        if (prefix.startsWith("com.anythink"))
        {
            return "TopOn";
        }
        if (prefix.startsWith("com.tradplus"))
        {
            return "TradPlus";
        }
        if (prefix.startsWith("com.facebook"))
        {
            return "Facebook";
        }
        if (prefix.startsWith("com.inmobi"))
        {
            return "Inmobi";
        }
        return "Interstitial";
    }
}
