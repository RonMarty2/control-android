package com.rnd.remoto.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Wake-on-LAN: only supports powering a device on via its magic packet. */
class WolClient(
    private val mac: String,
    private val broadcastAddress: String = "255.255.255.255"
) : RemoteController {

    override suspend fun send(command: RemoteCommand): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (command != RemoteCommand.POWER) {
                error("Wake-on-LAN solo puede encender el dispositivo")
            }
            sendMagicPacket(mac, broadcastAddress)
        }
    }

    private fun sendMagicPacket(mac: String, broadcastAddress: String, port: Int = 9) {
        val macBytes = mac.trim().split(":", "-")
            .map { it.toInt(16).toByte() }
            .toByteArray()
        require(macBytes.size == 6) { "Dirección MAC inválida: $mac" }

        val bytes = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) bytes[i] = 0xFF.toByte()
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, bytes, 6 + i * macBytes.size, macBytes.size)
        }

        val address = InetAddress.getByName(broadcastAddress)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.send(DatagramPacket(bytes, bytes.size, address, port))
        }
    }
}
