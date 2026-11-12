package tw.fatminmin.xposed.minminguard.next.core.name;

/**
 * Aturan seberapa jauh sebuah view boleh ditangani, berdasarkan asal
 * klasifikasinya.
 * <p>
 * Modul ini punya dua tingkat bukti yang sangat berbeda, dan keduanya dulu
 * diperlakukan sama:
 * <ul>
 *   <li><b>Kelas SDK</b> — namanya ada di daftar pemblokir untuk jaringan yang
 *       memang didukung. Buktinya kuat: kelas itu didesain sebagai container
 *       iklan.</li>
 *   <li><b>Heuristik nama</b> — hanya cocoknya akhiran seperti {@code
 *       BannerView} atau {@code AdView}. Untuk SDK yang tidak di-obfuscate ini
 *       berguna, tetapi pada aplikasi yang menamai komponennya sendiri dengan
 *       pola yang sama, hasilnya salah.</li>
 * </ul>
 * Membuang view hasil heuristik dari pohon view terbukti mematikan aplikasi.
 * Tiga crash nyata di perangkat, semuanya dengan pola yang sama — modul
 * menghapus view, lalu aplikasi memanggil {@code findViewById} dan menerima
 * {@code null}:
 * <ul>
 *   <li>CapCut: {@code com.vega.ui.banner.BannerView} dihapus, lalu
 *       {@code BannerCardViewHolder.t0} melempar "NullPointerException: must
 *       not be null".</li>
 *   <li>MiChat: {@code com.zenmen.palmchat.ui.widget.FeedBottomBannerView}
 *       dihapus, lalu {@code PhotoViewActivity.D1:139} NPE pada
 *       {@code setVisibility}.</li>
 *   <li>MiChat: {@code com.michatapp.ad.view.MeTabBannerAdView} dihapus, lalu
 *       {@code SettingsFragment:312} NPE pada
 *       {@code LifecycleRegistry.addObserver}.</li>
 * </ul>
 * Karena itu hasil heuristik hanya boleh <em>disusutkan</em> (tinggi 0 dan
 * {@code GONE}), tidak dilepas dari parent-nya: iklan tetap tidak terlihat,
 * tetapi {@code findViewById} tetap mengembalikan objeknya.
 * <p>
 * Kelas ini murni Java tanpa dependensi Android agar aturannya bisa diuji di
 * JVM — satu-satunya jenis tes yang berjalan di CI proyek ini.
 */
public final class AdClassification
{
    /** Nama jaringan untuk hasil yang hanya berasal dari heuristik nama. */
    public static final String HEURISTIC = "Heuristic";

    private AdClassification()
    {
        throw new AssertionError("No instances");
    }

    /** True bila klasifikasi ini hanya tebakan dari nama kelas. */
    public static boolean isHeuristic(String network)
    {
        return HEURISTIC.equals(network);
    }

    /**
     * True bila view dengan klasifikasi ini boleh dilepas dari pohon view.
     * <p>
     * Hanya kelas SDK yang boleh: melepas view yang masih akan dipakai
     * aplikasi adalah cara tercepat membuatnya crash.
     *
     * @param network nama jaringan hasil klasifikasi, atau {@code null} bila
     *                view bukan iklan
     */
    public static boolean mayDetachFromTree(String network)
    {
        return network != null && !network.isEmpty() && !isHeuristic(network);
    }
}
