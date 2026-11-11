package tw.fatminmin.xposed.minminguard.next.blocker.custom_mod;

import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import tw.fatminmin.xposed.minminguard.next.blocker.Util;

public final class _2chMate
{

    private _2chMate() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    public static boolean handleLoadPackage(final String packageName, LoadPackageParam lpparam)
    {
        if (!packageName.equals("jp.co.airfront.android.a2chMate"))
        {
            return false;
        }

        final Class<?> viewGroupClass = Util.findClassOrNull("android.view.ViewGroup", lpparam.classLoader);
        if (viewGroupClass == null)
        {
            return false;
        }

        XposedBridge.hookAllMethods(viewGroupClass, "addView", new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                if (param.args.length == 0 || !(param.args[0] instanceof View))
                {
                    return;
                }
                final View view = (View) param.args[0];
                if ("jp.syoboi.a2chMate.view.MyAdView".equals(view.getClass().getName()))
                {
                    Util.log(packageName, "Detect 2chmate MyAdView in " + packageName);
                    Util.setResultCompat(param, null);
                    Util.notifyRemoveAdView(null, packageName, 1);
                    Util.notifyAdNetwork(null, packageName, "2chMate");
                }
            }
        });
        Util.log(packageName, packageName + " is 2chmate");
        return true;
    }
}
