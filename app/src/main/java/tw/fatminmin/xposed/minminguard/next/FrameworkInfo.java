package tw.fatminmin.xposed.minminguard.next;

import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.regex.Pattern;

import de.robv.android.xposed.XposedBridge;
import tw.fatminmin.xposed.minminguard.next.blocker.Util;

/**
 * Reports whether the module is actually running, which Xposed-compatible
 * framework hosts it, and which root manager is present.
 * <p>
 * The previous implementation asked {@link XposedBridge#getXposedVersion()}
 * directly. That is a native call which can fail in the module's own process
 * even while the module is clearly running there (hooks installed, ads being
 * blocked), and the failure was reported as "not active" — the exact opposite
 * of the truth. The module now treats its own hook
 * ({@link Util#xposedEnabled()}) as the primary evidence of being active and
 * only uses the bridge to learn the API level, with a static-field fallback.
 * <p>
 * Root detection has the same "hidden is not absent" problem: manager
 * packages are commonly hidden from {@code PackageManager}, so package names
 * alone are not enough. KernelSU, Magisk and APatch also leave tokens in
 * {@code /proc/self/mountinfo}, which any app can read without root.
 */
public final class FrameworkInfo
{
    private FrameworkInfo() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    /**
     * True when the module code really runs inside this process.
     * <p>
     * {@link Util#xposedEnabled()} is hooked to {@code true} by the module
     * itself when the framework loads it into the module's own process, so it
     * is direct proof rather than an inference from the environment.
     */
    public static boolean isFrameworkActive()
    {
        try
        {
            if (Boolean.TRUE.equals(Util.xposedEnabled()))
            {
                return true;
            }
        }
        catch (Throwable ignored)
        {
        }
        return queryBridgeVersion() > 0;
    }

    public static int getXposedVersion()
    {
        int version = queryBridgeVersion();
        if (version > 0)
        {
            return version;
        }
        // Diisi saat initZygote. Dibaca dari Util, bukan dari Main: memuat
        // kelas Main di proses UI yang tidak di-hook bisa gagal karena ia
        // mengimplementasikan antarmuka framework yang tidak ada di sana.
        Integer fromZygote = Util.frameworkVersion;
        if (fromZygote != null && fromZygote > 0)
        {
            return fromZygote;
        }
        return -1;
    }

    /**
     * Hasil {@link #queryBridgeVersion()}; {@link Integer#MIN_VALUE} berarti
     * belum ditanyakan.
     * <p>
     * Di proses UI yang tidak di-hook, kelas {@code XposedBridge} tidak ada di
     * APK (dependensinya {@code compileOnly}) sehingga setiap panggilan
     * melempar {@code NoClassDefFoundError}. Baris runtime disegarkan pada
     * setiap perubahan statistik; melempar exception berulang di thread utama
     * itu pemborosan. Hasil pertama diingat karena framework tidak berubah
     * selama proses hidup.
     */
    private static volatile int sBridgeVersion = Integer.MIN_VALUE;

    private static int queryBridgeVersion()
    {
        int cached = sBridgeVersion;
        if (cached != Integer.MIN_VALUE)
        {
            return cached;
        }
        int version = -1;
        try
        {
            version = XposedBridge.getXposedVersion();
        }
        catch (Throwable ignored)
        {
            // Native call unavailable in this process; fall through.
        }
        if (version <= 0)
        {
            try
            {
                version = XposedBridge.XPOSED_BRIDGE_VERSION;
            }
            catch (Throwable ignored)
            {
                version = -1;
            }
        }
        sBridgeVersion = version;
        return version;
    }

    /**
     * @param moduleReported true when at least one app has already reported
     *                       blocking statistics. That is proof the module runs
     *                       in other processes even when this process itself is
     *                       not hooked (the module's own app is only hooked if
     *                       it, too, is in scope).
     */
    public static String getFrameworkName(Context context, boolean moduleReported)
    {
        int version = getXposedVersion();
        String name = detectedFrameworkName(context);

        if (name == null)
        {
            // Manajer bisa tidak terpasang sebagai paket biasa (LSPosed
            // menyembunyikan dirinya dari aplikasi lain), dan proses UI modul
            // tidak selalu di-hook: modul hanya dimuat di paket yang ada di
            // scope. Laporan dari aplikasi lain tetap bukti yang sah bahwa
            // framework berjalan, jadi jangan menulis "tidak aktif".
            if (!moduleReported && !isFrameworkActive())
            {
                return context.getString(R.string.framework_not_detected);
            }
            name = context.getString(R.string.framework_active);
        }

        return version > 0 ? name + " (API " + version + ")" : name;
    }

    /** Nama framework hasil deteksi; {@code null} bila tak dikenal. */
    private static volatile String sFrameworkName;
    private static volatile boolean sFrameworkNameDetected;

    private static String detectedFrameworkName(Context context)
    {
        if (!sFrameworkNameDetected)
        {
            sFrameworkName = detectFrameworkName(context);
            sFrameworkNameDetected = true;
        }
        return sFrameworkName;
    }

