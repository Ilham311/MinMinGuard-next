package tw.fatminmin.xposed.minminguard.next.blocker;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;

/**
 * Memberi tahu aplikasi bahwa iklan gagal dimuat setelah modul memblokirnya.
 * <p>
 * Versi sebelumnya hanya menghentikan method {@code load()} dan tidak pernah
 * memanggil callback milik SDK. Aplikasi lalu menunggu {@code onAdLoaded} /
 * {@code onAdFailedToLoad} yang tidak akan pernah datang — itulah layar
 * "muter-muter" tanpa ujung pada alur iklan reward.
 * <p>
 * Masalah yang sama muncul satu lapis lebih dalam pada mediator: adapter
 * TradPlus/MAX/ironSource menunggu listener jaringan di bawahnya. Bila listener
 * itu tidak pernah diberi kabar, seluruh waterfall mediator menggantung dan
 * aplikasi berputar — walaupun aplikasinya sendiri tidak memakai SDK tersebut
 * secara langsung. Karena itu setiap pemblokiran {@code load} di modul ini harus
 * melewati {@link #notifyFromArgs} / {@link #notifyCaptured}.
 * <p>
 * Semua kegagalan di sini ditelan: lebih baik aplikasi tetap menunggu daripada
 * modul menjatuhkannya karena satu callback yang bentuknya tidak dikenal.
 */
public final class AdCallbacks
{
    /**
     * Jeda sebelum callback kegagalan dikirim.
     * <p>
     * SDK sungguhan butuh ratusan milidetik untuk gagal karena jaringan.
     * Menunda menjaga alur aplikasi (dan animasi loading-nya) tetap wajar, dan
     * mencegah aplikasi yang mengulang pemuatan berputar tanpa jeda sama
     * sekali.
     */
    private static final long NOTIFY_DELAY_MS = 350L;

    /** Kedalaman maksimum penelusuran field saat mencari listener. */
    private static final int MAX_FIELD_DEPTH = 2;

    /** Batas jumlah field yang diperiksa per objek. */
    private static final int MAX_FIELDS = 24;

    /**
     * Nama method callback kegagalan, diurutkan dari yang paling spesifik.
     * <p>
     * Daftar ini menyatukan penamaan beberapa SDK sekaligus karena pemanggilnya
     * tidak tahu callback mana yang sedang dipegang: Google ({@code
     * onAdFailedToLoad}), AppLovin MAX ({@code onAdLoadFailed}), TradPlus
     * ({@code onAdFailed} / {@code onAdLoadFailed}), ironSource ({@code
     * onInterstitialAdLoadFailed}), Mintegral ({@code onInterstitialLoadFail}),
     * Unity Ads ({@code onUnityAdsFailedToLoad}), Facebook ({@code onError}),
     * dan Pangle ({@code onError}).
     * <p>
     * {@code onAdAllLoaded} diletakkan paling akhir: pada TradPlus ia berarti
     * "seluruh waterfall selesai tanpa hasil", yaitu kabar gagal yang sah.
     */
    private static final String[] FAILURE_METHODS = {
            "onAdFailedToLoad",
            "onAdLoadFailed",
            "onInterstitialAdLoadFailed",
            "onRewardedVideoAdLoadFailed",
            "onInterstitialLoadFail",
            "onVideoLoadFail",
            "onUnityAdsFailedToLoad",
            "onBannerAdLoadFailed",
            "onNativeAdLoadFailed",
            "onSplashAdLoadFailed",
            "onAdFetchFailed",
            "onLoadFailed",
            "onAdFailed",
            "onResourceLoadFail",
            "onUnavailable",
            "onError",
            "onAdAllLoaded",
    };

    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    /**
     * Nama method callback "iklan ditutup".
     * <p>
     * Dipakai saat {@code show()} / {@code play()} diblokir. Aplikasi menunggu
     * salah satu nama ini untuk menutup layar loading reward-nya; tanpa kabar,
     * layar tersebut tetap terbuka dan pengguna melihat putaran tanpa ujung.
     * <p>
     * Nama-nama di sini diambil dari listener sungguhan di dalam APK:
     * <ul>
     *   <li>AppLovin MAX: {@code onAdHidden} (bukan {@code onAdClosed}) —
     *       ini yang dipakai listener reward;</li>
     *   <li>Vungle: {@code onAdEnd};</li>
     *   <li>AdMob: {@code onAdDismissedFullScreenContent};</li>
     *   <li>Unity Ads: {@code onUnityAdsShowComplete};</li>
     *   <li>TradPlus: {@code onAdClosed};</li>
     *   <li>Facebook/Domob: {@code onAdClose};</li>
     *   <li>Mintegral/InMobi: {@code onAdDismissed}.</li>
     * </ul>
     * Sebelumnya daftar ini hanya memuat {@code onAdClosed}/{@code onAdDismissed},
     * sehingga blokir {@code show()} pada MAX dan Vungle tidak pernah memberi
     * kabar apa pun dan spinner reward berputar sampai timeout SDK.
     * <p>
     * Callback hadiah ({@code onUserEarnedReward}, {@code onAdReward},
     * {@code onUserRewarded}) sengaja TIDAK ada: memberi hadiah tanpa iklan
     * ditonton bukan tujuan modul ini.
     * <p>
     * Nama yang tidak dipakai versi SDK tertentu tidak berbahaya: dispatch
     * mencobanya berurutan dan berhenti di nama pertama yang benar-benar ada
     * pada listener, jadi biayanya hanya satu pencarian di daftar yang sudah
     * di-cache. Nama-nama itu tetap dipertahankan untuk versi SDK lain.
     */
    private static final String[] DISMISS_METHODS = {
            // Yang paling sering dipakai listener reward sungguhan.
            "onAdHidden",
            "onAdEnd",
            "onAdClosed",
            "onAdDismissed",
            "onAdDismissedFullScreenContent",
            "onUnityAdsShowComplete",
            "onInterstitialClosed",
            "onInterstitialDismissed",
            // Varian per-tipe yang dipakai SDK lain untuk peristiwa yang sama,
            // semuanya diperiksa ada di APK sungguhan. Tanpa nama ini, SDK
            // tersebut tidak menerima kabar apa pun dan layar reward berputar.
            "onInterstitialAdClosed",
            "onInterstitialAdDismissed",
            "onInterstitialAdHidden",
            "onRewardedAdHidden",
            "onRewardedAdDismissed",
            "onRewardedInterstitialClosed",
            "onRewardedVideoAdClosed",
            "onRewardedVideoClosed",
            "onAdClose",
            "onAdSkipped",
            "onVideoClosed",
            "onClose",
            "onHide",
    };

