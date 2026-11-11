package tw.fatminmin.xposed.minminguard.next.ui.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import tw.fatminmin.xposed.minminguard.next.Common;
import tw.fatminmin.xposed.minminguard.next.FrameworkInfo;
import tw.fatminmin.xposed.minminguard.next.R;
import tw.fatminmin.xposed.minminguard.next.blocker.Util;
import tw.fatminmin.xposed.minminguard.next.ui.MainActivity;
import tw.fatminmin.xposed.minminguard.next.ui.adapter.AppsAdapter;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppDetails;

public class MainFragment extends Fragment
{

    public boolean isAlive = false;
    private MainActivity mainActivity;
    private FragmentMode mMode;
    private Button mBtnMode;
    private Button mBtnBatchSelectAll;
    private View mBatchBar;
    private TextView mTxtBatchCount;
    private TextView mTxtEmpty;
    private AppsAdapter adapter;
    private SharedPreferences mModPref;
    private final View.OnClickListener btnModeClick = new View.OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            mModPref.edit().putString(Common.KEY_MODE, modeValue()).apply();
            adapter.notifyDataSetChanged();

            String[] modes = getResources().getStringArray(R.array.mode_array);
            String label = mMode.ordinal() < modes.length ? modes[mMode.ordinal()] : "";
            Snackbar.make(v, getString(R.string.msg_mode_applied, label), Snackbar.LENGTH_LONG).show();
        }
    };

    public static MainFragment newInstance(FragmentMode mode)
    {
        MainFragment fragment = new MainFragment();
        Bundle args = new Bundle();
        args.putInt(Common.KEY_MODE, mode.ordinal());
        fragment.setArguments(args);
        return fragment;
    }

    /** Nilai preferensi untuk mode ini (dipakai saat tombol "Terapkan" ditekan). */
    public String modeValue()
    {
        switch (mMode)
        {
            case AUTO:
                return Common.VALUE_MODE_AUTO;
            case WHITELIST:
                return Common.VALUE_MODE_WHITELIST;
            case BLACKLIST:
            default:
                return Common.VALUE_MODE_BLACKLIST;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mMode = FragmentMode.values()[getArguments().getInt(Common.KEY_MODE)];
    }

    public void refreshUI()
    {
        if (mBtnMode != null)
        {
            mBtnMode.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mainActivity = (MainActivity) getActivity();
        mModPref = mainActivity.modPref;

        View view = inflater.inflate(R.layout.fragment_main, container, false);

        TextView mTxtXposedEnabled = view.findViewById(R.id.txt_xposed_enable);
        TextView mTxtRuntime = view.findViewById(R.id.txt_runtime);
        TextView mTxtModeInfo = view.findViewById(R.id.txt_mode_info);
        mBtnMode = view.findViewById(R.id.btn_mode);

        switch (mMode)
        {
            case AUTO:
                mTxtModeInfo.setText(R.string.mode_info_auto);
                break;
            case WHITELIST:
                mTxtModeInfo.setText(R.string.mode_info_whitelist);
                break;
            case BLACKLIST:
            default:
                mTxtModeInfo.setText(R.string.mode_info_blacklist);
                break;
        }

        RecyclerView mRecyclerView = view.findViewById(R.id.recycler_view);
        mTxtEmpty = view.findViewById(R.id.txt_empty);

        if (!Util.xposedEnabled())
        {
            mTxtXposedEnabled.setVisibility(View.VISIBLE);
        }

        mTxtRuntime.setText(FrameworkInfo.describe(getActivity()));

        /* setup apply button */
        mBtnMode.setOnClickListener(btnModeClick);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(mainActivity));
        adapter = new AppsAdapter(getActivity(), mainActivity.masterAppList, mMode);
        mRecyclerView.setAdapter(adapter);

        /* batch selection bar */
        mBatchBar = view.findViewById(R.id.batch_bar);
        mTxtBatchCount = view.findViewById(R.id.txt_batch_count);
        mBtnBatchSelectAll = view.findViewById(R.id.btn_batch_select_all);

        mBtnBatchSelectAll.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                adapter.toggleSelectAll();
            }
        });
        view.findViewById(R.id.btn_batch_enable).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                applyBatch(true);
            }
        });
        view.findViewById(R.id.btn_batch_disable).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                applyBatch(false);
            }
        });
        view.findViewById(R.id.btn_batch_cancel).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                exitSelectionMode();
            }
        });

        adapter.setOnSelectionChangedListener(new AppsAdapter.OnSelectionChangedListener()
        {
            @Override
            public void onSelectionChanged(int count)
            {
                if (mTxtBatchCount != null)
                {
                    mTxtBatchCount.setText(getString(R.string.batch_selected, count));
                }
                updateSelectAllLabel(count);
            }
        });
        adapter.setOnSelectionModeListener(new AppsAdapter.OnSelectionModeListener()
        {
            @Override
            public void onSelectionModeRequested()
            {
                enterSelectionMode();
            }
        });

        if (mMode == FragmentMode.AUTO)
        {
            mBatchBar.setVisibility(View.GONE);
        }
        else
        {
            mTxtBatchCount.setText(getString(R.string.batch_selected, 0));
            updateSelectAllLabel(0);
        }

        isAlive = true;
        return view;
    }

    private void applyBatch(boolean enable)
    {
        List<String> selected = adapter.getSelectedPackages();
        if (selected.isEmpty())
        {
            exitSelectionMode();
            return;
        }

        SharedPreferences.Editor editor = mModPref.edit();
        for (String pkgName : selected)
        {
            if (mMode == FragmentMode.BLACKLIST)
            {
                editor.putBoolean(pkgName, enable);
            }
            else if (mMode == FragmentMode.WHITELIST)
            {
                editor.putBoolean(Common.getWhiteListKey(pkgName), enable);
            }
        }
        editor.apply();

        final int count = selected.size();
        exitSelectionMode();
        mainActivity.refresh(false);
        View root = getView();
        if (root != null)
        {
            Snackbar.make(root, getString(R.string.msg_batch_done, count), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void exitSelectionMode()
    {
        if (adapter != null)
        {
            adapter.setSelectionMode(false);
        }
        if (mTxtBatchCount != null)
        {
            mTxtBatchCount.setText(getString(R.string.batch_selected, 0));
        }
        updateSelectAllLabel(0);
    }

    private void enterSelectionMode()
    {
        if (mMode == FragmentMode.AUTO || adapter == null || mBatchBar == null)
        {
            return;
        }
        adapter.setSelectionMode(true);
        mBatchBar.setVisibility(View.VISIBLE);
        mTxtBatchCount.setText(getString(R.string.batch_selected, 0));
        updateSelectAllLabel(0);
    }

    private void updateSelectAllLabel(int selectedCount)
    {
        if (mBtnBatchSelectAll == null || adapter == null)
        {
            return;
        }
        boolean allSelected = selectedCount > 0 && selectedCount >= adapter.getItemCount();
        mBtnBatchSelectAll.setText(allSelected ? R.string.batch_deselect_all : R.string.batch_select_all);
    }

    /** Menyegarkan hitungan iklan tanpa memuat ulang daftar aplikasi. */
    public void updateStats()
    {
        if (adapter != null)
        {
            adapter.reloadStats();
        }
    }

    public void updateAndNotify(ArrayList<AppDetails> list)
    {
        if (adapter != null)
        {
            adapter.setAppList(list);
            adapter.notifyDataSetChanged();
        }
        updateEmptyState(list);
    }

    private void updateEmptyState(List<AppDetails> list)
    {
        if (mTxtEmpty == null)
        {
            return;
        }
        boolean empty = list == null || list.isEmpty();
        mTxtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        isAlive = false;
        adapter = null;
    }

    public enum FragmentMode
    {
        AUTO, BLACKLIST, WHITELIST
    }
}
