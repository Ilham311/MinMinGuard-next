package tw.fatminmin.xposed.minminguard.next.blocker;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * pkg -> [blockDelta, map jaringan -> hitDelta].
     * <p>
     * Tipe konkret {@code ConcurrentHashMap}, bukan {@code Map}: {@code
     * putIfAbsent}/{@code remove(k,v)} pada antarmuka {@code Map} adalah default
     * method API 24, sementara versi {@code ConcurrentMap} sudah ada sejak
     * API 1 (minSdk modul 21).
     */
    private final ConcurrentHashMap<String, Pending> mPending = new ConcurrentHashMap<>();

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

    /**
     * Paket yang sudah didaftarkan sebagai "aktif" di proses ini.
     * <p>
     * Memakai {@code newSetFromMap} karena {@code ConcurrentHashMap.newKeySet()}
     * baru ada di API 24, sedangkan minSdk modul ini 21 — memanggilnya di
     * Android 5–6 akan melempar {@code NoSuchMethodError} dan mematikan
     * pelaporan statistik.
     */
    private final Set<String> mRegistered = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /**
     * Pendaftaran yang belum berhasil, beserta jumlah percobaan.
     * <p>
     * Dibersihkan setelah {@link #MAX_REGISTER_ATTEMPTS}; jumlahnya dibatasi
     * oleh jumlah aplikasi di scope, jadi tidak perlu batas tambahan.
     */
    private final ConcurrentHashMap<String, Registration> mRetry = new ConcurrentHashMap<>();

    /** Batas percobaan pendaftaran agar tidak mencoba selamanya. */
    private static final int MAX_REGISTER_ATTEMPTS = 8;

    /** Jeda sebelum pendaftaran yang gagal dicoba lagi (ms). */
    private static final long REGISTER_RETRY_DELAY_MS = 1_500L;

    /**
     * Penjaga agar satu percobaan ulang saja yang diantrekan.
     * <p>
     * Tidak bisa memakai {@link #mScheduledFlush}: method itu dipanggil dari
     * dalam task flush yang sedang berjalan, sehingga pemeriksaan
     * {@code isDone()} selalu menolak dan percobaan ulang tidak pernah terjadi.
     */
    private final java.util.concurrent.atomic.AtomicBoolean mRetryScheduled =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static final class Registration
    {
        final String version;
        int attempts;

        Registration(String version)
        {
            this.version = version;
        }
    }

    /**
     * Mendaftarkan paket ini sebagai aplikasi yang benar-benar di-hook modul.
     * <p>
     * Inilah sumber daftar "aplikasi target" di UI. Konfigurasi scope LSPosed
     * hanya bisa dibaca root, tetapi paket tempat modul berjalan dapat
     * melaporkan dirinya sendiri. Pendaftaran dilakukan sebelum hook dipasang
     * supaya aplikasi tetap muncul walaupun tidak ada SDK iklan yang dikenali.
     * <p>
     * Ditulis lewat executor yang sama dengan statistik agar urutan
     * (daftar aktif lalu angka) tetap terjaga.
     */
    public void registerActive(String pkgName, String version)
    {
        if (pkgName == null || pkgName.isEmpty() || !mRegistered.add(pkgName))
        {
            return;
        }
        mExecutor.execute(() -> writeActiveOrRetry(pkgName, version));
    }

    /**
     * Menulis pendaftaran; bila gagal, disimpan untuk dicoba lagi.
     * <p>
     * Provider bisa belum bisa dihubungi saat {@code Application.onCreate}
     * (proses modul belum hidup, atau ContentResolver belum siap). Versi
     * sebelumnya menyerah pada kegagalan pertama, sehingga aplikasi itu tidak
     * pernah muncul di daftar UI walaupun pemblokiran berjalan normal.
     */
    private void writeActiveOrRetry(final String pkgName, final String version)
    {
        if (writeActive(pkgName, version))
        {
            mRetry.remove(pkgName);
            return;
        }
        Registration pending = mRetry.get(pkgName);
        if (pending == null)
        {
            pending = new Registration(version);
            Registration previous = mRetry.putIfAbsent(pkgName, pending);
            if (previous != null)
            {
                pending = previous;
            }
        }
        pending.attempts++;
        if (pending.attempts >= MAX_REGISTER_ATTEMPTS)
        {
            mRetry.remove(pkgName);
            Util.warn("gagal mendaftarkan " + pkgName + " sebagai aplikasi aktif setelah "
                    + pending.attempts + " percobaan; aplikasi tetap diblokir tetapi tidak "
                    + "muncul di daftar");
            return;
        }
        scheduleRetry();
    }

    /** Menjadwalkan satu percobaan ulang pendaftaran bila belum ada yang diantrekan. */
    private void scheduleRetry()
    {
        if (!mRetryScheduled.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            // Jeda dipasang lewat ScheduledExecutorService, bukan sleep di
            // dalam task: sebelumnya task ini menahan satu-satunya thread
            // executor selama 1,5 detik, dan selama itu semua pengiriman
            // statistik ikut tertunda. Aplikasi yang membuka banyak iklan awal
            // (persis alur reward) jadi menumpuk ribuan delta di memori.
            sRetryScheduler.schedule(() -> {
                mRetryScheduled.set(false);
                flush();
            }, REGISTER_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        }
        catch (Throwable t)
        {
            mRetryScheduled.set(false);
        }
    }

    /** Penjadwal terpisah untuk percobaan ulang pendaftaran. */
    private static final java.util.concurrent.ScheduledExecutorService sRetryScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mmg-retry");
                t.setDaemon(true);
                return t;
            });

    /**
     * Mencoba ulang pendaftaran yang tertunda.
     *
     * @return true bila masih ada yang perlu dicoba lagi
     */
    private boolean retryRegistrations()
    {
        if (mRetry.isEmpty())
        {
            return false;
        }
        boolean stillFailing = false;
        for (String pkgName : new ArrayList<>(mRetry.keySet()))
        {
            Registration pending = mRetry.get(pkgName);
            if (pending == null)
            {
                continue;
            }
            if (writeActive(pkgName, pending.version))
            {
                mRetry.remove(pkgName);
                continue;
            }
            pending.attempts++;
            if (pending.attempts >= MAX_REGISTER_ATTEMPTS)
            {
                mRetry.remove(pkgName);
                Util.warn("berhenti mencoba mendaftarkan " + pkgName + " sebagai aplikasi aktif");
            }
            else
            {
                stillFailing = true;
            }
        }
        return stillFailing;
    }

    /** @return true bila pendaftaran tersimpan di provider. */
    private boolean writeActive(final String pkgName, final String version)
    {
        Context context = Util.getCurrentApplication();
        if (context == null)
        {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(Common.KEY_PKG_NAME, pkgName);
        values.put(Common.KEY_APP_VERSION, version);
        try
        {
            context.getContentResolver().update(Uri.parse(Common.URI_ACTIVE), values, null, null);
            return true;
        }
        catch (Throwable t)
        {
            Util.log(pkgName, "gagal mendaftarkan aplikasi aktif: " + t);
            return false;
        }
    }

    public void recordNetwork(String pkgName, String network)    {
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
        // computeIfAbsent juga API 24+; pakai pola cek-lalu-put yang setara
        // di bawah ini.
        Pending created = mPending.get(pkgName);
        if (created == null)
        {
            created = new Pending();
            Pending previous = mPending.putIfAbsent(pkgName, created);
            if (previous != null)
            {
                created = previous;
            }
        }
        return created;
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
        // Pendaftaran aplikasi aktif dicoba ulang lebih dulu: bila provider
        // akhirnya bisa dihubungi, barisnya langsung terlihat di UI.
        boolean retryNeeded = retryRegistrations();

        if (mPending.isEmpty())
        {
            if (retryNeeded)
            {
                scheduleRetry();
            }
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
            if (retryNeeded)
            {
                scheduleRetry();
            }
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
