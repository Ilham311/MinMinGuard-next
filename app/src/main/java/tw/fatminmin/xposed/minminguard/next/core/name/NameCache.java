package tw.fatminmin.xposed.minminguard.next.core.name;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cache hasil klasifikasi nama kelas per {@link Class}.
 * <p>
 * Klasifikasi dipanggil pada setiap {@code ViewGroup.addView}, setiap hasil
 * {@code LayoutInflater.inflate}, dan setiap keturunan dalam penelusuran pohon
 * sedalam 64 tingkat — di thread UI. Tanpa cache, satu view biasa (bukan
 * iklan, kasus paling umum) tetap harus menempuh 61 pemblokir × 2 panggilan
 * virtual, ditambah daftar prefix dan akhiran, ditambah dua
 * {@code BlockPrefs.get()} yang tersinkronisasi. Untuk daftar yang menggulir
 * itu berarti puluhan ribu operasi per detik dan dua monitor global per view.
 * <p>
 * Nama kelas tidak pernah berubah selama proses hidup, jadi hasilnya kunci
 * yang sempurna. Yang berubah hanyalah preferensi pengguna (prefix tambahan,
 * heuristik on/off), dan itu ditangani {@link #invalidate()} yang dipanggil
 * saat preferensi dimuat ulang.
 * <p>
 * Kunci memakai {@link WeakHashMap} atas {@code Class} agar kelas yang
 * di-unload (ClassLoader dilepas) tidak tertahan. Nilai {@code null} disimpan
 * sebagai penanda negatif: "sudah diperiksa, bukan iklan" — tanpa itu view
 * biasa akan tetap memeriksa ulang setiap kali.
 */
public final class NameCache
{
    /** Penanda untuk hasil negatif, karena WeakHashMap membolehkan null value. */
    private static final String NOT_AN_AD = "\u0000";

    private final Map<Class<?>, String> mByClass =
            Collections.synchronizedMap(new WeakHashMap<Class<?>, String>());

    /** Mencatat hasil klasifikasi; {@code network == null} berarti bukan iklan. */
    public void put(Class<?> type, String network)
    {
        if (type == null)
        {
            return;
        }
        mByClass.put(type, network == null ? NOT_AN_AD : network);
    }

    /**
     * @return jaringan iklan, string kosong bila sudah diperiksa dan bukan
     *         iklan, atau {@code null} bila belum pernah diperiksa
     */
    public String lookup(Class<?> type)
    {
        if (type == null)
        {
            return null;
        }
        String value = mByClass.get(type);
        if (value == null)
        {
            return null;
        }
        return NOT_AN_AD.equals(value) ? "" : value;
    }

    public boolean contains(Class<?> type)
    {
        return type != null && mByClass.containsKey(type);
    }

    /** Membuang seluruh hasil; dipanggil saat preferensi berubah. */
    public void invalidate()
    {
        mByClass.clear();
    }

    public int size()
    {
        return mByClass.size();
    }
}
