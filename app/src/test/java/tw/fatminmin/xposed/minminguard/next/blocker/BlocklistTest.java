package tw.fatminmin.xposed.minminguard.next.blocker;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pengujian daftar pola di JVM.
 * <p>
 * {@link Blocklist} tidak menyentuh Android atau Xposed, sehingga seluruh
 * perilaku pencocokannya bisa diuji tanpa perangkat. Ini menutup kelas bug
 * "blocklist salah format / pencocokan meleset" yang sebelumnya hanya terlihat
 * di perangkat.
 */
public class BlocklistTest
{
    private static Blocklist fresh()
    {
        // Singleton: buat instance baru lewat refleksi agar tes tidak saling
        // memengaruhi (INSTANCE di kelas aslinya privat dan dipakai bersama).
        try
        {
            java.lang.reflect.Constructor<Blocklist> ctor = Blocklist.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        }
        catch (Exception e)
        {
            throw new AssertionError("tidak bisa membuat Blocklist baru", e);
        }
    }

    @Test
    public void parsesHostsFileFormat()
    {
        Blocklist list = fresh();
        list.addLine("0.0.0.0 ads.example.com");
        assertTrue(list.isBlockedHost("ads.example.com"));
    }

    @Test
    public void parsesAdblockPlusFormat()
    {
        Blocklist list = fresh();
        list.addLine("||tracker.example^");
        assertTrue(list.isBlockedHost("tracker.example"));
    }

    @Test
    public void ignoresCommentsAndEmptyLines()
    {
        Blocklist list = fresh();
        list.addLine("# komentar");
        list.addLine("! komentar adblock");
        list.addLine("");
        list.addLine("   ");
        assertTrue("tidak ada entri yang harus tersimpan", list.size() == 0);
    }

    @Test
    public void wwwIsRedundant()
    {
        // Aset bawaan memuat 2.774 entri "www." yang menunjuk host yang sama.
        Blocklist list = fresh();
        list.addLine("www.example.com");
        list.addLine("example.com");
        assertTrue("duplikat www harus menyatu", list.size() == 1);
        assertTrue(list.isBlockedHost("example.com"));
        assertTrue(list.isBlockedHost("www.example.com"));
    }

    @Test
    public void subdomainMatchesParent()
    {
        Blocklist list = fresh();
        list.addLine("example.com");
        assertTrue(list.isBlockedHost("a.b.example.com"));
    }

    @Test
    public void unrelatedDomainDoesNotMatch()
    {
        Blocklist list = fresh();
        list.addLine("example.com");
        assertFalse(list.isBlockedHost("notexample.com"));
        assertFalse(list.isBlockedHost("example.com.evil.net"));
    }

    @Test
    public void pathPrefixIsMatched()
    {
        Blocklist list = fresh();
        list.addLine("ads.example.com/banner/");
        assertTrue(list.isBlockedUrl("https://ads.example.com/banner/1.png"));
        assertFalse(list.isBlockedUrl("https://ads.example.com/other/1.png"));
    }

    @Test
    public void urlWithPortIsMatched()
    {
        Blocklist list = fresh();
        list.addLine("ads.example.com");
        assertTrue(list.isBlockedUrl("https://ads.example.com:8443/x"));
    }

    @Test
    public void javascriptSchemeDoesNotThrow()
    {
        Blocklist list = fresh();
        list.addLine("ads.example.com");
        // java.net.URL melempar untuk skema ini; extractHost tidak boleh.
        assertFalse(list.isBlockedUrl("javascript:void(0)"));
    }

    @Test
    public void resourcePatternIsCaseInsensitive()
    {
        Blocklist list = fresh();
        list.addResourcePattern("FacebookAdCanary");
        assertTrue(list.containsPattern("<script>facebookadcanary.setup()</script>"));
        assertTrue(list.containsPattern("FACEBOOKADCANARY"));
    }

    @Test
    public void resourcePatternIgnoresComment()
    {
        Blocklist list = fresh();
        list.addResourcePattern("# bukan pola");
        assertFalse(list.containsPattern("# bukan pola"));
    }

    @Test
    public void emptyListBlocksNothing()
    {
        Blocklist list = fresh();
        assertFalse(list.isBlockedHost("ads.example.com"));
        assertFalse(list.isBlockedUrl("https://ads.example.com"));
    }
}
