package tw.fatminmin.xposed.minminguard.next.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tw.fatminmin.xposed.minminguard.next.Common;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppStats;

/**
 * Pembaca statistik untuk UI.
 * <p>
 * Semua data diambil lewat {@link ContentResolver} sehingga UI tidak membuka
 * koneksi database kedua (versi lama membuka {@code DaoMaster.DevOpenHelper}
 * di adapter, dialog, dan activity sekaligus).
 */
public final class StatsRepository
{
    private StatsRepository() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    public static Map<String, AppStats> loadStats(Context context)
    {
        Map<String, AppStats> result = new HashMap<>();
        if (context == null)
        {
            return result;
        }

        ContentResolver resolver = context.getContentResolver();
        Map<String, List<String>> networksByPackage = loadNetworkNames(resolver, null);

        Cursor cursor = null;
        try
        {
            cursor = resolver.query(Uri.parse(Common.URI_STATS),
                    new String[]{Common.COL_PKG_NAME, Common.COL_BLOCK_NUM, Common.COL_LAST_SEEN,
                            Common.COL_AD_NETWORKS},
                    null, null, null);
            if (cursor == null)
            {
                return result;
            }
            while (cursor.moveToNext())
            {
                String pkgName = cursor.getString(0);
                if (pkgName == null)
                {
                    continue;
                }
                int blockNum = cursor.isNull(1) ? 0 : cursor.getInt(1);
                long lastSeen = cursor.isNull(2) ? 0L : cursor.getLong(2);
                List<String> networks = networksByPackage.get(pkgName);
                if (networks == null)
                {
                    networks = splitNetworks(cursor.isNull(3) ? null : cursor.getString(3));
                }
                result.put(pkgName, new AppStats(pkgName, blockNum, lastSeen, networks));
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

        return result;
    }

    /** Semua jaringan beserta total hit-nya, diurutkan menurun. */
    public static List<Map.Entry<String, Integer>> loadNetworkTotals(Context context)
    {
        Map<String, Integer> totals = new HashMap<>();
        if (context == null)
        {
            return new ArrayList<>();
        }

        Cursor cursor = null;
        try
        {
            cursor = context.getContentResolver().query(Uri.parse(Common.URI_NETWORKS),
                    new String[]{Common.COL_NETWORK, Common.COL_NETWORK_HIT}, null, null, null);
            if (cursor == null)
            {
                return new ArrayList<>();
            }
            while (cursor.moveToNext())
            {
                String network = cursor.getString(0);
                if (network == null || network.length() == 0)
                {
                    continue;
                }
                int hits = cursor.isNull(1) ? 0 : cursor.getInt(1);
                Integer current = totals.get(network);
                totals.put(network, current == null ? hits : current + hits);
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

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(totals.entrySet());
        Collections.sort(entries, (l, r) -> Integer.compare(r.getValue(), l.getValue()));
        return entries;
    }

    /** Hapus semua statistik (dipakai aksi "Reset statistik"). */
    public static void clearAll(Context context)
    {
        if (context == null)
        {
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        try
        {
            resolver.delete(Uri.parse(Common.URI_STATS), null, null);
            resolver.delete(Uri.parse(Common.URI_NETWORKS), null, null);
        }
        catch (Throwable ignored)
        {
        }
    }

    public static void clearForPackage(Context context, String pkgName)
    {
        if (context == null || pkgName == null)
        {
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        try
        {
            resolver.delete(Uri.withAppendedPath(Uri.parse(Common.URI_STATS), pkgName), null, null);
            resolver.delete(Uri.withAppendedPath(Uri.parse(Common.URI_NETWORKS), pkgName), null, null);
        }
        catch (Throwable ignored)
        {
        }
    }

    private static Map<String, List<String>> loadNetworkNames(ContentResolver resolver, String pkgName)
    {
        Map<String, List<String>> result = new HashMap<>();
        Cursor cursor = null;
        try
        {
            Uri uri = Uri.parse(Common.URI_NETWORKS);
            if (pkgName != null)
            {
                uri = Uri.withAppendedPath(uri, pkgName);
            }
            cursor = resolver.query(uri,
                    new String[]{Common.COL_PKG_NAME, Common.COL_NETWORK}, null, null, null);
            if (cursor == null)
            {
                return result;
            }
            while (cursor.moveToNext())
            {
                String pkg = cursor.getString(0);
                String network = cursor.getString(1);
                if (pkg == null || network == null)
                {
                    continue;
                }
                List<String> list = result.get(pkg);
                if (list == null)
                {
                    list = new ArrayList<>();
                    result.put(pkg, list);
                }
                if (!list.contains(network))
                {
                    list.add(network);
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
        return result;
    }

    private static List<String> splitNetworks(String raw)
    {
        if (raw == null || raw.length() == 0)
        {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (String part : raw.split("\\s+"))
        {
            String trimmed = part.trim();
            if (trimmed.length() > 0)
            {
                list.add(trimmed);
            }
        }
        return list;
    }
}
