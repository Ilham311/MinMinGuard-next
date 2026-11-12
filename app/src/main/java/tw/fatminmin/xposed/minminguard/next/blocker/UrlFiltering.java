package tw.fatminmin.xposed.minminguard.next.blocker;

import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Penyaring URL untuk aplikasi berbasis WebView (opt-in per aplikasi).
 * <p>
 * Dua lapis:
 * <ol>
 *   <li>navigasi halaman: {@code loadUrl}/{@code loadDataWithBaseURL} yang
 *       menuju domain iklan dibatalkan;</li>
 *   <li>sub-resource: semua {@link WebViewClient} yang dipasang aplikasi
 *       dibungkus sehingga {@code shouldInterceptRequest} mengembalikan
 *       respons kosong untuk permintaan iklan. Lapisan kedua ini yang
 *       benar-benar memblokir banner di dalam halaman.</li>
 * </ol>
 * Versi lama hanya memakai {@code url.contains(pattern)} untuk setiap 26 ribu
 * pola, yang lambat dan melewatkan hampir semua kasus nyata.
 */
public final class UrlFiltering
{
    private static final String MIME_PLAIN = "text/plain";
    private static final String ENCODING = "utf-8";

    private UrlFiltering() throws InstantiationException
    {
        throw new InstantiationException("This class is not for instantiation");
    }

    public static boolean removeWebViewAds(final String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        boolean hooked = false;

        hooked |= Util.hookAllMethods("android.webkit.WebView", lpparam.classLoader, "loadUrl",
                navigationHook(packageName));

        hooked |= Util.hookAllMethods("android.webkit.WebView", lpparam.classLoader, "loadData",
                dataHook(packageName));

        hooked |= Util.hookAllMethods("android.webkit.WebView", lpparam.classLoader, "loadDataWithBaseURL",
                dataWithBaseHook(packageName));

        hooked |= hookSetWebViewClient(packageName, lpparam);

        return hooked;
    }

