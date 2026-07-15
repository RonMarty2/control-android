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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.net.ssl.SSLSocket

private const val TAG = "AndroidTvRemote"

/**
 * Android TV Remote v2 control connection (port 6466). Must already be paired via
 * [AndroidTvPairingClient] - this class presents the same client certificate the TV
 * approved during pairing, which is what lets it skip pairing on reconnect.
 *
 * If the saved [ip] no longer answers (e.g. the TV got a new IP from the router's DHCP), and a
 * [context] was provided, this rescans the local network for any host that still accepts our
 * client cert on [port] and switches to it automatically — no re-pairing needed, since the TV
 * already trusts this app's certificate regardless of which IP it's reached at.
 */
class AndroidTvRemoteClient(
    private var ip: String,
    private val port: Int = 6466,
    private val context: Context? = null,
    private val onIpChanged: ((String) -> Unit)? = null
) : RemoteController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private var socket: SSLSocket? = null
    private var readLoopJob: Job? = null
    private var ready: CompletableDeferred<Boolean>? = null

    private fun openHandshakedSocket(targetIp: String): SSLSocket? = try {
        val sslSocket = AndroidTvIdentity.buildSslContext().socketFactory.createSocket(targetIp, port) as SSLSocket
        sslSocket.enabledProtocols = sslSocket.supportedProtocols
        sslSocket.enabledCipherSuites = sslSocket.supportedCipherSuites
        sslSocket.startHandshake()
        sslSocket
    } catch (e: Exception) {
        Log.d(TAG, "No responde en $targetIp: ${e.message}")
        null
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

    private suspend fun ensureConnected(): Boolean {
        ready?.let { existing ->
            if (existing.await()) return true
        }

        val readyDeferred = CompletableDeferred<Boolean>()
        ready = readyDeferred

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
            readyDeferred.complete(false)
            return false
        }

        socket = sslSocket
        readLoopJob = scope.launch { readLoop(readyDeferred) }
        return readyDeferred.await()
    }

    private suspend fun readLoop(readyDeferred: CompletableDeferred<Boolean>) {
        val sslSocket = socket ?: return
        try {
            while (true) {
                val raw = ProtoFraming.readFramed(sslSocket.inputStream)
                handleMessage(ProtoReader(raw).readFields(), readyDeferred)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Conexión con el Android TV cerrada: ${e.message}")
            if (!readyDeferred.isCompleted) readyDeferred.complete(false)
        }
    }

    private suspend fun handleMessage(fields: List<ProtoField>, readyDeferred: CompletableDeferred<Boolean>) {
        when {
            fields.has(RemoteMessages.FIELD_REMOTE_CONFIGURE) -> writeMessage(RemoteMessages.configureReply())
            fields.has(RemoteMessages.FIELD_REMOTE_SET_ACTIVE) -> writeMessage(RemoteMessages.setActiveReply())
            fields.has(RemoteMessages.FIELD_REMOTE_PING_REQUEST) -> {
                val val1 = fields.submessage(RemoteMessages.FIELD_REMOTE_PING_REQUEST)?.varint(1) ?: 0L
                writeMessage(RemoteMessages.pingResponse(val1))
            }
            fields.has(RemoteMessages.FIELD_REMOTE_START) -> {
                if (!readyDeferred.isCompleted) readyDeferred.complete(true)
            }
        }
    }

    private suspend fun writeMessage(message: ByteArray) {
        val sslSocket = socket ?: return
        writeMutex.withLock {
            ProtoFraming.writeFramed(sslSocket.outputStream, message)
        }
    }

    override suspend fun send(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!ensureConnected()) {
                error("No se pudo conectar al Android TV. Revisá que esté encendido, en la misma red, y ya emparejado.")
            }
            val keyCode = mapKeyCode(command) ?: error("Comando no soportado")
            writeMessage(RemoteMessages.keyInject(keyCode))
        }
    }

    override fun close() {
        readLoopJob?.cancel()
        runCatching { socket?.close() }
        socket = null
        ready = null
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
