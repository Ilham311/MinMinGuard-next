package tw.fatminmin.xposed.minminguard.next.core.hook;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Penelusuran hierarki kelas untuk pencarian method yang akan di-hook.
 * <p>
 * Kelas ini <strong>tidak boleh</strong> meng-import {@code de.robv.android.xposed}
 * atau {@code android.*}. Isinya logika murni yang dijalankan di JVM biasa
 * supaya semantik ART tentang {@code ClassLoader} bisa diuji tanpa perangkat.
 * Itulah satu-satunya cara regresi 3.1.3 (hook ke kelas framework tidak pernah
 * terpasang) bisa tertangkap sebelum dirilis.
 *
 * <h3>Semantik ClassLoader di ART</h3>
 * Kelas framework ({@code android.*}, {@code java.*}) dimuat bootstrap class
 * loader, sehingga {@link Class#getClassLoader()} mengembalikan {@code null}.
 * Kelas SDK iklan dan kelas aplikasi dimuat application class loader dan
 * mengembalikan loader yang tidak null.
 * <p>
 * Aturan yang benar:
 * <ul>
 *   <li>kelas <em>awal</em> selalu diproses, termasuk bila ia kelas framework —
 *       {@code android.view.ViewGroup#addView} justru target utama modul ini;</li>
 *   <li>penelusuran berhenti saat <em>naik</em> ke leluhur yang dimuat
 *       bootstrap, karena leluhur framework tidak pernah berisi method SDK
 *       iklan dan refleksinya mahal.</li>
 * </ul>
 * Versi yang salah (3.1.3) membalik keduanya: ia memeriksa kelas awal dan
 * berhenti di depth 0, sehingga setiap hook ke kelas framework hilang tanpa
 * pesan.
 */
public final class HierarchyWalk
{
    /** Batas kedalaman agar hierarki yang sangat dalam tidak memakan waktu. */
    public static final int MAX_DEPTH = 8;

    private HierarchyWalk()
    {
        throw new AssertionError("No instances");
    }

    /**
     * @return {@code true} bila {@code current} boleh diproses dan dijelajahi.
     *         Kelas awal selalu boleh; leluhur hanya bila bukan kelas bootstrap.
     */
    public static boolean shouldClimb(Class<?> start, Class<?> current)
    {
        if (current == null)
        {
            return false;
        }
        return current == start || current.getClassLoader() != null;
    }

    /**
     * Mengumpulkan deklarasi method konkret yang perlu di-hook, dari kelas
     * paling turunan ke kelas dasar.
     * <p>
     * Deklarasi yang ditimpa di kelas turunan menutup deklarasi kelas dasar
     * dengan signature sama: aplikasi memanggil method lewat objek kelas
     * turunan, dan memasang hook di kedua level membuat satu iklan terhitung
     * dua kali. Method abstrak dilewati (tidak punya badan untuk di-hook)
     * tetapi tetap menandai signature-nya, sama seperti perilaku sebelumnya.
     *
     * @return daftar {@link Method}, atau daftar kosong bila tidak ada.
     */
    public static List<Method> collect(Class<?> start, String methodName)
    {
        if (start == null || methodName == null)
        {
            return Collections.emptyList();
        }

        List<Method> result = new ArrayList<>();
        Set<String> handled = new HashSet<>();
        Class<?> current = start;

        for (int depth = 0; depth < MAX_DEPTH && shouldClimb(start, current); depth++)
        {
            for (Method method : declaredMethods(current))
            {
                if (!method.getName().equals(methodName))
                {
                    continue;
                }
                String signature = signature(method);
                if (!handled.add(signature))
                {
                    // Sudah ditangani deklarasi di kelas turunan.
                    continue;
                }
                if (Modifier.isAbstract(method.getModifiers()))
                {
                    continue;
                }
                result.add(method);
            }
            current = current.getSuperclass();
        }

        return result;
    }

    /**
     * Semua nama method yang dideklarasikan kelas ini beserta leluhurnya.
     * <p>
     * Dipakai untuk menyaring sebelum memasang hook: modul mencoba puluhan SDK
     * pada setiap proses aplikasi, dan sebagian besar tidak dipakai aplikasi
     * itu. Nama yang tidak ada tidak perlu ditelusuri.
     */
    public static Set<String> names(Class<?> start)
    {
        if (start == null)
        {
            return Collections.emptySet();
        }

        Set<String> names = new LinkedHashSet<>();
        Class<?> current = start;

        for (int depth = 0; depth < MAX_DEPTH && shouldClimb(start, current); depth++)
        {
            for (Method method : declaredMethods(current))
            {
                names.add(method.getName());
            }
            current = current.getSuperclass();
        }

        return names;
    }

    /**
     * Nama method beserta tipe parameternya, untuk mencocokkan overload.
     * Tipe balik sengaja tidak disertakan: override di Java tidak boleh
     * mengubahnya, dan mengikutkannya akan memisahkan deklarasi yang
     * sebenarnya saling menimpa.
     */
    public static String signature(Method method)
    {
        StringBuilder builder = new StringBuilder(method.getName());
        builder.append('(');
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++)
        {
            if (i > 0)
            {
                builder.append(',');
            }
            builder.append(types[i].getName());
        }
        builder.append(')');
        return builder.toString();
    }

    /**
     * {@code getDeclaredMethods()} bisa melempar {@code NoClassDefFoundError}
     * atau {@code SecurityException} pada kelas SDK yang rusak/ter-obfuscate.
     * Satu kelas bermasalah tidak boleh membatalkan pencarian leluhurnya.
     */
    private static Method[] declaredMethods(Class<?> clazz)
    {
        try
        {
            return clazz.getDeclaredMethods();
        }
        catch (Throwable ignored)
        {
            return EMPTY_METHODS;
        }
    }

    private static final Method[] EMPTY_METHODS = new Method[0];
}
