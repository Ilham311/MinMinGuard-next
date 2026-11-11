package tw.fatminmin.xposed.minminguard.next.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import tw.fatminmin.xposed.minminguard.next.Common;
import tw.fatminmin.xposed.minminguard.next.PrefFileManager;
import tw.fatminmin.xposed.minminguard.next.R;
import tw.fatminmin.xposed.minminguard.next.orm.AppData;
import tw.fatminmin.xposed.minminguard.next.orm.DaoMaster;
import tw.fatminmin.xposed.minminguard.next.ui.adapter.ModeFragmentAdapter;
import tw.fatminmin.xposed.minminguard.next.ui.dialog.SettingsDialogFragment;
import tw.fatminmin.xposed.minminguard.next.ui.dialog.StatsDialogFragment;
import tw.fatminmin.xposed.minminguard.next.ui.fragments.MainFragment;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppDetails;

public class MainActivity extends AppCompatActivity
{

    public volatile ArrayList<AppDetails> masterAppList;

    private ArrayList<AppDetails> filteredAppList;
    private SharedPreferences uiPref;
    public SharedPreferences modPref;
    private ViewPager mViewPager;
    private ModeFragmentAdapter mAdapter;
    private DrawerLayout mDrawerLayout;
    private final NavigationView.OnNavigationItemSelectedListener mNavListener = new NavigationView.OnNavigationItemSelectedListener()
    {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem menuItem)
        {
            mDrawerLayout.closeDrawers();

            // AGP 8 generates R.id.* as non-final fields (android.nonFinalResIds),
            // so a switch over them is not a compile-time constant expression.
            int id = menuItem.getItemId();
            if (id == R.id.action_settings)
            {
                SettingsDialogFragment.newInstance().show(getSupportFragmentManager(), "dialog");
            }
            else if (id == R.id.action_statistics)
            {
                StatsDialogFragment.newInstance().show(getSupportFragmentManager(), "stats");
            }
            else if (id == R.id.action_tutorial)
            {
                showIntro();
            }
            else if (id == R.id.action_donate)
            {
                Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=47GXTVHUB4LYS"));
                startActivity(it);
            }
            else if (id == R.id.action_about)
            {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://fatminmin.com/pages/minminguard.html")));
            }

            return true;
        }
    };
    private ActionBarDrawerToggle mToggle;
    private ProgressDialog progressDialog;
    private SwipeRefreshLayout swipeRefreshLayout;
    private PrefFileManager prefFileManager;

    @Override
    public void attachBaseContext(Context newBase)
    {
        super.attachBaseContext(newBase);
        prefFileManager = PrefFileManager.getInstance(this);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        prefFileManager.onResume();
    }

    @Override
    protected void onStop()
    {
        prefFileManager.onPause();
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        boolean darkTheme = Common.isDarkTheme(getSharedPreferences(Common.UI_PREFS, Common.getPrefMode())
                .getString(Common.KEY_THEME, Common.VALUE_THEME_SYSTEM));
        setTheme(darkTheme ? R.style.AppThemeDark : R.style.AppTheme);

        if (darkTheme)
        {
            EdgeToEdge.enable(this, SystemBarStyle.dark(android.graphics.Color.TRANSPARENT));
        }
        else
        {
            EdgeToEdge.enable(this, SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT));
        }

        super.onCreate(savedInstanceState);
        prefFileManager.fixFolderPermissionsAsync();

        Context ctx = ContextCompat.createDeviceProtectedStorageContext(this);
        if (ctx == null) ctx = this;

        uiPref = getSharedPreferences(Common.UI_PREFS, Common.getPrefMode());
        modPref = ctx.getSharedPreferences(Common.MOD_PREFS, Common.getPrefMode());

        progressDialog = new ProgressDialog(this, R.style.MainSpinnerDialogStyle);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setMessage(getString(R.string.msg_wait));
        progressDialog.setCancelable(false);

        if (uiPref.getBoolean(Common.KEY_FIRST_TIME, true))
        {
            uiPref.edit().putBoolean(Common.KEY_FIRST_TIME, false).apply();
            showIntro();
        }

        setContentView(R.layout.activity_main);

        // toolbar setup
        Toolbar toolbar = findViewById(R.id.top_toolbar);
        if (toolbar instanceof MaterialToolbar)
        {
            MaterialToolbar materialToolbar = (MaterialToolbar) toolbar;
            int inset = (int) (getResources().getDisplayMetrics().density * 16f + 0.5f);
            materialToolbar.setContentInsetStartWithNavigation(inset);
            materialToolbar.setContentInsetEndWithActions(inset);
        }
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
        {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        // drawer view and navigation view setup
        NavigationView mNavigationView = findViewById(R.id.navigation_view);
        mNavigationView.setNavigationItemSelectedListener(mNavListener);

        mDrawerLayout = findViewById(R.id.drawer_layout);
        mToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.app_name, R.string.app_name);
        mDrawerLayout.addDrawerListener(mToggle);
        mToggle.syncState();

        // setup tab view
        mAdapter = new ModeFragmentAdapter(getSupportFragmentManager(), MainActivity.this);
        mViewPager = findViewById(R.id.viewpager);
        mViewPager.setAdapter(mAdapter);
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener()
        {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)
            {
            }

            @Override
            public void onPageSelected(int position)
            {
                MainFragment fragment = getFragmentAt(position);
                if (fragment != null && fragment.isAlive)
                {
                    fragment.refreshUI();
                }
                refresh(true);
            }

            @Override
            public void onPageScrollStateChanged(int state)
            {
                if (state == 1)
                {
                    swipeRefreshLayout.setEnabled(false);
                }
                else if (state == 0)
                {
                    swipeRefreshLayout.setEnabled(true);
                }
            }
        });

        TabLayout mTabLayout = findViewById(R.id.sliding_tabs);
        mTabLayout.setupWithViewPager(mViewPager);

        switch (modPref.getString(Common.KEY_MODE, Common.VALUE_MODE_BLACKLIST))
        {
            case Common.VALUE_MODE_AUTO:
                mViewPager.setCurrentItem(0);
                break;
            case Common.VALUE_MODE_BLACKLIST:
                mViewPager.setCurrentItem(1);
                break;
            case Common.VALUE_MODE_WHITELIST:
                mViewPager.setCurrentItem(2);
                break;
            default:
                break;
        }

        // keep the toolbar and content clear of the status/navigation bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer_layout), new OnApplyWindowInsetsListener()
        {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets)
            {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                View tb = findViewById(R.id.top_toolbar);
                if (tb != null)
                {
                    tb.setPadding(bars.left, 0, bars.right, 0);
                }

                View sr = findViewById(R.id.swipeRefresh);
                if (sr != null)
                {
                    sr.setPadding(0, 0, 0, bars.bottom);
                }

                return insets;
            }
        });

        masterAppList = new ArrayList<>();
        filteredAppList = new ArrayList<>();

        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                refresh(false);
            }
        });

        refresh(true);
    }

    @Override
    public void onResume()
    {
        refresh(false);
        super.onResume();
    }

    @Override
    public void onBackPressed()
    {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START))
        {
            mDrawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        else if (mDrawerLayout.isDrawerOpen(GravityCompat.END))
        {
            mDrawerLayout.closeDrawer(GravityCompat.END);
            return;
        }

        moveTaskToBack(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.action_search));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener()
        {
            @Override
            public boolean onQueryTextSubmit(String query)
            {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query)
            {
                filterAppList(query);
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        return mToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    private MainFragment getCurrentFragment()
    {
        return getFragmentAt(mViewPager.getCurrentItem());
    }

    private MainFragment getFragmentAt(int position)
    {
        Fragment fragment = getSupportFragmentManager()
                .findFragmentByTag("android:switcher:" + R.id.viewpager + ":" + position);
        if (fragment instanceof MainFragment)
        {
            return (MainFragment) fragment;
        }
        return (MainFragment) mAdapter.getItem(position);
    }

    private void showIntro()
    {
        Intent intent = new Intent(this, MinMinGuardIntro.class);
        startActivity(intent);
    }

    public void refresh(final boolean displayProgressDialog)
    {
        new AsyncTask<Void, Void, Void>()
        {

            @Override
            protected void onPreExecute()
            {
                if (displayProgressDialog)
                {
                    progressDialog.show();
                }
            }

            @Override
            protected Void doInBackground(Void... voids)
            {
                updateAppList(getPackageManager());
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid)
            {
                getCurrentFragment().updateAndNotify(masterAppList);
                progressDialog.dismiss();
                swipeRefreshLayout.setRefreshing(false);
            }
        }.execute();
    }

    private void updateAppList(final PackageManager pm)
    {
        boolean showSystemApps = uiPref.getBoolean(Common.KEY_SHOW_SYSTEM_APPS, false);
        String sort = uiPref.getString(Common.KEY_SORT, Common.VALUE_SORT_NAME);
        ArrayList<AppDetails> temp = new ArrayList<>();

        for (PackageInfo info : pm.getInstalledPackages(0))
        {
            if (showSystemApps || (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
            {
                String name = (String) info.applicationInfo.loadLabel(pm);
                String packageName = info.packageName;
                boolean isEnabled = isPackageEnabled(packageName);

                temp.add(new AppDetails(name, packageName, info.applicationInfo, isEnabled));
            }

            // setting initial value for system apps
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1 && !modPref.contains(info.packageName))
            {
                modPref.edit().putBoolean(info.packageName, false).putBoolean(Common.getWhiteListKey(info.packageName), true).apply();
            }
        }

        if (Common.VALUE_SORT_PACKAGE.equals(sort))
        {
            Collections.sort(temp, new Comparator<AppDetails>()
            {
                @Override
                public int compare(AppDetails l, AppDetails r)
                {
                    return l.getPackageName().compareToIgnoreCase(r.getPackageName());
                }
            });
        }
        else if (Common.VALUE_SORT_BLOCKED.equals(sort))
        {
            final Map<String, Integer> blockCounts = new HashMap<>();
            try
            {
                DaoMaster.DevOpenHelper helper = new DaoMaster.DevOpenHelper(this, "mmg", null);
                for (AppData data : new DaoMaster(helper.getWritableDatabase()).newSession().getAppDataDao().loadAll())
                {
                    blockCounts.put(data.getPkgName(), data.getBlockNum());
                }
                helper.close();
            }
            catch (Exception ignored)
            {
            }

            Collections.sort(temp, new Comparator<AppDetails>()
            {
                @Override
                public int compare(AppDetails l, AppDetails r)
                {
                    int rc = Integer.compare(
                            blockCounts.containsKey(r.getPackageName()) ? blockCounts.get(r.getPackageName()) : 0,
                            blockCounts.containsKey(l.getPackageName()) ? blockCounts.get(l.getPackageName()) : 0);
                    return rc != 0 ? rc : l.getName().compareToIgnoreCase(r.getName());
                }
            });
        }
        else
        {
            Collections.sort(temp, new Comparator<AppDetails>()
            {
                @Override
                public int compare(AppDetails l, AppDetails r)
                {
                    return l.getName().compareToIgnoreCase(r.getName());
                }
            });
        }

        masterAppList = temp;
    }

    private boolean isPackageEnabled(String pkgName)
    {
        if (getCurrentFragment().getMode() == MainFragment.FragmentMode.AUTO)
        {
            return true;
        }

        if (getCurrentFragment().getMode() == MainFragment.FragmentMode.BLACKLIST)
        {
            return modPref.getBoolean(pkgName, false);
        }

        return modPref.getBoolean(Common.getWhiteListKey(pkgName), false);
    }

    private void filterAppList(String keyword)
    {
        filteredAppList.clear();
        String lower = keyword.toLowerCase();

        for (AppDetails info : masterAppList)
        {
            if (info.getName().toLowerCase().contains(lower)
                    || info.getPackageName().toLowerCase().contains(lower))
            {
                filteredAppList.add(info);
            }
        }

        getCurrentFragment().updateAndNotify(filteredAppList);
    }
}
