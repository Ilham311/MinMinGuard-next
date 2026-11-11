package tw.fatminmin.xposed.minminguard.next.blocker;

import java.net.UnknownHostException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Pemblokiran tingkat DNS (opt-in per aplikasi).
 * <p>
 * Bila host tujuan ada di daftar, resolusi nama digagalkan dengan
 * {@link UnknownHostException}. SDK iklan menangani kegagalan ini sebagai
 * "tidak ada koneksi", sehingga seluruh jalur (HTTP, HTTPS, socket mentah)
 * tertutup tanpa perlu mengenali library yang dipakai.
 * <p>
 * Hanya dipasang pada aplikasi yang secara eksplisit mengaktifkannya, karena
 * menggagalkan resolusi nama bisa memengaruhi fungsi selain iklan.
 * <p>
 * Kelas ini menggantikan versi lama yang {@code @Deprecated}, tidak pernah
 * dipanggil, dan mencocokkan URL dengan 26 ribu pola.
 */
public final class HostBlock
{
    private HostBlock() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    public static boolean install(final String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        if (Blocklist.get().size() == 0)
        {
            return false;
        }

        boolean hooked = false;

        // Semua jalur resolusi publik bermuara pada dua method ini.
        hooked |= hook("getByName", packageName, lpparam);
        hooked |= hook("getAllByName", packageName, lpparam);

        // Android 8+ memakai varian dengan parameter Network untuk query
        // per-jaringan; signature-nya berbeda antar versi sehingga di-hook
        // berdasarkan nama saja.
        Util.hookAllMethods("java.net.InetAddress", lpparam.classLoader, "getByNameOnNet",
                onNetHook(packageName));
        Util.hookAllMethods("java.net.InetAddress", lpparam.classLoader, "getAllByNameOnNet",
                onNetHook(packageName));

        return hooked;
    }

    private static boolean hook(String methodName, final String packageName,
                                XC_LoadPackage.LoadPackageParam lpparam)
    {
        return Util.hookMethod("java.net.InetAddress", lpparam.classLoader, methodName, new Object[]{
                String.class,
                new XC_MethodHook()
                {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable
                    {
                        if (param.args.length == 0 || !(param.args[0] instanceof String))
                        {
                            return;
                        }
                        // Dicek sebelum resolusi: kueri DNS untuk host iklan tidak
                        // perlu dikirim ke jaringan sama sekali.
                        String host = (String) param.args[0];
                        if (isBlocked(packageName, host))
                        {
                            param.setThrowable(new UnknownHostException(host));
                        }
                    }
                }
        });
    }

    private static XC_MethodHook onNetHook(final String packageName)
    {
        return new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable
            {
                if (param.args.length == 0 || !(param.args[0] instanceof String))
                {
                    return;
                }
                String host = (String) param.args[0];
                if (isBlocked(packageName, host))
                {
                    param.setThrowable(new UnknownHostException(host));
                }
            }
        };
    }

    private static boolean isBlocked(String packageName, String host)
    {
        if (host == null || !Blocklist.get().isBlockedHost(host))
        {
            return false;
        }

        Util.log(packageName, "DNS diblokir: " + host);
        Util.notifyRemoveAdView(null, packageName, 1);
        Util.notifyAdNetwork(null, packageName, "DNS");
        return true;
    }
}
