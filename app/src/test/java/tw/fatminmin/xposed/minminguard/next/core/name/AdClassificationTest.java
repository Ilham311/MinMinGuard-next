package tw.fatminmin.xposed.minminguard.next.core.name;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Mengunci aturan kapan sebuah view boleh dilepas dari pohon view.
 * <p>
 * Aturan ini lahir dari tiga crash nyata di perangkat (CapCut dan MiChat).
 * Semuanya berawal dari satu keputusan yang tampak masuk akal: heuristik nama
 * menganggap sebuah view sebagai iklan, lalu hook {@code addView} memanggil
 * {@code param.setResult(null)} — view itu tidak pernah masuk ke parent-nya.
 * Aplikasi kemudian mencari view tersebut lewat {@code findViewById} dan
 * menerima {@code null}, lalu melempar NullPointerException di tempat yang
 * sama sekali tidak berhubungan dengan iklan:
 *
 * <pre>
 * CapCut  com.vega.ui.banner.BannerView
 *         -> BannerCardViewHolder.t0 "must not be null"
 * MiChat  com.zenmen.palmchat.ui.widget.FeedBottomBannerView
 *         -> PhotoViewActivity.D1:139 setVisibility on null
 * MiChat  com.michatapp.ad.view.MeTabBannerAdView
 *         -> SettingsFragment:312 addObserver
 * </pre>
 *
 * Pelajarannya: tebakan nama tidak cukup kuat untuk menghapus sesuatu.
 */
public class AdClassificationTest
{
    @Test
    public void heuristicResultsMayNotBeDetached()
    {
        assertTrue(AdClassification.isHeuristic(AdClassification.HEURISTIC));
        assertFalse("tebakan nama tidak boleh melepas view dari pohon",
                AdClassification.mayDetachFromTree(AdClassification.HEURISTIC));
    }

    @Test
    public void sdkResultsMayBeDetached()
    {
        assertTrue(AdClassification.mayDetachFromTree("GoogleAdmob"));
        assertTrue(AdClassification.mayDetachFromTree("Applovin"));
        assertTrue(AdClassification.mayDetachFromTree("Pangle"));
        assertTrue(AdClassification.mayDetachFromTree("Custom"));
    }

    @Test
    public void unknownOrEmptyClassificationIsNeverDetached()
    {
        assertFalse(AdClassification.mayDetachFromTree(null));
        assertFalse(AdClassification.mayDetachFromTree(""));
        assertFalse(AdClassification.isHeuristic(null));
        assertFalse(AdClassification.isHeuristic(""));
        assertFalse(AdClassification.isHeuristic("GoogleAdmob"));
    }
}
