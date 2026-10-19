package com.rnd.remoto.network.androidtv

import android.content.Context
import android.util.Log
import com.rnd.remoto.network.NetworkScanner
import com.rnd.remoto.network.RemoteCommand
import com.rnd.remoto.network.RemoteController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.text.Normalizer
import javax.net.ssl.SSLSocket

private const val TAG = "AndroidTvRemote"
private const val CONNECT_TIMEOUT_MS = 3000
private const val READY_TIMEOUT_MS = 8000L
private const val STALE_AFTER_MS = 20_000L
private const val KEY_INTERVAL_MS = 25L
private const val KEYCODE_ENTER = 66

/**
 * Android TV Remote v2 control connection (port 6466). Must already be paired via
 * [AndroidTvPairingClient] - this class presents the same client certificate the TV
 * approved during pairing, which is what lets it skip pairing on reconnect.
 *
 * If the saved [ip] no longer answers (e.g. the TV got a new IP from the router's DHCP), and a
 * [context] was provided, this rescans the local network for any host that still accepts our
 * client cert on [port] and switches to it automatically — no re-pairing needed, since the TV
 * already trusts this app's certificate regardless of which IP it's reached at.
 *
 * The connection is re-validated on every command: if the socket died silently (Wi-Fi switch,
 * TV powered off, long inactivity) it is dropped and re-opened instead of writing into it.
 */
