package com.rnd.remoto.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Vizio SmartCast remote control, once already paired (see VizioPairingClient). */
class VizioClient(
    private val ip: String,
    private val authToken: String,
    private val port: Int = 7345
) : RemoteController {

    private val http: OkHttpClient = buildTrustAllTvClient()

    override suspend fun send(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (command == RemoteCommand.POWER) {
                sendPowerToggle()
            } else {
                val (codeset, code) = mapKey(command) ?: error("Comando no soportado por este TV Vizio")
                sendKey(codeset, code)
            }
        }
    }

    private fun sendPowerToggle() {
        val isOn = runCatching { currentlyOn() }.getOrDefault(true)
        sendKey(codeset = 11, code = if (isOn) 0 else 1)
    }

    private fun currentlyOn(): Boolean {
        val request = Request.Builder()
            .url("https://$ip:$port/state/device/power_mode")
            .addHeader("AUTH", authToken)
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: error("Sin respuesta del TV Vizio")
            val items = JSONObject(text).optJSONArray("ITEMS") ?: error("Respuesta inesperada")
            return items.optJSONObject(0)?.optInt("VALUE", 1) != 0
        }
    }

    private fun sendKey(codeset: Int, code: Int) {
        val keyEntry = JSONObject().apply {
            put("CODESET", codeset)
            put("CODE", code)
            put("ACTION", "KEYPRESS")
        }
        val body = JSONObject().apply {
            put("KEYLIST", JSONArray().put(keyEntry))
        }
        val request = Request.Builder()
            .url("https://$ip:$port/key_command/")
            .addHeader("AUTH", authToken)
            .put(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use {
            if (!it.isSuccessful) error("El TV Vizio respondió HTTP ${it.code}")
        }
    }

    private fun mapKey(command: RemoteCommand): Pair<Int, Int>? = when (command) {
        RemoteCommand.VOLUME_UP -> 5 to 1
        RemoteCommand.VOLUME_DOWN -> 5 to 0
        RemoteCommand.MUTE -> 5 to 4
        RemoteCommand.CHANNEL_UP -> 8 to 1
        RemoteCommand.CHANNEL_DOWN -> 8 to 0
        else -> null
    }
}
