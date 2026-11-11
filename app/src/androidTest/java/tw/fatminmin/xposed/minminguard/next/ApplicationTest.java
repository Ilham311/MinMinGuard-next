package tw.fatminmin.xposed.minminguard.next;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * Instrumented smoke test: make sure the module package installed on the device
 * still matches the package name the Xposed hooks expect.
 */
@RunWith(AndroidJUnit4.class)
public class ApplicationTest
{
    @Test
    public void modulePackageMatchesApplicationId()
    {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals(Common.PACKAGE_NAME, context.getPackageName());
    }
}
