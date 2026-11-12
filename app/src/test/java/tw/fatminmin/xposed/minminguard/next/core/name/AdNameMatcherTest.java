package tw.fatminmin.xposed.minminguard.next.core.name;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Mengunci perilaku pencocokan nama iklan.
 * <p>
 * Tes ini lahir dari bug nyata: aturan batas-karakter lama menolak hampir
 * semua nama kelas banner yang dipakai SDK sungguhan, sehingga lapisan nama
 * praktis tidak menangkap apa pun. Daftar di {@code sdkBannerClassesAreRecognised}
 * adalah kelas yang benar-benar ada di SDK yang didukung modul ini.
 */
public class AdNameMatcherTest
{
    /** Daftar akhiran yang sama dengan NameBlocking.HEURISTIC_SUFFIXES. */
    private static final String[] SUFFIXES = {
            "AdView", "AdViewGroup", "AdContainer", "AdLayout", "AdWrapper",
            "BannerView", "BannerLayout", "NativeAdView", "NativeAdLayout",
            "InterstitialAdView", "AdFrameLayout", "AdRelativeLayout",
            "AdWebView", "RewardedAdView", "AppOpenAdView",
    };

    @Test
    public void tokensSplitCamelCase()
    {
        assertEquals(Arrays.asList("Max", "Ad", "View"), AdNameMatcher.tokens("MaxAdView"));
        assertEquals(Arrays.asList("Banner", "View"), AdNameMatcher.tokens("BannerView"));
        assertEquals(Arrays.asList("Head", "View"), AdNameMatcher.tokens("HeadView"));
    }

    @Test
    public void tokensKeepDigitsWithTheirWord()
    {
        assertEquals(Arrays.asList("Ad2iction"), AdNameMatcher.tokens("Ad2iction"));
        assertEquals(Arrays.asList("Ad", "Manager", "Interstitial", "Ad"),
                AdNameMatcher.tokens("AdManagerInterstitialAd"));
    }

    @Test
    public void tokensHandleAcronyms()
    {
        assertEquals(Arrays.asList("AT", "Banner", "View"),
                AdNameMatcher.tokens("ATBannerView"));
        assertEquals(Arrays.asList("MB", "Banner", "View"),
                AdNameMatcher.tokens("MBBannerView"));
    }

    @Test
    public void simpleNameStripsPackageAndOuterClass()
    {
        assertEquals("AdView", AdNameMatcher.simpleName("com.google.android.gms.ads.AdView"));
        assertEquals("AdView", AdNameMatcher.simpleName("com.x.Outer$AdView"));
        assertEquals("", AdNameMatcher.simpleName(null));
    }

    /**
     * Regresi inti: kelas-kelas ini sebelumnya TIDAK dikenali. Kalau suatu
     * saat matcher dilonggarkan atau diperketat tanpa sengaja, tes ini gagal.
     * <p>
     * Nama kelas diambil dari AAR resmi SDK-nya, bukan dari dokumentasi:
     * {@code com.unity3d.services.banners.view.BannerView} yang dipakai versi
     * sebelumnya ternyata tidak pernah ada — yang benar adalah
     * {@code com.unity3d.services.banners.BannerView} (diperiksa pada
     * unity-ads 3.7.5, 4.12.5, dan 4.21.0).
     */
    @Test
    public void sdkBannerClassesAreRecognised()
    {
        List<String> classes = Arrays.asList(
                "com.google.android.gms.ads.AdView",
                "com.applovin.mediation.ads.MaxAdView",
                "com.vungle.ads.VungleBannerView",
                "com.unity3d.mediation.banner.LevelPlayBannerAdView",
                "com.pangle.banner.view.BannerAdView",
                "com.mbridge.msdk.banner.MBBannerView",
                "com.unity3d.services.banners.BannerView",
                "com.anythink.banner.api.ATBannerView",
                "com.huawei.hms.ads.banner.BannerView",
                "com.facebook.ads.AdView");

        for (String className : classes)
        {
            String simple = AdNameMatcher.simpleName(className);
            assertTrue("tidak dikenali: " + className,
                    AdNameMatcher.matchesAnySuffix(simple, SUFFIXES));
        }
    }

    /**
     * Aturan yang harus tetap dipertahankan: "HeadView" mengandung "ad" di
     * tengah kata dan bukan iklan. Itulah alasan aturan batas-karakter dibuat
     * sejak awal, dan alasan itu masih sah.
     */
    @Test
    public void ordinaryViewsAreNotMatched()
    {
        List<String> classes = Arrays.asList(
                "android.view.ViewGroup",
                "com.example.app.HeadView",
                "com.example.app.AddView",
                "com.example.app.AdvertorialPage",
                "com.example.app.LoadingView",
                "com.example.app.ShadeView",
                "com.example.app.RoadView");

        for (String className : classes)
        {
            String simple = AdNameMatcher.simpleName(className);
            assertFalse("salah cocok: " + className,
                    AdNameMatcher.matchesAnySuffix(simple, SUFFIXES));
        }
    }

    @Test
    public void exactSuffixMatchesWithoutPrefix()
    {
        assertTrue(AdNameMatcher.endsWithTokens("AdView", "AdView"));
        assertTrue(AdNameMatcher.endsWithTokens("BannerView", "BannerView"));
    }

    @Test
    public void suffixLongerThanNameDoesNotMatch()
    {
        assertFalse(AdNameMatcher.endsWithTokens("View", "NativeAdView"));
        assertFalse(AdNameMatcher.endsWithTokens("", "AdView"));
    }

    @Test
    public void nullAndEmptyAreSafe()
    {
        assertFalse(AdNameMatcher.endsWithTokens(null, "AdView"));
        assertFalse(AdNameMatcher.endsWithTokens("AdView", null));
        assertFalse(AdNameMatcher.matchesAnySuffix(null, SUFFIXES));
        assertTrue(AdNameMatcher.tokens(null).isEmpty());
        assertEquals("", AdNameMatcher.simpleName(""));
    }
}
