package tw.fatminmin.xposed.minminguard.next.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import tw.fatminmin.xposed.minminguard.next.Common;
import tw.fatminmin.xposed.minminguard.next.R;
import tw.fatminmin.xposed.minminguard.next.data.StatsRepository;
import tw.fatminmin.xposed.minminguard.next.ui.MainActivity;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppStats;

/**
 * Detail satu aplikasi: ikon, jumlah iklan diblokir, jaringan yang terdeteksi,
 * dan sakelar filter per aplikasi (URL, DNS, whitelist).
 */
public class AppDetailDialogFragment extends DialogFragment
{
    private static final String ARG_APP_NAME = "appName";
    private static final String ARG_PKG_NAME = "pkgName";
    private static final String ARG_AD_NETWORKS = "adNetworks";
    private static final String ARG_BLOCK_NUM = "blockNum";
    private static final String ARG_LAST_SEEN = "lastSeen";

    private ImageView imgAppIcon;
    private TextView txtAppName;
    private TextView txtPkgName;
    private TextView txtAdNetworks;
    private TextView txtAdsBlocked;
    private TextView txtLastSeen;
    private SwitchCompat swtUrlFilter;
    private SwitchCompat swtDnsFilter;
    private SwitchCompat swtWhitelist;

    private String appName;
    private String pkgName;
    private String adNetworks;
    private int blockNum;
    private long lastSeen;

    private SharedPreferences mPref;

    public static AppDetailDialogFragment newInstance(String appName, String pkgName, AppStats stats)
    {
        Bundle args = new Bundle();
        args.putString(ARG_APP_NAME, appName);
        args.putString(ARG_PKG_NAME, pkgName);
        if (stats != null)
        {
            args.putString(ARG_AD_NETWORKS, stats.getNetworksLabel());
            args.putInt(ARG_BLOCK_NUM, stats.getBlockNum());
            args.putLong(ARG_LAST_SEEN, stats.getLastSeen());
        }

        AppDetailDialogFragment fragment = new AppDetailDialogFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        appName = args.getString(ARG_APP_NAME);
        pkgName = args.getString(ARG_PKG_NAME);
        adNetworks = args.getString(ARG_AD_NETWORKS);
        blockNum = args.getInt(ARG_BLOCK_NUM, 0);
        lastSeen = args.getLong(ARG_LAST_SEEN, 0L);

        Context ctx = ContextCompat.createDeviceProtectedStorageContext(getActivity());
        if (ctx == null) ctx = getActivity();

        mPref = ctx.getSharedPreferences(Common.MOD_PREFS, Common.getPrefMode());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);

        View v = inflater.inflate(R.layout.dialog_app_detail, container, false);
        imgAppIcon = v.findViewById(R.id.detail_app_icon);
        txtAppName = v.findViewById(R.id.detail_app_name);
        txtPkgName = v.findViewById(R.id.detail_pkg_name);
        txtAdNetworks = v.findViewById(R.id.txt_ad_networks);
        txtAdsBlocked = v.findViewById(R.id.txt_ads_blocked);
        txtLastSeen = v.findViewById(R.id.txt_last_seen);
        swtUrlFilter = v.findViewById(R.id.switch_url_filter);
        swtDnsFilter = v.findViewById(R.id.switch_dns_filter);
        swtWhitelist = v.findViewById(R.id.switch_whitelist);

        txtAppName.setText(appName);
        txtPkgName.setText(pkgName);
        txtAdNetworks.setText(adNetworks == null || adNetworks.length() == 0
                ? getString(R.string.detail_no_networks) : adNetworks);
        txtAdsBlocked.setText(String.valueOf(blockNum));
        txtLastSeen.setText(lastSeen > 0
                ? DateUtils.getRelativeTimeSpanString(lastSeen, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
                : getString(R.string.detail_never_seen));

        loadIcon(v);

        swtUrlFilter.setChecked(mPref.getBoolean(Common.getUrlFilterKey(pkgName), false));
        swtDnsFilter.setChecked(mPref.getBoolean(Common.getDnsFilterKey(pkgName), false));
        swtWhitelist.setChecked(mPref.getBoolean(Common.getWhiteListKey(pkgName), false));

        swtUrlFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                mPref.edit().putBoolean(Common.getUrlFilterKey(pkgName), isChecked).apply();
                showRestartHint(buttonView);
            }
        });
        swtDnsFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                mPref.edit().putBoolean(Common.getDnsFilterKey(pkgName), isChecked).apply();
                showRestartHint(buttonView);
            }
        });
        swtWhitelist.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                mPref.edit().putBoolean(Common.getWhiteListKey(pkgName), isChecked).apply();
                showRestartHint(buttonView);
            }
        });

        // Menghapus statistik satu aplikasi (sebelumnya method repositori ini
        // tidak pernah dipakai dari UI).
        TextView txtHint = v.findViewById(R.id.txt_toggles_hint);
        if (txtHint != null)
        {
            txtHint.setText(R.string.detail_toggles_hint);
        }

        View clearButton = v.findViewById(R.id.btn_clear_app_stats);
        if (clearButton != null)
        {
            clearButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    confirmClearStats(view);
                }
            });
        }

        return v;
    }

    /** Memberi tahu bahwa perubahan sakelar baru berlaku setelah aplikasi dibuka ulang. */
    private void showRestartHint(View anchor)
    {
        Snackbar.make(anchor, R.string.msg_restart_hint, Snackbar.LENGTH_SHORT).show();
    }

    private void confirmClearStats(final View anchor)
    {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.detail_clear_stats)
                .setMessage(R.string.detail_clear_stats_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        StatsRepository.clearForPackage(requireContext(), pkgName);
                        if (txtAdsBlocked != null)
                        {
                            txtAdsBlocked.setText("0");
                        }
                        Snackbar.make(anchor, R.string.detail_stats_cleared, Snackbar.LENGTH_SHORT).show();
                        notifyParentList();
                    }
                })
                .show();
    }

    /** Memuat ulang statistik di belakang dialog supaya hitungan ikut berubah. */
    private void notifyParentList()
    {
        if (getActivity() instanceof MainActivity)
        {
            ((MainActivity) getActivity()).refreshStatsOnly();
        }
    }

    private void loadIcon(View root)
    {
        final PackageManager pm = root.getContext().getPackageManager();
        try
        {
            Drawable appIcon = pm.getApplicationIcon(pkgName);
            if (appIcon != null)
            {
                imgAppIcon.setImageDrawable(appIcon);
            }
        }
        catch (PackageManager.NameNotFoundException ignored)
        {
        }
    }
}
