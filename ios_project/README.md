# GamoEkkIOS

Port nativo iOS de la aplicación Android `com.gamoekk.app` (Java + WebView),
preparado para compilarse con [iOS-Builder](https://github.com/MobAI-App/ios-builder).

La app Android original carga `https://gamo-ekk.pages.dev/` en un WebView y expone
un puente JavaScript llamado `AndroidApp`. Las pantallas locales
(`sinconexion.html`, `qrsinconexion.html`, `actualizacion.html`) y `offline.css`
se mantienen sincronizadas con `app/src/main/assets`.

## Estructura

```
ios_project/
├── GamoEkkIOS.xcodeproj/
│   ├── project.pbxproj
│   └── xcshareddata/xcschemes/GamoEkkIOS.xcscheme
└── GamoEkkIOS/
    ├── Info.plist
    ├── Sources/
    │   ├── AppDelegate.swift
    │   ├── AppConfig.swift
    │   ├── MainViewController.swift
    │   ├── WebViewBridge.swift
    │   ├── NetworkMonitor.swift
    │   └── NotificationController.swift
    └── Resources/
        ├── Assets.xcassets/
        ├── LaunchScreen.storyboard
        └── WebAssets/            (assets originales, sin cambios)
```

`builder.json` (en la raíz del repositorio) apunta a:

- `ios.path`: `ios_project`
- `ios.scheme`: `GamoEkkIOS`
- `ios.bundleId`: `com.gamoekk.app`
- `ios.configuration`: `Debug`

## Correspondencia Android → iOS

| Android (MainActivity.java) | iOS (este proyecto) |
|---|---|
| `WebView` + `WebSettings` | `WKWebView` + `WKWebViewConfiguration` |
| `addJavascriptInterface(obj, "AndroidApp")` | `WKScriptMessageHandler` + shim JS que expone `window.AndroidApp.*` |
| `WebViewClient` (onPageStarted/onPageFinished/onReceivedError) | `WKNavigationDelegate` |
| `WebChromeClient.onShowFileChooser` | `WKUIDelegate.runOpenPanelWith` |
| `onPermissionRequest` (cámara) | `WKUIDelegate.requestMediaCapturePermissionFor` + `AVCaptureDevice` |
| `DownloadManager` + `FileProvider` (APK) | Abrir la URL de actualización en el sistema |
| `SharedPreferences "GamoPrefs"` | `UserDefaults` (mismas claves) |
| `NotificationChannel` + `NotificationManager` | `UNUserNotificationCenter` |
| `ConnectivityManager.NetworkCallback` | `NWPathMonitor` |
| `setNavigationBarColor` + `SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR` | color de fondo de la vista + `preferredStatusBarStyle` |
| `onBackPressed` → `webView.goBack()` | `allowsBackForwardNavigationGestures` (gesto de retroceso) |
| `file:///android_asset/...` | `Bundle.main` (`WebAssets/...`) con `loadFileURL` |

### API JavaScript conservada

El shim define exactamente los métodos usados por el sitio y por los HTML locales:

`setNavigationBackground`, `guardarIdUsuario`, `guardarUsuarioSesion`,
`limpiarUsuarioSesion`, `abrirQrSinConexion`, `mostrarNotificacion`,
`estadoNotificaciones`, `pedirPermisoNotificaciones`, `tienePermisoCamara`,
`comprobarActualizacion`, `retry`, `obtenerUsuarioSesion`, `volverSinConexion`,
`descargarActualizacion`, `omitirActualizacion`.

Los métodos que en Android devolvían un valor de forma sincrónica
(`estadoNotificaciones`, `tienePermisoCamara`, `obtenerUsuarioSesion`) se
responden desde un estado cacheado en JS (`window.__gamoNotifState`,
`window.__gamoCamPerm`, `window.__gamoUsuarioSesion`), actualizado por el nativo.
Esto es necesario porque `WKScriptMessageHandler` es asincrónico.

Se conservan también los scripts inyectados del original:

- `DYNAMIC_BACKGROUND_JS` (observa el color de fondo y llama a
  `setNavigationBackground`).
- `NOTIFICATION_SHIM_JS` (reemplaza `window.Notification` y usa
  `mostrarNotificacion` / `pedirPermisoNotificaciones`).

## Adaptaciones inevitables de plataforma

Estas no eliminan funcionalidades de la app, sino que cambian el mecanismo por
límites de iOS:

1. **Actualizaciones**: iOS no permite descargar e instalar un APK desde la app.
   Se conserva la pantalla `actualizacion.html` y la comprobación de
   `version.json`; el botón de descarga abre la URL de actualización en el
   sistema (App Store / TestFlight / web). Si `version.json` incluye `iosUrl`,
   se usa esa; si la URL termina en `.apk`, se abre el sitio para evitar un
   enlace inútil.
2. **Cookies de terceros**: Android habilita `setAcceptThirdPartyCookies`.
   WKWebView no expone un equivalente público; se usa el comportamiento
   predeterminado de `WKWebsiteDataStore.default()`.
3. **Permisos**: se declaran `NSCameraUsageDescription`,
   `NSPhotoLibraryUsageDescription`, `NSPhotoLibraryAddUsageDescription` y
   `NSMicrophoneUsageDescription` en `Info.plist`.
4. **Selector de archivos**: en iOS se muestra una hoja con Cámara, Fototeca y
   Archivos (equivalente al chooser de Android con cámara + contenido).

## Compilar con iOS-Builder

Requisitos: `builder` instalado y sesión de GitHub (`builder auth github`).

```bash
# En la raíz del repositorio (donde está builder.json)
builder init          # crea/actualiza el workflow si aún no existe
builder signing setup --distribution development --device <UDID> --yes
builder ios build     # compila y descarga el IPA en ./dist/
```

El workflow `.github/workflows/ios-build.yml` ya está incluido. El runner hace
`cd ios_project`, detecta `GamoEkkIOS.xcodeproj`, usa el scheme `GamoEkkIOS` y
ejecuta `xcodebuild`.
