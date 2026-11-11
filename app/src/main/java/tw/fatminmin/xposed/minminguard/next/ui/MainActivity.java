package tw.fatminmin.xposed.minminguard.next.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
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
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tw.fatminmin.xposed.minminguard.next.Common;
import tw.fatminmin.xposed.minminguard.next.PrefFileManager;
import tw.fatminmin.xposed.minminguard.next.R;
import tw.fatminmin.xposed.minminguard.next.data.StatsRepository;
import tw.fatminmin.xposed.minminguard.next.ui.adapter.ModeFragmentAdapter;
import tw.fatminmin.xposed.minminguard.next.ui.dialog.SettingsDialogFragment;
import tw.fatminmin.xposed.minminguard.next.ui.dialog.StatsDialogFragment;
import tw.fatminmin.xposed.minminguard.next.ui.fragments.MainFragment;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppDetails;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppStats;

public class MainActivity extends AppCompatActivity
{

    public volatile ArrayList<AppDetails> masterAppList;

    private ArrayList<AppDetails> filteredAppList;
    /** Kueri pencarian aktif; string kosong berarti tanpa filter. */
    private String mQuery = "";
    /** Statistik hasil pemuatan di thread latar; dipakai ulang saat filter berubah. */
    private Map<String, AppStats> mStats;
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
            else if (id == R.id.action_reset_stats)
            {
                confirmResetStatistics();
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

    /** Satu thread latar untuk memuat daftar aplikasi. */
    private final ExecutorService mLoader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mmg-apps");
        t.setDaemon(true);
        return t;
    });

    /** Mencegah pemuatan bertumpuk saat swipe-refresh dan pindah tab beruntun. */
    private final java.util.concurrent.atomic.AtomicBoolean mLoading = new java.util.concurrent.atomic.AtomicBoolean(false);

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
                applyFilter();
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

        // Predictive back (Android 13+, wajib di targetSdk 36): onBackPressed()
        // tidak lagi dipanggil. Gunakan OnBackPressedCallback.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true)
        {
            @Override
            public void handleOnBackPressed()
            {
                if (mDrawerLayout.isDrawerOpen(GravityCompat.START))
                {
                    mDrawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                if (mDrawerLayout.isDrawerOpen(GravityCompat.END))
                {
                    mDrawerLayout.closeDrawer(GravityCompat.END);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    /** True bila daftar sudah pernah dimuat di sesi activity ini. */
    private boolean mLoadedOnce;

    @Override
    public void onResume()
    {
        if (!mLoadedOnce)
        {
            // Pemuatan pertama (atau setelah activity dibuat ulang).
            mLoadedOnce = true;
            refresh(false);
        }
        super.onResume();
    }

    /**
     * Memuat ulang hitungan iklan tanpa membaca ulang daftar aplikasi.
     * Dipanggil setelah statistik satu aplikasi dihapus dari dialog detail.
     */
    public void refreshStatsOnly()
    {
        MainFragment fragment = getFragmentAt(mViewPager == null ? 0 : mViewPager.getCurrentItem());
        if (fragment != null && fragment.isAlive)
        {
            fragment.updateStats();
        }
    }


    @Override
    protected void onDestroy()
    {
        mLoader.shutdownNow();
        super.onDestroy();
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

    private void confirmResetStatistics()
    {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_reset_stats)
                .setMessage(R.string.setting_reset_stats_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    StatsRepository.clearAll(this);
                    refresh(false);
                    Snackbar.make(findViewById(R.id.drawer_layout),
                            R.string.msg_stats_reset, Snackbar.LENGTH_SHORT).show();
                })
                .show();
    }

    public void refresh(final boolean displayProgressDialog)
    {
        // Abaikan permintaan yang datang saat pemuatan masih berjalan. Sebelumnya
        // setiap swipe/tab switch menumpuk satu tugas di executor tunggal sehingga
        // daftar bisa ter-render beberapa kali berturut-turut dengan data lama.
        if (!mLoading.compareAndSet(false, true))
        {
            // Permintaan ini tidak akan memuat apa pun, jadi jangan tinggalkan
            // spinner berputar.
            if (swipeRefreshLayout != null)
            {
                swipeRefreshLayout.setRefreshing(false);
            }
            return;
        }

        if (displayProgressDialog && progressDialog != null)
        {
            progressDialog.show();
        }

        mLoader.execute(() -> {
            final ArrayList<AppDetails> list;
            final Map<String, AppStats> stats;
            try
            {
                // Statistik dimuat di sini, sekali per pemuatan daftar, agar
                // pencarian di kotak teks tidak menyentuh database setiap ketikan.
                stats = StatsRepository.loadStats(this);
                list = buildAppList(getPackageManager(), stats);
            }
            catch (Throwable t)
            {
                runOnUiThread(() -> finishRefresh(null));
                return;
            }
            runOnUiThread(() -> finishRefresh(list, stats));
        });
    }

    private void finishRefresh(ArrayList<AppDetails> list)
    {
        finishRefresh(list, null);
    }

    private void finishRefresh(ArrayList<AppDetails> list, Map<String, AppStats> stats)
    {
        mLoading.set(false);

        // Spinner dan dialog harus selalu berhenti, termasuk ketika activity
        // sedang selesai; kalau tidak, pengguna melihat spinner yang macet.
        if (progressDialog != null && progressDialog.isShowing())
        {
            progressDialog.dismiss();
        }
        if (swipeRefreshLayout != null)
        {
            swipeRefreshLayout.setRefreshing(false);
        }

        if (isFinishing() || isDestroyed())
        {
            return;
        }
        if (list != null)
        {
            masterAppList = list;
            if (stats != null)
            {
                mStats = stats;
            }
            applyFilter();
        }
    }

    private ArrayList<AppDetails> buildAppList(final PackageManager pm, final Map<String, AppStats> stats)
    {
        boolean showSystemApps = uiPref.getBoolean(Common.KEY_SHOW_SYSTEM_APPS, false);
        String sort = uiPref.getString(Common.KEY_SORT, Common.VALUE_SORT_NAME);
        MainFragment.FragmentMode mode = currentMode();
        ArrayList<AppDetails> temp = new ArrayList<>();
        SharedPreferences.Editor defaults = null;

        for (PackageInfo info : pm.getInstalledPackages(0))
        {
            boolean isSystem = (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

            if (showSystemApps || !isSystem)
            {
                String name = String.valueOf(info.applicationInfo.loadLabel(pm));
                String packageName = info.packageName;
                temp.add(new AppDetails(name, packageName, info.applicationInfo,
                        isPackageEnabled(packageName, mode)));
            }

            // Nilai awal untuk aplikasi sistem yang belum pernah disentuh.
            if (isSystem && !modPref.contains(info.packageName))
            {
                if (defaults == null)
                {
                    defaults = modPref.edit();
                }
                defaults.putBoolean(info.packageName, false)
                        .putBoolean(Common.getWhiteListKey(info.packageName), true);
            }
        }

        if (defaults != null)
        {
            defaults.apply();
        }

        if (Common.VALUE_SORT_PACKAGE.equals(sort))
        {
            Collections.sort(temp, (l, r) -> l.getPackageName().compareToIgnoreCase(r.getPackageName()));
        }
        else if (Common.VALUE_SORT_BLOCKED.equals(sort))
        {
            final Map<String, Integer> blockCounts = new HashMap<>();
            if (stats != null)
            {
                for (AppStats appStats : stats.values())
                {
                    blockCounts.put(appStats.getPackageName(), appStats.getBlockNum());
                }
            }

            Collections.sort(temp, (l, r) -> {
                int lc = blockCounts.containsKey(l.getPackageName()) ? blockCounts.get(l.getPackageName()) : 0;
                int rc = blockCounts.containsKey(r.getPackageName()) ? blockCounts.get(r.getPackageName()) : 0;
                int cmp = Integer.compare(rc, lc);
                return cmp != 0 ? cmp : l.getName().compareToIgnoreCase(r.getName());
            });
        }
        else
        {
            Collections.sort(temp, (l, r) -> l.getName().compareToIgnoreCase(r.getName()));
        }

        return temp;
    }

    /** Mode fragment yang sedang tampil; aman dipanggil dari thread latar. */
    private MainFragment.FragmentMode currentMode()
    {
        int item = mViewPager == null ? 1 : mViewPager.getCurrentItem();
        switch (item)
        {
            case 0:
                return MainFragment.FragmentMode.AUTO;
            case 2:
                return MainFragment.FragmentMode.WHITELIST;
            case 1:
            default:
                return MainFragment.FragmentMode.BLACKLIST;
        }
    }

    private boolean isPackageEnabled(String pkgName, MainFragment.FragmentMode mode)
    {
        if (mode == MainFragment.FragmentMode.AUTO)
        {
            return true;
        }
        if (mode == MainFragment.FragmentMode.BLACKLIST)
        {
            return modPref.getBoolean(pkgName, false);
        }
        return modPref.getBoolean(Common.getWhiteListKey(pkgName), false);
    }

    private void filterAppList(String keyword)
    {
        mQuery = keyword == null ? "" : keyword.trim().toLowerCase();
        applyFilter();
    }

    /**
     * Menyusun daftar tampil dari {@link #masterAppList} menurut kueri aktif
     * lalu menyetorkannya ke semua fragmen tab (bukan hanya tab yang sedang
     * tampil) supaya hasil pencarian konsisten ketika pengguna berpindah tab.
     */
    private void applyFilter()
    {
        if (masterAppList == null || mAdapter == null)
        {
            return;
        }

        if (mQuery.isEmpty())
        {
            filteredAppList = new ArrayList<>();
        }
        else
        {
            filteredAppList.clear();
            for (AppDetails info : masterAppList)
            {
                if (info.getName().toLowerCase().contains(mQuery)
                        || info.getPackageName().toLowerCase().contains(mQuery))
                {
                    filteredAppList.add(info);
                }
            }
        }

        ArrayList<AppDetails> shown = mQuery.isEmpty() ? masterAppList : filteredAppList;
        for (int i = 0; i < mAdapter.getCount(); i++)
        {
            MainFragment fragment = getFragmentAt(i);
            if (fragment != null && fragment.isAlive)
            {
                fragment.setStats(mStats);
                fragment.updateAndNotify(shown);
            }
        }
    }
}
