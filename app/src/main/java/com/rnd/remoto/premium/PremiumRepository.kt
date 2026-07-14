package com.rnd.remoto.premium

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.premiumDataStore by preferencesDataStore(name = "premium_store")

data class AppState(
    val isPremium: Boolean = false,
    val photoWallpaperUri: String? = null,
    val videoWallpaperUri: String? = null,
    val hasUsedFreePhotoSlot: Boolean = false,
    val hasUsedFreeVideoSlot: Boolean = false,
    val lockScreenControlsEnabled: Boolean = false,
    val lastUsedDeviceId: String? = null
)

/** Local state for the one-time "premium" unlock and the wallpaper/lock-screen features it gates. */
class PremiumRepository(private val context: Context) {

    private object Keys {
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val PHOTO_WALLPAPER_URI = stringPreferencesKey("photo_wallpaper_uri")
        val VIDEO_WALLPAPER_URI = stringPreferencesKey("video_wallpaper_uri")
        val USED_FREE_PHOTO = booleanPreferencesKey("used_free_photo")
        val USED_FREE_VIDEO = booleanPreferencesKey("used_free_video")
        val LOCK_SCREEN_CONTROLS = booleanPreferencesKey("lock_screen_controls")
        val LAST_USED_DEVICE_ID = stringPreferencesKey("last_used_device_id")
    }

    val state: Flow<AppState> = context.premiumDataStore.data.map { prefs ->
        AppState(
            isPremium = prefs[Keys.IS_PREMIUM] ?: false,
            photoWallpaperUri = prefs[Keys.PHOTO_WALLPAPER_URI],
            videoWallpaperUri = prefs[Keys.VIDEO_WALLPAPER_URI],
            hasUsedFreePhotoSlot = prefs[Keys.USED_FREE_PHOTO] ?: false,
            hasUsedFreeVideoSlot = prefs[Keys.USED_FREE_VIDEO] ?: false,
            lockScreenControlsEnabled = prefs[Keys.LOCK_SCREEN_CONTROLS] ?: false,
            lastUsedDeviceId = prefs[Keys.LAST_USED_DEVICE_ID]
        )
    }

    suspend fun setPremium(value: Boolean) {
        context.premiumDataStore.edit { it[Keys.IS_PREMIUM] = value }
    }

    suspend fun setPhotoWallpaper(uri: String) {
        context.premiumDataStore.edit {
            it[Keys.PHOTO_WALLPAPER_URI] = uri
            it[Keys.USED_FREE_PHOTO] = true
        }
    }

    suspend fun setVideoWallpaper(uri: String) {
        context.premiumDataStore.edit {
            it[Keys.VIDEO_WALLPAPER_URI] = uri
            it[Keys.USED_FREE_VIDEO] = true
        }
    }

    suspend fun setLockScreenControlsEnabled(enabled: Boolean) {
        context.premiumDataStore.edit { it[Keys.LOCK_SCREEN_CONTROLS] = enabled }
    }

    suspend fun setLastUsedDeviceId(deviceId: String) {
        context.premiumDataStore.edit { it[Keys.LAST_USED_DEVICE_ID] = deviceId }
    }
}
