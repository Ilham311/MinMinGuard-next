package tw.fatminmin.xposed.minminguard.next.core.name;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Mengunci pengecualian untuk aplikasi yang iklannya first-party.
 * <p>
 * Semua nama kelas di sini diambil dari bytecode APK asli di perangkat, bukan
 * karangan. Tes ini lahir dari bug yang ditemukan saat mengaudit Instagram:
 * heuristik nama menghapus {@code OngoingMetaAiVoiceBannerView} — banner
 * panggilan Meta AI di DM — hanya karena namanya berakhiran {@code BannerView}.
 * Pengguna kehilangan fitur, dan tidak ada satu iklan pun yang hilang.
 * <p>
 * Pelajaran desain yang juga dikunci di sini: namespace kode aplikasi TIDAK
 * selalu berada di bawah {@code applicationId}. Instagram
 * ({@code com.instagram.android}) memakai {@code com.instagram.*}; YouTube
 * ({@code com.google.android.youtube}) memakai
 * {@code com.google.android.libraries.youtube.*}. Desain pertama yang memakai
 * {@code applicationId} gagal pada kedua kasus itu.
 */
public class FirstPartyAppsTest
{
    private static final String[] HEURISTIC = {
            "AdView", "AdViewGroup", "AdContainer", "AdLayout", "AdWrapper",
            "BannerView", "BannerLayout", "NativeAdView", "NativeAdLayout",
            "InterstitialAdView", "AdFrameLayout", "AdRelativeLayout",
            "AdWebView", "RewardedAdView", "AppOpenAdView",
    };

    @Test
    public void instagramBannerThatIsNotAnAdIsExcluded()
    {
        // Dari com.instagram.android 454: banner panggilan Meta AI di DM.
        String cls = "com.instagram.direct.rtc.ongoingcallbar.metaaivoice"
                + ".OngoingMetaAiVoiceBannerView";

        // Heuristik nama memang menganggapnya iklan — itu sumber masalahnya.
        assertTrue("prasyarat: nama ini cocok dengan akhiran BannerView",
                AdNameMatcher.matchesAnySuffix(AdNameMatcher.simpleName(cls), HEURISTIC));

        assertTrue(FirstPartyApps.isFirstParty("com.instagram.android"));
        assertTrue(FirstPartyApps.ownsClass("com.instagram.android", cls));
    }

    @Test
    public void youtubeAdUiIsExcludedFromNameLayer()
    {
        String disclosure = "com.google.android.libraries.youtube.ads.player.ui"
                + ".AdDisclosureBannerView";

        assertTrue("prasyarat: nama ini cocok dengan akhiran BannerView",
                AdNameMatcher.matchesAnySuffix(
                        AdNameMatcher.simpleName(disclosure), HEURISTIC));

        assertTrue(FirstPartyApps.isFirstParty("com.google.android.youtube"));
        assertTrue(FirstPartyApps.ownsClass("com.google.android.youtube", disclosure));
    }

    @Test
    public void michatOwnBannerViewsAreExcludedFromNameLayer()
    {
        // Dari jejak crash di perangkat (LSPosed verbose log):
        //   MinMinGuard [com.michatapp.im] addView:
        //       com.zenmen.palmchat.ui.widget.FeedBottomBannerView
        //   2 ms kemudian:
        //   NullPointerException ... setVisibility(int) on a null object
        //       at com.zenmen.palmchat.photoview.PhotoViewActivity.D1:139
        // dan pada proses lain:
        //   com.michatapp.ad.view.MeTabBannerAdView -> SettingsFragment:312.
        // Kedua kelas milik aplikasi sendiri, dipakai ulang lewat
        // findViewById. Menghapusnya dari pohon view mematikan aplikasi.
        String feedBanner = "com.zenmen.palmchat.ui.widget.FeedBottomBannerView";
        String tabBanner = "com.michatapp.ad.view.MeTabBannerAdView";

        assertTrue("prasyarat: nama ini cocok dengan akhiran BannerView",
                AdNameMatcher.matchesAnySuffix(
                        AdNameMatcher.simpleName(feedBanner), HEURISTIC));
        assertTrue("prasyarat: nama ini cocok dengan akhiran AdView",
                AdNameMatcher.matchesAnySuffix(
                        AdNameMatcher.simpleName(tabBanner), HEURISTIC));

        assertTrue(FirstPartyApps.isFirstParty("com.michatapp.im"));
        assertTrue(FirstPartyApps.ownsClass("com.michatapp.im", feedBanner));
        assertTrue(FirstPartyApps.ownsClass("com.michatapp.im", tabBanner));

        // SDK iklan yang dipakai MiChat tetap disaring.
        assertFalse(FirstPartyApps.ownsClass("com.michatapp.im",
                "com.google.android.gms.ads.AdView"));
        assertFalse(FirstPartyApps.ownsClass("com.michatapp.im",
                "com.ironsource.mediationsdk.IronSource"));
    }

