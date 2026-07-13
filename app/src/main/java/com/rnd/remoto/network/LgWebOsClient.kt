package com.rnd.remoto.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LG webOS second-screen protocol: a control websocket (port 3000) used to
 * register/pair, plus a second "pointer input" websocket used to send button
 * presses as plain "type:button\nname:KEY\n\n" frames.
 *
 * On first use the TV shows an on-screen "Allow/Deny" prompt; the client-key
 * returned afterwards must be persisted (see onClientKeyReceived) to skip
 * that prompt on future connections.
 */
class LgWebOsClient(
    private val ip: String,
    private var clientKey: String?,
    private val onClientKeyReceived: (String) -> Unit
) : RemoteController {

    private val http = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    private var mainSocket: WebSocket? = null
    private var pointerSocket: WebSocket? = null
    private var connected = false

    private suspend fun ensureConnected(): Boolean {
        if (connected && pointerSocket != null) return true

        val registered = CompletableDeferred<Boolean>()
        val pointerUrl = CompletableDeferred<String?>()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (json.optString("type")) {
                    "registered" -> {
                        val key = json.optJSONObject("payload")?.optString("client-key")
                        if (!key.isNullOrEmpty()) {
                            clientKey = key
                            onClientKeyReceived(key)
                        }
                        if (!registered.isCompleted) registered.complete(true)
                        webSocket.send(pointerSocketRequestPayload())
                    }
                    "response" -> {
                        val socketPath = json.optJSONObject("payload")?.optString("socketPath")
                        if (!socketPath.isNullOrEmpty() && !pointerUrl.isCompleted) {
                            pointerUrl.complete(socketPath)
                        }
                    }
                    "error" -> {
                        if (!registered.isCompleted) registered.complete(false)
                        if (!pointerUrl.isCompleted) pointerUrl.complete(null)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!registered.isCompleted) registered.complete(false)
                if (!pointerUrl.isCompleted) pointerUrl.complete(null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
            }
        }

        mainSocket = http.newWebSocket(Request.Builder().url("ws://$ip:3000").build(), listener)
        mainSocket?.send(registerPayload())

        if (!registered.await()) return false
        val path = pointerUrl.await() ?: return false

        val pointerReady = CompletableDeferred<Boolean>()
        val pointerListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                pointerReady.complete(true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!pointerReady.isCompleted) pointerReady.complete(false)
            }
        }
        pointerSocket = http.newWebSocket(Request.Builder().url(path).build(), pointerListener)
        connected = pointerReady.await()
        return connected
    }

    override suspend fun send(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!ensureConnected()) {
                error("No se pudo conectar al TV LG. Revisa que esté encendido y acepta el permiso en su pantalla.")
            }
            val name = mapButton(command) ?: error("Comando no soportado")
            pointerSocket?.send("type:button\nname:$name\n\n") ?: error("Socket no disponible")
        }
    }

    override fun close() {
        pointerSocket?.close(1000, null)
        mainSocket?.close(1000, null)
    }

    private fun registerPayload(): String {
        val manifest = JSONObject().apply {
            put("manifestVersion", 1)
            put("appVersion", "1.1")
            put(
                "permissions",
                listOf(
                    "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO",
                    "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_POWER",
                    "READ_INSTALLED_APPS", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK",
                    "CONTROL_INPUT_MEDIA_RECORDING", "CONTROL_INPUT_TEXT",
                    "CONTROL_MOUSE_AND_KEYBOARD", "READ_CURRENT_CHANNEL", "READ_RUNNING_APPS",
                    "READ_UPDATE_INFO", "UPDATE_FROM_REMOTE_APP", "READ_LGE_TV_INPUT_EVENTS",
                    "READ_TV_CURRENT_TIME"
                )
            )
        }
        val payload = JSONObject().apply {
            put("forcePairing", false)
            put("pairingType", "PROMPT")
            put("manifest", manifest)
            if (!clientKey.isNullOrEmpty()) put("client-key", clientKey)
        }
        return JSONObject().apply {
            put("type", "register")
            put("id", "register_0")
            put("payload", payload)
        }.toString()
    }

    private fun pointerSocketRequestPayload(): String = JSONObject().apply {
        put("type", "request")
        put("id", "pointer_0")
        put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket")
    }.toString()

    private fun mapButton(command: RemoteCommand): String? = when (command) {
        RemoteCommand.POWER -> "POWER"
        RemoteCommand.VOLUME_UP -> "VOLUMEUP"
        RemoteCommand.VOLUME_DOWN -> "VOLUMEDOWN"
        RemoteCommand.MUTE -> "MUTE"
        RemoteCommand.CHANNEL_UP -> "CHANNELUP"
        RemoteCommand.CHANNEL_DOWN -> "CHANNELDOWN"
        RemoteCommand.UP -> "UP"
        RemoteCommand.DOWN -> "DOWN"
        RemoteCommand.LEFT -> "LEFT"
        RemoteCommand.RIGHT -> "RIGHT"
        RemoteCommand.SELECT -> "ENTER"
        RemoteCommand.BACK -> "BACK"
        RemoteCommand.HOME -> "HOME"
        RemoteCommand.PLAY_PAUSE -> "PLAY"
    }
}
