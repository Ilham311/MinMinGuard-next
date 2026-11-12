package tw.fatminmin.xposed.minminguard.next.blocker.custom_mod;

import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import tw.fatminmin.xposed.minminguard.next.blocker.Util;

//FIXME Use newer XposedHelper class
public final class OneWeather
{
    private static final String LAYOUT = "com.handmark.expressweather";

    private OneWeather() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    public static boolean handleLoadPackage(final String packageName, LoadPackageParam lpparam)
    {
        Class<?> billing = Util.findClassOrNull(
                "com.handmark.expressweather.billing.BillingUtils", lpparam.classLoader);
        if (billing == null)
        {
            return false;
        }
        Util.hookAllMethods(billing, "isPurchased", new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                Util.setResultCompat(param, Boolean.TRUE);
            }
        });
        return true;
    }

    public static void handleInitPackageResources(InitPackageResourcesParam resparam)
    {
        if (!resparam.packageName.equals(LAYOUT))
        {
            return;
        }

        resparam.res.hookLayout(LAYOUT, "layout", "main_phone", new XC_LayoutInflated()
        {

            @Override
            public void handleLayoutInflated(LayoutInflatedParam liparam)
            {

                View ad = liparam.view.findViewById(liparam.res.getIdentifier("adview", "id", LAYOUT));
                if (ad != null)
                {
                    ad.setVisibility(View.GONE);
                }

                ad = liparam.view.findViewById(liparam.res.getIdentifier("share_ad_cover", "id", LAYOUT));
                if (ad != null)
                {
                    ad.setVisibility(View.GONE);
                }
            }
        });
    }
}
