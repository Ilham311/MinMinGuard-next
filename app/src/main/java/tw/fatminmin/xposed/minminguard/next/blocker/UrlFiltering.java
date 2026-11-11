package tw.fatminmin.xposed.minminguard.next.blocker;

import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import de.robv.android.xposed.XC_MethodHook;
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

                    Util.notifyAdNetwork(null, packageName, "WebView");
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
                    Util.notifyAdNetwork(null, packageName, "WebView");
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
                    Util.notifyAdNetwork(null, packageName, "WebView");
                    param.setResult(null);
                }
            }
        };
    }

    /**
     * Membungkus setiap WebViewClient yang dipasang aplikasi. WebViewClient
     * adalah kelas biasa (bukan final), jadi menurunkan kelasnya di sini
     * cukup, tanpa hook tambahan.
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
                        WebViewClient client = (WebViewClient) param.args[0];
                        if (client == null || client instanceof BlockingWebViewClient)
                        {
                            return;
                        }
                        param.args[0] = new BlockingWebViewClient(client, packageName);
                    }
                }
        });
    }

    /**
     * Delegasi yang mengembalikan respons kosong untuk permintaan iklan dan
     * meneruskan sisanya ke client asli.
     */
    private static final class BlockingWebViewClient extends WebViewClient
    {
        private final WebViewClient mDelegate;
        private final String mPackageName;

        BlockingWebViewClient(WebViewClient delegate, String packageName)
        {
            mDelegate = delegate;
            mPackageName = packageName;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url)
        {
            if (Blocklist.get().isBlockedUrl(url))
            {
                Util.log(mPackageName, "shouldOverrideUrlLoading diblokir: " + url);
                Util.notifyRemoveAdView(null, mPackageName, 1);
                Util.notifyAdNetwork(null, mPackageName, "WebView");
                return true;
            }
            return mDelegate.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
        {
            if (request != null && Blocklist.get().isBlockedUrl(request.getUrl().toString()))
            {
                Util.log(mPackageName, "shouldOverrideUrlLoading diblokir");
                Util.notifyRemoveAdView(null, mPackageName, 1);
                Util.notifyAdNetwork(null, mPackageName, "WebView");
                return true;
            }
            return mDelegate.shouldOverrideUrlLoading(view, request);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url)
        {
            if (Blocklist.get().isBlockedUrl(url))
            {
                return blockedResponse(url);
            }
            return mDelegate.shouldInterceptRequest(view, url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request)
        {
            if (request != null && Blocklist.get().isBlockedUrl(request.getUrl().toString()))
            {
                return blockedResponse(request.getUrl().toString());
            }
            return mDelegate.shouldInterceptRequest(view, request);
        }

        /**
         * Respons kosong untuk sub-resource iklan; sekaligus dicatat supaya
         * banner di dalam halaman ikut terhitung pada statistik.
         */
        private WebResourceResponse blockedResponse(String url)
        {
            Util.log(mPackageName, "sub-resource diblokir: " + url);
            Util.notifyRemoveAdView(null, mPackageName, 1);
            Util.notifyAdNetwork(null, mPackageName, "WebView");
            return new WebResourceResponse(MIME_PLAIN, ENCODING, new ByteArrayInputStream(new byte[0]));
        }
    }

}
