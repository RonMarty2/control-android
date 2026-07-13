package com.rnd.remoto.network

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Samsung Tizen "smart.remote.control" websocket protocol (wss, port 8002).
 * Samsung TVs serve this endpoint with a self-signed certificate, so this
 * client trusts it explicitly. Only use this against a TV IP on your own
 * local network.
 */
class SamsungClient(
    private val ip: String,
    private var token: String?,
    private val onTokenReceived: (String) -> Unit,
    appName: String = "ControlRemoto"
) : RemoteController {

    private val encodedName = Base64.encodeToString(appName.toByteArray(), Base64.NO_WRAP)
    private val http: OkHttpClient = trustingLocalTvClient()

    private var socket: WebSocket? = null
    private var ready = false

    private suspend fun ensureConnected(): Boolean {
        if (ready && socket != null) return true
        val connected = CompletableDeferred<Boolean>()
        val tokenParam = if (!token.isNullOrEmpty()) "&token=$token" else ""
        val url = "wss://$ip:8002/api/v2/channels/samsung.remote.control?name=$encodedName$tokenParam"

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (json.optString("event") == "ms.channel.connect") {
                    val newToken = json.optJSONObject("data")?.optString("token")
                    if (!newToken.isNullOrEmpty()) {
                        token = newToken
                        onTokenReceived(newToken)
                    }
                    if (!connected.isCompleted) connected.complete(true)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!connected.isCompleted) connected.complete(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ready = false
            }
        }

        socket = http.newWebSocket(Request.Builder().url(url).build(), listener)
        ready = connected.await()
        return ready
    }

    override suspend fun send(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!ensureConnected()) {
                error("No se pudo conectar al TV Samsung. Revisa que esté encendido y acepta el permiso en su pantalla.")
            }
            val key = mapKey(command) ?: error("Comando no soportado")
            val payload = JSONObject().apply {
                put("method", "ms.remote.control")
                put("params", JSONObject().apply {
                    put("Cmd", "Click")
                    put("DataOfCmd", key)
                    put("Option", "false")
                    put("TypeOfRemote", "SendRemoteKey")
                })
            }
            socket?.send(payload.toString()) ?: error("Socket no disponible")
        }
    }

    override fun close() {
        socket?.close(1000, null)
    }

    private fun mapKey(command: RemoteCommand): String? = when (command) {
        RemoteCommand.POWER -> "KEY_POWER"
        RemoteCommand.VOLUME_UP -> "KEY_VOLUP"
        RemoteCommand.VOLUME_DOWN -> "KEY_VOLDOWN"
        RemoteCommand.MUTE -> "KEY_MUTE"
        RemoteCommand.CHANNEL_UP -> "KEY_CHUP"
        RemoteCommand.CHANNEL_DOWN -> "KEY_CHDOWN"
        RemoteCommand.UP -> "KEY_UP"
        RemoteCommand.DOWN -> "KEY_DOWN"
        RemoteCommand.LEFT -> "KEY_LEFT"
        RemoteCommand.RIGHT -> "KEY_RIGHT"
        RemoteCommand.SELECT -> "KEY_ENTER"
        RemoteCommand.BACK -> "KEY_RETURN"
        RemoteCommand.HOME -> "KEY_HOME"
        RemoteCommand.PLAY_PAUSE -> "KEY_PLAY"
    }

    private fun trustingLocalTvClient(): OkHttpClient {
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
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