class AndroidTvRemoteClient(
    private var ip: String,
    private val port: Int = 6466,
    private val context: Context? = null,
    private val onIpChanged: ((String) -> Unit)? = null
) : RemoteController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val connectMutex = Mutex()

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var connected = false
    @Volatile private var lastInboundAt = 0L
    private var readLoopJob: Job? = null

    private fun openHandshakedSocket(targetIp: String): SSLSocket? {
        var plain: Socket? = null
        return try {
            // Timeout explícito: sin él, una IP de otra red (p. ej. tras cambiar de Wi-Fi) deja
            // el connect colgado ~2 minutos en vez de pasar rápido a re-buscar el equipo.
            plain = Socket().apply { connect(InetSocketAddress(targetIp, port), CONNECT_TIMEOUT_MS) }
            val sslSocket = AndroidTvIdentity.buildSslContext().socketFactory
                .createSocket(plain, targetIp, port, true) as SSLSocket
            sslSocket.enabledProtocols = sslSocket.supportedProtocols
            sslSocket.enabledCipherSuites = sslSocket.supportedCipherSuites
            sslSocket.soTimeout = CONNECT_TIMEOUT_MS
            sslSocket.startHandshake()
            sslSocket.soTimeout = 0
            sslSocket.keepAlive = true
            sslSocket
        } catch (e: Exception) {
            Log.d(TAG, "No responde en $targetIp: ${e.message}")
            runCatching { plain?.close() }
            null
        }
    }

    private suspend fun rediscoverIp(): String? {
        val ctx = context ?: return null
        Log.d(TAG, "La IP guardada ($ip) no responde, buscando el Android TV en la red...")
        val candidates = NetworkScanner(ctx).findHostsWithOpenPort(port).filter { it != ip }
        for (candidate in candidates) {
            val probeSocket = openHandshakedSocket(candidate) ?: continue
            runCatching { probeSocket.close() }
            return candidate
        }
        return null
    }

    /** Conexión viva = socket abierto, handshake de control completo y el TV todavía nos habla
     * (manda un ping cada pocos segundos; si pasó mucho sin nada, la conexión murió sin avisar,
     * típico al cambiar de Wi-Fi o volver de inactividad). */
    private fun isAlive(): Boolean {
        val s = socket ?: return false
        return connected && !s.isClosed &&
            System.currentTimeMillis() - lastInboundAt < STALE_AFTER_MS
    }

    private fun teardown() {
        connected = false
        readLoopJob?.cancel()
        readLoopJob = null
        runCatching { socket?.close() }
        socket = null
    }

    private suspend fun ensureConnected(): Boolean = connectMutex.withLock {
        if (isAlive()) return@withLock true
        teardown()

        var sslSocket = openHandshakedSocket(ip)
        if (sslSocket == null) {
            val rediscoveredIp = rediscoverIp()
            sslSocket = rediscoveredIp?.let { openHandshakedSocket(it) }
            if (sslSocket != null) {
                ip = rediscoveredIp!!
                onIpChanged?.invoke(rediscoveredIp)
            }
        }

        if (sslSocket == null) {
            Log.e(TAG, "No se pudo conectar al Android TV")
            return@withLock false
        }

        val readyDeferred = CompletableDeferred<Boolean>()
        socket = sslSocket
        lastInboundAt = System.currentTimeMillis()
        readLoopJob = scope.launch { readLoop(sslSocket, readyDeferred) }

        val ok = withTimeoutOrNull(READY_TIMEOUT_MS) { readyDeferred.await() } == true
        if (ok) connected = true else teardown()
        ok
    }

    private suspend fun readLoop(sslSocket: SSLSocket, readyDeferred: CompletableDeferred<Boolean>) {
        try {
            while (true) {
                val raw = ProtoFraming.readFramed(sslSocket.inputStream)
                lastInboundAt = System.currentTimeMillis()
                handleMessage(sslSocket, ProtoReader(raw).readFields(), readyDeferred)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Conexión con el Android TV cerrada: ${e.message}")
        } finally {
            if (!readyDeferred.isCompleted) readyDeferred.complete(false)
            // Solo invalidamos si este sigue siendo el socket vigente (no uno ya reemplazado).
            if (socket === sslSocket) {
                connected = false
                runCatching { sslSocket.close() }
            }
        }
    }

    private suspend fun handleMessage(
        sslSocket: SSLSocket,
        fields: List<ProtoField>,
        readyDeferred: CompletableDeferred<Boolean>
    ) {
        when {
            fields.has(RemoteMessages.FIELD_REMOTE_CONFIGURE) -> writeMessage(sslSocket, RemoteMessages.configureReply())
            fields.has(RemoteMessages.FIELD_REMOTE_SET_ACTIVE) -> writeMessage(sslSocket, RemoteMessages.setActiveReply())
            fields.has(RemoteMessages.FIELD_REMOTE_PING_REQUEST) -> {
                val val1 = fields.submessage(RemoteMessages.FIELD_REMOTE_PING_REQUEST)?.varint(1) ?: 0L
                writeMessage(sslSocket, RemoteMessages.pingResponse(val1))
            }
            fields.has(RemoteMessages.FIELD_REMOTE_START) -> {
                if (!readyDeferred.isCompleted) readyDeferred.complete(true)
            }
        }
    }

    private suspend fun writeMessage(sslSocket: SSLSocket, message: ByteArray) {
        writeMutex.withLock {
            ProtoFraming.writeFramed(sslSocket.outputStream, message)
        }
    }

    /** Conecta si hace falta y manda [message]; si el socket resulta estar muerto, reconecta
     * y reintenta una vez (evita tener que cerrar la app tras cambiar de red o inactividad). */
    private suspend fun sendWithReconnect(message: ByteArray) {
        val notConnected = "No se pudo conectar al Android TV. Revisá que esté encendido y en la misma red. " +
            "Si sigue sin responder, volvé a emparejarlo."
        repeat(2) {
            if (!ensureConnected()) error(notConnected)
            val current = socket ?: return@repeat
            try {
                writeMessage(current, message)
                return
            } catch (e: IOException) {
                Log.d(TAG, "Falló el envío, reconectando: ${e.message}")
                teardown()
            }
        }
        error(notConnected)
    }

    override suspend fun send(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val keyCode = mapKeyCode(command) ?: error("Comando no soportado")
            sendWithReconnect(RemoteMessages.keyInject(keyCode))
        }
    }

    override suspend fun launchApp(appLink: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { sendWithReconnect(RemoteMessages.appLinkLaunchRequest(appLink)) }
    }

    /** Escribe en el campo de texto enfocado del TV mandando teclas una a una (sin necesitar los
     * contadores del IME de Android TV). Se ignoran mayúsculas/acentos: "Ñandú" -> "nandu". */
    override suspend fun sendText(text: String, submit: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val keys = textToKeyCodes(text)
            if (keys.isEmpty() && !submit) error("No hay texto para enviar")
            for (key in keys) {
                sendWithReconnect(RemoteMessages.keyInject(key))
                delay(KEY_INTERVAL_MS)
            }
            if (submit) sendWithReconnect(RemoteMessages.keyInject(KEYCODE_ENTER))
        }
    }

    private fun textToKeyCodes(text: String): List<Int> {
        val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return plain.mapNotNull { ch ->
            when (ch) {
                in 'a'..'z' -> 29 + (ch - 'a')
                in '0'..'9' -> 7 + (ch - '0')
                ' ' -> 62
                '.' -> 56
                ',' -> 55
                '-' -> 69
                '\'' -> 75
                '/' -> 76
                '@' -> 77
                '+' -> 81
                else -> null
            }
        }
    }

    override fun close() {
        teardown()
        scope.cancel()
    }

    private fun mapKeyCode(command: RemoteCommand): Int? = when (command) {
        RemoteCommand.POWER -> 26
        RemoteCommand.VOLUME_UP -> 24
        RemoteCommand.VOLUME_DOWN -> 25
        RemoteCommand.MUTE -> 164
        RemoteCommand.CHANNEL_UP -> 166
        RemoteCommand.CHANNEL_DOWN -> 167
        RemoteCommand.UP -> 19
        RemoteCommand.DOWN -> 20
        RemoteCommand.LEFT -> 21
        RemoteCommand.RIGHT -> 22
        RemoteCommand.SELECT -> 23
        RemoteCommand.BACK -> 4
        RemoteCommand.HOME -> 3
        RemoteCommand.PLAY_PAUSE -> 85
    }
}
