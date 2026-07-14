package com.rnd.remoto.wallpaper

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Selected photo/video wallpapers are copied into app-private storage under a fixed filename.
 * Android's Photo Picker only grants a temporary read permission for the content Uri it returns,
 * so anything that needs to survive beyond the current picker session (in particular the live
 * wallpaper service, which may run long after the picker call returned) needs its own copy.
 */
object WallpaperFiles {
    private const val PHOTO_FILENAME = "wallpaper_photo"
    private const val VIDEO_FILENAME = "wallpaper_video.mp4"

    fun photoFile(context: Context): File = File(context.filesDir, PHOTO_FILENAME)

    fun videoFile(context: Context): File = File(context.filesDir, VIDEO_FILENAME)

    fun copyToInternal(context: Context, uri: Uri, destination: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se pudo leer el archivo seleccionado")
    }
}
