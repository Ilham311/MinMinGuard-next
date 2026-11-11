package tw.fatminmin.xposed.minminguard.next.ui.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tw.fatminmin.xposed.minminguard.next.Common;
import tw.fatminmin.xposed.minminguard.next.R;
import tw.fatminmin.xposed.minminguard.next.data.StatsRepository;
import tw.fatminmin.xposed.minminguard.next.ui.UIUtils;
import tw.fatminmin.xposed.minminguard.next.ui.dialog.AppDetailDialogFragment;
import tw.fatminmin.xposed.minminguard.next.ui.fragments.MainFragment;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppDetails;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppStats;

/**
 * Created by fatminmin on 2015/10/1.
 */
public class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.ViewHolder>
{

    private final Context mContext;
    private final SharedPreferences mPref;
    private ArrayList<AppDetails> appList;
    /** Statistik per paket; diambil sekali per daftar, bukan per baris. */
    private Map<String, AppStats> mStats = Collections.emptyMap();
    private final MainFragment.FragmentMode mMode;

    private final Set<String> mSelected = new HashSet<>();
    private boolean mSelectionMode;
    private OnSelectionChangedListener mSelectionListener;
    private OnSelectionModeListener mModeListener;

    public interface OnSelectionChangedListener
    {
        void onSelectionChanged(int count);
    }

    public interface OnSelectionModeListener
    {
        void onSelectionModeRequested();
    }

    public AppsAdapter(Context context, ArrayList<AppDetails> list, MainFragment.FragmentMode mode)
    {
        super();
        mContext = context;
        appList = list;

        Context ctx = ContextCompat.createDeviceProtectedStorageContext(context);
        if (ctx == null) ctx = context;

        mPref = ctx.getSharedPreferences(Common.MOD_PREFS, Common.getPrefMode());

        mMode = mode;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener)
    {
        mSelectionListener = listener;
    }

    public void setOnSelectionModeListener(OnSelectionModeListener listener)
    {
        mModeListener = listener;
    }

    public void setSelectionMode(boolean enabled)
    {
        mSelectionMode = enabled;
        if (!enabled)
        {
            mSelected.clear();
        }
        notifyDataSetChanged();
    }

    public void toggleSelection(String pkgName)
    {
        if (!mSelectionMode)
        {
            return;
        }
        if (!mSelected.remove(pkgName))
        {
            mSelected.add(pkgName);
        }
        notifyDataSetChanged();
        if (mSelectionListener != null)
        {
            mSelectionListener.onSelectionChanged(mSelected.size());
        }
    }

    public void toggleSelectAll()
    {
        if (appList.isEmpty())
        {
            return;
        }
        if (!mSelectionMode)
        {
            setSelectionMode(true);
        }
        if (mSelected.size() == appList.size())
        {
            mSelected.clear();
        }
        else
        {
            mSelected.clear();
            for (AppDetails item : appList)
            {
                mSelected.add(item.getPackageName());
            }
        }
        notifyDataSetChanged();
        if (mSelectionListener != null)
        {
            mSelectionListener.onSelectionChanged(mSelected.size());
        }
    }

    public List<String> getSelectedPackages()
    {
        return new ArrayList<>(mSelected);
    }

    /** Memuat ulang statistik saja (dipakai setelah data dihapus dari dialog). */
    public void reloadStats()
    {
        mStats = StatsRepository.loadStats(mContext);
        notifyDataSetChanged();
    }

