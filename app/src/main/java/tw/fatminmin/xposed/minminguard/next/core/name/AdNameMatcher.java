package tw.fatminmin.xposed.minminguard.next.core.name;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pencocokan nama kelas view iklan berdasarkan <em>token</em> camel-case.
 * <p>
 * Versi sebelumnya memakai aturan batas karakter: nama kelas dianggap cocok
 * bila berakhir dengan akhiran seperti {@code AdView} dan huruf tepat sebelum
 * akhiran itu kapital. Aturan itu maksudnya baik — menolak {@code HeadView}
 * yang di tengahnya ada huruf "ad" — tetapi hasilnya membuang justru kelas
 * yang paling umum:
 *
 * <pre>
 * MaxAdView                -> ditolak (huruf sebelumnya 'x')
 * LevelPlayBannerAdView    -> ditolak (huruf sebelumnya 'r')
 * VungleBannerView         -> ditolak (akhiran BannerView didahului 'e')
 * BannerAdView             -> ditolak (huruf sebelumnya 'r')
 * </pre>
 *
 * Semua itu adalah banner iklan sungguhan dari SDK yang didukung modul ini.
 * Akibatnya lapisan nama praktis hanya menangkap {@code AdView} polos.
 * <p>
 * Di sini nama dipecah menjadi token camel-case lebih dulu, lalu akhiran
 * dibandingkan per token. {@code Max|Ad|View} berakhir dengan {@code Ad|View}
 * sehingga cocok; {@code Head|View} tidak, karena tokennya {@code Head} dan
 * {@code View}, bukan {@code Ad} dan {@code View}. Dengan begitu aturan
 * "jangan salah tangkap HeadView" tetap terjaga tanpa membuang kelas nyata.
 * <p>
 * Kelas ini murni Java tanpa dependensi Android agar bisa diuji di JVM.
 */
public final class AdNameMatcher
{
    private AdNameMatcher()
    {
        throw new AssertionError("No instances");
    }

    /**
     * Nama kelas tanpa paket dan tanpa kelas luar.
     * {@code com.x.Outer$AdView} menjadi {@code AdView}.
     */
    public static String simpleName(String className)
    {
        if (className == null || className.isEmpty())
        {
            return "";
        }
        int dot = className.lastIndexOf('.');
        String simple = dot >= 0 ? className.substring(dot + 1) : className;
        int dollar = simple.lastIndexOf('$');
        return dollar >= 0 ? simple.substring(dollar + 1) : simple;
    }

    /**
     * Memecah nama menjadi token camel-case.
     * <p>
     * Batas token dipasang sebelum huruf kapital yang mengikuti huruf kecil
     * atau angka ({@code Max|Ad}), dan sebelum huruf kapital yang mengakhiri
     * rangkaian kapital ({@code AT|Banner}). Angka menempel pada tokennya
     * sehingga {@code Ad2iction} tetap satu token.
     */
    public static List<String> tokens(String name)
    {
        if (name == null || name.isEmpty())
        {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < name.length(); i++)
        {
            char previous = name.charAt(i - 1);
            char current = name.charAt(i);
            boolean boundary = false;
            if (Character.isUpperCase(current))
            {
                if (Character.isLowerCase(previous) || Character.isDigit(previous))
                {
                    boundary = true;
                }
                else if (Character.isUpperCase(previous)
                        && i + 1 < name.length()
                        && Character.isLowerCase(name.charAt(i + 1)))
                {
                    // "ATBanner" -> "AT" + "Banner"
                    boundary = true;
                }
            }
            if (boundary)
            {
                out.add(name.substring(start, i));
                start = i;
            }
        }
        out.add(name.substring(start));
        return out;
    }

    /**
     * True bila {@code simple} berakhir dengan token-token {@code suffix}.
     * <p>
     * Perbandingan per token, bukan per karakter, sehingga {@code MaxAdView}
     * cocok dengan {@code AdView} sementara {@code HeadView} tidak.
     */
    public static boolean endsWithTokens(String simple, String suffix)
    {
        List<String> nameTokens = tokens(simple);
        List<String> suffixTokens = tokens(suffix);
        if (suffixTokens.isEmpty() || nameTokens.size() < suffixTokens.size())
        {
            return false;
        }
        int offset = nameTokens.size() - suffixTokens.size();
        for (int i = 0; i < suffixTokens.size(); i++)
        {
            if (!suffixTokens.get(i).equals(nameTokens.get(offset + i)))
            {
                return false;
            }
        }
        return true;
    }

    /** True bila nama kelas berakhir dengan salah satu akhiran yang diberikan. */
    public static boolean matchesAnySuffix(String simple, String[] suffixes)
    {
        if (simple == null || simple.isEmpty() || suffixes == null)
        {
            return false;
        }
        for (String suffix : suffixes)
        {
            if (endsWithTokens(simple, suffix))
            {
                return true;
            }
        }
        return false;
    }
}
