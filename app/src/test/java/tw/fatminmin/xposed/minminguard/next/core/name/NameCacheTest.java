package tw.fatminmin.xposed.minminguard.next.core.name;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Mengunci perilaku cache klasifikasi nama kelas.
 * <p>
 * Cache ini bukan mikro-optimasi: tanpa dia satu view biasa menempuh 61
 * pemblokir × 2 panggilan virtual pada setiap {@code addView}, di thread UI.
 * Juga penting bahwa hasil negatif ikut disimpan — kalau tidak, view biasa
 * tetap diperiksa ulang selamanya dan cache tidak menolong sama sekali.
 */
public class NameCacheTest
{
    @Test
    public void unknownClassReturnsNull()
    {
        NameCache cache = new NameCache();
        assertNull(cache.lookup(String.class));
        assertFalse(cache.contains(String.class));
    }

    @Test
    public void positiveResultIsRemembered()
    {
        NameCache cache = new NameCache();
        cache.put(Integer.class, "GoogleAdmob");

        assertEquals("GoogleAdmob", cache.lookup(Integer.class));
        assertTrue(cache.contains(Integer.class));
        assertEquals(1, cache.size());
    }

    /**
     * Inti desain: hasil negatif harus tersimpan sebagai "sudah diperiksa",
     * bukan dianggap "belum diperiksa". Dibedakan lewat string kosong.
     */
    @Test
    public void negativeResultIsRememberedDistinctFromUnknown()
    {
        NameCache cache = new NameCache();
        cache.put(Long.class, null);

        assertEquals("", cache.lookup(Long.class));
        assertTrue(cache.contains(Long.class));
        // Kelas lain tetap "belum diperiksa".
        assertNull(cache.lookup(Double.class));
    }

    @Test
    public void invalidateClearsEverything()
    {
        NameCache cache = new NameCache();
        cache.put(Integer.class, "Applovin");
        cache.put(Long.class, null);

        cache.invalidate();

        assertNull(cache.lookup(Integer.class));
        assertNull(cache.lookup(Long.class));
        assertEquals(0, cache.size());
    }

    @Test
    public void nullTypeIsSafe()
    {
        NameCache cache = new NameCache();
        cache.put(null, "X");
        assertNull(cache.lookup(null));
        assertFalse(cache.contains(null));
        assertEquals(0, cache.size());
    }
}