    /** Memblokir navigasi ke domain iklan dan menghapus seluruh WebView-nya. */
    private static XC_MethodHook navigationHook(final String packageName)
    {
        return new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                if (param.args.length == 0 || !(param.args[0] instanceof String))
                {
                    return;
                }

                String url = (String) param.args[0];
                if (Blocklist.get().isBlockedUrl(url))
                {
                    Util.log(packageName, "blokir navigasi: " + url);

                    if (param.thisObject instanceof View)
                    {
                        View webView = (View) param.thisObject;
                        Util.notifyRemoveAdView(webView.getContext(), packageName, 1);
                        ViewBlocking.removeAdView(packageName, webView);
                    }
                    else
                    {
                        Util.notifyRemoveAdView(null, packageName, 1);
                    }

                    Util.countBlockedAd(null, packageName, "WebView");
                    param.setResult(null);
                }
            }
        };
    }

    private static XC_MethodHook dataHook(final String packageName)
    {
        return new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                if (param.args.length == 0 || !(param.args[0] instanceof String))
                {
                    return;
                }
                String data = (String) param.args[0];
                if (Blocklist.get().containsPattern(data) || Blocklist.get().isBlockedUrl(data))
                {
                    Util.log(packageName, "blokir loadData");
                    Util.notifyRemoveAdView(null, packageName, 1);
                    Util.countBlockedAd(null, packageName, "WebView");
                    param.setResult(null);
                }
            }
        };
    }

    private static XC_MethodHook dataWithBaseHook(final String packageName)
    {
        return new XC_MethodHook()
        {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
            {
                String url = param.args.length > 0 && param.args[0] instanceof String ? (String) param.args[0] : null;
                String data = param.args.length > 1 && param.args[1] instanceof String ? (String) param.args[1] : null;

                if (Blocklist.get().isBlockedUrl(url) || Blocklist.get().containsPattern(data))
                {
                    Util.log(packageName, "blokir loadDataWithBaseURL: " + url);
                    Util.notifyRemoveAdView(null, packageName, 1);
                    Util.countBlockedAd(null, packageName, "WebView");
                    param.setResult(null);
                }
            }
        };
    }

    /**
     * Memasang hook pada method {@link WebViewClient} milik kelas client
     * aplikasi, bukan mengganti objeknya.
     * <p>
     * Versi sebelumnya membungkus client dengan subclass yang hanya
     * meng-override empat callback. Semua callback lain — {@code onPageStarted},
     * {@code onPageFinished}, {@code onReceivedError}, {@code onLoadResource},
     * {@code doUpdateVisitedHistory}, dan seterusnya — ikut hilang, sehingga
     * progres halaman yang ditunggu aplikasi tidak pernah dilaporkan dan
     * halaman bisa tampak menggantung. Dengan hook per method, perilaku asli
     * tetap utuh; hanya URL iklan yang dipotong.
     */
    private static boolean hookSetWebViewClient(final String packageName, XC_LoadPackage.LoadPackageParam lpparam)
    {
        final ClassLoader classLoader = lpparam.classLoader;

        return Util.hookMethod("android.webkit.WebView", classLoader, "setWebViewClient", new Object[]{
                WebViewClient.class,
                new XC_MethodHook()
                {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param)
                    {
                        if (param.args.length == 0 || !(param.args[0] instanceof WebViewClient))
                        {
                            return;
                        }
                        hookClientClass(((WebViewClient) param.args[0]).getClass(), packageName);
                    }
                }
        });
    }

    /** Memasang hook sekali per kelas client; aplikasi sering memasang yang sama berkali-kali. */
    private static void hookClientClass(Class<?> clientClass, String packageName)
    {
        if (clientClass == null || clientClass == WebViewClient.class)
        {
            // Client bawaan: hook pada WebViewClient akan kena semua subclass,
            // termasuk milik framework. Cukup kelas milik aplikasi.
            return;
        }

        String key = "webviewclient:" + clientClass.getName();
        if (!Util.markHookedOnce(key))
        {
            return;
        }

        hookClientMethod(clientClass, "shouldOverrideUrlLoading", packageName);
        hookClientMethod(clientClass, "shouldInterceptRequest", packageName);
    }

    private static void hookClientMethod(Class<?> clientClass, String methodName, final String packageName)
    {
        // Lewat Util, bukan XposedBridge.hookAllMethods langsung: versi bridge
        // hanya melihat getDeclaredMethods() kelas ini dan melempar bila salah
        // satu overload abstrak, sehingga WebView client turunan (mis. dari
        // SDK iklan) tidak tersentuh sama sekali.
        Util.hookAllMethods(clientClass, methodName, new XC_MethodHook()
            {
                @Override
                protected void afterHookedMethod(MethodHookParam param)
                {
                    // Signature yang di-hook:
                    //   shouldOverrideUrlLoading(WebView, String)
                    //   shouldOverrideUrlLoading(WebView, WebResourceRequest)
                    //   shouldInterceptRequest(WebView, String)
                    //   shouldInterceptRequest(WebView, WebResourceRequest)
                    // URL selalu argumen kedua.
                    String url = param.args.length > 1 ? urlOf(param.args[1]) : null;
                    if (url == null || !Blocklist.get().isBlockedUrl(url))
                    {
                        return;
                    }

                    Class<?> returnType = Util.returnTypeOfMethod(param);
                    if (returnType == WebResourceResponse.class)
                    {
                        // Respons kosong: sumber daya iklan tidak pernah diunduh.
                        param.setResult(new WebResourceResponse(MIME_PLAIN, ENCODING,
                                new ByteArrayInputStream(new byte[0])));
                    }
                    else if (returnType == boolean.class || returnType == Boolean.class)
                    {
                        // Menahan navigasi ke domain iklan.
                        param.setResult(Boolean.TRUE);
                    }
                    else
                    {
                        // Method lain: biarkan berjalan, jangan dicatat sebagai blokir.
                        return;
                    }

                    Util.log(packageName, "WebView diblokir: " + url);
                    Util.notifyRemoveAdView(null, packageName, 1);
                    Util.countBlockedAd(null, packageName, "WebView");
                }
            });
    }

    /** Mengambil URL dari argumen {@code String} atau {@link WebResourceRequest}. */
    private static String urlOf(Object arg)
    {
        if (arg instanceof String)
        {
            return (String) arg;
        }
        if (arg instanceof WebResourceRequest)
        {
            android.net.Uri uri = ((WebResourceRequest) arg).getUrl();
            return uri == null ? null : uri.toString();
        }
        return null;
    }
}
