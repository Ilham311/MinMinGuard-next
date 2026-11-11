package tw.fatminmin.xposed.minminguard.next.blocker;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Utilitas yang dipakai di dalam proses aplikasi yang di-hook.
 * <p>
 * Semua method di sini harus tetap aman dipanggil walaupun framework Xposed
 * tidak aktif (kelas ini juga dimuat oleh UI modul sendiri).
 */
public final class Util
{
    public static final String TAG = "MinMinGuard";

    /** Log debug detail. Diubah dari dialog pengaturan. */
    public static volatile boolean DEBUG = false;

    private static final Set<String> sHookedMethods = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /**
     * Cache hasil pencarian kelas per ClassLoader.
     * <p>
     * Setiap aplikasi yang di-hook mencoba memasang belasan SDK iklan yang
     * umumnya tidak dipakai aplikasi itu. {@code findClass} untuk kelas yang
     * tidak ada melempar {@code ClassNotFoundError}, dan melempar exception
     * sangat mahal dibanding satu lookup map. Hasil negatif diingat per
     * ClassLoader (aplikasi) dan otomatis hilang saat proses berakhir.
     */
    private static final Map<ClassLoader, Map<String, Class<?>>> sClassCache =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, Map<String, Class<?>>>());

    private Util() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    /** Di-hook menjadi {@code true} saat modul berjalan di prosesnya sendiri. */
    public static Boolean xposedEnabled()
    {
        return false;
    }

    public static void log(String packageName, String msg)
    {
        if (DEBUG)
        {
            XposedBridge.log(TAG + " [" + packageName + "] " + msg);
        }
    }

    /** Pesan penting yang selalu dicatat, mis. kegagalan konfigurasi. */
    public static void warn(String msg)
    {
        try
        {
            XposedBridge.log(TAG + " " + msg);
        }
        catch (Throwable ignored)
        {
        }
    }

    public static Application getCurrentApplication()
    {
        try
        {
            return AndroidAppHelper.currentApplication();
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    /**
     * Mencatat bahwa sebuah jaringan iklan dipakai aplikasi ini.
     * <p>
     * Dipanggil saat hook dipasang, jadi ini <em>bukan</em> bukti ada iklan
     * yang diblokir: kelas SDK hanya ditemukan. Karena itu hanya nama jaringan
     * yang ditambahkan ke {@code AD_NETWORKS}, tanpa menambah {@code HIT_NUM}.
     * Hitungan sesungguhnya datang dari jalur yang benar-benar memblokir.
     */
    public static void notifyAdNetwork(Context context, String pkgName, String adNetwork)
    {
        StatsReporter.get().detectNetwork(pkgName, adNetwork);
    }

    /** Mencatat satu iklan yang benar-benar diblokir pada sebuah jaringan. */
    public static void countBlockedAd(Context context, String pkgName, String adNetwork)
    {
        StatsReporter.get().recordNetwork(pkgName, adNetwork);
    }

    public static void notifyRemoveAdView(Context context, String pkgName, int blockNum)
    {
        StatsReporter.get().recordBlock(pkgName, blockNum);
    }

    /**
     * Mencari kelas sekali per ClassLoader; hasil negatif di-cache.
     *
     * @return kelas, atau {@code null} bila tidak ada di ClassLoader tersebut.
     */
    public static Class<?> findClassOrNull(String className, ClassLoader classLoader)
    {
        Map<String, Class<?>> cache;
        synchronized (sClassCache)
        {
            cache = sClassCache.get(classLoader);
            if (cache == null)
            {
                cache = new ConcurrentHashMap<>();
                sClassCache.put(classLoader, cache);
            }
        }

        Class<?> cached = cache.get(className);
        if (cached != null)
        {
            return cached == MISSING ? null : cached;
        }

        try
        {
            Class<?> clazz = XposedHelpers.findClass(className, classLoader);
            cache.put(className, clazz);
            return clazz;
        }
        catch (Throwable t)
        {
            // Penanda negatif: cukup satu kali exception untuk kelas yang sama.
            cache.put(className, MISSING);
            return null;
        }
    }

    /** Penanda "kelas tidak ada" di {@link #findClassOrNull}. */
    private static final Class<?> MISSING = Void.class;

    /**
     * Memasang hook pada semua overload sebuah method dan melaporkan kegagalan
     * sebagai {@code false} alih-alih melempar.
     */
    public static boolean hookAllMethods(String className, ClassLoader classLoader, String method, XC_MethodHook callback)
    {
        String key = className + "#" + method + "@" + System.identityHashCode(callback);
        if (!sHookedMethods.add(key))
        {
            // Callback yang sama persis sudah terpasang; mencegah hook ganda
            // saat onCreate dipanggil lebih dari sekali (mis. proses restart).
            return true;
        }

        try
        {
            Class<?> clazz = findClassOrNull(className, classLoader);
            if (clazz == null)
            {
                sHookedMethods.remove(key);
                return false;
            }
            XposedBridge.hookAllMethods(clazz, method, callback);
            return true;
        }
        catch (Throwable t)
        {
            sHookedMethods.remove(key);
            log("mmg", "hookAllMethods " + className + "#" + method + " gagal: " + t);
            return false;
        }
    }

    /**
     * Memasang hook pada satu method dengan signature tertentu.
     */
    public static boolean hookMethod(String className, ClassLoader classLoader, String method,
                                     Object[] parameterTypesAndCallback)
    {
        if (findClassOrNull(className, classLoader) == null)
        {
            return false;
        }
        try
        {
            XposedHelpers.findAndHookMethod(className, classLoader, method, parameterTypesAndCallback);
            return true;
        }
        catch (Throwable t)
        {
            log("mmg", "hookMethod " + className + "#" + method + " gagal: " + t);
            return false;
        }
    }

    /**
     * Mengisi nilai balik yang "aman" untuk tipe balik method yang di-hook.
     * <p>
     * Xposed melakukan cast hasil ke tipe balik method; memanggil
     * {@code setResult(new Object())} pada method yang mengembalikan
     * {@code boolean}/{@code int}/void akan memicu ClassCastException di
     * aplikasi target. Ini adalah bug laten pada versi sebelumnya.
     */
    public static void setDefaultResult(XC_MethodHook.MethodHookParam param)
    {
        Class<?> returnType = returnTypeOf(param);

        if (returnType == null || returnType == void.class || returnType == Void.class)
        {
            param.setResult(null);
        }
        else if (returnType == boolean.class || returnType == Boolean.class)
        {
            param.setResult(Boolean.FALSE);
        }
        else if (returnType == int.class || returnType == Integer.class)
        {
            param.setResult(0);
        }
        else if (returnType == long.class || returnType == Long.class)
        {
            param.setResult(0L);
        }
        else if (returnType == short.class || returnType == Short.class)
        {
            param.setResult((short) 0);
        }
        else if (returnType == byte.class || returnType == Byte.class)
        {
            param.setResult((byte) 0);
        }
        else if (returnType == char.class || returnType == Character.class)
        {
            param.setResult((char) 0);
        }
        else if (returnType == float.class || returnType == Float.class)
        {
            param.setResult(0f);
        }
        else if (returnType == double.class || returnType == Double.class)
        {
            param.setResult(0d);
        }
        else if (returnType.isPrimitive())
        {
            // Tidak ada konversi aman; biarkan method asli berjalan.
            return;
        }
        else
        {
            param.setResult(null);
        }
    }

    /**
     * Seperti {@link #setDefaultResult} tetapi memakai nilai tertentu bila
     * tipenya cocok dengan tipe balik method.
     */
    public static void setResultCompat(XC_MethodHook.MethodHookParam param, Object value)
    {
        Class<?> returnType = returnTypeOf(param);
        if (returnType == null || returnType == void.class)
        {
            param.setResult(null);
            return;
        }
        if (value == null)
        {
            if (!returnType.isPrimitive())
            {
                param.setResult(null);
            }
            return;
        }
        if (returnType.isInstance(value))
        {
            param.setResult(value);
        }
        else
        {
            setDefaultResult(param);
        }
    }

    /** Tipe balik method yang sedang di-hook, atau {@code null} bila tak diketahui. */
    public static Class<?> returnTypeOfMethod(XC_MethodHook.MethodHookParam param)
    {
        return returnTypeOf(param);
    }

    /**
     * Menandai sebuah pasangan kelas#method sudah dipasangi hook.
     *
     * @return {@code true} bila pemanggil pertama kali memasangnya
     */
    public static boolean markHookedOnce(String key)
    {
        return sHookedMethods.add(key);
    }

    private static Class<?> returnTypeOf(XC_MethodHook.MethodHookParam param)
    {
        Member member = param.method;
        if (member instanceof Method)
        {
            return ((Method) member).getReturnType();
        }
        return null;
    }
}