    @Test
    public void capcutBannerViewIsExcludedFromNameLayer()
    {
        // Dari crash nyata: com.vega.ui.banner.BannerView dihapus oleh hook
        // addView, lalu BannerCardViewHolder.t0 melempar
        // "java.lang.NullPointerException: must not be null" saat bind.
        // CapCut menaruh kodenya di com.vega.*, bukan di applicationId
        // com.lemon.lvoverseas.
        String banner = "com.vega.ui.banner.BannerView";

        assertTrue("prasyarat: nama ini cocok dengan akhiran BannerView",
                AdNameMatcher.matchesAnySuffix(
                        AdNameMatcher.simpleName(banner), HEURISTIC));

        assertTrue(FirstPartyApps.isFirstParty("com.lemon.lvoverseas"));
        assertTrue(FirstPartyApps.ownsClass("com.lemon.lvoverseas", banner));

        // SDK iklan CapCut tetap disaring: Pangle, GMS, AppLovin, Mintegral.
        assertFalse(FirstPartyApps.ownsClass("com.lemon.lvoverseas",
                "com.bytedance.sdk.openadsdk.api.banner.PAGBannerAd"));
        assertFalse(FirstPartyApps.ownsClass("com.lemon.lvoverseas",
                "com.google.android.gms.ads.AdView"));
    }

    @Test
    public void capcutAndMichatCodeNamespacesDoNotOverlapTheApplicationId()
    {
        // Pelajaran yang sama seperti Instagram/YouTube: kode aplikasi jarang
        // berada tepat di bawah applicationId.
        assertFalse(AdNameMatcher.belongsToApp("com.vega.ui.banner.BannerView",
                FirstPartyApps.namespacesOf("com.michatapp.im")));
        assertFalse(AdNameMatcher.belongsToApp(
                "com.zenmen.palmchat.ui.widget.FeedBottomBannerView",
                FirstPartyApps.namespacesOf("com.lemon.lvoverseas")));
    }

    @Test
    public void applicationsWithoutFirstPartyAdsAreNotExcluded()
    {
        // Aplikasi lain tetap memakai heuristik penuh — jangan dilonggarkan.
        assertFalse(FirstPartyApps.isFirstParty("com.example.game"));
        assertFalse(FirstPartyApps.ownsClass("com.example.game", "com.instagram.Foo"));
    }

    @Test
    public void sdkClassesInOtherNamespacesAreStillEligible()
    {
        // Kelas SDK tetap disaring walau aplikasi target memakai SDK itu.
        assertFalse(FirstPartyApps.ownsClass("com.instagram.android",
                "com.applovin.mediation.ads.MaxAdView"));
        assertFalse(FirstPartyApps.ownsClass("com.google.android.youtube",
                "com.google.android.gms.ads.AdView"));
        // Catatan: com.google.android.gms BUKAN bagian dari
        // com.google.android.libraries.youtube, jadi tidak ikut dikecualikan.
    }

    @Test
    public void packageBoundaryIsRespected()
    {
        // "com.instagram.androidx" bukan bagian dari "com.instagram.android".
        assertFalse(AdNameMatcher.belongsToApp(
                "com.instagram.androidx.AdView", "com.instagram.android"));
        // Awalan sama tapi bukan sub-paket.
        assertFalse(AdNameMatcher.belongsToApp(
                "com.instagramAndroid.AdView", "com.instagram"));
        // Kelas yang persis sama dengan namespace bukan "di dalam" namespace.
        assertFalse(AdNameMatcher.belongsToApp("com.instagram", "com.instagram"));
    }

    @Test
    public void nullAndEmptyInputsAreSafe()
    {
        assertFalse(AdNameMatcher.belongsToApp(null, "com.instagram"));
        assertFalse(AdNameMatcher.belongsToApp("com.instagram.Foo"));
        assertFalse(AdNameMatcher.belongsToApp("com.instagram.Foo", (String[]) null));
        assertFalse(FirstPartyApps.ownsClass(null, "com.instagram.Foo"));
        assertFalse(FirstPartyApps.ownsClass("com.instagram.android", null));
        assertFalse(FirstPartyApps.isFirstParty(null));
        assertFalse(FirstPartyApps.isFirstParty(""));
    }
}
