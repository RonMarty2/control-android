package com.rnd.remoto.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Philips JointSpace API v1 (older, non-Android Philips TVs) - plain HTTP, no auth, port 1925.
 * Newer Android-based Philips TVs are Google-certified Android TV devices and should be added
 * as an "Android TV" device instead (this class doesn't implement JointSpace v6's HMAC pairing).
 */
class PhilipsClient(
    private val ip: String,
    private val port: Int = 1925
) : RemoteController {

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    override suspend fun send(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val key = mapKey(command) ?: error("Comando no soportado por este TV Philips")
            val body = JSONObject().put("key", key).toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("http://$ip:$port/1/input/key")
                .post(body)
                .build()
            http.newCall(request).execute().use {
                if (!it.isSuccessful) error("El TV Philips respondió HTTP ${it.code}")
            }
        }
    }

    private fun mapKey(command: RemoteCommand): String? = when (command) {
        RemoteCommand.POWER -> "Standby"
        RemoteCommand.VOLUME_UP -> "VolumeUp"
        RemoteCommand.VOLUME_DOWN -> "VolumeDown"
        RemoteCommand.MUTE -> "Mute"
        RemoteCommand.CHANNEL_UP -> "ChannelStepUp"
        RemoteCommand.CHANNEL_DOWN -> "ChannelStepDown"
        RemoteCommand.UP -> "CursorUp"
        RemoteCommand.DOWN -> "CursorDown"
        RemoteCommand.LEFT -> "CursorLeft"
        RemoteCommand.RIGHT -> "CursorRight"
        RemoteCommand.SELECT -> "Confirm"
        RemoteCommand.BACK -> "Back"
        RemoteCommand.HOME -> "Home"
        RemoteCommand.PLAY_PAUSE -> "PlayPause"
    }
}
