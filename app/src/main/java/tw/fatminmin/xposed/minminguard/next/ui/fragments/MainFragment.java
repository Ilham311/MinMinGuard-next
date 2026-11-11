package tw.fatminmin.xposed.minminguard.next.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

import tw.fatminmin.xposed.minminguard.next.FrameworkInfo;
import tw.fatminmin.xposed.minminguard.next.R;
import tw.fatminmin.xposed.minminguard.next.ui.MainActivity;
import tw.fatminmin.xposed.minminguard.next.ui.adapter.AppsAdapter;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppDetails;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppStats;

/**
 * Satu-satunya layar daftar: ringkasan total blokir + aplikasi yang ada di
 * scope LSPosed.
 * <p>
 * Tidak ada lagi tab mode, tombol "Terapkan", bilah pilih-massal, atau sakelar
 * per aplikasi. On/off sepenuhnya diatur dari scope LSPosed; layar ini hanya
 * melaporkan apa yang sedang berjalan.
 */
public class MainFragment extends Fragment
{
    public boolean isAlive = false;
    private MainActivity mainActivity;
    private AppsAdapter adapter;
    private TextView mTxtEmpty;
    private RecyclerView mRecyclerView;
    private TextView mTxtTotalBlocked;
    private TextView mTxtActiveCount;
    private TextView mTxtActiveLabel;
    private TextView mTxtListHeader;
    private TextView mTxtRuntime;
    private TextView mTxtXposedEnabled;

    public static MainFragment newInstance()
    {
        return new MainFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mainActivity = (MainActivity) getActivity();

        View view = inflater.inflate(R.layout.fragment_main, container, false);

        mTxtRuntime = view.findViewById(R.id.txt_runtime);
        mTxtXposedEnabled = view.findViewById(R.id.txt_xposed_enable);
        mTxtEmpty = view.findViewById(R.id.txt_empty);
        mTxtListHeader = view.findViewById(R.id.txt_list_header);
        mTxtTotalBlocked = view.findViewById(R.id.txt_total_blocked);
        mTxtActiveCount = view.findViewById(R.id.txt_active_count);
        mTxtActiveLabel = view.findViewById(R.id.txt_active_count_label);

        // Baris runtime sengaja tidak diisi di sini: render() selalu berjalan
        // di onViewCreated() dan mengisi nilai yang sudah memperhitungkan
        // laporan aplikasi. Mengisinya lebih awal hanya membuat label sempat
        // berkedip ke "tidak terdeteksi" lalu berubah.

        mRecyclerView = view.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AppsAdapter(requireContext(),
                mainActivity == null ? null : mainActivity.activeAppList);
        mRecyclerView.setAdapter(adapter);

        isAlive = true;
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
        if (mainActivity == null)
        {
            // Tanpa activity tidak ada daftar untuk di-render, tetapi baris
            // runtime tetap diisi supaya layar tidak menampilkan baris kosong.
            android.content.Context context = getContext();
            if (mTxtRuntime != null && context != null)
            {
                mTxtRuntime.setText(FrameworkInfo.describe(context, false));
            }
            return;
        }
        render(visibleListOfActivity(), mainActivity.activeAppList.size(),
                mainActivity.stats, mainActivity.isFiltering());
    }

    /** Daftar yang sedang tampil; kosong bila activity belum siap. */
    private List<AppDetails> visibleListOfActivity()
    {
        return mainActivity == null ? null : mainActivity.visibleList();
    }

    /**
     * Mengganti daftar dan angka ringkasan sekaligus.
     *
     * @param list      aplikasi yang sedang tampil (sudah difilter pencarian)
     * @param scopeSize jumlah aplikasi di scope tanpa filter; angka ringkasan
     *                  memakai nilai ini supaya tidak berubah saat mencari
     * @param stats     statistik per paket
     * @param filtering true bila daftar sedang disaring kueri pencarian
     */
    public void render(List<AppDetails> list, int scopeSize, Map<String, AppStats> stats, boolean filtering)
    {
        if (adapter != null)
        {
            adapter.setAppList(list);
            adapter.setStats(stats);
            adapter.notifyDataSetChanged();
        }
        updateSummary(list, scopeSize, stats, filtering);
    }

    /** Menyegarkan angka saja tanpa memuat ulang daftar aplikasi. */
    public void updateSummaryOnly(List<AppDetails> list, int scopeSize, Map<String, AppStats> stats, boolean filtering)
    {
        if (adapter != null)
        {
            adapter.setStats(stats);
            adapter.notifyDataSetChanged();
        }
        updateSummary(list, scopeSize, stats, filtering);
    }

    private void updateSummary(List<AppDetails> list, int scopeSize, Map<String, AppStats> stats, boolean filtering)
    {
        if (mTxtTotalBlocked == null)
        {
            return;
        }

        long total = 0;
        if (stats != null)
        {
            for (AppStats item : stats.values())
            {
                total += item.getBlockNum();
            }
        }
        mTxtTotalBlocked.setText(String.valueOf(total));

        mTxtActiveCount.setText(String.valueOf(scopeSize));
        mTxtActiveLabel.setText(scopeSize == 1
                ? R.string.summary_active_app_singular
                : R.string.summary_active_apps);

        // Baris runtime disegarkan bersama ringkasan: begitu ada aplikasi yang
        // melapor, modul terbukti berjalan walaupun proses UI ini sendiri tidak
        // di-hook, sehingga labelnya tidak boleh tetap "tidak terdeteksi".
        // getContext() dipakai, bukan requireContext(): ringkasan bisa tiba
        // tepat saat fragmen sedang dilepas dan itu tidak boleh melempar.
        android.content.Context context = getContext();
        if (mTxtRuntime != null && context != null)
        {
            mTxtRuntime.setText(FrameworkInfo.describe(context, scopeSize > 0));
        }
        if (mTxtXposedEnabled != null)
        {
            boolean active = scopeSize > 0 || FrameworkInfo.isFrameworkActive();
            mTxtXposedEnabled.setVisibility(active ? View.GONE : View.VISIBLE);
        }

        // Header daftar hanya berguna bila memang ada aplikasi di scope;
        // menampilkannya di atas pesan kosong terlihat seperti bug.
        if (mTxtListHeader != null)
        {
            mTxtListHeader.setVisibility(scopeSize == 0 ? View.GONE : View.VISIBLE);
        }

        boolean empty = list == null || list.isEmpty();
        if (mTxtEmpty != null)
        {
            if (empty)
            {
                // Pencarian tanpa hasil berbeda dari scope yang masih kosong:
                // pesan "belum ada aplikasi" akan menyesatkan.
                mTxtEmpty.setText(filtering ? R.string.msg_no_match : R.string.msg_empty_scope);
            }
            mTxtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (mRecyclerView != null)
        {
            mRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        isAlive = false;
        adapter = null;
        mRecyclerView = null;
        mTxtEmpty = null;
        mTxtListHeader = null;
        mTxtTotalBlocked = null;
        mTxtActiveCount = null;
        mTxtActiveLabel = null;
        mTxtRuntime = null;
        mTxtXposedEnabled = null;
    }
}
