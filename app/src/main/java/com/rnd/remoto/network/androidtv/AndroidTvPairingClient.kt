package com.rnd.remoto.network.androidtv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

private const val TAG = "AndroidTvPairing"

/**
 * Handles the two-step Android TV Remote v2 pairing handshake against port 6467:
 * connect + request PIN (TV shows a 6-digit hex code on screen), then submit that PIN.
 */
class AndroidTvPairingClient(private val ip: String, private val port: Int = 6467) {

    private var socket: SSLSocket? = null

    suspend fun connectAndRequestPin(clientName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sslSocket = AndroidTvIdentity.buildSslContext().socketFactory.createSocket(ip, port) as SSLSocket
            sslSocket.enabledProtocols = sslSocket.supportedProtocols
            sslSocket.enabledCipherSuites = sslSocket.supportedCipherSuites
            sslSocket.startHandshake()
            socket = sslSocket

            send(PoloMessages.pairingRequest(clientName))
            val ack = receive()
            check(statusOf(ack) == PoloMessages.STATUS_OK) { "El TV rechazó la solicitud de emparejamiento" }
            check(ack.has(PoloMessages.FIELD_PAIRING_REQUEST_ACK)) {
                "Respuesta inesperada del TV (se esperaba pairing_request_ack)"
            }

            send(PoloMessages.options())
            val optionsReply = receive()
            check(optionsReply.has(PoloMessages.FIELD_OPTIONS)) {
                "Respuesta inesperada del TV (se esperaba options)"
            }

            send(PoloMessages.configuration())
            val configAck = receive()
            check(configAck.has(PoloMessages.FIELD_CONFIGURATION_ACK)) {
                "Respuesta inesperada del TV (se esperaba configuration_ack)"
            }
        }.onFailure {
            Log.e(TAG, "connectAndRequestPin falló", it)
            closeQuietly()
        }
    }

    /** Submits the 6-digit hex PIN shown on the TV. Returns the TV's certificate on success. */
    suspend fun submitPin(pin: String): Result<X509Certificate> = withContext(Dispatchers.IO) {
        runCatching {
            val sslSocket = socket ?: error("No hay una conexión de emparejamiento activa")
            val cleanPin = pin.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
            require(cleanPin.length == 6) { "El PIN debe tener 6 caracteres hexadecimales (0-9, A-F)" }

            val serverCert = sslSocket.session.peerCertificates.first() as X509Certificate
            val serverKey = serverCert.publicKey as RSAPublicKey
            val (clientModulus, clientExponent) = AndroidTvIdentity.clientModulusAndExponent()

            val secret = PairingCrypto.computeSecret(
                pin = cleanPin,
                clientModulus = clientModulus,
                clientExponent = clientExponent,
                serverModulus = serverKey.modulus,
                serverExponent = serverKey.publicExponent
            )
            check((secret[0].toInt() and 0xFF) == PairingCrypto.expectedFirstByte(cleanPin)) {
                "El PIN ingresado no coincide con este TV. Revisá el código en pantalla e intentá de nuevo."
            }

            send(PoloMessages.secret(secret))
            val secretAck = receive()
            check(statusOf(secretAck) == PoloMessages.STATUS_OK) { "El TV rechazó el PIN" }
            check(secretAck.has(PoloMessages.FIELD_SECRET_ACK)) {
                "Respuesta inesperada del TV (se esperaba secret_ack)"
            }

            serverCert
        }.also {
            closeQuietly()
        }
    }

    fun closeQuietly() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun statusOf(fields: List<ProtoField>): Int =
        (fields.varint(PoloMessages.FIELD_STATUS)?.toInt()) ?: PoloMessages.STATUS_OK

    private fun send(message: ByteArray) {
        val sslSocket = socket ?: error("Socket no disponible")
        Log.d(TAG, "-> ${message.size}B ${message.joinToString("") { "%02x".format(it) }}")
        ProtoFraming.writeFramed(sslSocket.outputStream, message)
    }

    private fun receive(): List<ProtoField> {
        val sslSocket = socket ?: error("Socket no disponible")
        val raw = ProtoFraming.readFramed(sslSocket.inputStream)
        Log.d(TAG, "<- ${raw.size}B ${raw.joinToString("") { "%02x".format(it) }}")
        return ProtoReader(raw).readFields()
    }
}