    public void setAppList(ArrayList<AppDetails> list)
    {
        appList = list;
        mStats = StatsRepository.loadStats(mContext);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_app, parent, false);
        return new ViewHolder(v);
    }

    private void setState(String pkgName, boolean checked)
    {
        /* auto mode */
        if (mMode == MainFragment.FragmentMode.AUTO)
        {
            return;
        }

        if (mMode == MainFragment.FragmentMode.BLACKLIST)
        {
            /* blacklist mode */
            mPref.edit().putBoolean(pkgName, checked).apply();
        }
        else
        {
            /* whitelist mode */
            mPref.edit().putBoolean(Common.getWhiteListKey(pkgName), checked).apply();
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position)
    {

        final AppDetails currentAppDetails = appList.get(position);

        final AppStats appData = mStats.get(currentAppDetails.getPackageName());
        if (appData != null && appData.getBlockNum() != 0)
        {
            holder.txtBlockNum.setText(mContext.getString(R.string.msg_block_num, appData.getBlockNum()));
        }
        else
        {
            holder.txtBlockNum.setText("");
        }

        boolean selected = mSelected.contains(currentAppDetails.getPackageName());
        holder.card.setActivated(selected);
        holder.card.setSelected(selected);

        if (mSelectionMode)
        {
            // The M3 color attrs live in the Material library, not in this
            // module: AGP 8 uses non-transitive R classes by default, so
            // R.attr.* here would only contain attrs declared by this app.
            int color = selected
                    ? resolveColor(com.google.android.material.R.attr.colorSecondaryContainer)
                    : resolveColor(com.google.android.material.R.attr.colorSurfaceContainerLow);
            holder.card.setCardBackgroundColor(color);
        }
        else
        {
            holder.card.setCardBackgroundColor(ColorStateList.valueOf(
                    resolveColor(com.google.android.material.R.attr.colorSurfaceContainerLow)));
        }

        currentAppDetails.loadIcon(holder.imgAppIcon);
        holder.txtAppName.setText(currentAppDetails.getName());

        if (mMode == MainFragment.FragmentMode.AUTO)
        {
            holder.switchEnable.setVisibility(View.GONE);
        }
        else
        {
            holder.switchEnable.setVisibility(View.VISIBLE);
        }

        holder.switchEnable.setChecked(currentAppDetails.isEnabled());
        holder.switchEnable.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                SwitchCompat sw = (SwitchCompat) v;
                setState(currentAppDetails.getPackageName(), sw.isChecked());
                currentAppDetails.setEnabled(sw.isChecked());
            }
        });

        holder.card.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mSelectionMode)
                {
                    toggleSelection(currentAppDetails.getPackageName());
                    return;
                }
                DialogFragment dialog = AppDetailDialogFragment.newInstance(currentAppDetails.getName(), currentAppDetails.getPackageName(), appData);
                AppCompatActivity activity = (AppCompatActivity) mContext;
                dialog.show(activity.getSupportFragmentManager(), "dialog");            }
        });

        holder.card.setOnLongClickListener(new View.OnLongClickListener()
        {
            @Override
            public boolean onLongClick(View v)
            {
                if (!mSelectionMode && mMode != MainFragment.FragmentMode.AUTO)
                {
                    if (mModeListener != null)
                    {
                        mModeListener.onSelectionModeRequested();
                    }
                    setSelectionMode(true);
                    toggleSelection(currentAppDetails.getPackageName());
                }
                return true;
            }
        });

        holder.imgAppIcon.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                UIUtils.restartApp(mContext, currentAppDetails.getPackageName());
            }
        });
    }

    private int resolveColor(int attr)
    {
        TypedValue value = new TypedValue();
        if (mContext.getTheme().resolveAttribute(attr, value, true))
        {
            return value.data;
        }
        return android.graphics.Color.TRANSPARENT;
    }

    @Override
    public int getItemCount()
    {
        return appList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
    {

        MaterialCardView card;
        ImageView imgAppIcon;
        TextView txtAppName;
        TextView txtBlockNum;
        SwitchCompat switchEnable;

        ViewHolder(View v)
        {
            super(v);

            card = (MaterialCardView) v;
            imgAppIcon = v.findViewById(R.id.img_app_icon);
            txtAppName = v.findViewById(R.id.txt_app_name);
            txtBlockNum = v.findViewById(R.id.txt_block_num);
            switchEnable = v.findViewById(R.id.switch_enable);
        }
    }
}
