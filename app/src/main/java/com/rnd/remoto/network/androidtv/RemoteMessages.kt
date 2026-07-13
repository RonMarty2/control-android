package com.rnd.remoto.network.androidtv

/**
 * Message builders for the Android TV remote-control protocol (remotemessage.proto), based on
 * https://github.com/tronikos/androidtvremote2.
 */
object RemoteMessages {
    const val FIELD_REMOTE_CONFIGURE = 1
    const val FIELD_REMOTE_SET_ACTIVE = 2
    const val FIELD_REMOTE_PING_REQUEST = 8
    const val FIELD_REMOTE_PING_RESPONSE = 9
    const val FIELD_REMOTE_KEY_INJECT = 10
    const val FIELD_REMOTE_START = 40

    // Feature bitmask: PING(1) | KEY(2) | POWER(32) | VOLUME(64) | APP_LINK(512)
    const val ACTIVE_FEATURES = 1 or 2 or 32 or 64 or 512
    const val DIRECTION_SHORT = 3

    fun configureReply(): ByteArray {
        val deviceInfo = ProtoWriter().apply {
            writeVarintField(3, 1) // unknown1 = 1
            writeStringField(4, "1") // unknown2 = "1"
            writeStringField(5, "atvremote") // package_name
            writeStringField(6, "1.0.0") // app_version
        }.toByteArray()
        val configure = ProtoWriter().apply {
            writeVarintField(1, ACTIVE_FEATURES) // code1
            writeMessageField(2, deviceInfo) // device_info
        }.toByteArray()
        return ProtoWriter().apply { writeMessageField(FIELD_REMOTE_CONFIGURE, configure) }.toByteArray()
    }

    fun setActiveReply(): ByteArray {
        val setActive = ProtoWriter().apply { writeVarintField(1, ACTIVE_FEATURES) }.toByteArray()
        return ProtoWriter().apply { writeMessageField(FIELD_REMOTE_SET_ACTIVE, setActive) }.toByteArray()
    }

    fun pingResponse(val1: Long): ByteArray {
        val ping = ProtoWriter().apply { writeVarintField(1, val1) }.toByteArray()
        return ProtoWriter().apply { writeMessageField(FIELD_REMOTE_PING_RESPONSE, ping) }.toByteArray()
    }

    fun keyInject(keyCode: Int, direction: Int = DIRECTION_SHORT): ByteArray {
        val inject = ProtoWriter().apply {
            writeVarintField(1, keyCode)
            writeVarintField(2, direction)
        }.toByteArray()
        return ProtoWriter().apply { writeMessageField(FIELD_REMOTE_KEY_INJECT, inject) }.toByteArray()
    }
}