    /**
     * Nama method callback "iklan gagal ditampilkan".
     * <p>
     * Dipakai bila listener tidak punya callback tutup sama sekali. Pada MAX
     * {@code onAdDisplayFailed} dan pada Vungle {@code onAdFailedToPlay}
     * keduanya berarti "penayangan tidak terjadi", dan aplikasi menanganinya
     * dengan menutup alur iklan — inilah fallback yang membuat layar reward
     * tetap bisa selesai ketika SDK tidak menyediakan callback tutup.
     */
    private static final String[] SHOW_FAILURE_METHODS = {
            "onAdDisplayFailed",
            "onAdFailedToPlay",
            "onAdFailedToShowFullScreenContent",
            "onAdFailedToShow",
            "onAdShowFailed",
            "onShowFailed",
            "onUnityAdsShowFailure",
    };

    /**
     * Notifikasi yang sudah dijadwalkan untuk sebuah callback.
     * <p>
     * Dipakai menggantikan pembatas berbasis waktu. Versi sebelumnya membuang
     * notifikasi bila datang kurang dari 250 ms setelah yang sebelumnya, dan
     * itu justru menciptakan bug yang dilaporkan: aplikasi menerima callback
     * gagal pertama, langsung memuat ulang, panggilan blokir kedua datang
     * dalam hitungan milidetik, notifikasinya dibuang, lalu aplikasi menunggu
     * callback yang tidak akan pernah datang — spinner berputar lagi.
     * <p>
     * Penjagaan ini hanya mencegah <em>duplikat dalam satu blokir</em> (jalur
     * argumen, listener tersimpan, dan field bisa menunjuk callback yang
     * sama). Setelah callback dijalankan penanda dilepas, sehingga percobaan
     * ulang aplikasi selalu mendapat jawabannya sendiri.
     */
    private static final Map<Object, Boolean> sPending =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    /**
     * Kunci percobaan -> jumlah notifikasi dalam jendela waktu terakhir.
     * <p>
     * Aplikasi yang mengulang pemuatan setelah menerima callback gagal akan
     * membuat objek callback baru setiap kali, sehingga hitungan per objek di
     * {@link #sPending} selalu kembali dari nol dan putaran tidak pernah
     * berhenti — inilah "muter-muter" yang dilaporkan pengguna.
     * <p>
     * Kuncinya adalah kelas callback + jenis callback + ID unit iklan, yang
     * bertahan lintas objek. Jumlah percobaan dipakai untuk memperpanjang jeda
     * secara eksponensial, sehingga aplikasi tetap menerima jawaban tetapi
     * frekuensi pengulangannya menurun dengan cepat alih-alih berputar tanpa
     * henti.
     */
    private static final Map<String, Backoff> sAttempts =
            Collections.synchronizedMap(new HashMap<String, Backoff>());

