package tw.fatminmin.xposed.minminguard.next.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import tw.fatminmin.xposed.minminguard.next.Common;

/**
 * Database statistik modul (menggantikan greenDAO).
 * <p>
 * Versi lama memakai {@code DaoMaster.DevOpenHelper} yang menghapus seluruh
 * tabel setiap kali versi skema naik, sehingga semua statistik pengguna hilang
 * setelah pembaruan aplikasi. Helper ini melakukan migrasi {@code ALTER TABLE}
 * yang mempertahankan data.
 * <p>
 * Skema 2 menambahkan kolom {@code LAST_SEEN} pada {@code APP_DATA} dan tabel
 * {@code NETWORK_STAT} untuk hitungan per-jaringan iklan. Semua penulisan harus
 * melalui {@link tw.fatminmin.xposed.minminguard.next.MinMinProvider}; UI hanya
 * membaca lewat provider agar tidak ada dua koneksi yang saling mengunci.
 */
public final class StatsDatabase extends SQLiteOpenHelper
{
    public static final String DB_NAME = "mmg";
    public static final int SCHEMA_VERSION = 2;

    public static final String TABLE_APP = "APP_DATA";
    public static final String TABLE_NETWORK = "NETWORK_STAT";

    public static final String COL_PKG = Common.COL_PKG_NAME;
    public static final String COL_NETWORKS = Common.COL_AD_NETWORKS;
    public static final String COL_BLOCKS = Common.COL_BLOCK_NUM;
    public static final String COL_LAST_SEEN = Common.COL_LAST_SEEN;
    public static final String COL_NETWORK = Common.COL_NETWORK;
    public static final String COL_HITS = Common.COL_NETWORK_HIT;

    private static volatile StatsDatabase sInstance;

    private StatsDatabase(Context context)
    {
        super(context.getApplicationContext(), DB_NAME, null, SCHEMA_VERSION);
    }

    public static StatsDatabase get(Context context)
    {
        StatsDatabase instance = sInstance;
        if (instance == null)
        {
            synchronized (StatsDatabase.class)
            {
                instance = sInstance;
                if (instance == null)
                {
                    instance = new StatsDatabase(context);
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    @Override
    public void onConfigure(SQLiteDatabase db)
    {
        super.onConfigure(db);
        // Provider menulis dari proses aplikasi yang di-hook, UI membaca:
        // WAL membuat pembaca tidak memblokir penulis.
        try
        {
            db.enableWriteAheadLogging();
        }
        catch (Throwable ignored)
        {
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        db.execSQL("CREATE TABLE IF NOT EXISTS \"" + TABLE_APP + "\" ("
                + "\"" + COL_PKG + "\" TEXT PRIMARY KEY NOT NULL,"
                + "\"" + COL_NETWORKS + "\" TEXT,"
                + "\"" + COL_BLOCKS + "\" INTEGER DEFAULT 0,"
                + "\"" + COL_LAST_SEEN + "\" INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS \"" + TABLE_NETWORK + "\" ("
                + "\"" + COL_PKG + "\" TEXT NOT NULL,"
                + "\"" + COL_NETWORK + "\" TEXT NOT NULL,"
                + "\"" + COL_HITS + "\" INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (\"" + COL_PKG + "\", \"" + COL_NETWORK + "\"))");

        db.execSQL("CREATE INDEX IF NOT EXISTS \"IDX_NETWORK_PKG\" ON \""
                + TABLE_NETWORK + "\" (\"" + COL_PKG + "\")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        if (oldVersion < 2)
        {
            migrateToV2(db);
        }
    }

    /**
     * Skema 1 hanya punya {@code APP_DATA(PKG_NAME, AD_NETWORKS, BLOCK_NUM)}.
     * Daftar jaringan disimpan sebagai teks dipisah spasi; di sini dipindahkan
     * ke {@code NETWORK_STAT} agar bisa dihitung per jaringan.
     */
    private void migrateToV2(SQLiteDatabase db)
    {
        db.beginTransaction();
        try
        {
            if (!hasColumn(db, TABLE_APP, COL_LAST_SEEN))
            {
                db.execSQL("ALTER TABLE \"" + TABLE_APP + "\" ADD COLUMN \""
                        + COL_LAST_SEEN + "\" INTEGER DEFAULT 0");
            }

            db.execSQL("CREATE TABLE IF NOT EXISTS \"" + TABLE_NETWORK + "\" ("
                    + "\"" + COL_PKG + "\" TEXT NOT NULL,"
                    + "\"" + COL_NETWORK + "\" TEXT NOT NULL,"
                    + "\"" + COL_HITS + "\" INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (\"" + COL_PKG + "\", \"" + COL_NETWORK + "\"))");
            db.execSQL("CREATE INDEX IF NOT EXISTS \"IDX_NETWORK_PKG\" ON \""
                    + TABLE_NETWORK + "\" (\"" + COL_PKG + "\")");

            // Pindahkan daftar jaringan lama (teks) ke tabel baru.
            android.database.Cursor cursor = db.rawQuery(
                    "SELECT \"" + COL_PKG + "\", \"" + COL_NETWORKS + "\", \""
                            + COL_BLOCKS + "\" FROM \"" + TABLE_APP + "\"", null);
            try
            {
                while (cursor.moveToNext())
                {
                    String pkg = cursor.getString(0);
                    String networks = cursor.isNull(1) ? null : cursor.getString(1);
                    int blocks = cursor.isNull(2) ? 0 : cursor.getInt(2);
                    if (networks == null || networks.length() == 0 || pkg == null)
                    {
                        continue;
                    }
                    for (String network : networks.split("\\s+"))
                    {
                        String trimmed = network.trim();
                        if (trimmed.length() == 0)
                        {
                            continue;
                        }
                        db.execSQL("INSERT OR IGNORE INTO \"" + TABLE_NETWORK + "\" (\""
                                        + COL_PKG + "\",\"" + COL_NETWORK + "\",\"" + COL_HITS + "\") VALUES (?,?,0)",
                                new Object[]{pkg, trimmed});
                    }
                    if (blocks > 0)
                    {
                        db.execSQL("UPDATE \"" + TABLE_APP + "\" SET \"" + COL_BLOCKS + "\" = ? WHERE \""
                                + COL_PKG + "\" = ?", new Object[]{blocks, pkg});
                    }
                }
            }
            finally
            {
                cursor.close();
            }

            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String column)
    {
        android.database.Cursor cursor = null;
        try
        {
            cursor = db.rawQuery("PRAGMA table_info(\"" + table + "\")", null);
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext())
            {
                if (column.equalsIgnoreCase(cursor.getString(nameIndex)))
                {
                    return true;
                }
            }
        }
        catch (Throwable ignored)
        {
        }
        finally
        {
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return false;
    }
}
