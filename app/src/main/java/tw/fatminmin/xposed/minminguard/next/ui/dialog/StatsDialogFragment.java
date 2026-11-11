package tw.fatminmin.xposed.minminguard.next.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tw.fatminmin.xposed.minminguard.next.R;
import tw.fatminmin.xposed.minminguard.next.orm.AppData;
import tw.fatminmin.xposed.minminguard.next.orm.DaoMaster;

/**
 * Shows total blocked ads plus the top apps and ad networks seen so far.
 */
public class StatsDialogFragment extends DialogFragment
{
    public static StatsDialogFragment newInstance()
    {
        return new StatsDialogFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        getDialog().setTitle(R.string.action_statistics);

        View v = inflater.inflate(R.layout.dialog_stats, container, false);

        TextView txtTotal = v.findViewById(R.id.stats_total);
        TextView txtApps = v.findViewById(R.id.stats_apps);
        TextView txtNetworks = v.findViewById(R.id.stats_networks);

        DaoMaster.DevOpenHelper helper = new DaoMaster.DevOpenHelper(requireContext(), "mmg", null);
        List<AppData> all = new DaoMaster(helper.getWritableDatabase()).newSession().getAppDataDao().loadAll();
        helper.close();

        if (all.isEmpty())
        {
            txtTotal.setText("0");
            txtApps.setText(R.string.stats_empty);
            txtNetworks.setText("");
            return v;
        }

        Map<String, Integer> networkCounts = new HashMap<>();
        long total = 0;
        for (AppData data : all)
        {
            if (data.getBlockNum() != null)
            {
                total += data.getBlockNum();
            }
            if (data.getAdNetworks() != null)
            {
                for (String network : data.getAdNetworks().split(" "))
                {
                    if (network.trim().length() > 0)
                    {
                        Integer count = networkCounts.get(network);
                        networkCounts.put(network, count == null ? 1 : count + 1);
                    }
                }
            }
        }
        txtTotal.setText(String.valueOf(total));

        ArrayList<AppData> apps = new ArrayList<>(all);
        Collections.sort(apps, new Comparator<AppData>()
        {
            @Override
            public int compare(AppData l, AppData r)
            {
                return Integer.compare(
                        r.getBlockNum() != null ? r.getBlockNum() : 0,
                        l.getBlockNum() != null ? l.getBlockNum() : 0);
            }
        });

        StringBuilder appSb = new StringBuilder();
        int shown = 0;
        for (AppData data : apps)
        {
            if (data.getBlockNum() != null && data.getBlockNum() != 0)
            {
                appSb.append(data.getPkgName())
                        .append(" - ")
                        .append(data.getBlockNum())
                        .append(getString(R.string.msg_ads_blocked).toLowerCase().replace(":", ""))
                        .append("\n");
                shown++;
                if (shown >= 10)
                {
                    break;
                }
            }
        }
        if (shown == 0)
        {
            appSb.append(getString(R.string.stats_empty));
        }
        txtApps.setText(appSb.toString().trim());

        ArrayList<Map.Entry<String, Integer>> networks = new ArrayList<>(networkCounts.entrySet());
        Collections.sort(networks, new Comparator<Map.Entry<String, Integer>>()
        {
            @Override
            public int compare(Map.Entry<String, Integer> l, Map.Entry<String, Integer> r)
            {
                return Integer.compare(r.getValue(), l.getValue());
            }
        });

        StringBuilder netSb = new StringBuilder();
        int netShown = 0;
        for (Map.Entry<String, Integer> entry : networks)
        {
            netSb.append(entry.getKey())
                    .append(" - ")
                    .append(getString(R.string.stats_apps_count, entry.getValue()))
                    .append("\n");
            netShown++;
            if (netShown >= 10)
            {
                break;
            }
        }
        txtNetworks.setText(netSb.toString().trim());

        return v;
    }
}
