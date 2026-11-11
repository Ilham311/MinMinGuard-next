package tw.fatminmin.xposed.minminguard.next;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import tw.fatminmin.xposed.minminguard.next.data.StatsDatabase;

/**
 * Satu-satunya penulis statistik pemblokiran.
 * <p>
 * Sebelumnya provider hanya mengimplementasikan {@code update()} dengan pola
 * baca-ubah-tulis tanpa transaksi, sehingga dua proses aplikasi yang diblokir
 * hampir bersamaan bisa saling menimpa hitungan. Sekarang semua penambahan
 * dilakukan lewat satu statement SQL atomik
 * ({@code INSERT ... ON CONFLICT DO UPDATE}) di dalam transaksi.
 * <p>
 * {@code query()} dipakai UI untuk membaca statistik tanpa membuka database
 * sendiri, dan {@code delete()} menyediakan fitur "reset statistik".
 */
public class MinMinProvider extends ContentProvider
{
    private static final int MATCH_STATS = 1;
    private static final int MATCH_STATS_ITEM = 2;
    private static final int MATCH_NETWORKS = 3;
    private static final int MATCH_NETWORKS_ITEM = 4;

    private static final UriMatcher sMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static
    {
        sMatcher.addURI(Common.AUTHORITY, "stats", MATCH_STATS);
        sMatcher.addURI(Common.AUTHORITY, "stats/*", MATCH_STATS_ITEM);
        sMatcher.addURI(Common.AUTHORITY, "networks", MATCH_NETWORKS);
        sMatcher.addURI(Common.AUTHORITY, "networks/*", MATCH_NETWORKS_ITEM);
    }

    private StatsDatabase mDatabase;

