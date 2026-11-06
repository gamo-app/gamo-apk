package com.gamoekk.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final String SITE_URL =
            "https://gamo-ekk.pages.dev/";

    private static final String DYNAMIC_BACKGROUND_JS =
            "(function(){"
            + "if(window.__themeObserverStarted)return;"
            + "window.__themeObserverStarted=true;"
            + "function update(){"
            + "var c=getComputedStyle(document.documentElement).backgroundColor;"
            + "if(!c||c==='transparent'||c==='rgba(0, 0, 0, 0)'){"
            + "c=getComputedStyle(document.body).backgroundColor;"
            + "}"
            + "if(!c||c==='transparent'||c==='rgba(0, 0, 0, 0)')return;"
            + "var m=c.match(/[\\d.]+/g);"
            + "if(m&&m.length>=3&&c.indexOf('rgb')===0){"
            + "if(m.length>=4&&Number(m[3])===0)return;"
            + "c='#'+m.slice(0,3).map(function(n){"
            + "var h=Math.max(0,Math.min(255,Math.round(Number(n)))).toString(16);"
            + "return h.length<2?'0'+h:h;"
            + "}).join('');"
            + "}"
            + "if(window.AndroidApp){"
            + "window.AndroidApp.setNavigationBackground(c);"
            + "}"
            + "}"
            + "update();"
            + "new MutationObserver(update).observe(document.documentElement,"
            + "{attributes:true,childList:true,subtree:true});"
            + "if(document.body){"
            + "new MutationObserver(update).observe(document.body,"
            + "{attributes:true,childList:true,subtree:true});"
            + "}"
            + "window.addEventListener('load',update);"
            + "window.addEventListener('pageshow',update);"
            + "document.addEventListener('visibilitychange',update);"
            + "window.setInterval(update,1000);"
            + "})();";

    private WebView webView;
    private WebView offlineView;
    private FrameLayout root;
    private FrameLayout content;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean pageLoaded;
    private int navigationColor = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();

        // Dibujar el fondo detrás de las barras del sistema.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }

        window.setStatusBarColor(Color.rgb(2, 90, 245));
        window.setNavigationBarColor(navigationColor);

        connectivityManager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        root = new FrameLayout(this);
        root.setBackgroundColor(navigationColor);

        // El contenedor ocupa la pantalla y respeta las áreas seguras.
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // Aplicar los insets al contenido, no al fondo de la ventana.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars()
                                | WindowInsets.Type.displayCutout()
                );

                content.setPadding(
                        bars.left,
                        bars.top,
                        bars.right,
                        bars.bottom
                );

                return insets;
            });
        } else {
            root.setFitsSystemWindows(true);
        }

        webView = new WebView(this);

        // La web y la API usan dominios distintos (pages.dev/workers.dev),
        // por lo que las cookies de sesión son cookies de terceros en WebView.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );

        webView.setBackgroundColor(Color.TRANSPARENT);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void setNavigationBackground(String color) {
                runOnUiThread(() -> {
                    try {
                        applyNavigationColor(Color.parseColor(color));
                    } catch (IllegalArgumentException ignored) {
                        // Ignorar valores CSS no válidos.
                    }
                });
            }

            @JavascriptInterface
            public void guardarIdUsuario(String id) {
                MainActivity.this.guardarIdUsuario(id);
            }

            @JavascriptInterface
            public void guardarUsuarioSesion(String datos) {
                MainActivity.this.guardarUsuarioSesion(datos);
            }

            @JavascriptInterface
            public void limpiarUsuarioSesion() {
                MainActivity.this.limpiarUsuarioSesion();
            }

            @JavascriptInterface
            public void abrirQrSinConexion() {
                runOnUiThread(() -> mostrarQrSinConexion());
            }

        }, "AndroidApp");

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (url.startsWith(SITE_URL)) {
                    pageLoaded = true;
                    webView.setVisibility(View.VISIBLE);
                    offlineView.setVisibility(View.GONE);

                    view.evaluateJavascript(
                            DYNAMIC_BACKGROUND_JS,
                            null
                    );
                }
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                super.onReceivedError(view, request, error);

                if (request.isForMainFrame() && !pageLoaded) {
                    showOffline();
                }
            }
        });

        content.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        offlineView = createOfflineView();
        offlineView.setVisibility(View.GONE);

        content.addView(offlineView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);

        registerNetworkCallback();

        if (hasInternet()) {
            loadSite();
        } else {
            showOffline();
        }
    }

    private void guardarIdUsuario(String id) {
        getSharedPreferences("GamoPrefs", MODE_PRIVATE)
                .edit()
                .putString("id_usuario", id)
                .apply();
    }

    private void guardarUsuarioSesion(String datos) {
        if (datos == null || datos.length() > 8192) return;

        try {
            JSONObject usuario = new JSONObject(datos);
            JSONObject perfil = new JSONObject();
            perfil.put("uuid_usuario", usuario.optString("uuid_usuario"));
            perfil.put("nombre", usuario.optString("nombre"));
            perfil.put("apellido", usuario.optString("apellido"));
            perfil.put("email", usuario.optString("email"));
            perfil.put("rol", usuario.optString("rol"));

            getSharedPreferences("GamoPrefs", MODE_PRIVATE)
                    .edit()
                    .putString("usuario_sesion", perfil.toString())
                    .putString("id_usuario", perfil.optString("uuid_usuario"))
                    .apply();
        } catch (JSONException ignored) {
            // Ignorar datos inválidos recibidos desde JavaScript.
        }
    }

    private void limpiarUsuarioSesion() {
        getSharedPreferences("GamoPrefs", MODE_PRIVATE)
                .edit()
                .remove("usuario_sesion")
                .remove("id_usuario")
                .apply();
    }

    private void applyNavigationColor(int color) {
        navigationColor = color;

        // El fondo de la ventana queda visible detrás de la barra.
        root.setBackgroundColor(color);
        getWindow().setNavigationBarColor(color);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            double luminance = (
                    0.2126 * Color.red(color)
                    + 0.7152 * Color.green(color)
                    + 0.0722 * Color.blue(color)
            ) / 255.0;

            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();

            if (luminance > 0.5) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }

            decor.setSystemUiVisibility(flags);
        }
    }

    private WebView createOfflineView() {
        WebView offlineWebView = new WebView(this);

        WebSettings settings = offlineWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        offlineWebView.setBackgroundColor(Color.TRANSPARENT);

        offlineWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void retry() {
                runOnUiThread(() -> {
                    if (hasInternet()) {
                        loadSite();
                    } else {
                        showOffline();
                    }
                });
            }

            @JavascriptInterface
            public void abrirQrSinConexion() {
                runOnUiThread(() -> mostrarQrSinConexion());
            }

            @JavascriptInterface
            public void guardarIdUsuario(String id) {
                MainActivity.this.guardarIdUsuario(id);
            }

            @JavascriptInterface
            public String obtenerUsuarioSesion() {
                return getSharedPreferences("GamoPrefs", MODE_PRIVATE)
                        .getString("usuario_sesion", "{}");
            }

            @JavascriptInterface
            public void volverSinConexion() {
                runOnUiThread(() -> offlineWebView.loadUrl(
                        "file:///android_asset/sinconexion.html"
                ));
            }

        }, "AndroidApp");

        offlineWebView.setWebChromeClient(new WebChromeClient());

        offlineWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                return true;
            }
        });

        offlineWebView.loadUrl(
                "file:///android_asset/sinconexion.html"
        );

        return offlineWebView;
    }

    private void registerNetworkCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    if (!pageLoaded) {
                        loadSite();
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> {
                    if (!hasInternet()) {
                        showOffline();
                    }
                });
            }
        };

        connectivityManager.registerNetworkCallback(
                request,
                networkCallback
        );
    }

    private boolean hasInternet() {
        if (connectivityManager == null) return false;

        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(network);

        return capabilities != null
                && capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void loadSite() {
        if (webView == null) return;

        pageLoaded = false;
        offlineView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(SITE_URL);
    }

    private void showOffline() {
        if (offlineView != null) {
            offlineView.setVisibility(View.VISIBLE);
        }

        if (webView != null) {
            webView.setVisibility(View.GONE);
        }
    }

    private void mostrarQrSinConexion() {
        if (offlineView == null) return;
        offlineView.loadUrl("file:///android_asset/qrsinconexion.html");
        offlineView.setVisibility(View.VISIBLE);
        if (webView != null) webView.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(
                        networkCallback
                );
            } catch (IllegalArgumentException ignored) {
                // Ya estaba desconectado.
            }
        }

        if (webView != null) {
            webView.destroy();
        }

        if (offlineView != null) {
            offlineView.destroy();
        }

        super.onDestroy();
    }
}
