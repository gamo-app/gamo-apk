# GAMO Android

Aplicación Android ligera que abre el sitio web de GAMO (`https://gamo-ekk.pages.dev/`) dentro de una vista WebView. Si el dispositivo no tiene conexión, muestra una pantalla local con la opción de reintentar.

## Requisitos

- Android Studio (recomendado) con Android SDK 36 instalado.
- JDK 17, requerido por Android Gradle Plugin 8.9.1.
- Para instalar la app: dispositivo o emulador Android 6.0 (API 23) o posterior.
- Conexión a Internet para descargar dependencias de Gradle y cargar el sitio.

## Abrir y ejecutar

1. Clona el repositorio y ábrelo en Android Studio:

   ```bash
   git clone https://github.com/TU-USUARIO/TU-REPOSITORIO.git
   cd TU-REPOSITORIO
   ```

2. En Android Studio, selecciona **Open** y elige la carpeta del proyecto. Espera a que termine la sincronización de Gradle.
3. Conecta un dispositivo con la depuración USB habilitada o inicia un emulador.
4. Pulsa **Run** (▶) para compilar e instalar la aplicación.

También puedes compilar desde la terminal en la raíz del proyecto:

```bash
# Windows
./gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`. Puedes instalarlo en un dispositivo conectado con:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Uso

- Abre **GAMO** desde el dispositivo. La aplicación carga el sitio configurado en `MainActivity.java`.
- Se requiere acceso a Internet. Sin conexión aparece la pantalla local de desconexión; al recuperar Internet, la aplicación intenta cargar el sitio. También se puede usar el botón de reintento de esa pantalla.
- El botón **Atrás** vuelve a la página anterior dentro de la WebView; si no hay una página anterior, sale de la aplicación.

## Cámara, imágenes y notificaciones

La aplicación expone funciones nativas al sitio web a través del puente JavaScript `AndroidApp` y de la WebView:

- **Selección de imágenes y cámara en formularios**: cualquier `<input type="file">` del sitio abre un selector con las opciones **Galería** y **Cámara**. El resultado (incluida la foto tomada) se entrega al input como una URI válida. Funciona también con `accept="image/*"`, `capture` y selección múltiple.
- **Cámara en vivo (`getUserMedia`)**: el sitio puede solicitar la cámara con `navigator.mediaDevices.getUserMedia({ video: true })`. La app pide el permiso de Android la primera vez y lo concede a la WebView. También se puede consultar con `AndroidApp.tienePermisoCamara()`.
- **Notificaciones**: la app crea el canal `Avisos de GAMO`, declara `POST_NOTIFICATIONS` y solicita el permiso en Android 13 o superior la primera vez que el sitio lo pide.

### API disponible para el sitio

El sitio puede usar la API estándar del navegador; la app la adapta a notificaciones nativas:

```js
// Pide permiso (Android 13+) y crea la notificación del sistema.
if (Notification.permission !== 'granted') {
  await Notification.requestPermission();
}

new Notification('Título', { body: 'Contenido del aviso' });
```

También están disponibles los métodos directos del puente:

```js
// Notificación nativa inmediata (sin pasar por la API del navegador).
AndroidApp.mostrarNotificacion('Título', 'Contenido del aviso');

// Estado del permiso: "granted", "default" o "denied".
AndroidApp.estadoNotificaciones();

// Solicita permiso; al responder se llama a window.__gamoNotifResultado(id, estado).
AndroidApp.pedirPermisoNotificaciones('mi-id');

// true si el permiso de cámara ya está concedido.
AndroidApp.tienePermisoCamara();
```

Las fotos temporales de la cámara se guardan en la caché de la app (`capturas/`) y se eliminan automáticamente después de 24 horas.

## Configuración