    /** Nama framework dari bukti yang tersedia; {@code null} bila tak dikenal. */
    private static String detectFrameworkName(Context context)
    {
        if (isPackageInstalled(context, "org.lsposed.manager")
                || isPackageInstalled(context, "org.lsposed.lspd")
                || isPackageInstalled(context, "io.github.lsposed.manager"))
        {
            return "LSPosed / Vector";
        }
        if (isPackageInstalled(context, "com.elderdrivers.riru.edxp")
                || isPackageInstalled(context, "org.meowcat.edxposed.manager"))
        {
            return "EdXposed";
        }
        if (isPackageInstalled(context, "de.robv.android.xposed.installer"))
        {
            return "Xposed";
        }

        // Manajer bisa disembunyikan. LSPosed tetap meninggalkan jejak di
        // berkas atau di mount proc; keduanya cukup untuk menyebut namanya.
        if (new File("/data/adb/lspd").exists()
                || new File("/data/adb/modules/zygisk_lsposed").exists()
                || new File("/data/adb/modules/riru_lsposed").exists())
        {
            return "LSPosed / Vector";
        }
        String mountInfo = mountInfo();
        if (containsToken(mountInfo, "lsposed") || containsToken(mountInfo, "lspd"))
        {
            return "LSPosed / Vector";
        }
        return null;
    }

    /** Hasil deteksi root; nama manajer tidak berubah selama proses hidup. */
    private static volatile String sRootManager;

    public static String getRootManager(Context context)
    {
        String cached = sRootManager;
        if (cached == null)
        {
            cached = detectRootManager(context);
            sRootManager = cached;
        }
        return cached;
    }

    private static String detectRootManager(Context context)
    {
        ArrayList<String> managers = new ArrayList<>();

        if (isPackageInstalled(context, "com.topjohnwu.magisk")
                || isPackageInstalled(context, "io.github.vvb2060.magisk")
                || isPackageInstalled(context, "io.github.huskydg.magisk")
                || new File("/data/adb/magisk").exists())
        {
            managers.add("Magisk");
        }
        if (isPackageInstalled(context, "me.weishu.kernelsu")
                || isPackageInstalled(context, "com.rifsxd.ksunext")
                || new File("/data/adb/ksu").exists())
        {
            managers.add("KernelSU");
        }
        if (isPackageInstalled(context, "me.bmax.apatch")
                || new File("/data/adb/ap").exists())
        {
            managers.add("APatch");
        }

        // Paket manajer bisa disembunyikan dari PackageManager. Nama provider
        // root tetap muncul di mount proc karena itu cara mount-nya bekerja.
        String mountInfo = mountInfo();
        if (!managers.contains("Magisk") && containsToken(mountInfo, "magisk"))
        {
            managers.add("Magisk");
        }
        if (!managers.contains("KernelSU") && containsToken(mountInfo, "ksu"))
        {
            managers.add("KernelSU");
        }
        if (!managers.contains("APatch") && containsToken(mountInfo, "apatch"))
        {
            managers.add("APatch");
        }

        if (!managers.isEmpty())
        {
            return TextUtils.join(" + ", managers);
        }

        // Tidak ada nama yang terbaca, tetapi perangkat jelas ber-root: `su`
        // ada di path sistem atau /data/adb bisa diakses. "Unknown" akan
        // menyesatkan, "manager hidden" menyatakan apa yang sebenarnya terjadi.
        return hasRootEvidence()
                ? context.getString(R.string.root_manager_hidden)
                : context.getString(R.string.root_not_detected);
    }

    /** Jejak root yang bisa dibaca aplikasi tanpa izin root. */
    private static boolean hasRootEvidence()
    {
        String[] suPaths = {
                "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/su/bin/su", "/debug_ramdisk/su"
        };
        for (String path : suPaths)
        {
            try
            {
                if (new File(path).exists())
                {
                    return true;
                }
            }
            catch (Throwable ignored)
            {
            }
        }
        try
        {
            if (new File("/data/adb").exists())
            {
                return true;
            }
        }
        catch (Throwable ignored)
        {
        }
        return false;
    }

    /**
     * Isi {@code /proc/self/mountinfo} yang sudah dibaca, atau {@code null}.
     * <p>
     * Dibaca sekali saja: baris runtime disegarkan setiap kali ringkasan
     * berubah, dan membaca berkas proc puluhan kilobita di thread utama pada
     * setiap penyegaran itu tidak ada gunanya. Daftar mount perangkat tidak
     * berubah selama proses hidup.
     */
    private static volatile String sMountInfo;

    private static String mountInfo()
    {
        String cached = sMountInfo;
        if (cached == null)
        {
            cached = readMountInfo();
            sMountInfo = cached;
        }
        return cached;
    }

    private static String readMountInfo()
    {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader("/proc/self/mountinfo"));
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line).append('\n');
            }
        }
        catch (Throwable ignored)
        {
            return "";
        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (Throwable ignored)
                {
                }
            }
        }
        // Locale.US wajib: pada locale Turki, toLowerCase() mengubah "I"
        // menjadi "ı" dan token seperti "MAGISK" tidak akan cocok.
        return sb.toString().toLowerCase(java.util.Locale.US);
    }

    /** Mencocokkan token utuh agar "ksu" tidak cocok dengan potongan path acak. */
    private static boolean containsToken(String haystack, String token)
    {
        if (haystack == null || haystack.isEmpty())
        {
            return false;
        }
        try
        {
            return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(token) + "(?![a-z0-9])")
                    .matcher(haystack).find();
        }
        catch (Throwable t)
        {
            return haystack.contains(token);
        }
    }

    public static String describe(Context context, boolean moduleReported)
    {
        if (context == null)
        {
            return "";
        }
        return context.getString(R.string.msg_runtime,
                getFrameworkName(context, moduleReported), getRootManager(context));
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
