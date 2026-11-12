package com.gamoekk.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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

    private static final String CANAL_NOTIFICACIONES = "gamo_general";

    private static final String UPDATE_VERSION_URL =
            "https://github.com/gamo-app/gamo-apk/"
                    + "releases/latest/download/version.json";

    private static final String UPDATE_APK_URL =
            "https://github.com/gamo-app/gamo-apk/"
                    + "releases/latest/download/gamo.apk";

    private static final String NOMBRE_APK_ACTUALIZACION =
            "gamo-actualizacion.apk";

    private static final int REQ_SELECCION_ARCHIVO = 1001;
    private static final int REQ_PERMISO_CAMARA = 1002;
    private static final int REQ_PERMISO_NOTIFICACIONES = 1003;

    private static final String NOTIFICATION_SHIM_JS =
            "(function(){"
            + "if(window.__gamoNotifShim)return;"
            + "if(!window.AndroidApp"
            + "||typeof window.AndroidApp.mostrarNotificacion!=='function')return;"
            + "window.__gamoNotifShim=true;"
            + "var pendientes={};"
            + "function crear(titulo,opciones){"
            + "opciones=opciones||{};"
            + "this.title=titulo==null?'':String(titulo);"
            + "this.body=opciones.body==null?'':String(opciones.body);"
            + "this.tag=opciones.tag;"
            + "this.onclick=null;this.onclose=null;this.onerror=null;"
            + "var self=this;"
            + "setTimeout(function(){try{"
            + "AndroidApp.mostrarNotificacion(self.title,self.body);"
            + "}catch(e){}},0);"
            + "}"
            + "crear.requestPermission=function(cb){"
            + "return new Promise(function(resolve){"
            + "var id='n'+Date.now()+Math.floor(Math.random()*100000);"
            + "pendientes[id]=function(r){resolve(r);"
            + "if(typeof cb==='function')cb(r);};"
            + "try{AndroidApp.pedirPermisoNotificaciones(id);}"
            + "catch(e){delete pendientes[id];resolve('denied');}"
            + "});"
            + "};"
            + "window.__gamoNotifResultado=function(id,r){"
            + "var f=pendientes[id];if(!f)return;delete pendientes[id];f(r);"
            + "};"
            + "try{Object.defineProperty(crear,'permission',{"
            + "configurable:true,get:function(){"
            + "try{return AndroidApp.estadoNotificaciones();}"
            + "catch(e){return 'default';}"
            + "}});}catch(e){}"
            + "try{Object.defineProperty(window,'Notification',{"
            + "configurable:true,writable:true,value:crear});"
            + "}catch(e){try{window.Notification=crear;}catch(e2){}}"
            + "})();";

    private WebView webView;
    private WebView offlineView;
    private FrameLayout root;
    private FrameLayout content;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private ValueCallback<Uri[]> seleccionArchivoCallback;
    private Uri uriFotoCamara;

    private PermissionRequest permisoWebPendiente;
    private String callbackPermisoNotificaciones;

    private View pantallaActualizacion;
    private WebView updateView;
    private int codigoActualizacion;
    private String versionNameActualizacion;
    private String notasActualizacion;
    private String urlActualizacion;
    private String urlActualizacionPendiente;
    private long idDescargaActualizacion = -1;
    private BroadcastReceiver receptorDescarga;
    private boolean comprobandoActualizacion;

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

        crearCanalNotificaciones();

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

            @JavascriptInterface
            public void mostrarNotificacion(String titulo, String cuerpo) {
                runOnUiThread(() ->
                        MainActivity.this.mostrarNotificacion(titulo, cuerpo));
            }

            @JavascriptInterface
            public String estadoNotificaciones() {
                return MainActivity.this.estadoNotificaciones();
            }

            @JavascriptInterface
            public void pedirPermisoNotificaciones(String callbackId) {
                runOnUiThread(() ->
                        solicitarPermisoNotificaciones(callbackId));
            }

            @JavascriptInterface
            public boolean tienePermisoCamara() {
                return tienePermiso(Manifest.permission.CAMERA);
            }

            @JavascriptInterface
            public void comprobarActualizacion() {
                runOnUiThread(MainActivity.this::comprobarActualizacion);
            }

        }, "AndroidApp");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                return abrirSelectorArchivos(
                        filePathCallback,
                        fileChooserParams
                );
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> atenderPermisoWeb(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (permisoWebPendiente == request) {
                    permisoWebPendiente = null;
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {
                super.onPageStarted(view, url, favicon);
                inyectarShimNotificaciones(view);
            }

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

                inyectarShimNotificaciones(view);
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
            comprobarActualizacion();
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

    private void comprobarActualizacion() {
        if (comprobandoActualizacion) return;
        comprobandoActualizacion = true;

        Thread hilo = new Thread(() -> {
            JSONObject info = descargarInfoActualizacion();
            comprobandoActualizacion = false;

            if (info == null) return;

            int codigoRemoto = info.optInt("versionCode", 0);
            if (codigoRemoto <= obtenerVersionCode()) return;

            int ignorada = getSharedPreferences("GamoPrefs", MODE_PRIVATE)
                    .getInt("version_ignorada", 0);
            if (ignorada >= codigoRemoto) return;

            String nombre = info.optString(
                    "versionName",
                    String.valueOf(codigoRemoto)
            );
            String notas = info.optString("notes", "");
            String url = info.optString("url", UPDATE_APK_URL);
            if (url.isEmpty()) url = UPDATE_APK_URL;

            String urlFinal = url;

            runOnUiThread(() -> mostrarPantallaActualizacion(
                    codigoRemoto,
                    nombre,
                    notas,
                    urlFinal
            ));
        }, "gamo-comprobar-actualizacion");

        hilo.start();
    }

    private JSONObject descargarInfoActualizacion() {
        HttpURLConnection conexion = null;

        try {
            URL url = new URL(UPDATE_VERSION_URL);
            conexion = (HttpURLConnection) url.openConnection();
            conexion.setConnectTimeout(6000);
            conexion.setReadTimeout(6000);
            conexion.setInstanceFollowRedirects(true);
            conexion.setRequestProperty("Accept", "application/json");
            conexion.setRequestProperty("Cache-Control", "no-cache");

            if (conexion.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            StringBuilder texto = new StringBuilder();

            try (BufferedReader lector = new BufferedReader(
                    new InputStreamReader(
                            conexion.getInputStream(),
                            "UTF-8"
                    )
            )) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    texto.append(linea);
                }
            }

            return new JSONObject(texto.toString());
        } catch (Exception e) {
            return null;
        } finally {
            if (conexion != null) conexion.disconnect();
        }
    }

    @SuppressWarnings("deprecation")
    private int obtenerVersionCode() {
        try {
            PackageInfo info = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) info.getLongVersionCode();
            }

            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private void mostrarPantallaActualizacion(
            final int codigo,
            String nombre,
            String notas,
            final String url
    ) {
        if (isFinishing() || content == null || pantallaActualizacion != null) {
            return;
        }

        codigoActualizacion = codigo;
        versionNameActualizacion = nombre;
        notasActualizacion = notas;
        urlActualizacion = url;

        WebView vista = new WebView(this);

        WebSettings ajustes = vista.getSettings();
        ajustes.setJavaScriptEnabled(true);
        ajustes.setDomStorageEnabled(true);
        ajustes.setAllowFileAccess(true);

        vista.setBackgroundColor(Color.parseColor("#121C2E"));

        vista.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void descargarActualizacion() {
                runOnUiThread(() ->
                        iniciarActualizacion(urlActualizacion));
            }

            @JavascriptInterface
            public void omitirActualizacion() {
                runOnUiThread(() -> {
                    getSharedPreferences("GamoPrefs", MODE_PRIVATE)
                            .edit()
                            .putInt("version_ignorada", codigoActualizacion)
                            .apply();

                    cerrarPantallaActualizacion();
                });
            }
        }, "AndroidApp");

        vista.setWebChromeClient(new WebChromeClient());

        vista.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                super.onPageFinished(view, pageUrl);
                enviarDatosActualizacion(view);
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                return true;
            }
        });

        updateView = vista;
        pantallaActualizacion = vista;

        vista.loadUrl("file:///android_asset/actualizacion.html");

        content.addView(vista, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    private void enviarDatosActualizacion(WebView vista) {
        if (vista == null) return;

        try {
            JSONObject datos = new JSONObject();
            datos.put(
                    "versionName",
                    versionNameActualizacion == null
                            ? ""
                            : versionNameActualizacion
            );
            datos.put(
                    "notes",
                    notasActualizacion == null ? "" : notasActualizacion
            );

            vista.evaluateJavascript(
                    "window.mostrarActualizacion(" + datos + ");",
                    null
            );
        } catch (JSONException ignored) {
            // Sin datos: la pantalla muestra los valores por defecto.
        }
    }

    private void cerrarPantallaActualizacion() {
        if (pantallaActualizacion == null) return;

        ViewGroup padre = (ViewGroup) pantallaActualizacion.getParent();
        if (padre != null) {
            padre.removeView(pantallaActualizacion);
        }

        if (updateView != null) {
            updateView.destroy();
            updateView = null;
        }

        pantallaActualizacion = null;
    }

    private void habilitarDescargaActualizacion() {
        if (updateView == null) return;

        updateView.evaluateJavascript(
                "window.actualizacionError && "
                        + "window.actualizacionError('');",
                null
        );
    }

    private void marcarDescargaEnCurso() {
        if (updateView == null) return;

        updateView.evaluateJavascript(
                "window.actualizacionDescargando && "
                        + "window.actualizacionDescargando();",
                null
        );
    }

    private void avisarErrorActualizacion(String mensaje) {
        if (updateView != null) {
            String js = "window.actualizacionError && "
                    + "window.actualizacionError("
                    + JSONObject.quote(mensaje)
                    + ");";
            updateView.evaluateJavascript(js, null);
        }

        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
    }

    private boolean necesitaPermisoInstalacion() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls();
    }

    private void iniciarActualizacion(String url) {
        if (necesitaPermisoInstalacion()) {
            urlActualizacionPendiente = url;
            habilitarDescargaActualizacion();
            mostrarDialogoPermisoInstalacion();
            return;
        }

        marcarDescargaEnCurso();

        if (!descargarEInstalar(url)) {
            avisarErrorActualizacion("No se pudo iniciar la descarga.");
        }
    }

    private void mostrarDialogoPermisoInstalacion() {
        new AlertDialog.Builder(this)
                .setTitle("Permiso necesario")
                .setMessage(
                        "Android necesita tu permiso para instalar la "
                                + "actualización. Activa «Permitir desde esta "
                                + "fuente» y vuelve a la aplicación."
                )
                .setPositiveButton("Abrir ajustes", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())
                        );
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(
                                this,
                                "Abre los ajustes y permite instalar apps "
                                        + "desde GAMO.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    urlActualizacionPendiente = null;
                    habilitarDescargaActualizacion();
                })
                .setOnCancelListener(dialog -> {
                    urlActualizacionPendiente = null;
                    habilitarDescargaActualizacion();
                })
                .show();
    }

    private boolean descargarEInstalar(String url) {
        DownloadManager manager = (DownloadManager)
                getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) return false;

        try {
            borrarApkDescargado();

            DownloadManager.Request solicitud =
                    new DownloadManager.Request(Uri.parse(url));
            solicitud.setTitle("Actualización de GAMO");
            solicitud.setDescription("Descargando la nueva versión…");
            solicitud.setMimeType(
                    "application/vnd.android.package-archive"
            );
            solicitud.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            solicitud.setDestinationInExternalFilesDir(
                    this,
                    Environment.DIRECTORY_DOWNLOADS,
                    NOMBRE_APK_ACTUALIZACION
            );

            idDescargaActualizacion = manager.enqueue(solicitud);
            registrarReceptorDescarga();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private File archivoApkDescargado() {
        File carpeta = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (carpeta == null) return null;

        return new File(carpeta, NOMBRE_APK_ACTUALIZACION);
    }

    private void borrarApkDescargado() {
        File apk = archivoApkDescargado();
        if (apk != null && apk.exists()) {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
        }
    }

    private void registrarReceptorDescarga() {
        if (receptorDescarga != null) return;

        receptorDescarga = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID,
                        -1L
                );

                if (id != idDescargaActualizacion) return;

                idDescargaActualizacion = -1;
                instalarApkDescargado();
            }
        };

        IntentFilter filtro = new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                    receptorDescarga,
                    filtro,
                    Context.RECEIVER_EXPORTED
            );
        } else {
            registerReceiver(receptorDescarga, filtro);
        }
    }

    private void instalarApkDescargado() {
        File apk = archivoApkDescargado();

        if (apk == null || !apk.exists() || apk.length() == 0) {
            avisarErrorActualizacion(
                    "No se pudo descargar la actualización."
            );
            return;
        }

        Uri uri = GamoFileProvider.getUriForDescarga(this, apk);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(
                uri,
                "application/vnd.android.package-archive"
        );
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK
        );
        intent.setClipData(ClipData.newRawUri("apk", uri));

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Intent alterno = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            alterno.setData(uri);
            alterno.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                startActivity(alterno);
            } catch (Exception sinInstalador) {
                Toast.makeText(
                        this,
                        "No se encontró el instalador de Android.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private void inyectarShimNotificaciones(WebView view) {
        if (view == null) return;

        try {
            view.evaluateJavascript(NOTIFICATION_SHIM_JS, null);
        } catch (Exception ignored) {
            // La página aún no está lista; se reintenta en onPageFinished.
        }
    }

    private boolean abrirSelectorArchivos(
            ValueCallback<Uri[]> callback,
            WebChromeClient.FileChooserParams params
    ) {
        if (seleccionArchivoCallback != null) {
            seleccionArchivoCallback.onReceiveValue(null);
        }

        seleccionArchivoCallback = callback;
        uriFotoCamara = null;

        if (callback == null) return true;

        String[] aceptados = params != null ? params.getAcceptTypes() : null;
        String mime = obtenerMime(aceptados);
        boolean multiples = params != null
                && params.getMode()
                == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
        boolean usarCamara = (params != null && params.isCaptureEnabled())
                || admiteCamara(mime);

        Intent contenido = new Intent(Intent.ACTION_GET_CONTENT);
        contenido.addCategory(Intent.CATEGORY_OPENABLE);
        contenido.setType(mime);
        contenido.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiples);

        Intent selector = Intent.createChooser(
                contenido,
                "Seleccionar archivo"
        );

        Intent camara = usarCamara ? crearIntentCamara() : null;
        if (camara != null) {
            selector.putExtra(
                    Intent.EXTRA_INITIAL_INTENTS,
                    new Intent[]{camara}
            );
        }

        try {
            startActivityForResult(selector, REQ_SELECCION_ARCHIVO);
        } catch (ActivityNotFoundException e) {
            seleccionArchivoCallback = null;
            uriFotoCamara = null;
            callback.onReceiveValue(null);
        }

        return true;
    }

    private String obtenerMime(String[] aceptados) {
        if (aceptados == null || aceptados.length == 0) return "*/*";

        for (String tipo : aceptados) {
            if (tipo == null) continue;

            for (String parte : tipo.split(",")) {
                String limpio = parte.trim();
                if (limpio.isEmpty()) continue;
                if (limpio.contains("/")) return limpio;
                if (limpio.startsWith(".")) return "*/*";
            }
        }

        return "*/*";
    }

    private boolean admiteCamara(String mime) {
        return mime == null
                || mime.isEmpty()
                || "*/*".equals(mime)
                || mime.startsWith("image/");
    }

    private Intent crearIntentCamara() {
        try {
            File carpeta = new File(getCacheDir(), "capturas");
            if (!carpeta.exists() && !carpeta.mkdirs()) return null;

            limpiarCapturasAntiguas(carpeta);

            File foto = new File(
                    carpeta,
                    "foto_" + System.currentTimeMillis() + ".jpg"
            );

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) == null) {
                return null;
            }

            uriFotoCamara = GamoFileProvider.getUriForFile(
                    this,
                    foto
            );

            intent.putExtra(MediaStore.EXTRA_OUTPUT, uriFotoCamara);
            intent.addFlags(
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            return intent;
        } catch (Exception e) {
            uriFotoCamara = null;
            return null;
        }
    }

    private void limpiarCapturasAntiguas(File carpeta) {
        File[] archivos = carpeta.listFiles();
        if (archivos == null) return;

        long limite = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;

        for (File archivo : archivos) {
            if (archivo.isFile() && archivo.lastModified() < limite) {
                //noinspection ResultOfMethodCallIgnored
                archivo.delete();
            }
        }
    }

    private Uri[] extraerUris(Intent data) {
        if (data == null) {
            return uriFotoCamara != null
                    ? new Uri[]{uriFotoCamara}
                    : null;
        }

        if (data.getClipData() != null) {
            int total = data.getClipData().getItemCount();
            List<Uri> uris = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }

            return uris.isEmpty() ? null : uris.toArray(new Uri[0]);
        }

        if (data.getData() != null) {
            return new Uri[]{data.getData()};
        }

        return uriFotoCamara != null
                ? new Uri[]{uriFotoCamara}
                : null;
    }

    private boolean contieneUri(Uri[] uris, Uri buscada) {
        if (uris == null || buscada == null) return false;

        for (Uri uri : uris) {
            if (buscada.equals(uri)) return true;
        }

        return false;
    }

    private void eliminarFotoCamara() {
        if (uriFotoCamara == null) return;

        try {
            String nombre = uriFotoCamara.getLastPathSegment();
            if (nombre == null) return;

            File foto = new File(new File(getCacheDir(), "capturas"), nombre);
            if (foto.exists()) {
                //noinspection ResultOfMethodCallIgnored
                foto.delete();
            }
        } catch (Exception ignored) {
            // Nada que limpiar.
        }
    }

    private void atenderPermisoWeb(PermissionRequest request) {
        if (request == null) return;

        boolean pideCamara = false;
        for (String recurso : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(recurso)) {
                pideCamara = true;
                break;
            }
        }

        if (!pideCamara) {
            request.deny();
            return;
        }

        if (tienePermiso(Manifest.permission.CAMERA)) {
            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else {
            permisoWebPendiente = request;
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    REQ_PERMISO_CAMARA
            );
        }
    }

    private boolean tienePermiso(String permiso) {
        return checkSelfPermission(permiso)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean tienePermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return tienePermiso(Manifest.permission.POST_NOTIFICATIONS);
    }

    private String estadoNotificaciones() {
        if (tienePermisoNotificaciones()) return "granted";

        boolean pedido = getSharedPreferences("GamoPrefs", MODE_PRIVATE)
                .getBoolean("notif_pedido", false);

        if (!pedido) return "default";

        return shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS)
                ? "default"
                : "denied";
    }

    private void solicitarPermisoNotificaciones(String callbackId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || tienePermisoNotificaciones()) {
            if (callbackId != null && !callbackId.isEmpty()) {
                enviarResultadoNotificacionJS(
                        callbackId,
                        estadoNotificaciones()
                );
            }
            return;
        }

        callbackPermisoNotificaciones = callbackId;

        getSharedPreferences("GamoPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("notif_pedido", true)
                .apply();

        requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQ_PERMISO_NOTIFICACIONES
        );
    }

    private void enviarResultadoNotificacionJS(
            String callbackId,
            String estado
    ) {
        if (webView == null || callbackId == null) return;

        String js = "window.__gamoNotifResultado&&"
                + "window.__gamoNotifResultado("
                + JSONObject.quote(callbackId)
                + ","
                + JSONObject.quote(estado)
                + ");";

        webView.evaluateJavascript(js, null);
    }

    private void crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel canal = new NotificationChannel(
                CANAL_NOTIFICACIONES,
                "Avisos de GAMO",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        canal.setDescription(
                "Notificaciones enviadas por la aplicación GAMO."
        );

        manager.createNotificationChannel(canal);
    }

    private void mostrarNotificacion(String titulo, String cuerpo) {
        String tituloFinal = titulo == null ? "" : titulo.trim();
        if (tituloFinal.isEmpty()) tituloFinal = "GAMO";

        String cuerpoFinal = cuerpo == null ? "" : cuerpo;

        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        crearCanalNotificaciones();

        Intent abrirApp = new Intent(this, MainActivity.class);
        abrirApp.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent contenido = PendingIntent.getActivity(
                this,
                0,
                abrirApp,
                flags
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CANAL_NOTIFICACIONES);
        } else {
            builder = new Notification.Builder(this);
            builder.setPriority(Notification.PRIORITY_DEFAULT);
        }

        builder.setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle(tituloFinal)
                .setContentText(cuerpoFinal)
                .setStyle(new Notification.BigTextStyle().bigText(cuerpoFinal))
                .setAutoCancel(true)
                .setContentIntent(contenido);

        try {
            manager.notify(
                    (int) (System.currentTimeMillis() & 0x7fffffff),
                    builder.build()
            );
        } catch (SecurityException ignored) {
            // El usuario no ha concedido el permiso de notificaciones.
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        if (requestCode == REQ_SELECCION_ARCHIVO) {
            ValueCallback<Uri[]> callback = seleccionArchivoCallback;
            seleccionArchivoCallback = null;

            if (callback == null) return;

            Uri[] resultados = resultCode == RESULT_OK
                    ? extraerUris(data)
                    : null;

            if (resultados == null || resultados.length == 0) {
                eliminarFotoCamara();
            } else if (uriFotoCamara != null
                    && !contieneUri(resultados, uriFotoCamara)) {
                eliminarFotoCamara();
            }

            callback.onReceiveValue(resultados);
            uriFotoCamara = null;
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == REQ_PERMISO_CAMARA) {
            PermissionRequest pendiente = permisoWebPendiente;
            permisoWebPendiente = null;

            if (pendiente == null) return;

            try {
                if (tienePermiso(Manifest.permission.CAMERA)) {
                    pendiente.grant(
                            new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}
                    );
                } else {
                    pendiente.deny();
                }
            } catch (Exception ignored) {
                // La solicitud ya no está activa.
            }
        } else if (requestCode == REQ_PERMISO_NOTIFICACIONES) {
            String callbackId = callbackPermisoNotificaciones;
            callbackPermisoNotificaciones = null;

            if (callbackId != null) {
                enviarResultadoNotificacionJS(
                        callbackId,
                        estadoNotificaciones()
                );
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (urlActualizacionPendiente != null
                && !necesitaPermisoInstalacion()) {
            String url = urlActualizacionPendiente;
            urlActualizacionPendiente = null;

            marcarDescargaEnCurso();

            if (!descargarEInstalar(url)) {
                avisarErrorActualizacion(
                        "No se pudo iniciar la descarga."
                );
            }
        } else if (urlActualizacionPendiente != null) {
            habilitarDescargaActualizacion();
        }
    }

    @Override
    public void onBackPressed() {
        if (pantallaActualizacion != null) {
            return;
        }

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

        if (updateView != null) {
            updateView.destroy();
            updateView = null;
        }

        pantallaActualizacion = null;

        if (seleccionArchivoCallback != null) {
            seleccionArchivoCallback.onReceiveValue(null);
            seleccionArchivoCallback = null;
        }

        if (permisoWebPendiente != null) {
            try {
                permisoWebPendiente.deny();
            } catch (Exception ignored) {
                // La solicitud ya no está activa.
            }
            permisoWebPendiente = null;
        }

        callbackPermisoNotificaciones = null;
        uriFotoCamara = null;

        if (receptorDescarga != null) {
            try {
                unregisterReceiver(receptorDescarga);
            } catch (IllegalArgumentException ignored) {
                // Ya estaba desregistrado.
            }
            receptorDescarga = null;
        }

        super.onDestroy();
    }
}
