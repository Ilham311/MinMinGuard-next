package tw.fatminmin.xposed.minminguard.next.blocker;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Daftar pola host iklan.
 * <p>
 * Versi lama menyimpan 26.761 pola di dalam {@code HashSet<String>} lalu
 * menjalankan {@code url.contains(pattern)} untuk setiap pola pada setiap
 * pemanggilan WebView: 26 ribu perbandingan substring per navigasi. Di sini
 * pola dipecah berdasarkan bentuknya:
 * <ul>
 *   <li>pola tanpa garis miring diperlakukan sebagai host persis;</li>
 *   <li>pola dengan garis miring dianggap prefiks jalur;</li>
 *   <li>pencocokan host dilakukan dari kanan (suffix walk): untuk
 *       {@code a.b.example.com} cukup 3 pemeriksaan, bukan 26 ribu.</li>
 * </ul>
 */
public final class Blocklist
{
    private static final Blocklist INSTANCE = new Blocklist();

    private final Set<String> mHosts = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> mPathPrefixes = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> mResourcePatterns = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private Blocklist()
    {
    }

    public static Blocklist get()
    {
        return INSTANCE;
    }

    public int size()
    {
        return mHosts.size() + mPathPrefixes.size() + mResourcePatterns.size();
    }

    /**
     * Menambahkan satu baris daftar pola.
     * Baris kosong, komentar ({@code #}), dan entri daftar host standar
     * ({@code 0.0.0.0 host}) diabaikan/dinormalkan.
     */
    public void addLine(String raw)
    {
        if (raw == null)
        {
            return;
        }

        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("!"))
        {
            return;
        }

        // Format hosts file: "0.0.0.0 ads.example.com"
        int space = line.indexOf(' ');
        if (space > 0)
        {
            line = line.substring(space + 1).trim();
            if (line.isEmpty())
            {
                return;
            }
            int secondSpace = line.indexOf(' ');
            if (secondSpace > 0)
            {
                line = line.substring(0, secondSpace);
            }
        }

        line = line.toLowerCase(Locale.ROOT);

        // "||domain^" gaya Adblock Plus.
        if (line.startsWith("||"))
        {
            line = line.substring(2);
            if (line.endsWith("^"))
            {
                line = line.substring(0, line.length() - 1);
            }
        }

        if (line.isEmpty())
        {
            return;
        }

        if (line.indexOf('/') >= 0)
        {
            mPathPrefixes.add(line);
        }
        else
        {
            mHosts.add(line);
        }
    }

    /** Pola longgar untuk nama method/kelas (dipakai {@code mmg_pattern}). */
    public void addResourcePattern(String raw)
    {
        if (raw == null)
        {
            return;
        }
        String pattern = raw.trim();
        if (!pattern.isEmpty() && !pattern.startsWith("#"))
        {
            mResourcePatterns.add(pattern);
        }
    }

    /** @return {@code true} bila host (atau salah satu induknya) ada di daftar. */
    public boolean isBlockedHost(String host)
    {
        if (host == null || mHosts.isEmpty())
        {
            return false;
        }

        String value = host.toLowerCase(Locale.ROOT);
        if (value.startsWith("www."))
        {
            value = value.substring(4);
        }
        if (value.isEmpty())
        {
            return false;
        }

        if (mHosts.contains(value))
        {
            return true;
        }

        // suffix walk: a.b.example.com -> b.example.com -> example.com -> com
        int dot = value.indexOf('.');
        while (dot >= 0 && dot + 1 < value.length())
        {
            String suffix = value.substring(dot + 1);
            if (mHosts.contains(suffix))
            {
                return true;
            }
            dot = value.indexOf('.', dot + 1);
        }
        return false;
    }

    /** @return {@code true} bila URL cocok dengan pola host atau prefiks jalur. */
    public boolean isBlockedUrl(String url)
    {
        if (url == null || url.isEmpty())
        {
            return false;
        }

        String lower = url.toLowerCase(Locale.ROOT);

        for (String path : mPathPrefixes)
        {
            if (lower.contains(path))
            {
                return true;
            }
        }

        String host = extractHost(lower);
        if (host == null)
        {
            return false;
        }

        if (host.indexOf(':') >= 0)
        {
            host = host.substring(0, host.indexOf(':'));
        }

        return isBlockedHost(host);
    }

    /** @return {@code true} bila teks (HTML/data WebView) memuat pola apa pun. */
    public boolean containsPattern(String text)
    {
        if (text == null || text.isEmpty())
        {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String pattern : mResourcePatterns)
        {
            if (lower.contains(pattern.toLowerCase(Locale.ROOT)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Mengambil host dari URL tanpa {@code java.net.URL} (yang melempar
     * exception untuk skema yang tidak dikenal seperti {@code javascript:}).
     */
    private static String extractHost(String url)
    {
        int scheme = url.indexOf("://");
        int start;
        if (scheme >= 0)
        {
            start = scheme + 3;
        }
        else if (url.startsWith("//"))
        {
            start = 2;
        }
        else if (url.indexOf('/') == 0 || !url.contains("."))
        {
            // Data URI, mailto:, about:, dsb.
            return null;
        }
        else
        {
            start = 0;
        }

        int end = url.length();
        for (int i = start; i < url.length(); i++)
        {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#')
            {
                end = i;
                break;
            }
        }

        if (end <= start)
        {
            return null;
        }

        int at = url.lastIndexOf('@', end - 1);
        if (at >= start)
        {
            start = at + 1;
        }

        return url.substring(start, end);
    }
}
