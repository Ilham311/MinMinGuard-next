package tw.fatminmin.xposed.minminguard.next.core.hook;

import java.util.List;

/**
 * Satu-satunya pintu ke framework hooking.
 * <p>
 * Implementasi legacy membungkus {@code XposedBridge}/{@code XposedHelpers};
 * implementasi modern (libxposed API 102) membungkus interceptor chain.
 * Keduanya memakai semantik penelusuran yang sama dari {@link HierarchyWalk},
 * sehingga layer pemblokiran, plan jaringan, dan pelaporan tidak perlu berubah
 * saat engine berganti.
 * <p>
 * Semua method mengembalikan {@link HookResult} dan tidak pernah melempar:
 * satu SDK yang tidak ada di proses ini adalah hal normal, bukan error.
 */
public interface HookEngine
{
    /** Mencari kelas; {@code null} bila tidak ada. Hasil negatif di-cache. */
    Class<?> findClass(String className);

    /**
     * Memasang hook pada semua overload konkret bernama {@code methodName},
     * termasuk yang diwarisi dari kelas dasar.
     */
    HookResult hookAll(HookResult.Layer layer, Class<?> clazz, String methodName, HookCallback callback);

    /** Seperti {@link #hookAll} tetapi menerima nama kelas. */
    HookResult hookAll(HookResult.Layer layer, String className, String methodName, HookCallback callback);

    /** Memasang hook pada satu method dengan signature parameter tertentu. */
    HookResult hookExact(HookResult.Layer layer, Class<?> clazz, String methodName,
                         Class<?>[] parameterTypes, HookCallback callback);

    /** Memasang hook pada konstruktor dengan signature tertentu. */
    HookResult hookConstructor(HookResult.Layer layer, Class<?> clazz, Class<?>[] parameterTypes,
                               HookCallback callback);

    /** Nama method yang ada di kelas ini dan leluhurnya. */
    List<String> methodNames(Class<?> clazz);

    /** Menulis ke log framework. */
    void log(String message);
}
