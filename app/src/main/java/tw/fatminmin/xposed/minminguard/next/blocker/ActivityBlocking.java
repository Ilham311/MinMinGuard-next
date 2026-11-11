package tw.fatminmin.xposed.minminguard.next.blocker;

import android.content.Context;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.Common;
import tw.fatminmin.xposed.minminguard.next.Main;

/**
 * Finishes known interstitial / full screen ad activities before they are created.
 */
public final class ActivityBlocking
{
    private static final List<String> KNOWN_ACTIVITY_PREFIXES = new ArrayList<>(Arrays.asList(
            "com.google.android.gms.ads.AdActivity",
            "com.bytedance.sdk.openadsdk.activity.TTInterstitialActivity",
            "com.bytedance.sdk.openadsdk.activity.TTFullScreenVideoActivity",
            "com.bytedance.sdk.openadsdk.activity.TTRewardVideoActivity",
            "com.bytedance.sdk.openadsdk.activity.TTAppOpenAdActivity",
            "com.mintegral.msdk.activity.MTGInterstitialActivity",
            "com.mintegral.msdk.activity.MTGRewardActivity",
            "com.tapjoy.TJAdUnitActivity",
            "com.tapjoy.TJContentActivity",
            "com.qq.e.ads.ADActivity",
            "com.qq.e.ads.rewardvideo.RewardVideoADActivity",
            "com.unity3d.services.ads.adunit.AdUnitActivity"
    ));

    private ActivityBlocking() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    public static void blockInterstitialActivities(final String packageName, final XC_LoadPackage.LoadPackageParam lpparam)
    {
        try
        {
            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook()
            {
                @Override
                protected void beforeHookedMethod(MethodHookParam param)
                {
                    android.app.Activity activity = (android.app.Activity) param.thisObject;
                    String name = activity.getClass().getName();

                    if (matches(name, packageName, lpparam))
                    {
                        Util.log(packageName, "Finishing ad activity: " + name);
                        Util.notifyRemoveAdView(null, packageName, 1);
                        activity.finish();
                    }
                }
            });
        }
        catch (Throwable t)
        {
            Util.log(packageName, "ActivityBlocking: " + t);
        }
    }

    private static boolean matches(String activityName, String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        for (String prefix : KNOWN_ACTIVITY_PREFIXES)
        {
            if (activityName.startsWith(prefix))
            {
                return true;
            }
        }

        // User defined prefixes are stored in the module prefs.
        try
        {
            XSharedPreferences prefs = new XSharedPreferences(Main.class.getPackage().getName(), Common.MOD_PREFS);
            if (prefs.getFile().canRead())
            {
                String prefixes = prefs.getString(Common.KEY_USER_BLOCK_PREFIXES, "");
                if (prefixes != null)
                {
                    for (String line : prefixes.split("\n"))
                    {
                        String prefix = line.trim();
                        if (prefix.length() > 0 && activityName.startsWith(prefix))
                        {
                            return true;
                        }
                    }
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return false;
    }
}
