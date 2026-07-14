package com.rnd.remoto.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder

private const val TAG = "VideoWallpaper"

/** Plays the user's chosen video (copied to [WallpaperFiles.videoFile]) on a silent loop as a live wallpaper. */
class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = VideoEngine()

    private inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            val file = WallpaperFiles.videoFile(this@VideoWallpaperService)
            if (!file.exists()) {
                Log.w(TAG, "No hay video de wallpaper guardado")
                return
            }
            runCatching {
                val player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setSurface(holder.surface)
                    isLooping = true
                    setVolume(0f, 0f)
                    prepare()
                }
                mediaPlayer = player
                if (isVisible) player.start()
            }.onFailure { Log.e(TAG, "No se pudo preparar el video de wallpaper", it) }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            val player = mediaPlayer ?: return
            runCatching {
                if (visible) player.start() else player.pause()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            releasePlayer()
        }

        override fun onDestroy() {
            super.onDestroy()
            releasePlayer()
        }

        private fun releasePlayer() {
            mediaPlayer?.let { runCatching { it.release() } }
            mediaPlayer = null
        }
    }
}

object VideoWallpaperLauncher {
    fun changeLiveWallpaperIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, VideoWallpaperService::class.java)
            )
        }
}
