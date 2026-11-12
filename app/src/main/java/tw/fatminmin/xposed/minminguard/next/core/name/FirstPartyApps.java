package tw.fatminmin.xposed.minminguard.next.core.name;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Namespace kode milik aplikasi first-party yang iklannya tidak bisa
 * dipisahkan dari kodenya.
 * <p>
 * Aplikasi seperti Instagram dan YouTube mengirim iklan bersama konten dari
 * host yang sama, tanpa SDK iklan pihak ketiga. Tidak ada kelas SDK untuk
 * di-hook, jadi lapisan API tidak berlaku untuk mereka. Satu-satunya yang
 * masih berjalan adalah heuristik nama — dan justru itu yang berbahaya di sini,
 * karena aplikasi seperti ini punya banyak komponen UI biasa yang kebetulan
 * berakhiran {@code BannerView} atau {@code AdView}.
 * <p>
 * Contoh nyata, dari bytecode APK di perangkat:
 * <ul>
 *   <li>{@code com.instagram.direct.rtc.ongoingcallbar.metaaivoice.OngoingMetaAiVoiceBannerView}
 *       — banner panggilan Meta AI di DM. Bukan iklan. Namanya cocok dengan
 *       akhiran {@code BannerView}, sehingga tanpa pengecualian ini modul
 *       menghapusnya dari pohon view: pengguna kehilangan fitur, dan tidak ada
 *       iklan yang hilang.</li>
 *   <li>{@code com.google.android.libraries.youtube.ads.player.ui.AdDisclosureBannerView}
 *       — banner pengungkapan iklan YouTube. Menghapusnya menghilangkan
 *       keterangannya tanpa menghilangkan iklannya.</li>
 * </ul>
 * Karena itu namespace mereka dikecualikan dari lapisan nama: lebih baik
 * modul tidak melakukan apa pun di aplikasi ini daripada merusak UI-nya
 * sambil tetap tidak memblokir iklan.
 * <p>
 * Perhatikan: yang dikecualikan adalah namespace <em>aplikasi</em>, bukan
 * {@code applicationId}. Instagram (`com.instagram.android`) menaruh kodenya
 * di `com.instagram.*`; YouTube (`com.google.android.youtube`) di
 * `com.google.android.libraries.youtube.*` dan `com.google.android.apps.youtube.*`.
 */
public final class FirstPartyApps
{
    /**
     * applicationId → prefix namespace kode milik aplikasi itu.
     * <p>
     * Daftar ini sengaja kecil dan berbasis bukti bytecode, bukan tebakan.
     * Setiap entri baru harus disertai alasan mengapa iklannya first-party.
     */
    private static final Map<String, String[]> NAMESPACES;

    static
    {
        Map<String, String[]> map = new HashMap<>();

        // Iklan Instagram dan Threads dikirim lewat GraphQL dari i.instagram.com,
        // bukan lewat SDK iklan. 0 referensi ke AdMob/IMA/AppLovin/Pangle.
        map.put("com.instagram.android", new String[]{
                "com.instagram",
        });
        map.put("com.instagram.barcelona", new String[]{
                "com.instagram",
                "com.barcelona",
        });

        // YouTube: 0 referensi ke SDK iklan pihak ketiga. Iklan dikirim lewat
        // youtubei.googleapis.com bersama konten.
        map.put("com.google.android.youtube", new String[]{
                "com.google.android.libraries.youtube",
                "com.google.android.apps.youtube",
        });
        map.put("com.google.android.apps.youtube.music", new String[]{
                "com.google.android.libraries.youtube",
                "com.google.android.apps.youtube",
        });

        NAMESPACES = Collections.unmodifiableMap(map);
    }

    private FirstPartyApps()
    {
        throw new AssertionError("No instances");
    }

    /**
     * True bila aplikasi ini membawa iklannya sendiri tanpa SDK pihak ketiga.
     */
    public static boolean isFirstParty(String pkgName)
    {
        return pkgName != null && NAMESPACES.containsKey(pkgName);
    }

    /**
     * Namespace kode milik aplikasi, atau array kosong bila tidak dikenal.
     */
    public static String[] namespacesOf(String pkgName)
    {
        String[] namespaces = pkgName == null ? null : NAMESPACES.get(pkgName);
        return namespaces == null ? new String[0] : namespaces;
    }

    /**
     * True bila kelas ini milik aplikasi first-party yang sedang di-hook.
     *
     * @param className nama kelas yang diperiksa
     * @param pkgName   paket aplikasi yang prosesnya sedang di-hook
     */
    public static boolean ownsClass(String pkgName, String className)
    {
        return AdNameMatcher.belongsToApp(className, namespacesOf(pkgName));
    }
}
