package com.rnd.remoto.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Roku External Control Protocol (ECP) - plain HTTP, no auth, port 8060 by default. */
class RokuClient(
    private val ip: String,
    private val port: Int = 8060
) : RemoteController {

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    override suspend fun send(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val key = mapKey(command) ?: error("Comando no soportado por Roku")
            val request = Request.Builder()
                .url("http://$ip:$port/keypress/$key")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            http.newCall(request).execute().use {
                if (!it.isSuccessful) error("Roku respondió HTTP ${it.code}")
            }
        }
    }

    private fun mapKey(command: RemoteCommand): String? = when (command) {
        RemoteCommand.POWER -> "Power"
        RemoteCommand.VOLUME_UP -> "VolumeUp"
        RemoteCommand.VOLUME_DOWN -> "VolumeDown"
        RemoteCommand.MUTE -> "VolumeMute"
        RemoteCommand.CHANNEL_UP -> "ChannelUp"
        RemoteCommand.CHANNEL_DOWN -> "ChannelDown"
        RemoteCommand.UP -> "Up"
        RemoteCommand.DOWN -> "Down"
        RemoteCommand.LEFT -> "Left"
        RemoteCommand.RIGHT -> "Right"
        RemoteCommand.SELECT -> "Select"
        RemoteCommand.BACK -> "Back"
        RemoteCommand.HOME -> "Home"
        RemoteCommand.PLAY_PAUSE -> "Play"
    }
}
