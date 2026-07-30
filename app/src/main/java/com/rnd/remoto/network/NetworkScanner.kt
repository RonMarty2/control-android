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
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class ScanResult(val ip: String, val guessedType: DeviceType, val port: Int)

/**
 * Best-effort discovery: probes every host on the phone's /24 Wi-Fi subnet
 * for the well-known ports used by Roku (8060), Samsung (8002), LG webOS (3000)
 * and Android TV Remote v2 (6467). Assumes a typical home /24 network; doesn't
 * do real SSDP/mDNS.
 */
class NetworkScanner(private val context: Context) {

    /** Phone's own IPv4 address, for diagnostics (e.g. showing it in the UI). */
    fun getLocalIpAddress(): String? =
        localSubnetPrefixFromWifiManager()?.let { "$it.x (detectado por WifiManager)" }
            ?: localIpFromNetworkInterfaces()

    suspend fun scanLocalNetwork(timeoutMs: Int = 300): List<ScanResult> = withContext(Dispatchers.IO) {
        val baseIp = localSubnetPrefix() ?: return@withContext emptyList()
        val semaphore = Semaphore(48)
        (1..254).map { host ->
            async {
                semaphore.withPermit {
                    val ip = "$baseIp.$host"
                    detectDeviceType(ip, timeoutMs)?.let { (type, port) -> ScanResult(ip, type, port) }
                }
            }
        }.awaitAll().filterNotNull()
    }

    /** Whether a specific host answers on [port] right now. Used to detect that a device just
     * came back online (e.g. after sending it an IR power-on code) without a full subnet scan. */
    suspend fun isReachable(ip: String, port: Int, timeoutMs: Int = 500): Boolean =
        withContext(Dispatchers.IO) { isPortOpen(ip, port, timeoutMs) }

    /** Every host on the subnet with [port] open, regardless of device type. Used to re-find a
     * paired device that changed IP (e.g. after a DHCP lease renewal) without a full re-pair. */
    suspend fun findHostsWithOpenPort(port: Int, timeoutMs: Int = 300): List<String> = withContext(Dispatchers.IO) {
        val baseIp = localSubnetPrefix() ?: return@withContext emptyList()
        val semaphore = Semaphore(48)
        (1..254).map { host ->
            async {
                semaphore.withPermit {
                    val ip = "$baseIp.$host"
                    if (isPortOpen(ip, port, timeoutMs)) ip else null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun detectDeviceType(ip: String, timeoutMs: Int): Pair<DeviceType, Int>? = when {
        isPortOpen(ip, 8060, timeoutMs) -> DeviceType.ROKU to 8060
        isPortOpen(ip, 8002, timeoutMs) -> DeviceType.SAMSUNG to 8002
        isPortOpen(ip, 3000, timeoutMs) -> DeviceType.LG_WEBOS to 3000
        isPortOpen(ip, 6467, timeoutMs) -> DeviceType.ANDROID_TV to 6467
        isPortOpen(ip, 7345, timeoutMs) -> DeviceType.VIZIO to 7345
        isPortOpen(ip, 9000, timeoutMs) -> DeviceType.VIZIO to 9000
        isPortOpen(ip, 1925, timeoutMs) -> DeviceType.PHILIPS to 1925
        isPortOpen(ip, 55000, timeoutMs) -> DeviceType.PANASONIC to 55000
        isPortOpen(ip, 80, timeoutMs) -> DeviceType.SONY_BRAVIA to 80
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

    /** Prefers WifiManager (fast, exact); falls back to scanning network interfaces directly,
     * which is more resilient to OEM restrictions on WifiManager (seen on some MIUI builds). */
    private fun localSubnetPrefix(): String? =
        localSubnetPrefixFromWifiManager() ?: localIpFromNetworkInterfaces()?.let { toSubnetPrefix(it) }

    private fun localSubnetPrefixFromWifiManager(): String? {
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

    private fun localIpFromNetworkInterfaces(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filterNot { it.isLoopback || !it.isUp || it.isVirtual }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull()
                ?.hostAddress
        }.getOrNull()
    }

    private fun toSubnetPrefix(ip: String): String? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }
}
