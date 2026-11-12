package tw.fatminmin.xposed.minminguard.next.core.hook;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Mengunci semantik ART: kelas framework dimuat bootstrap class loader
 * sehingga {@code getClassLoader()} mengembalikan {@code null}.
 * <p>
 * Di JVM kelas {@code java.*} dan {@code javax.*} berperilaku sama, sehingga
 * regresi 3.1.3 bisa direproduksi tanpa perangkat: versi lama memeriksa kelas
 * <em>awal</em>, berhenti di depth 0, dan tidak memasang satu hook pun.
 */
public class HierarchyWalkTest
{
    /** Meniru {@code android.view.ViewGroup} yang turun dari {@code Object}. */
    static class FakeViewGroup
    {
        public void addView(Object child)
        {
        }

        public void addView(Object child, int index)
        {
        }

        public void unrelated()
        {
        }
    }

    /** Meniru {@code AdView} yang mewarisi {@code addView} dari ViewGroup. */
    static class FakeAdView extends FakeViewGroup
    {
        public void loadAd()
        {
        }
    }

    /** Meniru {@code BaseAdView#loadAd} yang diwarisi {@code AdView}. */
    static class FakeBaseAd
    {
        public void loadAd()
        {
        }
    }

    static class FakeRewardedAd extends FakeBaseAd
    {
        // load() tidak dideklarasikan ulang: harus ditemukan di kelas dasar.
    }

    /**
     * Meniru {@code LevelPlayBannerAdView}: subclass dari kelas framework yang
     * memakai loader aplikasi, dengan method sendiri.
     */
    static class FakeViewGroupChild extends FakeViewGroup
    {
        public void loadBanner()
        {
        }
    }

    /** Kelas abstrak seperti {@code RewardedAd#show} di AdMob. */
    static abstract class FakeAbstractAd
    {
        public abstract void show();

        public void show(int stub)
        {
        }
    }

    // --- Regresi 3.1.3 -----------------------------------------------------

    @Test
    public void frameworkClassIsProcessedAtDepthZero()
    {
        // java.util.ArrayList dimuat bootstrap di JVM, persis seperti kelas
        // android.* di ART: getClassLoader() == null.
        assertEquals("prasyarat: kelas bootstrap", null, java.util.ArrayList.class.getClassLoader());

        assertTrue("kelas awal harus selalu boleh diproses",
                HierarchyWalk.shouldClimb(java.util.ArrayList.class, java.util.ArrayList.class));
    }

    @Test
    public void frameworkAncestorStopsTheWalk()
    {
        // Naik DARI kelas aplikasi KE kelas bootstrap harus berhenti, tetapi
        // kelas awal tetap diproses. Inilah perbedaan yang membunuh 3.1.3.
        assertTrue(HierarchyWalk.shouldClimb(FakeAdView.class, FakeAdView.class));
        assertFalse("leluhur bootstrap tidak boleh didaki",
                HierarchyWalk.shouldClimb(FakeAdView.class, Object.class));
    }

    @Test
    public void nullCurrentIsNotClimbable()
    {
        assertFalse(HierarchyWalk.shouldClimb(FakeAdView.class, null));
    }

    /**
     * Reproduksi persis kegagalan 3.1.3 tanpa API baru: implementasi lama
     * memanggil {@code current.getClassLoader()} untuk kelas AWAL dan berhenti
     * saat null, sehingga kelas framework selalu menghasilkan nol hook. Di
     * ART itu {@code android.view.ViewGroup}; di JVM kelasnya
     * {@code java.util.ArrayList} yang juga dimuat bootstrap.
     */
    @Test
    public void startClassFromBootstrapLoaderIsStillCollected()
    {
        assertEquals("prasyarat: dimuat bootstrap", null,
                java.util.ArrayList.class.getClassLoader());

        List<java.lang.reflect.Method> methods = HierarchyWalk.collect(
                java.util.ArrayList.class, "size");

        assertEquals("kelas framework di depth 0 wajib diproses", 1, methods.size());
        assertEquals(java.util.ArrayList.class, methods.get(0).getDeclaringClass());
    }

