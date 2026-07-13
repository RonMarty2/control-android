package com.rnd.remoto.network.androidtv

import android.util.Log
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
 */
class AndroidTvRemoteClient(
    private val ip: String,
    private val port: Int = 6466
) : RemoteController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private var socket: SSLSocket? = null
    private var readLoopJob: Job? = null
    private var ready: CompletableDeferred<Boolean>? = null

    private suspend fun ensureConnected(): Boolean {
        ready?.let { existing ->
            if (existing.await()) return true
        }

        val readyDeferred = CompletableDeferred<Boolean>()
        ready = readyDeferred
        try {
            val sslSocket = AndroidTvIdentity.buildSslContext().socketFactory.createSocket(ip, port) as SSLSocket
            sslSocket.enabledProtocols = sslSocket.supportedProtocols
            sslSocket.enabledCipherSuites = sslSocket.supportedCipherSuites
            sslSocket.startHandshake()
            socket = sslSocket
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo conectar al Android TV", e)
            readyDeferred.complete(false)
            return false
        }

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
