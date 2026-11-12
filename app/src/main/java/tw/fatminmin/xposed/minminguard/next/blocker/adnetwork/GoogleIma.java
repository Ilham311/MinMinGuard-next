package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.AdCallbacks;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;
import tw.fatminmin.xposed.minminguard.next.blocker.Util;

/**
 * Google IMA (Interactive Media Ads), {@code com.google.ads.interactivemedia}.
 * <p>
 * SDK ini dipakai pemutar video (ExoPlayer, Media3, JW, dan pembungkus lain)
 * untuk menyisipkan iklan pre-roll/mid-roll. Sebelumnya modul tidak punya
 * blocker apa pun untuknya, sehingga iklan video tetap tampil walau panel
 * "Ads blocked" melaporkan aktivitas.
 * <p>
 * Mengapa hook tidak dipasang langsung ke {@code AdsLoader.requestAds}:
 * {@code AdsLoader} adalah <em>interface</em>. {@code XposedBridge.hookAllMethods}
 * pada interface hanya melihat method abstrak — tidak ada body, dan
 * implementasinya tidak ikut ter-hook. Implementasi konkretnya berada di paket
 * {@code impl} yang namanya berubah tiap rilis ({@code zzan} pada 3.40.0),
 * jadi menulis nama itu ke kode berarti hook yang mati pada versi berikutnya.
 * <p>
 * Karena itu yang di-hook adalah <em>kelas objek yang benar-benar dikembalikan</em>
 * {@code ImaSdkFactory.createAdsLoader(...)}. Kelas publik itu stabil antar
 * versi (diperiksa pada AAR 3.40.0), sedangkan kelas hasilnya ditangani apa
 * adanya, apa pun namanya.
 * <p>
 * {@code ImaSdkFactory.createAdsRequest()} sengaja <em>tidak</em> diblokir:
 * method itu mengembalikan objek konfigurasi yang langsung dipakai aplikasi
 * ({@code setAdTagUrl}, {@code setContentProgressProvider}). Mengembalikan
 * {@code null} di sana membuat aplikasi gagal dengan NullPointerException,
 * bukan iklan yang hilang.
 */
public class GoogleIma extends Blocker
{

    /**
     * IMA tidak mengekspos kelas view banner dengan nama tetap: iklannya
     * dirender di dalam {@code WebView} internal yang di-obfuscate. Prefix
     * paket tetap berguna sebagai jaring pengaman bila suatu saat Google
     * mengekspos kelas view bernama stabil.
     */
    private static final String BANNER_PREFIX = "com.google.ads.interactivemedia";

    private static final String SDK_FACTORY = "com.google.ads.interactivemedia.v3.api.ImaSdkFactory";

    /**
     * Callback dipakai bersama semua instance.
     * <p>
     * {@code Util.hookAllMethods} memakai identitas objek callback sebagai
     * bagian kunci dedupe. Membuat callback baru pada setiap pemanggilan akan
     * memasang hook yang sama berulang kali untuk setiap AdsLoader yang dibuat
     * aplikasi — pemutar video sering membuatnya ulang per tayangan.
     */
    private static final XC_MethodHook BLOCK_REQUEST = new XC_MethodHook()
    {
        @Override
        protected void beforeHookedMethod(MethodHookParam param)
        {
            // requestAds(AdsRequest) mengembalikan void; setDefaultResult
            // mengisi nilai balik yang sesuai tipe method itu.
            Util.setDefaultResult(param);

            // Aplikasi memasang AdErrorListener lewat addAdErrorListener()
            // sebelum meminta iklan. Tanpa kabar, pemutar menunggu
            // AdsLoadedListener yang tidak akan pernah datang dan layar
            // loading video berputar.
            AdCallbacks.notifyCaptured(param.thisObject, null, null);
        }
    };

    private static final XC_MethodHook CAPTURE_ERROR_LISTENER = new XC_MethodHook()
    {
        @Override
        protected void beforeHookedMethod(MethodHookParam param)
        {
            for (Object arg : param.args)
            {
                if (arg != null)
                {
                    AdCallbacks.captureListener(param.thisObject, null, arg);
                }
            }
        }
    };

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        // Dipasang setelah createAdsLoader selesai: hasilnya adalah objek
        // loader konkret yang bisa di-hook berdasarkan kelas nyatanya.
        return Util.hookAllMethods(SDK_FACTORY, lpparam.classLoader, "createAdsLoader",
                new XC_MethodHook()
                {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param)
                    {
                        Object loader = param.getResult();
                        if (loader != null)
                        {
                            hookLoader(loader.getClass());
                        }
                    }
                });
    }

    /**
     * Memasang blokir pada kelas implementasi loader.
     * <p>
     * {@code Util} menyaring nama method per kelas sebelum memasang, jadi
     * memanggil ini untuk kelas yang tidak punya {@code requestAds} hanya
     * menghasilkan satu pencarian nama yang sudah di-cache, bukan hook kosong.
     * <p>
     * {@code requestStream} sengaja TIDAK diblokir. Pada IMA, {@code requestAds}
     * hanya meminta iklan untuk konten yang diputar aplikasi sendiri, sehingga
     * menghentikannya menghilangkan iklan tanpa menyentuh video. Sebaliknya
     * {@code requestStream} adalah jalur <em>server-side ad insertion</em>:
     * yang dikembalikan adalah stream yang berisi konten, dan mengembalikan
     * {@code null} di sana mematikan pemutaran video pengguna sama sekali —
     * jauh melebihi tujuan modul ini.
     */
    private static void hookLoader(Class<?> loaderClass)
    {
        Util.hookAllMethods(loaderClass, "addAdErrorListener", CAPTURE_ERROR_LISTENER);
        Util.hookAllMethods(loaderClass, "requestAds", BLOCK_REQUEST);
    }

    @Override
    public String getBanner()
    {
        // Tidak ada kelas banner bernama stabil pada IMA.
        return null;
    }

    @Override
    public String getBannerPrefix()
    {
        return BANNER_PREFIX;
    }
}
