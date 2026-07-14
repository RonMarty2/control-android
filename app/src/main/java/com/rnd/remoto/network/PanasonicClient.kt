package com.rnd.remoto.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Panasonic Viera "p00NetworkControl" SOAP API, port 55000 - only the plain (non-encrypted)
 * variant used by most Viera Smart TVs. Newer models that require the encrypted pairing flow
 * will fail with a clear HTTP error instead of silently doing nothing.
 */
class PanasonicClient(
    private val ip: String,
    private val port: Int = 55000
) : RemoteController {

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    override suspend fun send(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val key = mapKey(command) ?: error("Comando no soportado por este TV Panasonic")
            val soapBody = """<?xml version="1.0" encoding="utf-8"?>
                |<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                |<s:Body>
                |<m:X_SendKey xmlns:m="urn:panasonic-com:service:p00NetworkControl:1"><X_KeyEvent>$key</X_KeyEvent></m:X_SendKey>
                |</s:Body>
                |</s:Envelope>""".trimMargin()

            val request = Request.Builder()
                .url("http://$ip:$port/nrc/control_0")
                .addHeader("SOAPAction", "\"urn:panasonic-com:service:p00NetworkControl:1#X_SendKey\"")
                .post(soapBody.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            http.newCall(request).execute().use {
                if (!it.isSuccessful) {
                    error(
                        if (it.code == 500) "El TV Panasonic requiere emparejamiento cifrado (modelo no soportado)"
                        else "El TV Panasonic respondió HTTP ${it.code}"
                    )
                }
            }
        }
    }

    private fun mapKey(command: RemoteCommand): String? = when (command) {
        RemoteCommand.POWER -> "NRC_POWER-ONOFF"
        RemoteCommand.VOLUME_UP -> "NRC_VOLUP-ONOFF"
        RemoteCommand.VOLUME_DOWN -> "NRC_VOLDOWN-ONOFF"
        RemoteCommand.MUTE -> "NRC_MUTE-ONOFF"
        RemoteCommand.CHANNEL_UP -> "NRC_CH_UP-ONOFF"
        RemoteCommand.CHANNEL_DOWN -> "NRC_CH_DOWN-ONOFF"
        RemoteCommand.UP -> "NRC_UP-ONOFF"
        RemoteCommand.DOWN -> "NRC_DOWN-ONOFF"
        RemoteCommand.LEFT -> "NRC_LEFT-ONOFF"
        RemoteCommand.RIGHT -> "NRC_RIGHT-ONOFF"
        RemoteCommand.SELECT -> "NRC_ENTER-ONOFF"
        RemoteCommand.BACK -> "NRC_RETURN-ONOFF"
        RemoteCommand.HOME -> "NRC_HOME-ONOFF"
        RemoteCommand.PLAY_PAUSE -> "NRC_PLAY-ONOFF"
    }
}
