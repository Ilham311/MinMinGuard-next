package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

public class Freewheel extends Blocker
{

    /**
     * Kelas banner dan prefix paketnya untuk lapisan pemblokiran berbasis nama.
     * <p>
     * Sebelumnya keduanya bernilai null, sehingga classifier nama tidak
     * pernah mengenali view dari SDK ini sama sekali: jaring pengaman yang
     * seharusnya menangkap iklan ketika signature API bergeser antar versi
     * SDK tidak pernah berfungsi. Nama kelas diambil dari SDK yang dipakai
     * aplikasi.
     */
    private static final String BANNER = "tv.freewheel.renderer.TVRenderer";
    private static final String BANNER_PREFIX = "tv.freewheel";
    public static final String AdInstace = "tv.freewheel.ad.AdInstance";

    private static final String Ad = "tv.freewheel.ad.Ad";

    public static final String Slot = "tv.freewheel.ad.slot.Slot";

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        result |= ApiBlocking.blockAdFunction(packageName, Ad, "parse", lpparam);
        /*result |= ApiBlocking.blockAdFunctionWithResult(packageName, AdInstace, "isPlayable", false, lpparam, removeAd);
        result |= ApiBlocking.blockAdFunctionWithResult(packageName, AdInstace, "isRequiredToShow", false, lpparam, removeAd);
        */

        return result;
    }

    @Override
    public String getBanner()
    {
        return BANNER;
    }

    @Override
    public String getBannerPrefix()
    {
        return BANNER_PREFIX;
    }
}