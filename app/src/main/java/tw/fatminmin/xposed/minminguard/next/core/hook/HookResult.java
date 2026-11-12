package tw.fatminmin.xposed.minminguard.next.core.hook;

/**
 * Hasil pemasangan satu hook.
 * <p>
 * Modul ini sebelumnya melaporkan keberhasilan sebagai {@code boolean}, dan
 * {@code false} tidak pernah sampai ke mana pun. Akibatnya rilis 3.1.3 bisa
 * kehilangan seluruh hook ke kelas framework tanpa satu pun tanda. Setiap
 * pemasangan sekarang menghasilkan catatan ini, dan laporan diagnostik
 * memakainya untuk memutuskan apakah sebuah layer benar-benar hidup.
 */
public final class HookResult
{
    /** Layer pemblokiran, dipakai untuk mengelompokkan diagnostik. */
    public enum Layer
    {
        /** Hook ke API SDK iklan. */
        SDK_API,
        /** Hook berbasis nama kelas view (ViewGroup/Activity/LayoutInflater). */
        NAME,
        /** Penyaringan URL WebView. */
        WEBVIEW,
        /** Pemblokiran DNS. */
        DNS,
        /** Aktivitas full screen milik SDK. */
        ACTIVITY,
        /** Patch khusus satu aplikasi. */
        APP_SPECIFIC
    }

    /** Alasan sebuah hook tidak terpasang. */
    public enum Reason
    {
        /** Minimal satu overload terpasang. */
        INSTALLED,
        /** Kelas tidak ada di ClassLoader proses ini (SDK tidak dipakai). */
        CLASS_MISSING,
        /** Kelas ada, tetapi tidak punya method bernama itu. */
        METHOD_MISSING,
        /** Method ada tetapi abstrak, atau semua overload gagal di-hook. */
        HOOK_FAILED
    }

    public final Layer layer;
    public final String className;
    public final String methodName;
    public final Reason reason;
    public final int installed;
    public final Throwable error;

    private HookResult(Layer layer, String className, String methodName, Reason reason,
                       int installed, Throwable error)
    {
        this.layer = layer;
        this.className = className;
        this.methodName = methodName;
        this.reason = reason;
        this.installed = installed;
        this.error = error;
    }

    public static HookResult installed(Layer layer, String className, String methodName, int count)
    {
        return new HookResult(layer, className, methodName, Reason.INSTALLED, count, null);
    }

    public static HookResult missingClass(Layer layer, String className, String methodName)
    {
        return new HookResult(layer, className, methodName, Reason.CLASS_MISSING, 0, null);
    }

    public static HookResult missingMethod(Layer layer, String className, String methodName)
    {
        return new HookResult(layer, className, methodName, Reason.METHOD_MISSING, 0, null);
    }

    public static HookResult failed(Layer layer, String className, String methodName, Throwable error)
    {
        return new HookResult(layer, className, methodName, Reason.HOOK_FAILED, 0, error);
    }

    public boolean isInstalled()
    {
        return reason == Reason.INSTALLED;
    }

    /**
     * Butuh perhatian: method <em>ada</em> di kelas itu tetapi tidak satu pun
     * overload berhasil di-hook. Kondisi ini selalu berarti bug modul atau
     * perubahan SDK, bukan "SDK tidak dipakai", dan karena itu wajib sampai ke
     * log tanpa menunggu mode verbose.
     * <p>
     * "Kelas tidak ada" dan "method tidak ada" sengaja <em>tidak</em> termasuk:
     * modul ini mencoba puluhan SDK dan method opsional (mis.
     * {@code getByNameOnNet} di bawah Android 8) pada setiap proses aplikasi,
     * dan memperlakukannya sebagai peringatan akan menenggelamkan sinyal yang
     * sebenarnya.
     */
    public boolean needsAttention()
    {
        return reason == Reason.HOOK_FAILED;
    }

    @Override
    public String toString()
    {
        return className + "#" + methodName + " -> " + reason
                + (installed > 0 ? " (" + installed + " overload)" : "");
    }
}
