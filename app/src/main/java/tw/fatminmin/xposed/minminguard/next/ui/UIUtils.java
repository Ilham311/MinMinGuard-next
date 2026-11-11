package tw.fatminmin.xposed.minminguard.next.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import tw.fatminmin.xposed.minminguard.next.R;

/**
 * Created by fatminmin on 2015/10/25.
 */
public final class UIUtils
{

    private UIUtils() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    static public void restartApp(Context context, String packageName)
    {
        if (context == null || packageName == null)
        {
            return;
        }

        // Context yang sampai di sini bisa berupa ContextThemeWrapper, bukan
        // Activity. Cast langsung dulu akan melempar ClassCastException dan
        // ketukan pada ikon tidak melakukan apa pun.
        Activity activity = context instanceof Activity ? (Activity) context : null;

        try
        {
            ActivityManager am = (ActivityManager) context.getSystemService(Activity.ACTIVITY_SERVICE);
            if (am != null)
            {
                am.killBackgroundProcesses(packageName);
            }
        }
        catch (Throwable ignored)
        {
            // Sebagian ROM membatasi ini; peluncuran tetap dicoba.
        }

        Intent it = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (it == null)
        {
            Toast.makeText(context, context.getString(R.string.msg_app_launch_fail), Toast.LENGTH_SHORT).show();
            return;
        }

        if (activity != null && activity.getCurrentFocus() != null)
        {
            Object ime = activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (ime instanceof InputMethodManager)
            {
                ((InputMethodManager) ime)
                        .hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
            }
        }

        it.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        try
        {
            context.startActivity(it);
        }
        catch (Throwable t)
        {
            Toast.makeText(context, context.getString(R.string.msg_app_launch_fail), Toast.LENGTH_SHORT).show();
        }
    }
}