    @Override
    public boolean onCreate()
    {
        mDatabase = StatsDatabase.get(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder)
    {
        int match = sMatcher.match(uri);
        SQLiteDatabase db = mDatabase.getReadableDatabase();

        String table;
        String where = selection;
        String[] whereArgs = selectionArgs;
        String order = sortOrder;

        switch (match)
        {
            case MATCH_STATS:
                table = StatsDatabase.TABLE_APP;
                if (order == null)
                {
                    order = StatsDatabase.COL_BLOCKS + " DESC";
                }
                break;
            case MATCH_STATS_ITEM:
                table = StatsDatabase.TABLE_APP;
                where = StatsDatabase.COL_PKG + " = ?";
                whereArgs = new String[]{uri.getLastPathSegment()};
                break;
            case MATCH_NETWORKS:
                table = StatsDatabase.TABLE_NETWORK;
                if (order == null)
                {
                    order = StatsDatabase.COL_HITS + " DESC";
                }
                break;
            case MATCH_NETWORKS_ITEM:
                table = StatsDatabase.TABLE_NETWORK;
                where = StatsDatabase.COL_PKG + " = ?";
                whereArgs = new String[]{uri.getLastPathSegment()};
                break;
            default:
                return null;
        }

        try
        {
            return db.query(table, projection, where, whereArgs, null, null, order);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri)
    {
        switch (sMatcher.match(uri))
        {
            case MATCH_STATS:
                return "vnd.android.cursor.dir/vnd." + Common.AUTHORITY + ".stats";
            case MATCH_STATS_ITEM:
                return "vnd.android.cursor.item/vnd." + Common.AUTHORITY + ".stats";
            case MATCH_NETWORKS:
                return "vnd.android.cursor.dir/vnd." + Common.AUTHORITY + ".networks";
            case MATCH_NETWORKS_ITEM:
                return "vnd.android.cursor.item/vnd." + Common.AUTHORITY + ".networks";
            default:
                return null;
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values)
    {
        // Tidak ada insert mentah dari luar: semua penulisan lewat update()
        // agar delta selalu atomik.
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs)
    {
        SQLiteDatabase db = mDatabase.getWritableDatabase();
        int deleted;

        switch (sMatcher.match(uri))
        {
            case MATCH_STATS:
                deleted = db.delete(StatsDatabase.TABLE_APP, selection, selectionArgs);
                break;
            case MATCH_STATS_ITEM:
                deleted = db.delete(StatsDatabase.TABLE_APP, StatsDatabase.COL_PKG + " = ?",
                        new String[]{uri.getLastPathSegment()});
                break;
            case MATCH_NETWORKS:
                deleted = db.delete(StatsDatabase.TABLE_NETWORK, selection, selectionArgs);
                break;
            case MATCH_NETWORKS_ITEM:
                deleted = db.delete(StatsDatabase.TABLE_NETWORK, StatsDatabase.COL_PKG + " = ?",
                        new String[]{uri.getLastPathSegment()});
                break;
            default:
                return 0;
        }

        notifyChange(uri);
        return deleted;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs)
    {
        if (values == null)
        {
            return 0;
        }

        switch (sMatcher.match(uri))
        {
            case MATCH_STATS:
                return updateStats(values);
            case MATCH_NETWORKS:
                return updateNetwork(values);
            default:
                return 0;
        }
    }

    /** Menambah hitungan blokir satu aplikasi (delta, bukan nilai absolut). */
    private int updateStats(ContentValues values)
    {
        String pkgName = values.getAsString(Common.KEY_PKG_NAME);
        Integer delta = values.getAsInteger(Common.KEY_BLOCK_NUM);
        if (pkgName == null)
        {
            return 0;
        }
        int blocks = delta == null ? 0 : delta;

        SQLiteDatabase db = mDatabase.getWritableDatabase();
        // UPSERT manual (INSERT OR IGNORE + UPDATE) di dalam transaksi.
        // Sintaks "ON CONFLICT DO UPDATE" baru ada di SQLite 3.24 (Android 11),
        // sedangkan minSdk modul ini 21.
        db.beginTransaction();
        try
        {
            db.execSQL("INSERT OR IGNORE INTO \"" + StatsDatabase.TABLE_APP + "\" (\""
                            + StatsDatabase.COL_PKG + "\",\"" + StatsDatabase.COL_NETWORKS + "\",\""
                            + StatsDatabase.COL_BLOCKS + "\",\"" + StatsDatabase.COL_LAST_SEEN + "\") "
                            + "VALUES (?, '', 0, 0)",
                    new Object[]{pkgName});
            db.execSQL("UPDATE \"" + StatsDatabase.TABLE_APP + "\" SET \""
                            + StatsDatabase.COL_BLOCKS + "\" = \"" + StatsDatabase.COL_BLOCKS + "\" + ?, \""
                            + StatsDatabase.COL_LAST_SEEN + "\" = ? WHERE \""
                            + StatsDatabase.COL_PKG + "\" = ?",
                    new Object[]{blocks, System.currentTimeMillis(), pkgName});
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }

        notifyChange(Uri.parse(Common.URI_STATS));
        return 1;
    }

    /** Menambah hitungan satu jaringan iklan untuk satu aplikasi. */
    private int updateNetwork(ContentValues values)
    {
        String pkgName = values.getAsString(Common.KEY_PKG_NAME);
        String network = values.getAsString(Common.KEY_NETWORK);
        Integer delta = values.getAsInteger(Common.KEY_NETWORK_COUNT);
        if (pkgName == null || network == null || network.length() == 0)
        {
            return 0;
        }
        int hits = delta == null ? 0 : delta;

        // Mode deteksi: SDK ditemukan saat hook dipasang. Nama jaringan tetap
        // dicatat supaya aplikasi tampil punya jaringan itu, tetapi HIT_NUM
        // tidak ditambah karena tidak ada iklan yang benar-benar diblokir.
        try
        {
            if (Boolean.TRUE.equals(values.getAsBoolean(Common.KEY_NETWORK_DETECT)))
            {
                hits = 0;
            }
        }
        catch (ClassCastException ignored)
        {
            // Nilai bukan boolean: perlakukan sebagai hit biasa.
        }

        SQLiteDatabase db = mDatabase.getWritableDatabase();
        db.beginTransaction();
        try
        {
            db.execSQL("INSERT OR IGNORE INTO \"" + StatsDatabase.TABLE_NETWORK + "\" (\""
                            + StatsDatabase.COL_PKG + "\",\"" + StatsDatabase.COL_NETWORK + "\",\""
                            + StatsDatabase.COL_HITS + "\") VALUES (?,?,0)",
                    new Object[]{pkgName, network});
            db.execSQL("UPDATE \"" + StatsDatabase.TABLE_NETWORK + "\" SET \""
                            + StatsDatabase.COL_HITS + "\" = \"" + StatsDatabase.COL_HITS + "\" + ? "
                            + "WHERE \"" + StatsDatabase.COL_PKG + "\" = ? AND \""
                            + StatsDatabase.COL_NETWORK + "\" = ?",
                    new Object[]{hits, pkgName, network});

            // Daftar jaringan untuk kartu aplikasi diambil dari sini.
            ensureAppRow(db, pkgName);
            appendNetworkName(db, pkgName, network);
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }

        notifyChange(Uri.parse(Common.URI_NETWORKS));
        return 1;
    }

    private void ensureAppRow(SQLiteDatabase db, String pkgName)
    {
        db.execSQL("INSERT OR IGNORE INTO \"" + StatsDatabase.TABLE_APP + "\" (\""
                        + StatsDatabase.COL_PKG + "\",\"" + StatsDatabase.COL_NETWORKS + "\",\""
                        + StatsDatabase.COL_BLOCKS + "\",\"" + StatsDatabase.COL_LAST_SEEN + "\") "
                        + "VALUES (?, '', 0, ?)",
                new Object[]{pkgName, System.currentTimeMillis()});
    }

    private void appendNetworkName(SQLiteDatabase db, String pkgName, String network)
    {
        android.database.Cursor cursor = null;
        try
        {
            cursor = db.rawQuery("SELECT \"" + StatsDatabase.COL_NETWORKS + "\" FROM \""
                    + StatsDatabase.TABLE_APP + "\" WHERE \"" + StatsDatabase.COL_PKG + "\" = ?", new String[]{pkgName});
            String current = null;
            if (cursor.moveToFirst() && !cursor.isNull(0))
            {
                current = cursor.getString(0);
            }
            if (current == null)
            {
                current = "";
            }
            for (String existing : current.split("\\s+"))
            {
                if (existing.equals(network))
                {
                    return;
                }
            }
            String updated = current.length() == 0 ? network : current + " " + network;
            db.execSQL("UPDATE \"" + StatsDatabase.TABLE_APP + "\" SET \""
                            + StatsDatabase.COL_NETWORKS + "\" = ? WHERE \"" + StatsDatabase.COL_PKG + "\" = ?",
                    new Object[]{updated, pkgName});
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
    }

    private void notifyChange(Uri uri)
    {
        try
        {
            if (getContext() != null)
            {
                getContext().getContentResolver().notifyChange(uri, null);
            }
        }
        catch (Throwable ignored)
        {
        }
    }
}
