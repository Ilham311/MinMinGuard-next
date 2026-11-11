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
     * Kunci method yang sudah dipasangi hook oleh jenis callback tertentu.
     * <p>
     * Penelusuran hierarki membuat satu method dasar bisa ditemukan dari
     * beberapa jenis iklan sekaligus: {@code BaseAdView#loadAd} tercapai dari
     * {@code AdView} maupun {@code AdManagerAdView}. Tanpa penjagaan ini
     * callback terpasang dua kali pada method yang sama, sehingga satu iklan
     * terhitung dua kali di statistik.
     * <p>
     * Kuncinya menyertakan kelas callback, bukan sekadar {@code Method}:
     * pemanggil berbeda memang sengaja memasang aksi berbeda pada method yang
     * sama (mis. {@code show()} diblokir sekaligus dilaporkan), dan memakai
     * {@code Method} saja akan membuang salah satunya.
     */
    private static final Set<String> sHookedMembers = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

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

    /**
     * Versi framework Xposed yang dibaca saat {@code initZygote}.
     * <p>
     * Disimpan di sini, bukan di {@code Main}, supaya proses UI bisa
     * membacanya tanpa memuat kelas {@code Main} — kelas itu mengimplementasikan
     * antarmuka framework yang tidak tersedia bila proses tersebut tidak di-hook.
     * Nilai {@code null} berarti belum diketahui di proses ini.
     */
    public static volatile Integer frameworkVersion;

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

    /**
     * Cadangan bila {@link AndroidAppHelper#currentApplication()} belum/tidak
     * mengembalikan apa pun.
     * <p>
     * Dulu seluruh pemblokiran bergantung pada helper itu. Bila nilainya null
     * saat {@code Application.onCreate} (terjadi pada sebagian ROM dan pada
     * proses yang dibuat lewat {@code ActivityThread} kustom), modul keluar
     * lebih awal dan aplikasi itu tidak pernah diblokir sama sekali — tanpa
     * pesan apa pun. Pemanggil sekarang bisa menyerahkan objek Application
     * yang sedang dibuat sebagai cadangan.
     */
    private static volatile Application sFallbackApplication;

    public static void setFallbackApplication(Application application)
    {
        if (application != null)
        {
            sFallbackApplication = application;
        }
    }

    public static Application getCurrentApplication()
    {
        try
        {
            Application application = AndroidAppHelper.currentApplication();
            if (application != null)
            {
                return application;
            }
        }
        catch (Throwable ignored)
        {
        }
        return sFallbackApplication;
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

            // XposedBridge.hookAllMethods hanya melihat getDeclaredMethods()
            // pada kelas yang diberikan. Method warisan karena itu tidak pernah
            // ter-hook: setAdListener pada AdView dideklarasikan di BaseAdView,
            // loadAd() pada NativeAd di NativeAdBase, dan load() pada
            // MBRewardVideoHandler di NonBidAdHandler. Tanpa menelusuri
            // hierarki ini, listener banner AdMob dan native Facebook tidak
            // pernah tertangkap, sehingga pemuatan yang diblokir tidak memberi
            // kabar apa pun dan UI berputar. Pemuatan Mintegral bahkan tidak
            // pernah diblokir sama sekali.
            //
            // Kedua arah TIDAK dicakup: penelusuran hanya berjalan ke atas
            // (kelas dasar). Method yang ditimpa di kelas turunan tidak ikut
            // ter-hook karena deklarasi turunan tidak memanggil super. Kasus
            // nyata dari APK: BaseFullscreenAd#load menimpa BaseAd#load,
            // IronSourceAdapter menimpa 11 method AbstractAdapter, dan
            // AdManagerInterstitialAd menimpa InterstitialAd#load. Kelas-kelas
            // seperti itu didaftarkan eksplisit di blocker masing-masing.
            return hookHierarchy(clazz, method, callback);
        }
        catch (Throwable t)
        {
            sHookedMethods.remove(key);
            log("mmg", "hookAllMethods " + className + "#" + method + " gagal: " + t);
            return false;
        }
    }

    /**
     * Memasang hook pada deklarasi method bernama ini di hierarki kelas.
     * <p>
     * Method abstrak dilewati satu per satu. {@code hookAllMethods} melempar
     * {@code IllegalArgumentException} untuk method seperti itu dan
     * menghentikan seluruh pemasangan — persis yang terjadi pada
     * {@code RewardedAd.show()}/{@code InterstitialAd.show()}: tidak satu pun
     * hook terpasang, tetapi kegagalannya tercatat seolah method tidak ada.
     * <p>
     * Overload dicocokkan per <em>signature</em>, bukan per nama. Untuk
     * signature yang ditimpa di kelas turunan, hanya deklarasi paling turunan
     * yang di-hook: aplikasi memanggil metode itu lewat objek kelas turunan,
     * dan bila kelas turunan memanggil {@code super}, memasang hook di kedua
     * level membuat satu iklan terhitung dua kali.
     * <p>
     * Hierarki juga dihentikan sebelum mencapai kelas framework/Android:
     * {@code BaseAdView} turun dari {@code android.view.ViewGroup}, dan
     * {@code getDeclaredMethods()} pada kelas framework adalah panggilan
     * refleksi mahal sekaligus berisiko memasang hook di luar cakupan modul.
     *
     * @return {@code true} bila minimal satu overload terpasang
     */
    private static boolean hookHierarchy(Class<?> clazz, String methodName, XC_MethodHook callback)
    {
        boolean hooked = false;
        // Signature yang sudah ditangani oleh kelas turunan, supaya deklarasi
        // yang ditimpa di kelas dasar tidak di-hook dua kali.
        Set<String> handled = new java.util.HashSet<>();
        Class<?> current = clazz;

        for (int depth = 0; current != null && depth < 8; depth++)
        {
            ClassLoader loader = current.getClassLoader();
            if (loader == null)
            {
                // Kelas framework dimuat bootstrap; tidak relevan untuk SDK iklan.
                break;
            }

            try
            {
                for (Method method : current.getDeclaredMethods())
                {
                    if (!method.getName().equals(methodName))
                    {
                        continue;
                    }
                    String signature = signatureOf(method);
                    if (!handled.add(signature))
                    {
                        // Sudah ditangani deklarasi di kelas turunan.
                        continue;
                    }
                    if (java.lang.reflect.Modifier.isAbstract(method.getModifiers()))
                    {
                        continue;
                    }
                    if (!sHookedMembers.add(current.getName() + "#" + signature
                            + "@" + callback.getClass().getName()))
                    {
                        hooked = true;
                        continue;
                    }
                    try
                    {
                        XposedBridge.hookMethod(method, callback);
                        hooked = true;
                    }
                    catch (Throwable t)
                    {
                        log("mmg", "hookMethod " + current.getName() + "#" + methodName + " gagal: " + t);
                    }
                }
            }
            catch (Throwable ignored)
            {
            }

            current = current.getSuperclass();
        }

        return hooked;
    }

    /**
     * Semua nama method yang dideklarasikan di kelas ini dan kelas dasarnya.
     * <p>
     * Dipakai untuk menyaring sebelum memasang hook. {@code captureAdListener}
     * mencoba belasan nama setter pada setiap kelas SDK, dan tanpa saringan ini
     * setiap nama yang tidak ada tetap menelusuri seluruh hierarki kelas lewat
     * refleksi. Pada aplikasi yang memuat banyak SDK (MiChat: TradPlus,
     * AppLovin, ironSource, Mintegral, Pangle, Facebook, GMS) itu berarti
     * ratusan penelusuran hierarki yang semuanya berakhir kosong, dan biayanya
     * dibayar saat proses aplikasi dibuat.
     * <p>
     * Hierarki dihentikan di kelas framework, sama seperti
     * {@link #hookHierarchy}: {@code getDeclaredMethods()} pada kelas Android
     * mahal dan tidak pernah berisi setter listener iklan.
     */
    public static Set<String> methodNamesInHierarchy(Class<?> clazz)
    {
        Set<String> cached = sMethodNames.get(clazz);
        if (cached != null)
        {
            return cached;
        }
        Set<String> names = new java.util.HashSet<>();
        Class<?> current = clazz;
        for (int depth = 0; current != null && depth < 8; depth++)
        {
            if (current.getClassLoader() == null)
            {
                break;
            }
            try
            {
                for (Method method : current.getDeclaredMethods())
                {
                    names.add(method.getName());
                }
            }
            catch (Throwable ignored)
            {
            }
            current = current.getSuperclass();
        }
        sMethodNames.put(clazz, names);
        return names;
    }

    /** Hasil {@link #methodNamesInHierarchy} per kelas. */
    private static final Map<Class<?>, Set<String>> sMethodNames =
            Collections.synchronizedMap(new WeakHashMap<Class<?>, Set<String>>());

    /** Nama method beserta tipe parameternya, untuk mencocokkan overload. */
    private static String signatureOf(Method method)    {
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
     * Method yang hanya <em>melaporkan status</em> ({@code isReady},
     * {@code isEnabled}, {@code getLoadedCount}) tidak boleh dihitung sebagai
     * iklan yang diblokir.
     * <p>
     * Versi sebelumnya menaikkan hitungan untuk setiap panggilan seperti itu;
     * aplikasi yang menanyakan kesiapan iklan berulang kali (hal biasa pada
     * alur reward) membuat angka statistik membengkak tanpa ada satu iklan pun
     * yang benar-benar dicegah.
     */
    public static boolean isStateQuery(String methodName)
    {
        if (methodName == null || methodName.isEmpty())
        {
            return false;
        }
        return methodName.startsWith("is")
                || methodName.startsWith("get")
                || methodName.startsWith("has")
                || methodName.startsWith("can")
                || methodName.startsWith("should");
    }

    /**
     * Setter/listener pendaftaran ({@code setAdListener},
     * {@code registerViewForInteraction}) bukan aksi menampilkan iklan.
     */
    public static boolean isCallbackSetter(String methodName)
    {
        if (methodName == null || methodName.isEmpty())
        {
            return false;
        }
        return methodName.startsWith("set")
                || methodName.startsWith("register")
                || methodName.startsWith("add");
    }

    /** True bila panggilan method ini layak dihitung sebagai satu blokir. */
    public static boolean countsAsBlock(String methodName)
    {
        return !isStateQuery(methodName) && !isCallbackSetter(methodName);
    }

    /**
     * Method yang <em>memuat</em> iklan.
     * <p>
     * Hanya jalur muat yang butuh notifikasi callback gagal-muat: aplikasi
     * menunggu {@code onAdLoaded}/{@code onAdFailedToLoad} setelah memanggil
     * method ini.
     * <p>
     * Awalan {@code init} ikut dihitung karena sebagian mediator menamai
     * pemuatannya begitu: ironSource memakai {@code initInterstitial},
     * {@code initRewardedVideoWithCallback}, dan {@code initBanners}. Tanpa itu
     * method tersebut diblokir tanpa pernah memberi kabar, dan adapter yang
     * menunggu callback tetap menggantung.
     */
    public static boolean isLoadPath(String methodName)
    {
        if (methodName == null || methodName.isEmpty())
        {
            return false;
        }
        String lower = methodName.toLowerCase(java.util.Locale.US);
        return lower.contains("load")
                || lower.equals("request")
                || lower.equals("preload")
                || lower.startsWith("init");
    }

    /**
     * Method yang <em>menampilkan</em> iklan.
     * <p>
     * Ini pun perlu notifikasi, tetapi dengan callback gagal-tampil yang
     * berbeda. Aplikasi menunggu {@code onAdClosed}/{@code onAdReward} setelah
     * memanggil {@code show()}; bila method itu diblokir tanpa kabar, layar
     * reward tetap terbuka dan pengguna melihat putaran tanpa ujung.
     */
    public static boolean isShowPath(String methodName)
    {
        if (methodName == null || methodName.isEmpty())
        {
            return false;
        }
        String lower = methodName.toLowerCase(java.util.Locale.US);
        return lower.startsWith("show") || lower.startsWith("present");
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
