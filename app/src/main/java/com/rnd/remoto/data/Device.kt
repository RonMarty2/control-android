package com.rnd.remoto.data

import kotlinx.serialization.Serializable
import java.util.UUID

enum class DeviceType {
    IR,
    ROKU,
    LG_WEBOS,
    SAMSUNG,
    WOL,
    ANDROID_TV
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
    val irCodes: Map<String, String> = emptyMap()
)
