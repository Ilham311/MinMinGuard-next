package tw.fatminmin.xposed.minminguard.next.core.hook;

/**
 * Badan hook yang dipanggil sebelum method asli.
 * <p>
 * Melempar exception dari sini harus diperlakukan sebagai kegagalan hook itu
 * saja, bukan kegagalan seluruh pemasangan — aplikasi target tidak boleh
 * terpengaruh.
 */
public interface HookCallback
{
    void before(HookContext context) throws Throwable;

    /** Dipanggil setelah method asli selesai. Default: tidak melakukan apa pun. */
    default void after(HookContext context) throws Throwable
    {
    }
}