    /** Batas atas pengali jeda; 2^4 = 16 kali {@link #NOTIFY_DELAY_MS}. */
    private static final int MAX_BACKOFF_SHIFT = 4;

    /**
     * Objek iklan -> listener yang dipasang aplikasi lewat {@code setAdListener}
     * / {@code setListener}.
     * <p>
     * Sebagian SDK (AppLovin MAX, TradPlus, ironSource) tidak menerima callback
     * pada method {@code load()}, melainkan lewat setter terpisah. Tanpa
     * menangkapnya, tidak ada yang bisa diberi tahu saat pemuatan diblokir.
     */
    private static final Map<Object, Object> sListeners =
            Collections.synchronizedMap(new WeakHashMap<Object, Object>());

    /**
     * Nama kelas SDK -> listener yang dipasang untuk kelas itu, dikelompokkan
     * per tipe listener.
     * <p>
     * Setter pada beberapa SDK bersifat statis ({@code IronSource
     * .setISDemandOnlyInterstitialListener}, {@code FairBid Interstitial
     * .setInterstitialListener}), sehingga tidak ada {@code thisObject} untuk
     * dijadikan kunci. Tanpa jalur ini, pemuatan yang diblokir pada API statis
     * tidak bisa memberitahu siapa pun dan mediator menggantung.
     * <p>
     * Satu kelas bisa punya beberapa listener sekaligus — {@code AdLoader}
     * memasang {@code AdListener} lewat {@code withAdListener} dan listener
     * native lewat {@code forNativeAd}. Karena itu kuncinya adalah tipe
     * listener, bukan sekadar kelas SDK.
     * <p>
     * Objek baru dari tipe yang sama <em>menggantikan</em> yang lama. Versi
     * sebelumnya menyimpan sampai empat objek lalu menolak sisanya: alur
     * reward yang mengulang pemuatan membuat objek listener baru setiap
     * percobaan, sehingga pada percobaan kelima listener yang sedang aktif
     * tidak pernah tersimpan — blokir berikutnya memberi kabar ke listener
     * basi dan aplikasi tetap menunggu selamanya.
     */
    private static final Map<String, Map<Class<?>, Object>> sClassListeners =
            Collections.synchronizedMap(new HashMap<String, Map<Class<?>, Object>>());

    /** Kelas yang field-nya sudah diperiksa, agar refleksi tidak diulang. */
    private static final Map<Class<?>, Field[]> sFields =
            Collections.synchronizedMap(new WeakHashMap<Class<?>, Field[]>());

