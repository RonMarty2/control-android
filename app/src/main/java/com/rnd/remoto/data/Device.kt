package com.rnd.remoto.data

import kotlinx.serialization.Serializable
import java.util.UUID

enum class DeviceType {
    IR,
    ROKU,
    LG_WEBOS,
    SAMSUNG,
    SONY_BRAVIA,
    VIZIO,
    PHILIPS,
    PANASONIC,
    ANDROID_TV,
    WOL
}

enum class DeviceCategory {
    INFRARROJO,
    SMART_TV,
    TV_BOX,
    OTRO
}

fun DeviceType.category(): DeviceCategory = when (this) {
    DeviceType.IR -> DeviceCategory.INFRARROJO
    DeviceType.SAMSUNG,
    DeviceType.LG_WEBOS,
    DeviceType.SONY_BRAVIA,
    DeviceType.VIZIO,
    DeviceType.PHILIPS,
    DeviceType.PANASONIC -> DeviceCategory.SMART_TV
    DeviceType.ROKU,
    DeviceType.ANDROID_TV -> DeviceCategory.TV_BOX
    DeviceType.WOL -> DeviceCategory.OTRO
}

/**
 * IR codes are user-supplied NEC (address, command) hex pairs per button,
 * since there is no bundled universal code database.
 */
@Serializable
data class RemoteDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: DeviceType,
    val ip: String? = null,
    val port: Int? = null,
    val mac: String? = null,
    val samsungToken: String? = null,
    val lgClientKey: String? = null,
    val androidTvPaired: Boolean = false,
    val sonyPsk: String? = null,
    val vizioDeviceId: String? = null,
    val vizioAuthToken: String? = null,
    val irCodes: Map<String, String> = emptyMap()
)