    @Test
    public void ownMethodsCollectedWhenSuperclassIsBootstrap()
    {
        // LevelPlayBannerAdView extends FrameLayout: method banner dideklarasikan
        // di subclass (app loader), leluhur framework hanya menghentikan walk.
        List<java.lang.reflect.Method> methods = HierarchyWalk.collect(
                FakeViewGroupChild.class, "loadBanner");

        assertEquals(1, methods.size());
        assertEquals(FakeViewGroupChild.class, methods.get(0).getDeclaringClass());
    }

    // --- Pemilihan overload ------------------------------------------------

    @Test
    public void collectsAllOverloadsDeclaredOnStart()
    {
        List<java.lang.reflect.Method> methods = HierarchyWalk.collect(FakeViewGroup.class, "addView");
        assertEquals("kedua overload addView harus terkumpul", 2, methods.size());
    }

    @Test
    public void collectsInheritedMethod()
    {
        // Kasus nyata: AdView#loadAd dideklarasikan di BaseAdView.
        List<java.lang.reflect.Method> methods = HierarchyWalk.collect(FakeRewardedAd.class, "loadAd");
        assertEquals(1, methods.size());
        assertEquals(FakeBaseAd.class, methods.get(0).getDeclaringClass());
    }

    @Test
    public void overriddenDeclarationWinsOverBase()
    {
        // addView dideklarasikan di FakeAdView sendiri dan di FakeViewGroup;
        // masing-masing signature hanya di-hook sekali (di level paling turunan).
        List<java.lang.reflect.Method> methods = HierarchyWalk.collect(FakeAdView.class, "addView");
        assertEquals(2, methods.size());
        for (java.lang.reflect.Method method : methods)
        {
            assertEquals("deklarasi paling turunan yang dipakai",
                    FakeViewGroup.class, method.getDeclaringClass());
        }
    }

    @Test
    public void abstractOverloadIsSkippedButConcreteOneIsKept()
    {
        // RewardedAd/InterstitialAd/AppOpenAd mendeklarasikan show() abstrak.
        // hookAllMethods lama melempar dan membatalkan semua overload.
        List<java.lang.reflect.Method> methods = HierarchyWalk.collect(FakeAbstractAd.class, "show");
        assertEquals(1, methods.size());
        assertFalse(java.lang.reflect.Modifier.isAbstract(methods.get(0).getModifiers()));
    }

    @Test
    public void missingMethodYieldsEmptyList()
    {
        assertTrue(HierarchyWalk.collect(FakeViewGroup.class, "tidakAda").isEmpty());
    }

    @Test
    public void methodNamesIncludeInheritedNames()
    {
        java.util.Set<String> names = HierarchyWalk.names(FakeAdView.class);
        assertTrue(names.contains("addView"));
        assertTrue(names.contains("loadAd"));
        assertTrue(names.contains("unrelated"));
    }

    @Test
    public void methodNamesOfFrameworkClassAreNotEmpty()
    {
        // Kelas framework tetap punya nama method yang bisa disaring.
        assertTrue(HierarchyWalk.names(java.util.ArrayList.class).contains("size"));
    }

    // --- Signature ---------------------------------------------------------

    @Test
    public void signatureIncludesParameterTypes() throws Exception
    {
        java.lang.reflect.Method withIndex = FakeViewGroup.class
                .getDeclaredMethod("addView", Object.class, int.class);
        assertEquals("addView(java.lang.Object,int)", HierarchyWalk.signature(withIndex));
    }

    @Test
    public void signatureDistinguishesOverloads() throws Exception
    {
        java.lang.reflect.Method one = FakeViewGroup.class.getDeclaredMethod("addView", Object.class);
        java.lang.reflect.Method two = FakeViewGroup.class
                .getDeclaredMethod("addView", Object.class, int.class);
        assertFalse(HierarchyWalk.signature(one).equals(HierarchyWalk.signature(two)));
    }
}
