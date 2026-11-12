package tw.fatminmin.xposed.minminguard.next.blocker;

import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XSharedPreferences;
import tw.fatminmin.xposed.minminguard.next.Common;

/**
 * Akses tunggal ke preferensi modul dari dalam proses yang di-hook.
 * <p>
 * Menangani dua jalur XSharedPreferences sekaligus:
 * <ul>
 *   <li>bridge &lt; 93: berkas XML dibaca langsung dari
 *       {@code /data/user_de/0/<pkg>/shared_prefs/ModSettings.xml};</li>
 *   <li>bridge &gt;= 93: LSPosed menaruh berkas di direktori acak, jadi harus
 *       lewat konstruktor {@code XSharedPreferences(pkg, name)}.</li>
 * </ul>
 * Pembacaan ulang dibatasi (throttle) karena {@code reload()} memanggil stat()
 * yang mahal ketika SELinux enforcing. Nilai di-cache sebentar supaya
 * perubahan pengaturan tetap terasa tanpa memanggil stat di setiap view.
 */
public final class BlockPrefs
{
    private static final long RELOAD_INTERVAL_MS = 3_000L;

    private static BlockPrefs sInstance;

    private final XSharedPreferences mPrefs;
    private final Object mReloadLock = new Object();
    private volatile long mLastReload;
    private volatile boolean mReadable;

    private volatile List<String> mUserPrefixes;
    private volatile boolean mHeuristic;
    private volatile boolean mVerbose;

    private BlockPrefs()
    {
        mPrefs = open();
        mReadable = mPrefs != null;
        reload(true);
    }

    public static synchronized BlockPrefs get()
    {
        if (sInstance == null)
        {
            sInstance = new BlockPrefs();
        }
        return sInstance;
    }

    private static XSharedPreferences open()
    {
        XSharedPreferences prefs = null;

        // Jalur modern (LSPosed API >= 93) selalu dicoba lebih dulu: berkasnya
        // ada di direktori acak sehingga konstruktor File tidak akan menemukannya.
        try
        {
            prefs = new XSharedPreferences(Common.PACKAGE_NAME, Common.MOD_PREFS);
            if (prefs.getFile() != null && prefs.getFile().canRead())
            {
                return prefs;
            }
        }
        catch (Throwable ignored)
        {
        }

        // Jalur legacy: bridge lama menyimpan berkas di lokasi yang tetap.
        String dataDir = Build.VERSION.SDK_INT > 23 ? "user_de/0/" : "data/";
        try
        {
            File file = new File("/data/" + dataDir + Common.PACKAGE_NAME + "/shared_prefs/" + Common.MOD_PREFS + ".xml");
            if (file.canRead())
            {
                return new XSharedPreferences(file);
            }
        }
        catch (Throwable ignored)
        {
        }

        return prefs;
    }

    /**
     * Memuat ulang preferensi bila sudah melewati interval throttle.
     * Aman dipanggil sesering apa pun.
     */
    public void reload()
    {
        reload(false);
    }

    private void reload(boolean force)
    {
        if (mPrefs == null)
        {
            mReadable = false;
            return;
        }

        // Cek throttle tanpa lock: jalur ini dipanggil untuk setiap view yang
        // di-inflate, jadi mengambil monitor bersama setiap kali akan membuat
        // semua thread UI saling menunggu.
        long now = System.currentTimeMillis();
        if (!force && now - mLastReload < RELOAD_INTERVAL_MS)
        {
            return;
        }

        synchronized (mReloadLock)
        {
            now = System.currentTimeMillis();
            if (!force && now - mLastReload < RELOAD_INTERVAL_MS)
            {
                return;
            }
            mLastReload = now;

            try
            {
                mReadable = mPrefs.getFile() != null && mPrefs.getFile().canRead();
                if (!mReadable)
                {
                    return;
                }

                mPrefs.reload();

                String rawPrefixes = mPrefs.getString(Common.KEY_USER_BLOCK_PREFIXES, "");
                List<String> prefixes = new ArrayList<>();
                if (rawPrefixes != null)
                {
                    for (String line : rawPrefixes.split("\n"))
                    {
                        String prefix = line.trim();
                        if (!prefix.isEmpty())
                        {
                            prefixes.add(prefix);
                        }
                    }
                }
                boolean previousHeuristic = mHeuristic;
                List<String> previousPrefixes = mUserPrefixes;

                mUserPrefixes = Collections.unmodifiableList(prefixes);
                mHeuristic = mPrefs.getBoolean(Common.KEY_HEURISTIC, true);
                mVerbose = mPrefs.getBoolean(Common.KEY_VERBOSE_LOG, false);

                // Hasil klasifikasi nama kelas di-cache, dan dua nilai di atas
                // ikut menentukan hasil itu. Bila berubah, cache lama akan
                // menjawab dengan aturan yang sudah tidak berlaku sampai proses
                // aplikasi di-restart — perubahan pengaturan harus langsung
                // terasa, jadi cache dibuang di sini.
                if (previousHeuristic != mHeuristic
                        || previousPrefixes == null
                        || !previousPrefixes.equals(mUserPrefixes))
                {
                    NameBlocking.invalidateCache();
                }
            }
            catch (Throwable t)
            {
                Util.log("mmg", "reload preferensi gagal: " + t);
            }
        }
    }

    public List<String> getUserPrefixes()
    {
        reload();
        return mUserPrefixes == null ? Collections.<String>emptyList() : mUserPrefixes;
    }

    public boolean isHeuristicEnabled()
    {
        reload();
        return mHeuristic;
    }

    public boolean isVerbose()
    {
        reload();
        return mVerbose;
    }

    /**
     * On/off satu-satunya sekarang adalah keanggotaan scope LSPosed: bila
     * modul dijalankan di dalam proses sebuah paket, paket itu memang dipilih
     * pengguna. Karena itu method ini selalu {@code true}, dan hanya tersisa
     * sebagai titik tunggal bila nanti diperlukan pengecualian.
     * <p>
     * Sebelumnya ada tiga mode (auto/blacklist/whitelist) beserta sakelar per
     * aplikasi; UI-nya membingungkan dan mudah membuat modul tampak "tidak
     * berfungsi" padahal aplikasinya hanya belum dicentang.
     */
    public boolean isEnabledFor(String pkgName)
    {
        return pkgName != null && pkgName.length() > 0;
    }

    public boolean isUrlFilterEnabled(String pkgName)
    {
        reload();
        return getBoolean(Common.getUrlFilterKey(pkgName), false);
    }

    public boolean isDnsFilterEnabled(String pkgName)
    {
        reload();
        return getBoolean(Common.getDnsFilterKey(pkgName), false);
    }

    public String getString(String key, String defValue)
    {
        if (mPrefs == null || !mReadable)
        {
            return defValue;
        }
        try
        {
            return mPrefs.getString(key, defValue);
        }
        catch (Throwable ignored)
        {
            return defValue;
        }
    }

    public boolean getBoolean(String key, boolean defValue)
    {
        if (mPrefs == null || !mReadable)
        {
            return defValue;
        }
        try
        {
            return mPrefs.getBoolean(key, defValue);
        }
        catch (Throwable ignored)
        {
            return defValue;
        }
    }
}
