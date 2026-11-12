package tw.fatminmin.xposed.minminguard.next.core.name;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * Mengunci urutan prefix pada daftar blocker.
 * <p>
 * Pencocokan nama memakai <em>entri pertama yang cocok</em>
 * ({@code NameBlocking.classifyOnly} dan {@code ApiBlocking.blockerNameOf}).
 * Artinya bila satu prefix merupakan awalan dari prefix lain, yang lebih
 * spesifik HARUS berada lebih dulu — kalau tidak, semua kelas milik jaringan
 * spesifik dilaporkan sebagai jaringan umum.
 * <p>
 * Kasus nyata: {@code com.google.ads.interactivemedia} (IMA) berada di dalam
 * {@code com.google.ads} (AdMob lama). Dengan urutan yang salah, setiap iklan
 * video IMA tercatat sebagai AdMob: blokir API-nya tetap bekerja, tetapi
 * statistik per-jaringan berbohong dan pengguna yang mematikan AdMob akan
 * mengira IMA juga mati.
 * <p>
 * Tes ini membaca sumber {@code Main.java} karena array {@code blockers}
 * berisi objek Android yang tidak bisa dimuat di JVM. Yang diperiksa bukan
 * daftar nama tertentu, melainkan invariannya: tidak boleh ada prefix umum
 * yang muncul sebelum prefix turunannya.
 */
public class BlockerOrderTest
{
    private static final String MAIN = "src/main/java/tw/fatminmin/xposed/minminguard/next/Main.java";

    /** Entri {@code new NamaKelas()} di dalam array blockers, berurutan. */
    private static List<String> blockerOrder(String source)
    {
        int start = source.indexOf("blockers = {");
        assertTrue("array blockers tidak ditemukan di Main.java", start >= 0);
        int end = source.indexOf("};", start);
        assertTrue("akhir array blockers tidak ditemukan", end > start);

        List<String> order = new ArrayList<>();
        Matcher matcher = Pattern.compile("new\\s+([A-Za-z0-9_]+)\\s*\\(\\)")
                .matcher(source.substring(start, end));
        while (matcher.find())
        {
            order.add(matcher.group(1));
        }
        return order;
    }

    /** Prefix paket yang dideklarasikan sebuah kelas blocker. */
    private static String prefixOf(String blockerClass, String root)
    {
        for (String dir : new String[]{"blocker/adnetwork", "blocker/custom_mod"})
        {
            File file = new File(root, "src/main/java/tw/fatminmin/xposed/minminguard/next/"
                    + dir + "/" + blockerClass + ".java");
            if (!file.isFile())
            {
                continue;
            }
            try
            {
                String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                // Sebagian blocker lama menamai konstantanya PREFIX, bukan
                // BANNER_PREFIX (Avocarrot, LevelPlay). Melewatkannya membuat
                // tes ini buta terhadap tabrakan yang melibatkan mereka.
                Matcher matcher = Pattern.compile(
                        "(?:BANNER_)?PREFIX\\s*=\\s*\"([^\"]+)\"").matcher(source);
                return matcher.find() ? matcher.group(1) : null;
            }
            catch (Exception e)
            {
                throw new AssertionError(e);
            }
        }
        return null;
    }

    private static String root()
    {
        // Gradle menjalankan tes dengan working directory berbeda-beda.
        return new File(MAIN).isFile() ? "." : "app";
    }

    @Test
    public void moreSpecificPrefixesComeFirst() throws Exception
    {
        String root = root();
        File main = new File(root, MAIN);
        assertTrue("Main.java tidak ditemukan: " + main.getAbsolutePath(), main.isFile());
        String source = new String(Files.readAllBytes(main.toPath()), StandardCharsets.UTF_8);

        List<String> order = blockerOrder(source);
        assertTrue("array blockers kosong", order.size() > 10);

        List<String> violations = new ArrayList<>();
        for (int i = 0; i < order.size(); i++)
        {
            String outer = prefixOf(order.get(i), root);
            if (outer == null)
            {
                continue;
            }
            for (int j = i + 1; j < order.size(); j++)
            {
                String inner = prefixOf(order.get(j), root);
                // Prefix turunan harus muncul lebih dulu, dan "startsWith"
                // sesudah titik memastikan yang dibandingkan benar-benar
                // sub-paket, bukan sekadar nama yang kebetulan berawalan sama.
                if (inner != null
                        && inner.startsWith(outer)
                        && !inner.equals(outer)
                        && inner.charAt(outer.length()) == '.')
                {
                    violations.add(order.get(j) + " (" + inner + ") setelah "
                            + order.get(i) + " (" + outer + ")");
                }
            }
        }

        assertTrue("prefix turunan harus berada sebelum prefix induknya: " + violations,
                violations.isEmpty());
    }
}
