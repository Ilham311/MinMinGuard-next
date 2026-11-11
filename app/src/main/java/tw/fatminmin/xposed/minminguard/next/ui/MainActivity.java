package tw.fatminmin.xposed.minminguard.next.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
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
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tw.fatminmin.xposed.minminguard.next.Common;
import tw.fatminmin.xposed.minminguard.next.PrefFileManager;
import tw.fatminmin.xposed.minminguard.next.R;
import tw.fatminmin.xposed.minminguard.next.data.StatsRepository;
import tw.fatminmin.xposed.minminguard.next.ui.dialog.SettingsDialogFragment;
import tw.fatminmin.xposed.minminguard.next.ui.dialog.StatsDialogFragment;
import tw.fatminmin.xposed.minminguard.next.ui.fragments.MainFragment;
import tw.fatminmin.xposed.minminguard.next.ui.models.ActiveApp;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppDetails;
import tw.fatminmin.xposed.minminguard.next.ui.models.AppStats;

/**
 * Layar utama: ringkasan pemblokiran + daftar aplikasi target.
 * <p>
 * Versi sebelumnya memakai ViewPager tiga tab (AUTO/BLACKLIST/WHITELIST),
 * tombol "Terapkan", bilah pemilihan massal, dan daftar seluruh aplikasi
 * terpasang. Model itu bertentangan dengan cara kerja modul: kode hanya dimuat
 * di paket yang ada di scope LSPosed, jadi pilihan mode di aplikasi tidak bisa
 * mengubah apa pun. Sekarang daftar diisi dari laporan modul sendiri
 * ({@code URI_ACTIVE}) dan on/off sepenuhnya ada di LSPosed.
 */
public class MainActivity extends AppCompatActivity
{
    /** Daftar aplikasi aktif tanpa filter pencarian. */
    public volatile ArrayList<AppDetails> activeAppList = new ArrayList<>();
    /** Statistik hasil pemuatan di thread latar; dipakai ulang saat filter berubah. */
    public volatile Map<String, AppStats> stats = Collections.emptyMap();

    private ArrayList<AppDetails> filteredAppList = new ArrayList<>();
    /** Kueri pencarian aktif; string kosong berarti tanpa filter. */
    private String mQuery = "";

    private SharedPreferences uiPref;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mToggle;
    private ProgressDialog progressDialog;
    private SwipeRefreshLayout swipeRefreshLayout;
    private PrefFileManager prefFileManager;
    private MainFragment mFragment;
    private SearchView mSearchView;

