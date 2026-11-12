package tw.fatminmin.xposed.minminguard.next.core.blocklist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validasi aset daftar pola host.
 * <p>
 * Dijalankan sebagai tes JVM, sehingga aset yang cacat menggagalkan build
 * alih-alih diam-diam mengurangi efektivitas modul. Sebelum ini tidak ada
 * pemeriksaan apa pun: aset bawaan berisi 2.774 duplikat {@code www.} dan sama
 * sekali tidak memuat host untuk delapan SDK besar yang justru punya blocker
 * sendiri (AppLovin, ironScale, Mintegral, Pangle, Vungle, Startapp, Unity).
 */
public final class BlocklistValidator
{
    /** Host yang wajib ada karena modul mengklaim mendukung jaringannya. */
    private static final String[] REQUIRED_SDK_HOSTS = {
            "googleads.g.doubleclick.net",
            // IMA memuat SDK-nya sendiri dari host ini; tanpa entri ini lapisan
            // DNS dan WebView tidak punya amunisi untuk iklan video walau
            // blocker GoogleIma sudah terpasang.
            "imasdk.googleapis.com",
            "applovin.com",
            "ironsrc.com",
            "mbridge.com",
            "pangle.io",
            "vungle.com",
            "startappservice.com",
            "unityads.unity3d.com",
    };

    private BlocklistValidator()
    {
        throw new AssertionError("No instances");
    }

    /** Temuan satu baris aset. */
    public static final class Finding
    {
        public final int line;
        public final String source;
        public final String message;

        Finding(int line, String source, String message)
        {
            this.line = line;
            this.source = source;
            this.message = message;
        }

        @Override
        public String toString()
        {
            return source + ":" + line + " " + message;
        }
    }

    /**
     * Memeriksa isi satu aset.
     *
     * @param source nama aset untuk pesan
     * @param lines  baris mentah
     * @return daftar masalah; kosong berarti aset sehat
     */
    public static List<Finding> validate(String source, List<String> lines)
    {
        List<Finding> findings = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < lines.size(); i++)
        {
            String raw = lines.get(i).trim();
            int lineNumber = i + 1;

            if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("!"))
            {
                continue;
            }
            if (raw.indexOf(' ') > 0)
            {
                findings.add(new Finding(lineNumber, raw, "format hosts tidak didukung di aset ini (satu host per baris)"));
                continue;
            }
            String lower = raw.toLowerCase(Locale.ROOT);
            if (!lower.equals(raw))
            {
                findings.add(new Finding(lineNumber, raw, "harus lowercase"));
            }
            if (lower.startsWith("www."))
            {
                findings.add(new Finding(lineNumber, raw, "awalan www. redundan"));
                // Dinormalkan, karena Blocklist juga membuangnya saat load.
                // Tanpa ini "www.example.com" lalu "example.com" tidak
                // terdeteksi sebagai duplikat padahal keduanya entri sama.
                lower = lower.substring(4);
            }
            if (lower.startsWith(".") || lower.endsWith("."))
            {
                findings.add(new Finding(lineNumber, raw, "titik di awal/akhir tidak valid"));
            }
            if (lower.startsWith("*") || lower.contains("://"))
            {
                findings.add(new Finding(lineNumber, raw, "pola wildcard/URL tidak didukung"));
            }
            if (!seen.add(lower))
            {
                findings.add(new Finding(lineNumber, raw, "duplikat"));
            }
        }

        return findings;
    }

    /**
     * Host SDK wajib yang tidak tercakup daftar.
     * <p>
     * Dicocokkan dengan suffix walk yang sama seperti
     * {@code Blocklist.isBlockedHost}, sehingga entri induk ("applovin.com")
     * memenuhi host turunannya.
     */
    public static List<String> missingSdkHosts(List<String> lines)
    {
        Set<String> hosts = new HashSet<>();
        for (String line : lines)
        {
            String value = line.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty() || value.startsWith("#") || value.startsWith("!"))
            {
                continue;
            }
            if (value.startsWith("www."))
            {
                value = value.substring(4);
            }
            hosts.add(value);
        }

        List<String> missing = new ArrayList<>();
        for (String required : REQUIRED_SDK_HOSTS)
        {
            if (!covered(hosts, required))
            {
                missing.add(required);
            }
        }
        return missing;
    }

    private static boolean covered(Set<String> hosts, String host)
    {
        if (hosts.contains(host))
        {
            return true;
        }
        int dot = host.indexOf('.');
        while (dot >= 0 && dot + 1 < host.length())
        {
            if (hosts.contains(host.substring(dot + 1)))
            {
                return true;
            }
            dot = host.indexOf('.', dot + 1);
        }
        return false;
    }
}
