package tw.fatminmin.xposed.minminguard.next.blocker;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
            if (pending.retired)
            {
                // Entri ini sudah dilepas dari peta saat flush berjalan; delta
                // harus dicatat ke entri yang sekarang, bukan ke objek mati.
                pending = null;
            }
            else
            {
                pending.blocks += delta;
            }
        }
        if (pending == null)
        {
            recordBlock(pkgName, delta);
            return;
        }
        scheduleFlush();
    }

    /**
     * Mencatat bahwa SDK sebuah jaringan ada di aplikasi ini, tanpa menambah
     * hitungan iklan. Dipakai saat hook dipasang; melihat kelas SDK bukan
     * berarti ada iklan yang diblokir.
     */
    public void detectNetwork(String pkgName, String network)
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
            if (pending.retired)
            {
                pending = null;
            }
            else
            {
                pending.detected.add(network);
            }
        }
        if (pending == null)
        {
            detectNetwork(pkgName, network);
            return;
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
            if (pending.retired)
            {
                pending = null;
            }
            else
            {
                Integer current = pending.networks.get(network);
                pending.networks.put(network, current == null ? 1 : current + 1);
            }
        }
        if (pending == null)
        {
            recordNetwork(pkgName, network);
            return;
        }
        scheduleFlush();
    }

    /**
     * Mengambil (atau membuat) entri untuk satu paket.
     * <p>
     * Peta dibatasi {@link #MAX_PENDING_PACKAGES} agar tidak tumbuh tanpa batas
     * ketika modul tidak terpasang dan {@code update()} selalu gagal. Entri
     * kosong dibuang lebih dulu supaya batas itu tidak membuat paket baru
     * berhenti mencatat statistik selamanya.
     */
    private Pending pendingFor(final String pkgName)
    {
        Pending pending = mPending.get(pkgName);
        if (pending != null)
        {
            return pending;
        }
        if (mPending.size() >= MAX_PENDING_PACKAGES)
        {
            evictIdleEntry();
        }
        return mPending.computeIfAbsent(pkgName, key -> new Pending());
    }

    /** Melepas satu entri tanpa delta tertunda; mengembalikan false bila tidak ada. */
    private boolean evictIdleEntry()
    {
        for (String key : mPending.keySet())
        {
            Pending candidate = mPending.get(key);
            if (candidate == null)
            {
                continue;
            }
            synchronized (candidate)
            {
                if (candidate.blocks != 0 || !candidate.networks.isEmpty())
                {
                    continue;
                }
                // Tandai mati di dalam lock yang sama dengan recordBlock/
                // recordNetwork, sehingga tidak ada penulis yang masih
                // memegang referensi ini saat entri dilepas.
                candidate.retired = true;
                return mPending.remove(key, candidate);
            }
        }
        return false;
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
            // Entri ditandai mati lebih dulu supaya penulis yang masih
            // memegang referensinya menulis ulang ke entri baru.
            for (Pending pending : mPending.values())
            {
                synchronized (pending)
                {
                    pending.retired = true;
                    pending.blocks = 0;
                    pending.networks.clear();
                }
            }
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
            Set<String> detected;
            synchronized (pending)
            {
                blocks = pending.blocks;
                networks = new HashMap<>(pending.networks);
                detected = new HashSet<>(pending.detected);
                pending.blocks = 0;
                pending.networks.clear();
                pending.detected.clear();
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

            // Jaringan yang hanya terdeteksi: catat namanya tanpa menghitung
            // sebagai iklan yang diblokir.
            for (String network : detected)
            {
                if (networks.containsKey(network))
                {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(Common.KEY_PKG_NAME, pkgName);
                values.put(Common.KEY_NETWORK, network);
                values.put(Common.KEY_NETWORK_COUNT, 0);
                values.put(Common.KEY_NETWORK_DETECT, true);
                try
                {
                    resolver.update(networkUri, values, null, null);
                }
                catch (Throwable t)
                {
                    Util.log(pkgName, "gagal mengirim deteksi jaringan: " + t);
                }
            }

            // Entri tidak dihapus di sini: recordBlock() yang datang bersamaan
            // bisa masih memegang referensinya. Entri kosong baru dilepas oleh
            // evictIdleEntry() ketika peta mencapai MAX_PENDING_PACKAGES, di
            // bawah lock yang sama dengan penulisnya.
        }
    }

    private static final class Pending
    {
        int blocks;
        /** Jaringan yang benar-benar memblokir iklan, beserta jumlahnya. */
        final Map<String, Integer> networks = new HashMap<>();
        /** Jaringan yang hanya terdeteksi terpasang di aplikasi. */
        final Set<String> detected = new HashSet<>();
        /** True bila entri sudah dilepas dari peta dan tidak boleh menerima delta baru. */
        boolean retired;
    }
}
