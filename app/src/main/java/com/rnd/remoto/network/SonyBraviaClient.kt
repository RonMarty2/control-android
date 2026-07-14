package com.rnd.remoto.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sony Bravia local REST/IRCC API. Requires the user to enable "IP Control" and set a
 * Pre-Shared Key on the TV (Settings > Network > Home Network Setup > IP Control), and enter
 * that same key here. Instead of hardcoding IRCC codes (which vary by model), this queries the
 * TV's own getRemoteControllerInfo for the exact codes it supports.
 */
class SonyBraviaClient(
    private val ip: String,
    private val psk: String,
    private val port: Int = 80
) : RemoteController {

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private var irccCodes: Map<String, String>? = null

    private suspend fun ensureIrccCodes(): Map<String, String> {
        irccCodes?.let { return it }

        val payload = JSONObject().apply {
            put("id", 1)
            put("method", "getRemoteControllerInfo")
            put("version", "1.0")
            put("params", JSONArray())
        }
        val request = Request.Builder()
            .url("http://$ip:$port/sony/system")
            .addHeader("X-Auth-PSK", psk)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val codes = mutableMapOf<String, String>()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("No se pudo consultar los códigos del TV (HTTP ${response.code})")
            val json = JSONObject(response.body?.string().orEmpty())
            val result = json.optJSONArray("result") ?: error("Respuesta inesperada del TV Sony")
            val buttons = result.optJSONArray(1) ?: error("El TV no devolvió su lista de botones")
            for (i in 0 until buttons.length()) {
                val entry = buttons.getJSONObject(i)
                codes[entry.getString("name").lowercase()] = entry.getString("value")
            }
        }
        irccCodes = codes
        return codes
    }

    override suspend fun send(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val codes = ensureIrccCodes()
            val candidates = candidateNames(command)
            val ircc = candidates.firstNotNullOfOrNull { codes[it.lowercase()] }
                ?: error("Este TV Sony no expone un botón para: ${candidates.first()}")

            val soapBody = """<?xml version="1.0" encoding="utf-8"?>
                |<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                |<s:Body>
                |<u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>$ircc</IRCCCode></u:X_SendIRCC>
                |</s:Body>
                |</s:Envelope>""".trimMargin()

            val request = Request.Builder()
                .url("http://$ip:$port/sony/IRCC")
                .addHeader("X-Auth-PSK", psk)
                .addHeader("SOAPAction", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
                .post(soapBody.toRequestBody("text/xml; charset=UTF-8".toMediaType()))
                .build()

            http.newCall(request).execute().use {
                if (!it.isSuccessful) error("El TV Sony respondió HTTP ${it.code}")
            }
        }
    }

    private fun candidateNames(command: RemoteCommand): List<String> = when (command) {
        RemoteCommand.POWER -> listOf("Power", "PowerOff", "TvPower")
        RemoteCommand.VOLUME_UP -> listOf("VolumeUp")
        RemoteCommand.VOLUME_DOWN -> listOf("VolumeDown")
        RemoteCommand.MUTE -> listOf("Mute")
        RemoteCommand.CHANNEL_UP -> listOf("ChannelUp")
        RemoteCommand.CHANNEL_DOWN -> listOf("ChannelDown")
        RemoteCommand.UP -> listOf("Up")
        RemoteCommand.DOWN -> listOf("Down")
        RemoteCommand.LEFT -> listOf("Left")
        RemoteCommand.RIGHT -> listOf("Right")
        RemoteCommand.SELECT -> listOf("Confirm", "Enter")
        RemoteCommand.BACK -> listOf("Return", "Back")
        RemoteCommand.HOME -> listOf("Home")
        RemoteCommand.PLAY_PAUSE -> listOf("Play", "Pause")
    }
}
