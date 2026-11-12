package tw.fatminmin.xposed.minminguard.next.core.hook;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Ledger adalah satu-satunya cara modul menyadari bahwa sebuah layer mati.
 * Tes ini mengunci kebijakan "kelas ada tapi hook gagal = wajib perhatian".
 */
public class HookLedgerTest
{
    private static HookResult ok(HookResult.Layer layer)
    {
        return HookResult.installed(layer, "com.example.Ad", "loadAd", 2);
    }

    @Test
    public void installedAddsOverloadCount()
    {
        HookLedger ledger = HookLedger.get();
        ledger.reset();
        ledger.record(ok(HookResult.Layer.NAME));
        ledger.record(ok(HookResult.Layer.NAME));

        assertEquals(4, ledger.installedCount(HookResult.Layer.NAME));
        assertFalse(ledger.hasAttentionFailures());
    }

    @Test
    public void missingClassIsNormalAndNotAttention()
    {
        HookLedger ledger = HookLedger.get();
        ledger.reset();
        ledger.record(HookResult.missingClass(HookResult.Layer.SDK_API, "com.tidak.Ada", "loadAd"));

        assertEquals(0, ledger.installedCount(HookResult.Layer.SDK_API));
        assertEquals(1, ledger.failureCount(HookResult.Layer.SDK_API));
        assertFalse("SDK yang tidak dipakai aplikasi bukan masalah",
                ledger.hasAttentionFailures());
    }

    @Test
    public void missingMethodOnExistingClassIsNotAttention()
    {
        // getByNameOnNet tidak ada sebelum Android 8: normal, bukan bug.
        HookLedger ledger = HookLedger.get();
        ledger.reset();
        ledger.record(HookResult.missingMethod(HookResult.Layer.DNS,
                "java.net.InetAddress", "getByNameOnNet"));

        assertFalse(ledger.hasAttentionFailures());
    }

    @Test
    public void existingMethodThatFailedToHookNeedsAttention()
    {
        // Inilah kondisi 3.1.3 yang tidak pernah terlihat: ViewGroup#addView
        // ada, kelasnya ada, tetapi nol overload terpasang.
        HookLedger ledger = HookLedger.get();
        ledger.reset();
        ledger.record(HookResult.failed(HookResult.Layer.NAME,
                "android.view.ViewGroup", "addView", new IllegalArgumentException("nol hook")));

        assertTrue(ledger.hasAttentionFailures());
        assertEquals(1, ledger.failures().size());
    }

    @Test
    public void hookFailureNeedsAttention()
    {
        HookLedger ledger = HookLedger.get();
        ledger.reset();
        ledger.record(HookResult.failed(HookResult.Layer.DNS,
                "java.net.InetAddress", "getByName", new RuntimeException("boom")));

        assertTrue(ledger.hasAttentionFailures());
    }

    @Test
    public void failuresAreCapped()
    {
        HookLedger ledger = HookLedger.get();
        ledger.reset();
        for (int i = 0; i < 200; i++)
        {
            ledger.record(HookResult.failed(HookResult.Layer.SDK_API, "c" + i, "m",
                    new RuntimeException("boom")));
        }
        assertEquals("memori proses target harus dibatasi", 64, ledger.failures().size());
    }

    @Test
    public void summaryIsReadable()
    {
        HookLedger ledger = HookLedger.get();
        ledger.reset();
        ledger.record(ok(HookResult.Layer.NAME));
        ledger.record(HookResult.missingClass(HookResult.Layer.WEBVIEW, "x", "y"));

        String summary = ledger.summary();
        assertTrue(summary, summary.contains("NAME=2/0"));
        assertTrue(summary, summary.contains("WEBVIEW=0/1"));
    }
}
