package com.rnd.remoto.network

import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.DeviceType
import com.rnd.remoto.data.RemoteDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Builds the right network client for a saved device and persists tokens/keys it learns. */
class RemoteControllerFactory(
    private val repository: DeviceRepository,
    private val scope: CoroutineScope
) {
    fun create(device: RemoteDevice): RemoteController? = when (device.type) {
        DeviceType.ROKU -> RokuClient(ip = device.ip ?: return null, port = device.port ?: 8060)

        DeviceType.WOL -> WolClient(mac = device.mac ?: return null)

        DeviceType.LG_WEBOS -> LgWebOsClient(
            ip = device.ip ?: return null,
            clientKey = device.lgClientKey
        ) { newKey ->
            scope.launch { repository.saveDevice(device.copy(lgClientKey = newKey)) }
        }

        DeviceType.SAMSUNG -> SamsungClient(
            ip = device.ip ?: return null,
            token = device.samsungToken
        ) { newToken ->
            scope.launch { repository.saveDevice(device.copy(samsungToken = newToken)) }
        }

        DeviceType.IR -> null // IR devices are driven directly by IrController, not RemoteController
    }
}