    /** Satu thread latar untuk memuat daftar aplikasi. */
    private final ExecutorService mLoader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mmg-apps");
        t.setDaemon(true);
        return t;
    });

    /** Mencegah pemuatan bertumpuk saat swipe-refresh beruntun. */
    private final java.util.concurrent.atomic.AtomicBoolean mLoading = new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Permintaan muat ulang yang datang saat pemuatan lain sedang berjalan. */
    private volatile boolean mRefreshPending;

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
            else if (id == R.id.action_forget_scope)
            {
                confirmForgetScope();
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

        uiPref = getSharedPreferences(Common.UI_PREFS, Common.getPrefMode());

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

        NavigationView navigationView = findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(mNavListener);

        mDrawerLayout = findViewById(R.id.drawer_layout);
        mToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.app_name, R.string.app_name);
        mDrawerLayout.addDrawerListener(mToggle);
        mToggle.syncState();

        // Satu fragmen, dipasang sekali; rotasi tidak membuatnya ulang karena
        // FragmentManager memulihkannya.
        mFragment = savedInstanceState == null ? MainFragment.newInstance() : findFragment();
        if (savedInstanceState == null)
        {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, mFragment)
                    .commit();
        }

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

        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                refresh(false);
            }
        });

        // Pemuatan awal dilakukan oleh onResume(), yang juga dipanggil tepat
        // setelah onCreate selesai.

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
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    /** Fragmen hasil pemulihan setelah rotasi / proses dibunuh. */
    private MainFragment findFragment()
    {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof MainFragment)
        {
            return (MainFragment) fragment;
        }
        MainFragment created = MainFragment.newInstance();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, created)
                .commitAllowingStateLoss();
        return created;
    }

    /** Handler utama untuk menggabungkan notifikasi perubahan dari provider. */
    private final android.os.Handler mMainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * Dipicu provider setiap kali statistik atau daftar aplikasi aktif berubah
     * (aplikasi target dibuka dan baru saja mendaftar, atau angka blokir naik).
     * Perubahan digabung sebentar karena satu sesi aplikasi bisa menulis
     * puluhan kali.
     */
    private final Runnable mProviderChanged = new Runnable()
    {
        @Override
        public void run()
        {
            if (isFinishing() || isDestroyed())
            {
                return;
            }
            refresh(false);
        }
    };

    private final android.database.ContentObserver mContentObserver =
            new android.database.ContentObserver(mMainHandler)
            {
                @Override
                public void onChange(boolean selfChange)
                {
                    mMainHandler.removeCallbacks(mProviderChanged);
                    mMainHandler.postDelayed(mProviderChanged, PROVIDER_DEBOUNCE_MS);
                }
            };

    /** Jeda penggabungan notifikasi provider (ms). */
    private static final long PROVIDER_DEBOUNCE_MS = 400L;

    private boolean mObserving;

    private void startObserving()
    {
        if (mObserving)
        {
            return;
        }
        try
        {
            android.content.ContentResolver resolver = getContentResolver();
            resolver.registerContentObserver(Uri.parse(Common.URI_ACTIVE), true, mContentObserver);
            resolver.registerContentObserver(Uri.parse(Common.URI_STATS), true, mContentObserver);
            mObserving = true;
        }
        catch (Throwable ignored)
        {
            // Provider belum tersedia: penyegaran manual tetap bekerja.
        }
    }

    private void stopObserving()
    {
        if (!mObserving)
        {
            return;
        }
        try
        {
            getContentResolver().unregisterContentObserver(mContentObserver);
        }
        catch (Throwable ignored)
        {
        }
        mMainHandler.removeCallbacks(mProviderChanged);
        mObserving = false;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        startObserving();
        // Aplikasi target yang baru dibuka mendaftarkan dirinya ke provider
        // saat kembali ke sini, jadi daftar harus dimuat ulang, bukan hanya
        // angkanya. Daftar hanya berisi paket di scope sehingga murah; dialog
        // tunggu tidak perlu dan hanya mengganggu saat kembali dari aplikasi
        // lain.
        refresh(false);
    }

    @Override
    protected void onPause()
    {
        stopObserving();
        super.onPause();
    }

    /**
     * Memuat ulang hitungan iklan tanpa membaca ulang daftar aplikasi.
     * Dipanggil setelah statistik satu aplikasi dihapus dari dialog detail.
     */
    public void refreshStatsOnly()
    {
        try
        {
            mLoader.execute(() -> {
                final Map<String, AppStats> loaded = StatsRepository.loadStats(this);
                runOnUiThread(() -> {
                    stats = loaded;
                    if (isFinishing() || isDestroyed())
                    {
                        return;
                    }
                    MainFragment fragment = currentFragment();
                    if (fragment != null && fragment.isAlive)
                    {
                        fragment.updateSummaryOnly(visibleList(), activeAppList.size(), loaded,
                                isFiltering());
                    }
                });
            });
        }
        catch (java.util.concurrent.RejectedExecutionException ignored)
        {
            // Activity sudah dihancurkan; tidak ada lagi yang perlu diperbarui.
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
        mSearchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.action_search));
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener()
        {
            @Override
            public boolean onQueryTextSubmit(String query)
            {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query)
            {
                mQuery = query == null ? "" : query.trim().toLowerCase();
                applyFilter();
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

    private void showIntro()
    {
        startActivity(new Intent(this, MinMinGuardIntro.class));
    }

    private void confirmResetStatistics()
    {
        new MaterialAlertDialogBuilder(this)
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

    /**
     * Menghapus catatan "aplikasi aktif".
     * <p>
     * Daftar ini dibangun dari laporan aplikasi saat mereka dijalankan, jadi
     * aplikasi yang scope-nya sudah dicabut tetap tampil sampai dibuka lagi.
     * Tanpa tombol ini, daftar hanya bisa dibersihkan dengan cara itu.
     */
    private void confirmForgetScope()
    {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_forget_scope)
                .setMessage(R.string.forget_scope_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    StatsRepository.clearActiveApps(this);
                    refresh(false);
                    Snackbar.make(findViewById(R.id.drawer_layout),
                            R.string.msg_scope_cleared, Snackbar.LENGTH_SHORT).show();
                })
                .show();
    }

    public void refresh(final boolean displayProgressDialog)
    {
        if (!mLoading.compareAndSet(false, true))
        {
            // Ada pemuatan yang sedang berjalan. Permintaan ini tidak boleh
            // hilang: notifikasi provider bisa datang tepat di tengah muat dan
            // justru membawa baris yang belum terlihat.
            mRefreshPending = true;
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

        try
        {
            mLoader.execute(() -> {
                final ArrayList<AppDetails> list;
                final Map<String, AppStats> loadedStats;
                try
                {
                    loadedStats = StatsRepository.loadStats(this);
                    list = buildActiveAppList(getPackageManager(), loadedStats);
                }
                catch (Throwable t)
                {
                    runOnUiThread(() -> finishRefresh(false));
                    return;
                }
                runOnUiThread(() -> {
                    stats = loadedStats;
                    activeAppList = list;
                    finishRefresh(true);
                });
            });
        }
        catch (java.util.concurrent.RejectedExecutionException e)
        {
            // Executor sudah dimatikan di onDestroy(): cukup tutup indikator.
            finishRefresh(false);
        }
    }

    private void finishRefresh(boolean dataReady)
    {
        mLoading.set(false);

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
        if (dataReady)
        {
            applyFilter();
        }

        // Notifikasi provider yang datang selama pemuatan tadi dijalankan lagi
        // di sini, dengan data yang lebih baru.
        if (mRefreshPending)
        {
            mRefreshPending = false;
            refresh(false);
        }
    }

    /**
     * Menyusun daftar tampil dari catatan {@code URI_ACTIVE}.
     * <p>
     * Data aplikasi terpasang diambil hanya untuk paket yang tercatat; jumlah
     * aplikasi di HP tidak lagi memengaruhi waktu muat.
     */
    private ArrayList<AppDetails> buildActiveAppList(final PackageManager pm,
                                                     final Map<String, AppStats> loadedStats)
    {
        List<ActiveApp> active = StatsRepository.loadActiveApps(this);
        ArrayList<AppDetails> result = new ArrayList<>(active.size());

        for (ActiveApp entry : active)
        {
            String pkgName = entry.getPackageName();
            if (pkgName.equals(getPackageName()))
            {
                continue;
            }

            ApplicationInfo info = null;
            String name = pkgName;
            boolean installed = true;
            try
            {
                info = pm.getApplicationInfo(pkgName, 0);
                CharSequence label = info.loadLabel(pm);
                if (label != null && label.length() > 0)
                {
                    name = label.toString();
                }
            }
            catch (PackageManager.NameNotFoundException e)
            {
                installed = false;
            }

            result.add(new AppDetails(name, pkgName, info, installed));
        }

        String sort = uiPref.getString(Common.KEY_SORT, Common.VALUE_SORT_BLOCKED);
        if (Common.VALUE_SORT_NAME.equals(sort))
        {
            Collections.sort(result, (l, r) -> l.getName().compareToIgnoreCase(r.getName()));
        }
        else if (Common.VALUE_SORT_PACKAGE.equals(sort))
        {
            Collections.sort(result, (l, r) -> l.getPackageName().compareToIgnoreCase(r.getPackageName()));
        }
        else
        {
            final Map<String, AppStats> byPackage = loadedStats == null
                    ? Collections.<String, AppStats>emptyMap() : loadedStats;
            final Map<String, Long> lastSeen = new HashMap<>();
            for (ActiveApp entry : active)
            {
                lastSeen.put(entry.getPackageName(), entry.getLastSeen());
            }
            // List.sort() butuh API 24; Collections.sort() tersedia sejak API 1.
            Collections.sort(result, (l, r) -> {
                int lc = blockCount(byPackage, l.getPackageName());
                int rc = blockCount(byPackage, r.getPackageName());
                int cmp = Integer.compare(rc, lc);
                if (cmp != 0)
                {
                    return cmp;
                }
                long ls = lastSeen.containsKey(l.getPackageName()) ? lastSeen.get(l.getPackageName()) : 0L;
                long rs = lastSeen.containsKey(r.getPackageName()) ? lastSeen.get(r.getPackageName()) : 0L;
                return Long.compare(rs, ls);
            });
        }

        return result;
    }

    private static int blockCount(Map<String, AppStats> stats, String pkgName)
    {
        AppStats item = stats.get(pkgName);
        return item == null ? 0 : item.getBlockNum();
    }

    /**
     * Menyusun daftar yang sedang tampil menurut kueri pencarian, lalu
     * menyerahkannya ke fragmen. Daftar induk tidak diubah sehingga pencarian
     * bisa dihapus tanpa memuat ulang apa pun.
     */
    private void applyFilter()
    {
        MainFragment fragment = currentFragment();
        if (fragment == null || !fragment.isAlive)
        {
            return;
        }
        fragment.render(visibleList(), activeAppList.size(), stats, isFiltering());
    }

    /** True bila daftar sedang disaring oleh kueri pencarian. */
    public boolean isFiltering()
    {
        return !mQuery.isEmpty();
    }

    /** Daftar yang sedang tampil, sudah difilter menurut kueri pencarian. */
    public ArrayList<AppDetails> visibleList()
    {
        if (mQuery.isEmpty())
        {
            return activeAppList;
        }
        filteredAppList = new ArrayList<>();
        for (AppDetails info : activeAppList)
        {
            if (info.getName().toLowerCase().contains(mQuery)
                    || info.getPackageName().toLowerCase().contains(mQuery))
            {
                filteredAppList.add(info);
            }
        }
        return filteredAppList;
    }

    private MainFragment currentFragment()
    {
        if (mFragment != null && mFragment.isAlive)
        {
            return mFragment;
        }
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof MainFragment)
        {
            mFragment = (MainFragment) fragment;
            return mFragment;
        }
        return null;
    }
}
