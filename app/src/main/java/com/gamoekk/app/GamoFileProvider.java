package com.gamoekk.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Provider mínimo que expone únicamente archivos temporales de la app:
 * - cacheDir/capturas: fotos tomadas con la cámara para los inputs de archivo.
 * - externalFilesDir/Download: APK descargado para instalar actualizaciones.
 *
 * Se usa para entregar URIs content:// válidas, ya que desde Android 7 no se
 * permiten URIs file:// entre aplicaciones.
 */
public class GamoFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.gamoekk.app.archivos";

    private static final String CARPETA_CAPTURAS = "capturas";
    private static final String SEGMENTO_DESCARGAS = "descargas";

    public static Uri getUriForFile(Context context, File archivo) {
        return construirUri(CARPETA_CAPTURAS, archivo.getName());
    }

    public static Uri getUriForDescarga(Context context, File archivo) {
        return construirUri(SEGMENTO_DESCARGAS, archivo.getName());
    }

    private static Uri construirUri(String segmento, String nombre) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(segmento)
                .appendPath(nombre)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        List<String> segmentos = uri.getPathSegments();

        if (segmentos.size() >= 1
                && SEGMENTO_DESCARGAS.equals(segmentos.get(0))) {
            return "application/vnd.android.package-archive";
        }

        return "image/jpeg";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        File archivo = resolverArchivo(uri);
        int flags = mode != null && mode.contains("w")
                ? ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_TRUNCATE
                        | ParcelFileDescriptor.MODE_WRITE_ONLY
                : ParcelFileDescriptor.MODE_READ_ONLY;

        return ParcelFileDescriptor.open(archivo, flags);
    }

    private File resolverArchivo(Uri uri) throws FileNotFoundException {
        Context contexto = getContext();
        if (contexto == null) {
            throw new FileNotFoundException("Contexto no disponible");
        }

        List<String> segmentos = uri.getPathSegments();
        if (segmentos.isEmpty()) {
            throw new FileNotFoundException("Ruta no válida");
        }

        String nombre = segmentos.get(segmentos.size() - 1);
        if (!nombreValido(nombre)) {
            throw new FileNotFoundException("Nombre no válido");
        }

        File carpeta;

        if (segmentos.size() >= 2
                && SEGMENTO_DESCARGAS.equals(segmentos.get(0))) {
            carpeta = contexto.getExternalFilesDir(
                    Environment.DIRECTORY_DOWNLOADS
            );
            if (carpeta == null) {
                throw new FileNotFoundException("Almacenamiento no disponible");
            }
        } else {
            carpeta = new File(contexto.getCacheDir(), CARPETA_CAPTURAS);
        }

        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw new FileNotFoundException("No se pudo crear la carpeta");
        }

        return new File(carpeta, nombre);
    }

    private boolean nombreValido(String nombre) {
        return nombre != null
                && !nombre.isEmpty()
                && !nombre.contains("/")
                && !nombre.contains("\\")
                && !nombre.contains("..");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
