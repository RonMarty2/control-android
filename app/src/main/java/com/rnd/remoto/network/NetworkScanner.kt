package com.rnd.remoto.network

import android.content.Context
import android.net.wifi.WifiManager
import com.rnd.remoto.data.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class ScanResult(val ip: String, val guessedType: DeviceType)

/**
 * Best-effort discovery: probes every host on the phone's /24 Wi-Fi subnet
 * for the well-known ports used by Roku (8060), Samsung (8002) and LG webOS
 * (3000). Assumes a typical home /24 network; doesn't do real SSDP/mDNS.
 */
class NetworkScanner(private val context: Context) {

    suspend fun scanLocalNetwork(timeoutMs: Int = 250): List<ScanResult> = withContext(Dispatchers.IO) {
        val baseIp = localSubnetPrefix() ?: return@withContext emptyList()
        val semaphore = Semaphore(48)
        (1..254).map { host ->
            async {
                semaphore.withPermit {
                    val ip = "$baseIp.$host"
                    detectDeviceType(ip, timeoutMs)?.let { ScanResult(ip, it) }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun detectDeviceType(ip: String, timeoutMs: Int): DeviceType? = when {
        isPortOpen(ip, 8060, timeoutMs) -> DeviceType.ROKU
        isPortOpen(ip, 8002, timeoutMs) -> DeviceType.SAMSUNG
        isPortOpen(ip, 3000, timeoutMs) -> DeviceType.LG_WEBOS
        else -> null
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        }
    } catch (e: Exception) {
        false
    }

    private fun localSubnetPrefix(): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipInt = wifiManager?.connectionInfo?.ipAddress ?: return null
        if (ipInt == 0) return null
        return "%d.%d.%d".format(
            ipInt and 0xFF,
            (ipInt shr 8) and 0xFF,
            (ipInt shr 16) and 0xFF
        )
    }
}
