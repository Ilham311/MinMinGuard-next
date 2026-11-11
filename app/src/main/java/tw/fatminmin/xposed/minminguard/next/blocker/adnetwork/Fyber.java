package tw.fatminmin.xposed.minminguard.next.blocker.adnetwork;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tw.fatminmin.xposed.minminguard.next.blocker.ApiBlocking;
import tw.fatminmin.xposed.minminguard.next.blocker.Blocker;

/**
 * Fyber / DT FairBid ({@code com.fyber}).
 * <p>
 * FairBid memakai API statis: {@code Interstitial.request(placementId)} dan
 * listener lewat {@code setInterstitialListener} / {@code setRewardedListener}
 * (nama khusus, bukan {@code setAdListener}). Nama setter itu sudah diperiksa
 * pada APK sungguhan dan dimuat di {@code ApiBlocking.LISTENER_SETTERS};
 * tanpanya listener tidak pernah tertangkap dan permintaan yang diblokir
 * membuat aplikasi menunggu selamanya.
 */
public class Fyber extends Blocker
{
    public static final String BANNER_PREFIX = "com.fyber";

    private static final String MEDIATOR = "com.fyber.inneractive.sdk.external.InneractiveAdManager";
    private static final String INTERSTITIAL_VIEW = "com.fyber.inneractive.sdk.external.InneractiveFullscreenUnitController";
    private static final String FAIRBID = "com.fyber.fairbid.ads.Interstitial";
    private static final String FAIRBID_REWARDED = "com.fyber.fairbid.ads.Rewarded";

    @Override
    public String getBanner()
    {
        return null;
    }

    @Override
    public String getBannerPrefix()
    {
        return BANNER_PREFIX;
    }

    @Override
    public boolean handleLoadPackage(String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean result = false;

        // InneractiveAdSpot adalah INTERFACE: requestAd() di sana abstrak, jadi
        // hook lama selalu gagal ("is abstract: it has no body to hook") dan
        // tidak pernah memblokir apa pun. Pemuatan FairBid di bawah memakai
        // method statis konkret, sedangkan jalur Inneractive ditangkap lewat
        // manager/controller yang punya badan nyata.
        result |= ApiBlocking.blockAdFunctionWithResult(packageName, MEDIATOR, "wasInitialized", false, lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, INTERSTITIAL_VIEW, "show", lpparam);

        // FairBid memisahkan listener dari request(): tangkap dulu agar
        // aplikasi menerima kabar gagal saat request diblokir.
        result |= ApiBlocking.captureAdListener(packageName, FAIRBID, lpparam);
        result |= ApiBlocking.captureAdListener(packageName, FAIRBID_REWARDED, lpparam);

        // request() adalah method statis; AdCallbacks menyimpan listener per
        // nama kelas sehingga thisObject yang null tidak menjadi masalah.
        result |= ApiBlocking.blockAdLoad(packageName, FAIRBID, "request", lpparam);
        result |= ApiBlocking.blockAdLoad(packageName, FAIRBID_REWARDED, "request", lpparam);

        result |= ApiBlocking.blockAdFunction(packageName, FAIRBID, "show", lpparam);
        result |= ApiBlocking.blockAdFunction(packageName, FAIRBID_REWARDED, "show", lpparam);

        return result;
    }
}
