package tw.fatminmin.xposed.minminguard.next.ui.dialog;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import tw.fatminmin.xposed.minminguard.next.Common;
import tw.fatminmin.xposed.minminguard.next.R;
import tw.fatminmin.xposed.minminguard.next.blocker.Util;
import tw.fatminmin.xposed.minminguard.next.ui.LauncherAliasActivity;
import tw.fatminmin.xposed.minminguard.next.ui.MainActivity;

public class SettingsDialogFragment extends DialogFragment
{

    private SharedPreferences mUiPref;
    private SharedPreferences mModPref;
    private MaterialCheckBox mCbShowLauncherIcon;
    private MaterialCheckBox mCbEnableCr;
    private MaterialCheckBox mCbHeuristic;
    private MaterialCheckBox mCbVerbose;

    private final ActivityResultLauncher<String> mExportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            new ActivityResultCallback<Uri>()
            {
                @Override
                public void onActivityResult(Uri uri)
                {
                    if (uri != null)
                    {
                        exportConfig(uri);
                    }
                }
            });

    private final ActivityResultLauncher<String[]> mImportLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            new ActivityResultCallback<Uri>()
            {
                @Override
                public void onActivityResult(Uri uri)
                {
                    if (uri != null)
                    {
                        importConfig(uri);
                    }
                }
            });

    public static SettingsDialogFragment newInstance()
    {
        return new SettingsDialogFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {

        Context ctx = requireContext();
        mUiPref = ctx.getSharedPreferences(Common.UI_PREFS, Common.getPrefMode());

        Context modCtx = ContextCompat.createDeviceProtectedStorageContext(ctx);
        if (modCtx != null)
        {
            ctx = modCtx;
        }
        mModPref = ctx.getSharedPreferences(Common.MOD_PREFS, Common.getPrefMode());

        getDialog().setTitle(R.string.action_settings);

        View v = inflater.inflate(R.layout.dialog_settings, container, false);

        mCbShowLauncherIcon = v.findViewById(R.id.cb_show_launcher_icon);
        mCbShowLauncherIcon.setChecked(mUiPref.getBoolean(Common.KEY_SHOW_LAUNCHER_ICON, true));

        mCbEnableCr = v.findViewById(R.id.cb_enable_cr);
        mCbEnableCr.setChecked(mUiPref.getBoolean(Common.KEY_ENABLE_CRASH_REPORT, false));

        mCbHeuristic = v.findViewById(R.id.cb_heuristic);
        mCbHeuristic.setChecked(mModPref.getBoolean(Common.KEY_HEURISTIC, true));

        mCbVerbose = v.findViewById(R.id.cb_verbose);
        mCbVerbose.setChecked(mModPref.getBoolean(Common.KEY_VERBOSE_LOG, Util.DEBUG));

        v.findViewById(R.id.btn_theme).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                showThemeDialog();
            }
        });
        v.findViewById(R.id.btn_sort).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                showSortDialog();
            }
        });
        v.findViewById(R.id.btn_user_prefixes).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                showUserPrefixesDialog();
            }
        });
        v.findViewById(R.id.btn_export).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                mExportLauncher.launch("MinMinGuardNext-config.json");
            }
        });
        v.findViewById(R.id.btn_import).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                mImportLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
            }
        });

        v.findViewById(R.id.btn_ok).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Util.DEBUG = mCbVerbose.isChecked();

                mUiPref.edit()
                        .putBoolean(Common.KEY_ENABLE_CRASH_REPORT, mCbEnableCr.isChecked())
                        .apply();

                // Opsi ini dibaca proses aplikasi yang di-hook lewat
                // XSharedPreferences, jadi harus ditulis ke preferensi mod.
                mModPref.edit()
                        .putBoolean(Common.KEY_HEURISTIC, mCbHeuristic.isChecked())
                        .putBoolean(Common.KEY_VERBOSE_LOG, mCbVerbose.isChecked())
                        .apply();

                setLauncherIconEnabled(mCbShowLauncherIcon.isChecked());

                if (getActivity() instanceof MainActivity)
                {
                    ((MainActivity) getActivity()).refresh(true);
                }
                dismiss();
            }
        });

        return v;
    }

    private void setLauncherIconEnabled(boolean enabled)
    {
        mUiPref.edit().putBoolean(Common.KEY_SHOW_LAUNCHER_ICON, enabled).apply();
        try
        {
            requireContext().getPackageManager().setComponentEnabledSetting(
                    new ComponentName(requireContext(), LauncherAliasActivity.class),
                    enabled ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private int getThemeIndex()
    {
        String theme = mUiPref.getString(Common.KEY_THEME, Common.VALUE_THEME_SYSTEM);
        if (Common.VALUE_THEME_LIGHT.equals(theme))
        {
            return 1;
        }
        return Common.VALUE_THEME_DARK.equals(theme) ? 2 : 0;
    }

    private void showThemeDialog()
    {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_theme)
                .setSingleChoiceItems(new CharSequence[]{
                        getString(R.string.setting_theme_system),
                        getString(R.string.setting_theme_light),
                        getString(R.string.setting_theme_dark)}, getThemeIndex(),
                        new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                String theme;
                                if (which == 1)
                                {
                                    theme = Common.VALUE_THEME_LIGHT;
                                }
                                else if (which == 2)
                                {
                                    theme = Common.VALUE_THEME_DARK;
                                }
                                else
                                {
                                    theme = Common.VALUE_THEME_SYSTEM;
                                }
                                applyTheme(theme);
                                dialog.dismiss();
                            }
                        })
                .show();
    }

    private void applyTheme(String theme)
    {
        mUiPref.edit().putString(Common.KEY_THEME, theme).apply();
        requireActivity().getWindow().getDecorView().post(new Runnable()
        {
            @Override
            public void run()
            {
                requireActivity().recreate();
            }
        });
    }

    private int getSortIndex()
    {
        String sort = mUiPref.getString(Common.KEY_SORT, Common.VALUE_SORT_NAME);
        if (Common.VALUE_SORT_PACKAGE.equals(sort))
        {
            return 1;
        }
        return Common.VALUE_SORT_BLOCKED.equals(sort) ? 2 : 0;
    }

    private void showSortDialog()
    {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_sort)
                .setSingleChoiceItems(new CharSequence[]{
                        getString(R.string.setting_sort_name),
                        getString(R.string.setting_sort_package),
                        getString(R.string.setting_sort_blocked)}, getSortIndex(),
                        new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                String sort;
                                if (which == 1)
                                {
                                    sort = Common.VALUE_SORT_PACKAGE;
                                }
                                else if (which == 2)
                                {
                                    sort = Common.VALUE_SORT_BLOCKED;
                                }
                                else
                                {
                                    sort = Common.VALUE_SORT_NAME;
                                }
                                mUiPref.edit().putString(Common.KEY_SORT, sort).apply();
                                dialog.dismiss();
                                if (requireActivity() instanceof MainActivity)
                                {
                                    ((MainActivity) requireActivity()).refresh(false);
                                }
                            }
                        })
                .show();
    }

    private void showUserPrefixesDialog()
    {
        final EditText editText = new EditText(requireContext());
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setMinLines(6);
        editText.setText(mModPref.getString(Common.KEY_USER_BLOCK_PREFIXES, ""));
        editText.setHint(R.string.setting_user_prefixes_hint);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_user_prefixes)
                .setView(editText)
                .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        mModPref.edit().putString(Common.KEY_USER_BLOCK_PREFIXES, editText.getText().toString().trim()).apply();
                        Snackbar.make(requireView(), R.string.msg_prefixes_saved, Snackbar.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void exportConfig(Uri uri)
    {
        try
        {
            JSONObject root = new JSONObject();

            JSONObject mod = new JSONObject();
            for (Map.Entry<String, ?> entry : mModPref.getAll().entrySet())
            {
                putJson(mod, entry.getKey(), entry.getValue());
            }
            root.put("mod", mod);

            JSONObject ui = new JSONObject();
            ui.put(Common.KEY_SHOW_LAUNCHER_ICON, mUiPref.getBoolean(Common.KEY_SHOW_LAUNCHER_ICON, true));
            root.put("ui", ui);

            OutputStream out = requireContext().getContentResolver().openOutputStream(uri, "w");
            if (out != null)
            {
                out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.close();
                Toast.makeText(requireContext(), R.string.msg_config_exported, Toast.LENGTH_SHORT).show();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Toast.makeText(requireContext(), R.string.msg_config_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void importConfig(Uri uri)
    {
        try
        {
            InputStream in = requireContext().getContentResolver().openInputStream(uri);
            if (in == null)
            {
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
            }
            reader.close();

            JSONObject root = new JSONObject(sb.toString());

            JSONObject mod = root.getJSONObject("mod");
            SharedPreferences.Editor modEditor = mModPref.edit();
            modEditor.clear();
            java.util.Iterator<String> keys = mod.keys();
            while (keys.hasNext())
            {
                String key = keys.next();
                putFromJson(modEditor, key, mod.get(key));
            }
            modEditor.apply();

            if (root.has("ui"))
            {
                JSONObject ui = root.getJSONObject("ui");
                SharedPreferences.Editor uiEditor = mUiPref.edit();
                if (ui.has(Common.KEY_SHOW_LAUNCHER_ICON))
                {
                    uiEditor.putBoolean(Common.KEY_SHOW_LAUNCHER_ICON, ui.getBoolean(Common.KEY_SHOW_LAUNCHER_ICON));
                }
                uiEditor.apply();
            }

            Toast.makeText(requireContext(), R.string.msg_config_imported, Toast.LENGTH_SHORT).show();
            if (getActivity() instanceof MainActivity)
            {
                ((MainActivity) getActivity()).refresh(true);
            }
        }
        catch (java.io.IOException | JSONException e)
        {
            e.printStackTrace();
            Toast.makeText(requireContext(), R.string.msg_config_import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static void putJson(JSONObject json, String key, Object value) throws JSONException
    {
        if (value instanceof String)
        {
            json.put(key, (String) value);
        }
        else if (value instanceof Integer)
        {
            json.put(key, (Integer) value);
        }
        else if (value instanceof Long)
        {
            json.put(key, (Long) value);
        }
        else if (value instanceof Boolean)
        {
            json.put(key, (Boolean) value);
        }
        else if (value instanceof Float)
        {
            json.put(key, (Float) value);
        }
        else if (value instanceof Set)
        {
            json.put(key, new JSONArray((java.util.Collection<?>) value));
        }
    }

    private static void putFromJson(SharedPreferences.Editor editor, String key, Object value)
    {
        if (value instanceof String)
        {
            editor.putString(key, (String) value);
        }
        else if (value instanceof Integer)
        {
            editor.putInt(key, (Integer) value);
        }
        else if (value instanceof Long)
        {
            editor.putLong(key, (Long) value);
        }
        else if (value instanceof Boolean)
        {
            editor.putBoolean(key, (Boolean) value);
        }
        else if (value instanceof Float)
        {
            editor.putFloat(key, (Float) value);
        }
        else if (value instanceof JSONArray)
        {
            try
            {
                JSONArray arr = (JSONArray) value;
                HashSet<String> set = new HashSet<>();
                for (int i = 0; i < arr.length(); i++)
                {
                    set.add(arr.getString(i));
                }
                editor.putStringSet(key, set);
            }
            catch (JSONException ignored)
            {
            }
        }
    }
}
