package tw.fatminmin.xposed.minminguard.next.blocker;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import tw.fatminmin.xposed.minminguard.next.Common;

/**
 * Mengumpulkan statistik pemblokiran di proses aplikasi lalu mengirimkannya
 * ke modul dalam satu gelombang.
 * <p>
 * Versi sebelumnya membuat {@code new Thread()} untuk setiap deteksi jaringan
 * iklan dan memakai executor terpisah untuk hitungan iklan, sehingga dua jalur
 * penulisan bisa saling menimpa (provider melakukan read-modify-write) dan
 * statistik kehilangan hit. Sekarang hanya ada satu antrean, dan penulisan
 * delta dilakukan di database secara atomik.
 */
public final class StatsReporter
{
    /** Jeda penggabungan sebelum gelombang dikirim (ms). */
    private static final long FLUSH_DELAY_MS = 700L;

    /** Batas jumlah paket yang ditahan agar memori tetap wajar. */
    private static final int MAX_PENDING_PACKAGES = 256;

    private static final StatsReporter INSTANCE = new StatsReporter();

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mmg-stats");
        t.setDaemon(true);
        return t;
    });

    /** pkg -> [blockDelta, map jaringan -> hitDelta]. */
    private final Map<String, Pending> mPending = new ConcurrentHashMap<>();

    private volatile Future<?> mScheduledFlush;

    private StatsReporter()
    {
    }

    public static StatsReporter get()
    {
        return INSTANCE;
    }

    public void recordBlock(String pkgName, int delta)
    {
        if (pkgName == null || delta <= 0)
        {
            return;
        }
        Pending pending = pendingFor(pkgName);
        if (pending == null)
        {
            return;
        }
        synchronized (pending)
        {
            pending.blocks += delta;
        }
        scheduleFlush();
    }

    public void recordNetwork(String pkgName, String network)
    {
        if (pkgName == null || network == null || network.isEmpty())
        {
            return;
        }
        Pending pending = pendingFor(pkgName);
        if (pending == null)
        {
            return;
        }
        synchronized (pending)
        {
            Integer current = pending.networks.get(network);
            pending.networks.put(network, current == null ? 1 : current + 1);
        }
        scheduleFlush();
    }

    private Pending pendingFor(String pkgName)
    {
        Pending pending = mPending.get(pkgName);
        if (pending != null)
        {
            return pending;
        }
        if (mPending.size() >= MAX_PENDING_PACKAGES)
        {
            // Modul sangat mungkin belum terpasang; jangan tumbuh tanpa batas.
            return null;
        }
        Pending created = new Pending();
        Pending previous = mPending.putIfAbsent(pkgName, created);
        return previous != null ? previous : created;
    }

    private void scheduleFlush()
    {
        if (mScheduledFlush != null && !mScheduledFlush.isDone())
        {
            return;
        }
        synchronized (this)
        {
            if (mScheduledFlush != null && !mScheduledFlush.isDone())
            {
                return;
            }
            mScheduledFlush = mExecutor.submit(() -> {
                try
                {
                    TimeUnit.MILLISECONDS.sleep(FLUSH_DELAY_MS);
                }
                catch (InterruptedException ignored)
                {
                    Thread.currentThread().interrupt();
                }
                flush();
            });
        }
    }

    /** Mengirim semua delta yang tertunda. Aman dipanggil dari thread mana pun. */
    public void flushNow()
    {
        mExecutor.submit(this::flush);
    }

    private void flush()
    {
        if (mPending.isEmpty())
        {
            return;
        }

        Context context = Util.getCurrentApplication();
        if (context == null)
        {
            // Aplikasi belum siap; buang agar tidak menumpuk tanpa batas.
            mPending.clear();
            return;
        }

        ContentResolver resolver = context.getContentResolver();
        Uri statsUri = Uri.parse(Common.URI_STATS);
        Uri networkUri = Uri.parse(Common.URI_NETWORKS);

        for (Map.Entry<String, Pending> entry : mPending.entrySet())
        {
            String pkgName = entry.getKey();
            Pending pending = entry.getValue();

            int blocks;
            Map<String, Integer> networks;
            synchronized (pending)
            {
                blocks = pending.blocks;
                networks = new HashMap<>(pending.networks);
                pending.blocks = 0;
                pending.networks.clear();
            }

            if (blocks > 0)
            {
                ContentValues values = new ContentValues();
                values.put(Common.KEY_PKG_NAME, pkgName);
                values.put(Common.KEY_BLOCK_NUM, blocks);
                try
                {
                    resolver.update(statsUri, values, null, null);
                }
                catch (Throwable t)
                {
                    Util.log(pkgName, "gagal mengirim statistik blokir: " + t);
                }
            }

            for (Map.Entry<String, Integer> network : networks.entrySet())
            {
                ContentValues values = new ContentValues();
                values.put(Common.KEY_PKG_NAME, pkgName);
                values.put(Common.KEY_NETWORK, network.getKey());
                values.put(Common.KEY_NETWORK_COUNT, network.getValue());
                try
                {
                    resolver.update(networkUri, values, null, null);
                }
                catch (Throwable t)
                {
                    Util.log(pkgName, "gagal mengirim statistik jaringan: " + t);
                }
            }

            // Entri sengaja tidak dihapus dari peta. Menghapusnya di sini bisa
            // membuat recordBlock() yang datang bersamaan menulis ke objek
            // Pending yang sudah tidak lagi dirujuk peta, sehingga delta itu
            // hilang. Ukuran peta sudah dibatasi MAX_PENDING_PACKAGES.
        }
    }

    private static final class Pending
    {
        int blocks;
        final Map<String, Integer> networks = new HashMap<>();
    }
}
