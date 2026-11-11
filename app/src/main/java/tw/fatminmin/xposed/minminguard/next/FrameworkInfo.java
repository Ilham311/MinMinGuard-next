package tw.fatminmin.xposed.minminguard.next;

import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.core.os.EnvironmentCompat;

import java.io.File;
import java.util.ArrayList;

import de.robv.android.xposed.XposedBridge;

/**
 * Reports which Xposed-compatible framework and root manager are present.
 */
public final class FrameworkInfo
{
    private FrameworkInfo() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    public static int getXposedVersion()
    {
        try
        {
            return XposedBridge.getXposedVersion();
        }
        catch (Throwable t)
        {
            return -1;
        }
    }

    public static String getFrameworkName(Context context)
    {
        String name;
        int version = getXposedVersion();

        if (isPackageInstalled(context, "org.lsposed.manager")
                || isPackageInstalled(context, "org.lsposed.lspd")
                || isPackageInstalled(context, "io.github.lsposed.manager"))
        {
            name = "LSPosed / Vector";
        }
        else if (isPackageInstalled(context, "com.elderdrivers.riru.edxp")
                || isPackageInstalled(context, "org.meowcat.edxposed.manager"))
        {
            name = "EdXposed";
        }
        else if (isPackageInstalled(context, "de.robv.android.xposed.installer"))
        {
            name = "Xposed";
        }
        else
        {
            name = null;
        }

        if (version < 0)
        {
            return "not active";
        }
        if (name != null)
        {
            return name + " (API " + version + ")";
        }
        return "Xposed-compatible (API " + version + ")";
    }

    public static String getRootManager(Context context)
    {
        ArrayList<String> managers = new ArrayList<>();

        if (isPackageInstalled(context, "com.topjohnwu.magisk") || new File("/data/adb/magisk").exists())
        {
            managers.add("Magisk");
        }
        if (isPackageInstalled(context, "me.weishu.kernelsu") || new File("/data/adb/ksu").exists())
        {
            managers.add("KernelSU");
        }
        if (isPackageInstalled(context, "me.bmax.apatch") || new File("/data/adb/ap").exists())
        {
            managers.add("APatch");
        }

        return managers.isEmpty() ? EnvironmentCompat.MEDIA_UNKNOWN : TextUtils.join(" + ", managers);
    }

    public static String describe(Context context)
    {
        if (context == null)
        {
            return "";
        }
        return context.getString(R.string.msg_runtime, getFrameworkName(context), getRootManager(context));
    }

    private static boolean isPackageInstalled(Context context, String pkgName)
    {
        try
        {
            context.getPackageManager().getPackageInfo(pkgName, 0);
            return true;
        }
        catch (PackageManager.NameNotFoundException e)
        {
            return false;
        }
    }
}
