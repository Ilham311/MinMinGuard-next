package tw.fatminmin.xposed.minminguard.next.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tw.fatminmin.xposed.minminguard.next.R;
import tw.fatminmin.xposed.minminguard.next.data.StatsRepository;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppStats;

/**
 * Menampilkan total iklan yang diblokir, aplikasi teratas, dan jaringan iklan
 * teratas. Semua data dibaca dari ContentProvider modul
 * ({@code content://.../stats} dan {@code .../networks}).
 */
public class StatsDialogFragment extends DialogFragment
{
    private static final int MAX_APPS = 10;
    private static final int MAX_NETWORKS = 10;

    public static StatsDialogFragment newInstance()
    {
        return new StatsDialogFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        setStyle(DialogFragment.STYLE_NORMAL, 0);
        if (getDialog() != null)
        {
            getDialog().setTitle(R.string.action_statistics);
        }

        View v = inflater.inflate(R.layout.dialog_stats, container, false);

        TextView txtTotal = v.findViewById(R.id.stats_total);
        TextView txtApps = v.findViewById(R.id.stats_apps);
        TextView txtNetworks = v.findViewById(R.id.stats_networks);
        TextView txtNetworksCount = v.findViewById(R.id.stats_networks_count);

        Map<String, AppStats> stats = StatsRepository.loadStats(requireContext());
        List<Map.Entry<String, Integer>> networks = StatsRepository.loadNetworkTotals(requireContext());

        long total = 0;
        List<AppStats> apps = new ArrayList<>();
        for (AppStats item : stats.values())
        {
            total += item.getBlockNum();
            if (item.getBlockNum() > 0)
            {
                apps.add(item);
            }
        }
        txtTotal.setText(String.valueOf(total));

        if (apps.isEmpty())
        {
            txtApps.setText(R.string.stats_empty);
        }
        else
        {
            apps.sort((l, r) -> Integer.compare(r.getBlockNum(), l.getBlockNum()));
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (AppStats item : apps)
            {
                sb.append(item.getPackageName())
                        .append(" - ")
                        .append(item.getBlockNum())
                        .append('\n');
                if (++shown >= MAX_APPS)
                {
                    break;
                }
            }
            txtApps.setText(sb.toString().trim());
        }

        if (networks.isEmpty())
        {
            txtNetworks.setText(R.string.stats_empty);
            if (txtNetworksCount != null)
            {
                txtNetworksCount.setText("");
            }
        }
        else
        {
            if (txtNetworksCount != null)
            {
                txtNetworksCount.setText(getString(R.string.stats_networks_count, networks.size()));
            }
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (Map.Entry<String, Integer> entry : networks)
            {
                sb.append(entry.getKey())
                        .append(" - ")
                        .append(getString(R.string.stats_hits_count, entry.getValue()))
                        .append('\n');
                if (++shown >= MAX_NETWORKS)
                {
                    break;
                }
            }
            txtNetworks.setText(sb.toString().trim());
        }

        return v;
    }
}
