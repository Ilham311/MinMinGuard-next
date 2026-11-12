package tw.fatminmin.xposed.minminguard.next.core.hook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catatan semua pemasangan hook pada satu proses aplikasi.
 * <p>
 * Inilah yang membuat modul bisa menyatakan keadaannya sendiri. Sebelumnya
 * {@code hookAllMethods} mengembalikan {@code false} dan nilainya dibuang oleh
 * semua pemanggil, sehingga rilis 3.1.3 kehilangan hook {@code ViewGroup},
 * {@code Activity}, {@code LayoutInflater}, dan {@code WebView} tanpa satu pun
 * baris log.
 * <p>
 * Ledger mengelompokkan hasil per layer. Layer yang seharusnya aktif tetapi
 * melaporkan nol hook adalah kondisi yang wajib ditampilkan, bukan disembunyikan.
 */
public final class HookLedger
{
    private static final HookLedger INSTANCE = new HookLedger();

    /** Batas jumlah kegagalan yang disimpan agar memori proses target tetap kecil. */
    private static final int MAX_FAILURES = 64;

    private final List<HookResult> mFailures = new ArrayList<>();
    private final Map<HookResult.Layer, int[]> mCounts = new LinkedHashMap<>();

    private HookLedger()
    {
    }

    public static HookLedger get()
    {
        return INSTANCE;
    }

    /** Mencatat satu hasil. Aman dipanggil dari banyak thread. */
    public synchronized void record(HookResult result)
    {
        if (result == null)
        {
            return;
        }

        int[] counts = mCounts.get(result.layer);
        if (counts == null)
        {
            counts = new int[2];
            mCounts.put(result.layer, counts);
        }

        if (result.isInstalled())
        {
            counts[0] += Math.max(1, result.installed);
        }
        else
        {
            counts[1]++;
            if (result.needsAttention() && mFailures.size() < MAX_FAILURES)
            {
                mFailures.add(result);
            }
        }
    }

    /** Jumlah overload terpasang untuk sebuah layer. */
    public synchronized int installedCount(HookResult.Layer layer)
    {
        int[] counts = mCounts.get(layer);
        return counts == null ? 0 : counts[0];
    }

    /** Jumlah percobaan yang gagal untuk sebuah layer. */
    public synchronized int failureCount(HookResult.Layer layer)
    {
        int[] counts = mCounts.get(layer);
        return counts == null ? 0 : counts[1];
    }

    /** Kegagalan yang butuh perhatian: kelas ada, tetapi hook tidak terpasang. */
    public synchronized List<HookResult> failures()
    {
        return Collections.unmodifiableList(new ArrayList<>(mFailures));
    }

    public synchronized boolean hasAttentionFailures()
    {
        return !mFailures.isEmpty();
    }

    /**
     * Baris ringkas untuk log/diagnostik, mis.
     * {@code SDK_API=412/0 NAME=3/0 WEBVIEW=0/3}.
     */
    public synchronized String summary()
    {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<HookResult.Layer, int[]> entry : mCounts.entrySet())
        {
            if (builder.length() > 0)
            {
                builder.append(' ');
            }
            builder.append(entry.getKey().name())
                    .append('=').append(entry.getValue()[0])
                    .append('/').append(entry.getValue()[1]);
        }
        return builder.toString();
    }

    /** Membersihkan catatan; dipanggil saat proses baru memasang hook. */
    public synchronized void reset()
    {
        mCounts.clear();
        mFailures.clear();
    }
}
