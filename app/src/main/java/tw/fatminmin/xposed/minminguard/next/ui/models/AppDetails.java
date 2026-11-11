package tw.fatminmin.xposed.minminguard.next.ui.models;

import android.content.pm.ApplicationInfo;
import android.widget.ImageView;

import tw.fatminmin.xposed.minminguard.next.ui.image.GlideModule;

/**
 * Satu baris di daftar aplikasi aktif. Hanya berisi data tampilan; angka
 * statistik diambil dari {@link AppStats} agar daftar bisa diperbarui tanpa
 * menyentuh database.
 */
public class AppDetails
{
    private final String name;
    private final String packageName;
    /** Null bila aplikasi sudah dicopot tetapi masih tercatat di scope. */
    private final ApplicationInfo applicationInfo;
    private final boolean installed;

    public AppDetails(String name, String packageName, ApplicationInfo applicationInfo, boolean installed)
    {
        this.name = name;
        this.packageName = packageName;
        this.applicationInfo = applicationInfo;
        this.installed = installed;
    }

    public String getName()
    {
        return name;
    }

    public String getPackageName()
    {
        return packageName;
    }

    public boolean isInstalled()
    {
        return installed;
    }

    public void loadIcon(ImageView imageView)
    {
        if (applicationInfo != null)
        {
            GlideModule.loadApplicationIcon(imageView.getContext(), applicationInfo, imageView);
            return;
        }
        // Aplikasi sudah tidak terpasang: pakai ikon bawaan modul supaya
        // Glide tidak menampilkan gambar sisa dari baris lain.
        imageView.setImageResource(tw.fatminmin.xposed.minminguard.next.R.mipmap.ic_launcher);
    }
}