    private AdCallbacks() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    /**
     * Menyimpan listener yang dipasang aplikasi pada sebuah objek iklan.
     *
     * @param adObject  objek iklan, atau {@code null} bila setter-nya statis
     * @param className nama kelas SDK pemilik setter
     * @param listener  objek yang akan diberi tahu saat pemuatan diblokir
     */
    public static void captureListener(Object adObject, String className, Object listener)
    {
        if (listener == null || !isListenerLike(listener))
        {
            return;
        }
        try
        {
            if (adObject != null)
            {
                sListeners.put(adObject, listener);
            }
            if (className != null)
            {
                Map<Class<?>, Object> byType = sClassListeners.get(className);
                if (byType == null)
                {
                    byType = new HashMap<>(4);
                    sClassListeners.put(className, byType);
                }
                // Tipe listener menjadi kunci: pemasangan ulang oleh aplikasi
                // menggantikan objek lama, sehingga yang diberi kabar selalu
                // listener yang sedang didengarkan aplikasi.
                byType.put(listener.getClass(), listener);
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    /**
     * Apakah objek ini masuk akal sebagai listener.
     * <p>
     * Setter yang diterima dari SDK tidak selalu berupa listener: {@code
     * setAutoLoadCallback(boolean)} dan {@code setDefaultConfig(String)} juga
     * berawalan {@code set}. Menyimpannya sebagai listener hanya membuat
     * dispatch mencoba memanggil refleksi pada Boolean/String.
     */
    private static boolean isListenerLike(Object value)
    {
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean)
        {
            return false;
        }
        if (value instanceof android.content.Context || value instanceof android.view.View)
        {
            return false;
        }
        if (value instanceof Map || value instanceof java.util.Collection)
        {
            return false;
        }
        return !value.getClass().isArray();
    }

    /**
     * Memberi tahu callback yang dikirim langsung sebagai argumen method.
     * <p>
     * Dipakai untuk API seperti
     * {@code RewardedAd.load(Context, String, AdRequest, RewardedAdLoadCallback)},
     * {@code UnityAds.load(String, IUnityAdsLoadListener)}, dan
     * {@code Facebook InterstitialAd.loadAd(InterstitialLoadAdConfig)} — pada
     * kasus terakhir listener berada di dalam objek konfigurasi, sehingga dicari
     * lewat field.
     *
     * @param param     parameter method yang sedang diblokir
     * @param adUnitId  id unit iklan bila diketahui, untuk mengisi parameter
     *                  {@code String} pada callback
     */
    public static void notifyFromArgs(XC_MethodHook.MethodHookParam param, String adUnitId)
    {
        if (param == null || param.args == null)
        {
            return;
        }
        try
        {
            for (Object arg : param.args)
            {
                if (arg == null || !isListenerLike(arg))
                {
                    continue;
                }
                notifyTarget(arg, adUnitId, 0, FAILURE_METHODS);
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    /**
     * Memberi tahu listener yang dikirim sebagai argumen {@code show()}.
     * <p>
     * Unity Ads ({@code show(Activity, String, IUnityAdsShowListener)}) dan
     * API sejenis menerima listener langsung di method tampil. Kalau
     * {@code show()} diblokir tanpa kabar, aplikasi menunggu
     * {@code onUnityAdsShowComplete} selamanya dan layar reward berputar.
     */
    public static void notifyDismissedFromArgs(XC_MethodHook.MethodHookParam param, String adUnitId)
    {
        if (param == null || param.args == null)
        {
            return;
        }
        try
        {
            for (Object arg : param.args)
            {
                if (arg == null || !isListenerLike(arg))
                {
                    continue;
                }
                notifyTarget(arg, adUnitId, 0, DISMISS_METHODS);
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    /**
     * Mencari listener yang disimpan <em>di dalam</em> objek iklan.
     * <p>
     * Sebagian SDK tidak menerima listener lewat argumen maupun setter yang
     * bisa di-hook: Facebook memasang {@code AdListener} melalui builder yang
     * seluruh overload-nya abstrak, lalu menyimpannya di field objek iklan.
     * Tanpa penelusuran ini, pemuatan Facebook yang diblokir tidak memberi
     * kabar apa pun dan aplikasi menunggu selamanya.
     *
     * @param adObject objek iklan yang method-nya diblokir
     * @param dismiss  {@code true} untuk callback "iklan ditutup", {@code false}
     *                 untuk callback gagal-muat
     */
    public static void notifyInFields(Object adObject, String adUnitId, boolean dismiss)
    {
        if (adObject == null)
        {
            return;
        }
        try
        {
            notifyTarget(adObject, adUnitId, 0, dismiss ? DISMISS_METHODS : FAILURE_METHODS);
        }
        catch (Throwable ignored)
        {
        }
    }

    /**
     * Mencari listener di dalam sebuah argumen.
     * <p>
     * Objek konfigurasi Facebook ({@code InterstitialLoadAdConfig}) menyimpan
     * listener sebagai field; tanpa penelusuran ini pemuatan Facebook yang
     * diblokir tidak pernah memberi kabar dan aplikasi terus menunggu.
     *
     * @param names        nama method yang dicari pada target
     */
    private static void notifyTarget(Object target, String adUnitId, int depth, String[] names)
    {
        if (target == null || depth > MAX_FIELD_DEPTH)
        {
            return;
        }
        if (hasAnyMethod(target.getClass(), names))
        {
            dispatchMethod(target, names, adUnitId);
            return;
        }

        Field[] fields = fieldsOf(target.getClass());
        int checked = 0;
        for (Field field : fields)
        {
            if (checked >= MAX_FIELDS)
            {
                break;
            }
            checked++;
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive())
            {
                continue;
            }
            Object value;
            try
            {
                field.setAccessible(true);
                value = field.get(target);
            }
            catch (Throwable ignored)
            {
                continue;
            }
            if (value == null || !isListenerLike(value))
            {
                continue;
            }
            notifyTarget(value, adUnitId, depth + 1, names);
        }
    }

    /** Field yang bisa dibaca dari sebuah kelas; dihitung sekali per kelas. */
    private static Field[] fieldsOf(Class<?> type)
    {
        Field[] cached = sFields.get(type);
        if (cached != null)
        {
            return cached;
        }
        Field[] result;
        try
        {
            result = type.getDeclaredFields();
        }
        catch (Throwable t)
        {
            result = new Field[0];
        }
        sFields.put(type, result);
        return result;
    }

    /** Apakah kelas ini punya salah satu method dari daftar nama. */
    private static boolean hasAnyMethod(Class<?> type, String[] names)
    {
        for (String name : names)
        {
            for (Method method : methodsOf(type))
            {
                if (method.getName().equals(name))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Memberi tahu listener yang sudah ditangkap untuk objek iklan atau kelas.
     */
    public static void notifyCaptured(Object adObject, String className, String adUnitId)
    {
        try
        {
            Object listener = adObject == null ? null : sListeners.get(adObject);
            if (listener != null)
            {
                dispatch(listener, adUnitId);
            }
        }
        catch (Throwable ignored)
        {
        }

        try
        {
            Map<Class<?>, Object> byType = className == null ? null : sClassListeners.get(className);
            if (byType != null)
            {
                // Salin dulu: listener bisa melepas dirinya sendiri (atau
                // memasang yang baru) di dalam callback, dan mengubah map saat
                // sedang ditelusuri akan melempar
                // ConcurrentModificationException di proses aplikasi.
                Object[] snapshot;
                synchronized (byType)
                {
                    snapshot = byType.values().toArray();
                }
                for (Object listener : snapshot)
                {
                    dispatch(listener, adUnitId);
                }
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    /**
     * Memberi tahu callback bahwa iklan yang diminta untuk ditampilkan tidak
     * akan muncul.
     * <p>
     * Dipakai setelah {@code show()} diblokir. {@code onAdClosed} lebih dipilih
     * daripada callback gagal-tampil: pada banyak SDK kegagalan tampil berarti
     * "coba lagi", sedangkan {@code onAdClosed} berarti "alur iklan selesai" —
     * itulah yang membuat layar reward tertutup, bukan berputar.
     */
    public static void notifyDismissed(Object adObject, String className, String adUnitId)
    {
        try
        {
            Object listener = adObject == null ? null : sListeners.get(adObject);
            if (listener != null)
            {
                dispatchClose(listener, adUnitId);
            }
        }
        catch (Throwable ignored)
        {
        }

        try
        {
            Map<Class<?>, Object> byType = className == null ? null : sClassListeners.get(className);
            if (byType != null)
            {
                Object[] snapshot;
                synchronized (byType)
                {
                    snapshot = byType.values().toArray();
                }
                for (Object listener : snapshot)
                {
                    dispatchClose(listener, adUnitId);
                }
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    /** Mengirim notifikasi kegagalan ke satu objek callback. */
    private static void dispatch(final Object callback, final String adUnitId)
    {
        dispatchMethod(callback, FAILURE_METHODS, adUnitId);
    }

    /** Menjadwalkan pemanggilan salah satu method dari daftar nama. */
    private static void dispatchMethod(final Object callback, final String[] names, final String adUnitId)
    {
        dispatchMethod(callback, names, SHOW_FAILURE_METHODS, adUnitId);
    }

    /**
     * Menjadwalkan pemanggilan callback tutup, dengan cadangan gagal-tampil.
     * <p>
     * Dispatcher tutup memakai dua daftar sekaligus: sebagian listener hanya
     * punya {@code onAdEnd}/{@code onAdHidden}, sebagian lain hanya punya
     * {@code onAdFailedToPlay}/{@code onAdDisplayFailed}. Keduanya menandakan
     * "penayangan tidak akan terjadi", jadi keduanya sah untuk menutup alur
     * aplikasi.
     */
    private static void dispatchClose(final Object callback, final String adUnitId)
    {
        dispatchMethod(callback, DISMISS_METHODS, SHOW_FAILURE_METHODS, adUnitId);
    }

    /**
     * Menjadwalkan pemanggilan salah satu method dari daftar nama.
     *
     * @param names    nama yang dicoba lebih dulu
     * @param fallback nama cadangan bila tidak satu pun dari {@code names}
     *                 bisa dipanggil
     */
    private static void dispatchMethod(final Object callback, final String[] names,
                                       final String[] fallback, final String adUnitId)
    {
        if (callback == null || !markPending(callback))
        {
            return;
        }

        // Jeda diperpanjang bila kelas callback yang sama sudah berkali-kali
        // diminta. Objek callback dibuat ulang tiap percobaan, jadi hitungan
        // per objek saja tidak cukup untuk menghentikan putaran.
        long delay = backoffDelay(callback, adUnitId, names);

        try
        {
            sMainHandler.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        if (!invokeNamed(callback, names, adUnitId))
                        {
                            invokeNamed(callback, fallback, adUnitId);
                        }
                    }
                    finally
                    {
                        sPending.remove(callback);
                    }
                }
            }, delay);
        }
        catch (Throwable t)
        {
            // Handler bisa tidak tersedia sangat awal; lepaskan penanda supaya
            // percobaan berikutnya tetap bisa dikirim.
            sPending.remove(callback);
        }
    }

    /**
     * Jeda yang membesar secara eksponensial untuk permintaan berulang.
     * <p>
     * Dipakai agar aplikasi yang mengulang pemuatan setelah gagal tidak
     * berputar tanpa henti. Jeda maksimum tetap 16x {@link #NOTIFY_DELAY_MS}
     * (±5 detik), jadi alur normal tidak terasa terhambat.
     * <p>
     * Hitungannya luruh kembali setelah alur reda. Tanpa itu jeda maksimum
     * menempel selamanya: pengguna yang membuka layar reward lagi sejam
     * kemudian tetap menunggu 5 detik sebelum callback gagal dikirim, padahal
     * itu percobaan pertama pada sesi baru.
     */
    private static long backoffDelay(Object callback, String adUnitId, String[] names)
    {
        String key = callback.getClass().getName() + "#" + (names.length > 0 ? names[0] : "")
                + "#" + (adUnitId == null ? "" : adUnitId);

        long now = System.currentTimeMillis();
        Backoff attempts = sAttempts.get(key);
        if (attempts == null)
        {
            attempts = new Backoff();
            Backoff previous = sAttempts.put(key, attempts);
            if (previous != null)
            {
                attempts = previous;
            }
        }

        int count;
        synchronized (attempts)
        {
            if (now - attempts.last > BACKOFF_RESET_MS)
            {
                // Alur sebelumnya sudah reda: mulai lagi dari jeda terpendek.
                attempts.count = 0;
            }
            attempts.last = now;
            count = attempts.count++;
        }

        int shift = count < MAX_BACKOFF_SHIFT ? count : MAX_BACKOFF_SHIFT;
        return NOTIFY_DELAY_MS << shift;
    }

    /** Penghitung percobaan beserta waktu sentuh terakhir. */
    private static final class Backoff
    {
        int count;
        long last;
    }

    /**
     * Lama diam yang mengembalikan jeda ke nilai terpendek.
     * <p>
     * Setengah menit jauh lebih lama daripada jeda maksimum (±5 detik), jadi
     * pengulangan cepat tetap mendapat jeda panjang, sedangkan sesi baru tidak
     * mewarisi jeda sesi sebelumnya.
     */
    private static final long BACKOFF_RESET_MS = 30_000L;

    /**
     * Menandai callback sedang punya notifikasi terjadwal.
     * <p>
     * Ini <em>bukan</em> pembatas laju: penanda dilepas tepat setelah callback
     * dijalankan, jadi setiap blokir berikutnya — termasuk yang datang beberapa
     * milidetik kemudian karena aplikasi mengulang pemuatan — tetap mendapat
     * notifikasinya sendiri. Yang dicegah hanyalah pengiriman ganda untuk satu
     * blokir, karena satu blokir bisa menemukan callback yang sama lewat
     * argumen, listener tersimpan, dan penelusuran field sekaligus.
     *
     * @return {@code true} bila notifikasi baru perlu dijadwalkan
     */
    private static boolean markPending(Object callback)
    {
        return sPending.put(callback, Boolean.TRUE) == null;
    }

    /**
     * Mencari lalu memanggil satu method dari daftar nama pada callback.
     *
     * @return {@code true} bila ada callback yang benar-benar dipanggil
     */
    private static boolean invokeNamed(Object callback, String[] names, String adUnitId)
    {
        Method handler = null;
        Object[] args = null;

        for (String name : names)
        {
            for (Method method : methodsOf(callback.getClass()))
            {
                if (!method.getName().equals(name))
                {
                    continue;
                }
                Object[] built = buildArguments(method, adUnitId);
                if (built != null)
                {
                    handler = method;
                    args = built;
                    break;
                }
            }
            if (handler != null)
            {
                break;
            }
        }

        if (handler == null)
        {
            // Bentuk callback tidak dikenal: diamkan. Memanggil dengan argumen
            // salah akan menjatuhkan aplikasi.
            Util.log("mmg", "callback " + names[0] + " tidak dikenali pada "
                    + callback.getClass().getName());
            return false;
        }

        try
        {
            handler.setAccessible(true);
            handler.invoke(callback, args);
            Util.log("mmg", "callback dikirim: "
                    + callback.getClass().getName() + "#" + handler.getName());
            return true;
        }
        catch (Throwable t)
        {
            Util.log("mmg", "gagal mengirim callback " + handler.getName() + ": " + t);
            return false;
        }
    }

    /**
     * Method publik sebuah kelas yang layak dicoba sebagai callback.
     * <p>
     * Hasilnya di-cache: {@code getMethods()} memindai seluruh hierarki dan
     * dipanggil berkali-kali per blokir (satu kali untuk setiap nama callback
     * yang dicoba, ditambah pemeriksaan {@code hasAnyMethod}).
     */
    private static List<Method> methodsOf(Class<?> type)
    {
        List<Method> cached = sMethods.get(type);
        if (cached != null)
        {
            return cached;
        }
        List<Method> result = new ArrayList<>();
        try
        {
            for (Method method : type.getMethods())
            {
                if (method.getParameterTypes().length <= 4
                        && !Modifier.isStatic(method.getModifiers()))
                {
                    result.add(method);
                }
            }
        }
        catch (Throwable ignored)
        {
        }
        sMethods.put(type, result);
        return result;
    }

    /** Hasil {@link #methodsOf} per kelas. */
    private static final Map<Class<?>, List<Method>> sMethods =
            Collections.synchronizedMap(new WeakHashMap<Class<?>, List<Method>>());

    /**
     * Menyiapkan argumen untuk sebuah method callback.
     * <p>
     * Parameter referensi yang tidak bisa dibuat diisi {@code null}, bukan
     * membatalkan callback. Membatalkan berarti aplikasi tidak menerima kabar
     * sama sekali — persis putaran tanpa ujung yang sedang diperbaiki di sini.
     * Hanya parameter primitif yang benar-benar menghalangi, karena tidak ada
     * nilai netral yang aman untuk semua tipe.
     *
     * @return argumen, atau {@code null} bila ada parameter primitif yang tidak
     *         bisa dipenuhi
     */
    private static Object[] buildArguments(Method method, String adUnitId)
    {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++)
        {
            Object value = buildValue(types[i], adUnitId, 0);
            if (value == UNSET)
            {
                if (types[i].isPrimitive())
                {
                    return null;
                }
                args[i] = null;
                continue;
            }
            args[i] = value;
        }
        return args;
    }

    /** Penanda "nilai tidak bisa dibuat" yang berbeda dari {@code null}. */
    private static final Object UNSET = new Object();

    private static Object buildValue(Class<?> type, String adUnitId, int depth)
    {
        if (depth > 3)
        {
            return UNSET;
        }
        if (type == String.class || type == CharSequence.class)
        {
            return adUnitId == null ? "no fill" : adUnitId;
        }
        if (type == boolean.class || type == Boolean.class)
        {
            return Boolean.FALSE;
        }
        if (type == int.class || type == Integer.class)
        {
            return 0;
        }
        if (type == long.class || type == Long.class)
        {
            return 0L;
        }
        if (type == float.class || type == Float.class)
        {
            return 0f;
        }
        if (type == double.class || type == Double.class)
        {
            return 0d;
        }
        if (type == short.class || type == Short.class)
        {
            return (short) 0;
        }
        if (type == byte.class || type == Byte.class)
        {
            return (byte) 0;
        }
        if (type == char.class || type == Character.class)
        {
            return (char) 0;
        }
        if (type.isEnum())
        {
            Object[] constants = type.getEnumConstants();
            return constants != null && constants.length > 0 ? constants[0] : UNSET;
        }
        if (type.isArray())
        {
            return java.lang.reflect.Array.newInstance(type.getComponentType(), 0);
        }
        if (type.isInterface())
        {
            Object proxy = newInterfaceStub(type);
            return proxy == null ? UNSET : proxy;
        }
        if (Modifier.isAbstract(type.getModifiers()))
        {
            // Tipe error abstrak (mis. AdError/LoadAdError/ResponseInfo pada
            // AdMob, IronSourceError, TPAdError). Aplikasi hampir selalu
            // langsung memanggil getMessage()/getCode() di dalam callback, jadi
            // mengirim null akan menjatuhkannya dengan NullPointerException —
            // aplikasi restart, memuat ulang, dan putaran itu berlanjut. Cari
            // implementasi konkret yang lazim dipakai SDK.
            //
            // Kelas abstrak yang benar-benar tidak punya implementasi di APK
            // tidak dipaksakan di sini: memaksa instance lewat Unsafe
            // menghasilkan objek dengan field null, dan memanggil method di
            // dalamnya melempar dari kode aplikasi — lebih buruk daripada null
            // yang bisa diperiksa aplikasi.
            Object implementation = instantiateImplementation(type, adUnitId, depth);
            return implementation == null ? UNSET : implementation;
        }

        Object created = instantiate(type, adUnitId, depth);
        return created == null ? UNSET : created;
    }

    /**
     * Mencari kelas konkret yang bisa dipakai untuk tipe abstrak.
     * <p>
     * Pola penamaan {@code MaxError} → {@code com.applovin.impl.mediation
     * .MaxErrorImpl} berlaku pada AppLovin; pola {@code <Nama>Impl} berlaku pada
     * beberapa SDK lain. Semua kandidat diuji assignable sebelum dipakai.
     */
    private static Object instantiateImplementation(Class<?> type, String adUnitId, int depth)
    {
        String name = type.getName();
        String[] candidates = {
                name + "Impl",
                name.replace(".mediation.", ".impl.mediation.") + "Impl",
                name.replace(".mediation.", ".impl.mediation."),
                name + "$Impl",
        };
        ClassLoader loader = type.getClassLoader();

        for (String candidate : candidates)
        {
            if (candidate.equals(name))
            {
                continue;
            }
            Class<?> implementation = Util.findClassOrNull(candidate, loader);
            if (implementation == null || !type.isAssignableFrom(implementation))
            {
                continue;
            }
            Object created = instantiate(implementation, adUnitId, depth);
            if (created != null)
            {
                return created;
            }
        }
        return null;
    }

    /**
     * Membuat instance kelas konkret lewat konstruktor mana pun yang bisa
     * dipenuhi.
     */
    private static Object instantiate(Class<?> type, String adUnitId, int depth)
    {
        if (Modifier.isAbstract(type.getModifiers()))
        {
            return null;
        }
        Constructor<?>[] constructors;
        try
        {
            constructors = type.getDeclaredConstructors();
        }
        catch (Throwable ignored)
        {
            return null;
        }

        // Konstruktor dengan parameter paling sedikit dicoba lebih dulu: ia
        // paling kecil kemungkinan membutuhkan objek internal SDK yang tidak
        // bisa dibuat di sini.
        java.util.Arrays.sort(constructors, new Comparator<Constructor<?>>()
        {
            @Override
            public int compare(Constructor<?> a, Constructor<?> b)
            {
                return a.getParameterTypes().length - b.getParameterTypes().length;
            }
        });

        for (Constructor<?> constructor : constructors)
        {
            Class<?>[] types = constructor.getParameterTypes();
            Object[] args = new Object[types.length];
            boolean usable = true;
            for (int i = 0; i < types.length; i++)
            {
                Object value = buildValue(types[i], adUnitId, depth + 1);
                if (value == UNSET)
                {
                    // Null aman untuk hampir semua parameter referensi; untuk
                    // tipe primitif buildValue selalu berhasil.
                    if (types[i].isPrimitive())
                    {
                        usable = false;
                        break;
                    }
                    value = null;
                }
                args[i] = value;
            }
            if (!usable)
            {
                continue;
            }
            try
            {
                constructor.setAccessible(true);
                Object created = constructor.newInstance(args);
                if (created != null && type.isInstance(created))
                {
                    return created;
                }
            }
            catch (Throwable ignored)
            {
                // Coba konstruktor berikutnya.
            }
        }
        return null;
    }

    /**
     * Stub untuk tipe callback/error berbentuk interface.
     * <p>
     * Aplikasi hanya membaca nilai dari antarmuka itu, jadi stub yang
     * mengembalikan nilai kosong sudah cukup dan tidak pernah menjatuhkannya.
     */
    private static Object newInterfaceStub(final Class<?> type)
    {
        try
        {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    new InvocationHandler()
                    {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args)
                        {
                            Class<?> ret = method.getReturnType();
                            if (ret == void.class)
                            {
                                return null;
                            }
                            if (ret == boolean.class)
                            {
                                return false;
                            }
                            if (ret == int.class)
                            {
                                return 0;
                            }
                            if (ret == long.class)
                            {
                                return 0L;
                            }
                            if (ret == float.class)
                            {
                                return 0f;
                            }
                            if (ret == double.class)
                            {
                                return 0d;
                            }
                            if (ret == String.class)
                            {
                                return "";
                            }
                            if (ret.isInterface())
                            {
                                return newInterfaceStub(ret);
                            }
                            return null;
                        }
                    });
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    /** Membersihkan penanda saat hook dipasang untuk proses baru. */
    public static void reset()
    {
        sPending.clear();
        sAttempts.clear();
        sListeners.clear();
        sClassListeners.clear();
        sFields.clear();
        sMethods.clear();
    }
}
