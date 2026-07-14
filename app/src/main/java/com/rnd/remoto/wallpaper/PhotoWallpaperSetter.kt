package com.rnd.remoto.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhotoWallpaperSetter {
    suspend fun setPhotoWallpaper(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val destination = WallpaperFiles.photoFile(context)
            WallpaperFiles.copyToInternal(context, uri, destination)
            destination.inputStream().use { stream ->
                WallpaperManager.getInstance(context).setStream(stream)
            }
            Unit
        }
    }
}
