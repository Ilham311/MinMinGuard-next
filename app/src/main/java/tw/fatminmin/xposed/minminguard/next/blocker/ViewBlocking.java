package tw.fatminmin.xposed.minminguard.next.blocker;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Menghapus iklan sekaligus ruang kosong yang ditinggalkannya.
 * <p>
 * Versi lama memproses view yang sama dua kali ({@code addView} before dan
 * after) sehingga hitungan statistik membengkak dan parameter layout ditulis
 * berulang. Sekarang setiap View hanya diproses sekali; penanda disimpan di
 * {@link WeakHashMap} agar tidak menyentuh tag milik aplikasi target (AppCompat
 * memakai tag yang sama untuk keperluan internal) dan tidak menahan view di
 * memori.
 */
public class ViewBlocking
{
    /** Tinggi maksimum (dp) yang dianggap ruang iklan, bukan konten utama. */
    private static final float MAX_AD_HEIGHT_DP = 51f;

    /** Batas kedalaman penelusuran ancestor agar tidak memakan waktu. */
    private static final int MAX_ANCESTOR_DEPTH = 8;

    private static final Map<View, Boolean> sProcessed =
            Collections.synchronizedMap(new WeakHashMap<View, Boolean>());

    public static void removeAdView(String packageName, View view)
    {
        if (view == null)
        {
            return;
        }

        if (sProcessed.put(view, Boolean.TRUE) != null)
        {
            // Sudah pernah diblokir; cukup pastikan tingginya nol.
            collapse(view);
            return;
        }

        Util.notifyRemoveAdView(view.getContext(), packageName, 1);
        removeAdView(packageName, view, true, 0);
    }

    private static void removeAdView(final String packageName, final View view, final boolean first, final int depth)
    {
        if (view == null)
        {
            return;
        }

        float adHeightDp = convertPixelsToDp(view.getContext(), view.getHeight());
        if (first || (adHeightDp > 0 && adHeightDp <= MAX_AD_HEIGHT_DP))
        {
            collapse(view);

            // LayoutParams sering belum final saat hook berjalan; ulangi sekali
            // setelah layout selesai.
            //
            // Ditunda lewat post() milik view itu sendiri, tetapi hanya bila
            // view masih terpasang. View yang sudah dilepas tidak akan pernah
            // menjalankan runnable-nya, dan memanggil collapse() pada view
            // tanpa parent justru mengubah LayoutParams lalu memicu
            // requestLayout pada pohon yang sedang dibuang — salah satu sumber
            // layout berulang yang membuat aplikasi terasa berputar.
            view.post(new Runnable()
            {
                @Override
                public void run()
                {
                    if (view.getParent() == null)
                    {
                        return;
                    }
                    float heightDp = convertPixelsToDp(view.getContext(), view.getHeight());
                    if (first || (heightDp > 0 && heightDp <= MAX_AD_HEIGHT_DP))
                    {
                        collapse(view);
                    }
                }
            });
        }

        if (depth >= MAX_ANCESTOR_DEPTH)
        {
            return;
        }

        if (view.getParent() instanceof ViewGroup)
        {
            removeAdView(packageName, (View) view.getParent(), false, depth + 1);
        }
    }

    private static void collapse(View view)
    {
        ViewGroup.LayoutParams params = view.getLayoutParams();

        if (params == null)
        {
            params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
            view.setLayoutParams(params);
        }
        else if (params.height != 0)
        {
            params.height = 0;
            view.setLayoutParams(params);
        }

        // View bertinggi 0 masih bisa menangkap sentuhan pada beberapa
        // container; sembunyikan sekalian.
        if (view.getVisibility() != View.GONE)
        {
            view.setVisibility(View.GONE);
        }
    }

    private static float convertPixelsToDp(Context context, float px)
    {
        if (context == null || context.getResources() == null)
        {
            return 0;
        }
        float density = context.getResources().getDisplayMetrics().density;
        return density <= 0 ? 0 : px / density;
    }

    /**
     * Membersihkan penanda proses. Dipanggil sekali saat hook dipasang untuk
     * sebuah paket: {@link WeakHashMap} bisa masih memuat view dari proses
     * sebelumnya yang kebetulan memakai alamat objek sama, dan itu membuat
     * view baru dianggap "sudah diblokir" sehingga statistik tidak tercatat.
     */
    public static void reset()
    {
        sProcessed.clear();
    }
}
