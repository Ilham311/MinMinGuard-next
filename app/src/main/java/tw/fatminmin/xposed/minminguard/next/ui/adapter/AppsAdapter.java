package tw.fatminmin.xposed.minminguard.next.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import tw.fatminmin.xposed.minminguard.next.R;
import tw.fatminmin.xposed.minminguard.next.ui.dialog.AppDetailDialogFragment;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppDetails;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppStats;

/**
 * Daftar aplikasi yang ada di scope LSPosed.
 * <p>
 * Versi lama menampilkan seluruh aplikasi terpasang dalam tiga tab mode
 * (AUTO/BLACKLIST/WHITELIST) dengan sakelar per baris dan bilah pemilihan
 * massal. Panel itu membingungkan: modul hanya bisa berjalan di paket yang ada
 * di scope, sehingga sakelar mode di aplikasi tidak menentukan apa pun yang
 * nyata. Sekarang daftar ini adalah cermin dari paket yang benar-benar di-hook,
 * dan satu-satunya sakelar on/off ada di LSPosed.
 */
public class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.ViewHolder>
{
    private final Context mContext;
    private List<AppDetails> appList;
    /** Statistik per paket; diambil sekali per daftar, bukan per baris. */
    private Map<String, AppStats> mStats = Collections.emptyMap();

    public AppsAdapter(Context context, List<AppDetails> list)
    {
        super();
        mContext = context;
        appList = list == null ? new ArrayList<>() : list;
    }

    public void setAppList(List<AppDetails> list)
    {
        appList = list == null ? new ArrayList<>() : list;
    }

    /** Mengisi statistik yang sudah dimuat di thread latar. */
    public void setStats(Map<String, AppStats> stats)
    {
        mStats = stats == null ? Collections.<String, AppStats>emptyMap() : stats;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_app, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position)
    {
        final AppDetails item = appList.get(position);
        final AppStats stats = mStats.get(item.getPackageName());

        holder.txtAppName.setText(item.getName());
        if (stats != null && stats.getBlockNum() > 0)
        {
            holder.txtBlockNum.setText(mContext.getString(R.string.msg_block_num, stats.getBlockNum()));
        }
        else if (!item.isInstalled())
        {
            holder.txtBlockNum.setText(R.string.msg_app_not_installed);
        }
        else
        {
            holder.txtBlockNum.setText(R.string.msg_no_block_yet);
        }

        item.loadIcon(holder.imgAppIcon);

        holder.card.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                DialogFragment dialog = AppDetailDialogFragment.newInstance(
                        item.getName(), item.getPackageName(), stats, item.isInstalled());
                if (mContext instanceof AppCompatActivity)
                {
                    dialog.show(((AppCompatActivity) mContext).getSupportFragmentManager(), "dialog");
                }
            }
        });

        // Ketuk ikon = buka ulang aplikasi target. Ini menggantikan tombol
        // "terapkan" pada versi lama: perubahan hanya berlaku saat proses
        // target dijalankan ulang, jadi jalur tercepat disediakan di sini.
        holder.imgAppIcon.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (item.isInstalled())
                {
                    tw.fatminmin.xposed.minminguard.next.ui.UIUtils.restartApp(mContext, item.getPackageName());
                }
            }
        });
    }

    @Override
    public int getItemCount()
    {
        return appList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder
    {
        MaterialCardView card;
        ImageView imgAppIcon;
        TextView txtAppName;
        TextView txtBlockNum;

        ViewHolder(View v)
        {
            super(v);
            card = (MaterialCardView) v;
            imgAppIcon = v.findViewById(R.id.img_app_icon);
            txtAppName = v.findViewById(R.id.txt_app_name);
            txtBlockNum = v.findViewById(R.id.txt_block_num);
        }
    }
}