- URL del sitio: constante `SITE_URL` en `app/src/main/java/com/gamoekk/app/MainActivity.java`.
- Pantalla sin conexión: `app/src/main/assets/sinconexion.html` y `app/src/main/assets/sininternet.svg`.
- Identificador Android: `com.gamoekk.app`.
- Versión: `versionCode 1` y `versionName 1.0` por defecto en `app/build.gradle`. GitHub Actions las sobrescribe con el número de ejecución (`1.0.<run_number>`).

## Descargar la app desde GitHub

Cada vez que se hace `push` a `main`, GitHub Actions compila el APK y lo publica en **Releases**:

- Página de descargas: <https://github.com/gamo-app/gamo-apk/releases>
- Enlace directo a la última versión: <https://github.com/gamo-app/gamo-apk/releases/latest/download/gamo.apk>

En la pestaña **Actions** también queda el APK de cada compilación como *artifact* (requiere sesión de GitHub para descargarlo).

## Actualizaciones automáticas

Al abrir la app se consulta el archivo `version.json` publicado en la última release. Si su `versionCode` es mayor que el instalado, se muestra una pantalla con la versión nueva, una nota y el botón **Descargar actualización**. La pantalla es HTML (`app/src/main/assets/actualizacion.html`) y usa el mismo `offline.css` que las demás pantallas locales, con `appcelular.svg` al centro.

1. La app descarga `gamo.apk` con el gestor de descargas del sistema.
2. Si es la primera vez, Android pide permitir la instalación desde GAMO.
3. Al terminar, se abre el instalador y la app se actualiza conservando los datos.

El botón **Ahora no** oculta el aviso para esa versión; con un `versionCode` mayor vuelve a aparecer.

### Firma estable (necesaria para actualizar)

Android solo instala una actualización si está firmada con la **misma clave** que la versión instalada. Como cada ejecución de GitHub Actions empieza de cero, hay que configurar una clave fija en el repositorio:

1. Genera un keystore una sola vez (guárdalo fuera del repositorio y consérvalo):

   ```bash
   keytool -genkeypair -v -keystore release.jks -alias gamo -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Obtén el keystore en base64:

   ```powershell
   # Windows PowerShell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
   ```

   ```bash
   # macOS / Linux
   base64 -w0 release.jks
   ```

3. En GitHub, ve a **Settings > Secrets and variables > Actions** y crea estos secrets:

   | Secret | Valor |
   | --- | --- |
   | `RELEASE_KEYSTORE_BASE64` | el contenido base64 del keystore |
   | `RELEASE_STORE_PASSWORD` | contraseña del keystore |
   | `RELEASE_KEY_ALIAS` | `gamo` |
   | `RELEASE_KEY_PASSWORD` | contraseña de la clave |

Sin estos secrets el workflow sigue funcionando, pero firma con la clave de depuración: el APK se instala, aunque no podrá actualizar sobre una versión anterior.

El workflow está en `.github/workflows/build-apk.yml`. Usa `github.run_number` como `versionCode` y `1.0.<run_number>` como `versionName`, así que cada push genera una versión nueva.

## Crear una versión de distribución

Para generar un APK de pruebas:

```bash
./gradlew assembleDebug
```

Para generar un APK de release firmado localmente, define las propiedades del keystore:

```bash
./gradlew assembleRelease \
  -PRELEASE_STORE_FILE=release.jks \
  -PRELEASE_STORE_PASSWORD=tu_clave \
  -PRELEASE_KEY_ALIAS=gamo \
  -PRELEASE_KEY_PASSWORD=tu_clave
```

Para publicar en Google Play, genera un **Android App Bundle** firmado desde Android Studio (**Build > Generate Signed Bundle / APK**). Protege la clave de firma y sus contraseñas: no las agregues al repositorio.

## Versión Flutter (Android e iOS)

La carpeta `gamo_flutter/` contiene una versión Flutter equivalente que compila para Android
e iOS con un solo código. Consulta `gamo_flutter/README.md` para ejecutarla y exportarla.
