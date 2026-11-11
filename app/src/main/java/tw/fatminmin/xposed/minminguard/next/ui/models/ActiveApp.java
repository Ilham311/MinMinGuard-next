package tw.fatminmin.xposed.minminguard.next.ui.models;

/**
 * Satu aplikasi yang benar-benar di-hook modul (ada di scope LSPosed dan sudah
 * dijalankan minimal sekali).
 * <p>
 * Data ini ditulis oleh modul dari dalam proses aplikasi target sendiri, jadi
 * lebih jujur daripada membaca konfigurasi LSPosed yang hanya bisa diakses
 * root.
 */
public class ActiveApp
{
    private final String packageName;
    private final String versionName;
    private final long firstSeen;
    private final long lastSeen;

    public ActiveApp(String packageName, String versionName, long firstSeen, long lastSeen)
    {
        this.packageName = packageName;
        this.versionName = versionName;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
    }

    public String getPackageName()
    {
        return packageName;
    }

    public String getVersionName()
    {
        return versionName;
    }

    public long getFirstSeen()
    {
        return firstSeen;
    }

    public long getLastSeen()
    {
        return lastSeen;
    }
}
