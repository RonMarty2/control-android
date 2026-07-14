package com.rnd.remoto.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Vizio SmartCast pairing (PIN shown on TV), based on the public Vizio_SmartCast_API docs. */
class VizioPairingClient(private val ip: String, private val port: Int = 7345) {

    private val http = buildTrustAllTvClient()
    private var pairingReqToken: Long = 0

    suspend fun startPairing(deviceId: String, deviceName: String = "Control Remoto"): Result<Unit> = runCatching {
        val body = JSONObject().apply {
            put("DEVICE_NAME", deviceName)
            put("DEVICE_ID", deviceId)
        }
        val json = post("/pairing/start", body) ?: error("El TV no respondió al iniciar el emparejamiento")
        val item = json.optJSONObject("ITEM") ?: error("Respuesta inesperada del TV Vizio")
        pairingReqToken = item.optLong("PAIRING_REQ_TOKEN")
    }

    suspend fun submitPin(deviceId: String, pin: String): Result<String> = runCatching {
        val body = JSONObject().apply {
            put("DEVICE_ID", deviceId)
            put("CHALLENGE_TYPE", 1)
            put("RESPONSE_VALUE", pin.trim())
            put("PAIRING_REQ_TOKEN", pairingReqToken)
        }
        val json = post("/pairing/pair", body) ?: error("El TV no respondió al confirmar el PIN")
        val item = json.optJSONObject("ITEM") ?: error("PIN incorrecto o el emparejamiento expiró")
        item.optString("AUTH_TOKEN").ifEmpty { error("El TV no devolvió un token de autenticación") }
    }

    private fun post(path: String, body: JSONObject): JSONObject? {
        val request = Request.Builder()
            .url("https://$ip:$port$path")
            .put(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string()
            if (!response.isSuccessful || text.isNullOrBlank()) return null
            return JSONObject(text)
        }
    }
}

internal fun buildTrustAllTvClient(): OkHttpClient {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier(HostnameVerifier { _, _ -> true })
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
}
