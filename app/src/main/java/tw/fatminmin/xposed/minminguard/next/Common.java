package tw.fatminmin.xposed.minminguard.next;

import android.content.Context;
import android.content.res.Resources;

/**
 * Konstanta bersama antara proses hook (zygote) dan proses UI.
 * Kelas ini sengaja tidak boleh bergantung pada kelas UI apa pun, karena
 * dimuat juga di dalam proses aplikasi lain.
 */
public class Common
{
    public final static String PACKAGE_NAME = BuildConfig.APPLICATION_ID;

    /** Otoritas ContentProvider satu-satunya. */
    public final static String AUTHORITY = PACKAGE_NAME;
    public final static String URI_STATS = "content://" + AUTHORITY + "/stats";
    public final static String URI_NETWORKS = "content://" + AUTHORITY + "/networks";
    /**
     * Daftar aplikasi yang benar-benar di-hook modul. Inilah satu-satunya
     * sumber "aplikasi target" untuk UI: berkas konfigurasi LSPosed hanya bisa
     * dibaca root, sedangkan paket yang di-hook bisa dicatat modul sendiri.
     */
    public final static String URI_ACTIVE = "content://" + AUTHORITY + "/active";

    public final static String MOD_PREFS = "ModSettings";
    public final static String UI_PREFS = "UI_PREF";

    // UI Pref
    public final static String KEY_FIRST_TIME = "first_time_2_0_alpha_7";
    public final static String KEY_SHOW_LAUNCHER_ICON = "show_launcher_icon";
    public final static String KEY_ENABLE_CRASH_REPORT = "enable_crash_report";
    public final static String KEY_SORT = "sort";
    public final static String KEY_THEME = "theme";
    public final static String VALUE_SORT_NAME = "name";
    public final static String VALUE_SORT_PACKAGE = "package";
    public final static String VALUE_SORT_BLOCKED = "blocked";

    public final static String VALUE_THEME_SYSTEM = "system";
    public final static String VALUE_THEME_LIGHT = "light";
    public final static String VALUE_THEME_DARK = "dark";

    // Xposed Mod Pref
    public final static String KEY_USER_BLOCK_PREFIXES = "user_block_prefixes";
    public final static String KEY_HEURISTIC = "heuristic_blocking";
    public final static String KEY_VERBOSE_LOG = "verbose_log";

    // Kontrak MinMinProvider
    public final static String KEY_PKG_NAME = "PKG_NAME";
    public final static String KEY_NETWORK = "AD_NETWORKS";
    public final static String KEY_BLOCK_NUM = "BLOCK_NUM";
    /** Delta hitungan untuk satu jaringan pada satu pemanggilan update(). */
    public final static String KEY_NETWORK_COUNT = "NETWORK_COUNT";
    /**
     * Bila true, jaringan hanya dicatat sebagai "ada di aplikasi ini" tanpa
     * menambah HIT_NUM. Dipakai saat hook dipasang, karena menemukan kelas SDK
     * bukan berarti ada iklan yang benar-benar diblokir.
     */
    public final static String KEY_NETWORK_DETECT = "NETWORK_DETECT";
    /** Nama versi aplikasi yang di-hook, dikirim bersama pendaftaran paket aktif. */
    public final static String KEY_APP_VERSION = "APP_VERSION";

    // Nama kolom cursor yang dikembalikan URI_STATS / URI_NETWORKS / URI_ACTIVE
    public final static String COL_PKG_NAME = "PKG_NAME";
    public final static String COL_AD_NETWORKS = "AD_NETWORKS";
    public final static String COL_BLOCK_NUM = "BLOCK_NUM";
    public final static String COL_LAST_SEEN = "LAST_SEEN";
    public final static String COL_FIRST_SEEN = "FIRST_SEEN";
    public final static String COL_APP_VERSION = "APP_VERSION";
    public final static String COL_NETWORK = "NETWORK";
    public final static String COL_NETWORK_HIT = "HIT_NUM";

    /** Akhiran kunci preferensi per aplikasi. */
    public final static String SUFFIX_URL = "_url";
    public final static String SUFFIX_DNS = "_dns";

    private Common() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    public static String getUrlFilterKey(String pkgName)
    {
        return pkgName + SUFFIX_URL;
    }

    public static String getDnsFilterKey(String pkgName)
    {
        return pkgName + SUFFIX_DNS;
    }

    /**
     * Mode penulisan preferensi modul.
     * <p>
     * Pada bridge Xposed &lt; 93 nilainya tetap MODE_PRIVATE; LSPosed &gt;= 93
     * mem-hook method ini di proses modul dan mengembalikan MODE_WORLD_READABLE
     * supaya berkas preferensi bisa dibaca proses lain.
     */
    public static Integer getPrefMode()
    {
        return Context.MODE_PRIVATE;
    }

    public static boolean isDarkTheme(String theme)
    {
        if (VALUE_THEME_LIGHT.equals(theme))
        {
            return false;
        }
        if (VALUE_THEME_DARK.equals(theme))
        {
            return true;
        }
        return (Resources.getSystem().getConfiguration().uiMode & 0x30) == 0x20;
    }
}
