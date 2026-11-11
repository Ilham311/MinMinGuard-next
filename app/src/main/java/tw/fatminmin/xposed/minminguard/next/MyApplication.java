package tw.fatminmin.xposed.minminguard.next;

import android.app.Application;

import java.io.PrintWriter;
import java.io.StringWriter;

import de.robv.android.xposed.XposedBridge;

/**
 * Created by fatminmin on 2015/10/3.
 */
public class MyApplication extends Application
{
    @Override
    public void onCreate()
    {
        super.onCreate();

        // Crashlytics was removed (EOL). Optionally mirror crashes into the Xposed log instead.
        if (getSharedPreferences(Common.UI_PREFS, Common.getPrefMode())
                .getBoolean(Common.KEY_ENABLE_CRASH_REPORT, false))
        {
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
            {
                private final Thread.UncaughtExceptionHandler mDefault =
                        Thread.getDefaultUncaughtExceptionHandler();

                @Override
                public void uncaughtException(Thread thread, Throwable throwable)
                {
                    try
                    {
                        StringWriter writer = new StringWriter();
                        throwable.printStackTrace(new PrintWriter(writer));
                        XposedBridge.log("MinMinGuard Next crash in " + thread.getName() + ":\n" + writer.toString());
                    }
                    catch (Throwable ignored)
                    {
                    }

                    if (mDefault != null)
                    {
                        mDefault.uncaughtException(thread, throwable);
                    }
                }
            });
        }
    }
}
