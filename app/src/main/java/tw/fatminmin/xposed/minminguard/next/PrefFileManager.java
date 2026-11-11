package tw.fatminmin.xposed.minminguard.next;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.FileObserver;

import androidx.core.content.ContextCompat;

import java.io.File;

/**
 * Membuat berkas preferensi modul tetap bisa dibaca oleh proses aplikasi lain.
 * <p>
 * Versi sebelumnya bernama {@code getInstance} tetapi selalu membuat objek
 * baru, sehingga setiap {@code Activity} yang dibuat ulang (rotasi, ganti
 * tema) meninggalkan satu {@link FileObserver} yang masih menonton folder dan
 * tidak pernah dilepas. Sekarang benar-benar singleton dan memakai
 * {@code Application} context, jadi observer hanya ada satu seumur proses.
 */
public final class PrefFileManager
{
    private static volatile PrefFileManager sInstance;

    private final Context mContext;
    private final Object mLock = new Object();
    private FileObserver mFileObserver;
    private boolean mSelfAttrChange;
    private int mResumeCount;

    private PrefFileManager(Context context)
    {
        mContext = Build.VERSION.SDK_INT >= 24 && !ContextCompat.isDeviceProtectedStorage(context)
                ? ContextCompat.createDeviceProtectedStorageContext(context) : context;
    }

    public static PrefFileManager getInstance(Context context)
    {
        if (context == null)
        {
            throw new IllegalArgumentException("Context cannot be null");
        }
        PrefFileManager instance = sInstance;
        if (instance == null)
        {
            synchronized (PrefFileManager.class)
            {
                instance = sInstance;
                if (instance == null)
                {
                    Context app = context.getApplicationContext();
                    instance = new PrefFileManager(app != null ? app : context);
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    public void fixFolderPermissionsAsync()
    {
        new Thread(new Runnable()
        {
            @SuppressLint("SetWorldReadable")
            @SuppressWarnings("ResultOfMethodCallIgnored")
            @Override
            public void run()
            {
                File pkgFolder = mContext.getFilesDir().getParentFile();
                if (pkgFolder != null && pkgFolder.exists())
                {
                    pkgFolder.setExecutable(true, false);
                    pkgFolder.setReadable(true, false);
                }
                File cacheFolder = mContext.getCacheDir();
                if (cacheFolder != null && cacheFolder.exists())
                {
                    cacheFolder.setExecutable(true, false);
                    cacheFolder.setReadable(true, false);
                }
                File filesFolder = mContext.getFilesDir();
                if (filesFolder != null && filesFolder.exists())
                {
                    filesFolder.setExecutable(true, false);
                    filesFolder.setReadable(true, false);
                    File[] children = filesFolder.listFiles();
                    if (children != null)
                    {
                        for (File f : children)
                        {
                            f.setExecutable(true, false);
                            f.setReadable(true, false);
                        }
                    }
                }
            }
        }, "mmg-perms").start();
    }

    /** Dipanggil dari {@code Activity#onStart}; aman untuk beberapa Activity. */
    public void onResume()
    {
        synchronized (mLock)
        {
            mResumeCount++;
            if (mResumeCount > 1)
            {
                return;
            }
            fixPermissions(true);
            startWatching();
        }
    }

    /** Dipanggil dari {@code Activity#onStop}; observer dilepas setelah pemakai terakhir. */
    public void onPause()
    {
        synchronized (mLock)
        {
            if (mResumeCount > 0)
            {
                mResumeCount--;
            }
            if (mResumeCount > 0)
            {
                return;
            }
            stopWatching();
            fixPermissions(true);
        }
    }

    private void startWatching()
    {
        if (mFileObserver != null)
        {
            return;
        }

        File sharedPrefs = new File(mContext.getFilesDir().getParentFile(), "shared_prefs");
        if (!sharedPrefs.exists())
        {
            return;
        }

        if (Build.VERSION.SDK_INT >= 29)
        {
            mFileObserver = new FileObserver(sharedPrefs, FileObserver.ATTRIB)
            {
                @Override
                public void onEvent(int event, String path)
                {
                    if ((event & FileObserver.ATTRIB) != 0)
                    {
                        onFileAttributesChanged(path);
                    }
                }
            };
        }
        else
        {
            mFileObserver = new FileObserver(sharedPrefs.getAbsolutePath(), FileObserver.ATTRIB)
            {
                @Override
                public void onEvent(int event, String path)
                {
                    if ((event & FileObserver.ATTRIB) != 0)
                    {
                        onFileAttributesChanged(path);
                    }
                }
            };
        }

        try
        {
            mFileObserver.startWatching();
        }
        catch (Throwable ignored)
        {
            mFileObserver = null;
        }
    }

    private void stopWatching()
    {
        if (mFileObserver == null)
        {
            return;
        }
        try
        {
            mFileObserver.stopWatching();
        }
        catch (Throwable ignored)
        {
        }
        mFileObserver = null;
    }

    @SuppressLint("SetWorldReadable")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void fixPermissions(boolean force)
    {
        File parent = mContext.getFilesDir().getParentFile();
        if (parent == null)
        {
            return;
        }
        File sharedPrefsFolder = new File(parent, "shared_prefs");
        if (sharedPrefsFolder.exists())
        {
            sharedPrefsFolder.setExecutable(true, false);
            sharedPrefsFolder.setReadable(true, false);
            File f = new File(sharedPrefsFolder, Common.MOD_PREFS + ".xml");
            if (f.exists())
            {
                mSelfAttrChange = !force;
                f.setReadable(true, false);
            }
        }
    }

    private void onFileAttributesChanged(String path)
    {
        if (path != null && path.endsWith(Common.MOD_PREFS + ".xml"))
        {
            if (mSelfAttrChange)
            {
                mSelfAttrChange = false;
                return;
            }
            fixPermissions(false);
        }
    }
}
