package tw.fatminmin.xposed.minminguard.next.core.hook;

import java.lang.reflect.Executable;

/**
 * Konteks satu pemanggilan method yang di-hook.
 * <p>
 * Antarmuka ini sengaja bebas dari API framework. Adapter legacy
 * ({@code de.robv.android.xposed}) dan adapter modern (libxposed) sama-sama
 * memenuhinya, sehingga seluruh layer pemblokiran tidak perlu tahu framework
 * apa yang sedang memuat modul. Itulah yang membuat layer bisa diuji di JVM
 * dengan implementasi palsu.
 */
public interface HookContext
{
    /** Objek penerima; {@code null} untuk method statis dan konstruktor. */
    Object thisObject();

    /** Argumen pemanggilan, tidak pernah {@code null}. */
    Object[] args();

    /** Argumen ke-{@code index}, atau {@code null} bila di luar batas. */
    Object arg(int index);

    /** {@code true} bila argumen ke-{@code index} ada dan bukan {@code null}. */
    boolean hasArg(int index);

    /** Method/konstruktor yang sedang berjalan. */
    Executable method();

    /** Tipe balik method, atau {@code null} bila tidak diketahui. */
    Class<?> returnType();

    /** Mengganti nilai balik dan melewati badan method asli. */
    void setResult(Object value);

    /** Nilai balik asli (hanya bermakna pada hook {@code after}). */
    Object getResult();

    /** Melempar exception dari method asli sebagai gantinya. */
    void setThrowable(Throwable throwable);

    /** {@code true} bila {@link #setResult(Object)} sudah dipanggil. */
    boolean hasResult();
}
