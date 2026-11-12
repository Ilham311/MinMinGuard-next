package tw.fatminmin.xposed.minminguard.next.core.blocklist;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

/**
 * Menjalankan {@link BlocklistValidator} pada aset yang benar-benar dikemas
 * ke APK.
 * <p>
 * Tes ini sengaja membaca berkas aset, bukan fixture sintetis: tujuan utamanya
 * mencegah aset bawaan kembali membusuk (duplikat, huruf besar, host SDK
 * hilang). Sebelum 4.0.0 tidak ada pemeriksaan apa pun, dan aset memang
 * membusuk: 2.774 duplikat {@code www.} serta nol host untuk delapan SDK.
 */
public class BlocklistAssetTest
{
    private static final String ASSET_DIR = "src/main/assets/host";

    private static File asset(String name)
    {
        File direct = new File(ASSET_DIR, name);
        if (direct.exists())
        {
            return direct;
        }
        // Gradle menjalankan tes dengan working directory berbeda-beda;
        // cari lewat jalur modul.
        return new File("app/" + ASSET_DIR, name);
    }

    private static List<String> read(String name) throws Exception
    {
        File file = asset(name);
        assertTrue("aset tidak ditemukan: " + file.getAbsolutePath(), file.isFile());
        return new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void outputFileHasNoDuplicatesOrFormatProblems() throws Exception
    {
        List<BlocklistValidator.Finding> findings =
                BlocklistValidator.validate("output_file", read("output_file"));
        assertTrue("aset output_file cacat: " + findings, findings.isEmpty());
    }

    @Test
    public void extraFileHasNoDuplicatesOrFormatProblems() throws Exception
    {
        List<BlocklistValidator.Finding> findings =
                BlocklistValidator.validate("mmg_extra", read("mmg_extra"));
        assertTrue("aset mmg_extra cacat: " + findings, findings.isEmpty());
    }

    @Test
    public void allSupportedSdkHostsAreCovered() throws Exception
    {
        List<String> lines = new ArrayList<>();
        lines.addAll(read("output_file"));
        lines.addAll(read("mmg_extra"));

        List<String> missing = BlocklistValidator.missingSdkHosts(lines);
        assertTrue("host SDK yang diklaim didukung tetapi tidak ada di daftar: " + missing,
                missing.isEmpty());
    }

    @Test
    public void extraFileDoesNotDuplicateOutputFile()
    {
        // Entri yang sudah ada di output_file tidak perlu diulang; duplikat
        // antar-aset tidak berbahaya tetapi menandakan aset tidak dirawat.
        try
        {
            java.util.Set<String> base = new java.util.HashSet<>();
            for (String line : read("output_file"))
            {
                base.add(line.trim().toLowerCase(Locale.ROOT));
            }
            List<String> duplicates = new ArrayList<>();
            for (String line : read("mmg_extra"))
            {
                String value = line.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty() || value.startsWith("#"))
                {
                    continue;
                }
                String bare = value.startsWith("www.") ? value.substring(4) : value;
                if (base.contains(bare))
                {
                    duplicates.add(value);
                }
            }
            assertTrue("entri mmg_extra sudah ada di output_file: " + duplicates,
                    duplicates.isEmpty());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    public void validatorFlagsKnownBadInput()
    {
        List<BlocklistValidator.Finding> findings = BlocklistValidator.validate("contoh",
                Arrays.asList("Ads.Example.COM", "www.example.com", "example.com", ""));
        assertTrue(findings.toString(), findings.size() == 3);
    }
}
