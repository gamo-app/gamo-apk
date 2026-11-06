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

## Configuración

- URL del sitio: constante `SITE_URL` en `app/src/main/java/com/gamoekk/app/MainActivity.java`.
- Pantalla sin conexión: `app/src/main/assets/sinconexion.html` y `app/src/main/assets/sininternet.svg`.
- Identificador Android: `com.gamoekk.app`.
- Versión actual: `1.0` (código `1`), definida en `app/build.gradle`.

## Crear una versión de distribución

Para generar un APK de pruebas:

```bash
./gradlew assembleDebug
```

Para publicar en Google Play, genera un **Android App Bundle** firmado desde Android Studio (**Build > Generate Signed Bundle / APK**). Protege la clave de firma y sus contraseñas: no las agregues al repositorio.

## Versión Flutter (Android e iOS)

La carpeta `gamo_flutter/` contiene una versión Flutter equivalente que compila para Android
e iOS con un solo código. Consulta `gamo_flutter/README.md` para ejecutarla y exportarla.
