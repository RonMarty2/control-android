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
    const val FIELD_REMOTE_IME_KEY_INJECT = 20
    const val FIELD_REMOTE_IME_BATCH_EDIT = 21
    const val FIELD_REMOTE_IME_SHOW_REQUEST = 22
    const val FIELD_REMOTE_VOICE_BEGIN = 30
    const val FIELD_REMOTE_VOICE_PAYLOAD = 31
    const val FIELD_REMOTE_VOICE_END = 32
    const val FIELD_REMOTE_START = 40
    const val FIELD_REMOTE_APP_LINK_LAUNCH_REQUEST = 90

    // Feature bitmask: PING(1) | KEY(2) | IME(4) | VOICE(8) | POWER(32) | VOLUME(64) | APP_LINK(512)
    // IME y VOICE hacen que el TV le avise al celular cuando se enfoca un cuadro de texto o se toca el micrófono.
    const val ACTIVE_FEATURES = 1 or 2 or 4 or 8 or 32 or 64 or 512
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


    /** Edición en vivo del campo de texto enfocado: [imeCounter]/[fieldCounter] salen del último
     * mensaje de IME que mandó el TV. */
    fun imeBatchEdit(imeCounter: Int, fieldCounter: Int, text: String): ByteArray {
        val obj = ProtoWriter().apply {
            writeVarintField(1, maxOf(text.length - 1, 0)) // start
            writeVarintField(2, maxOf(text.length - 1, 0)) // end
            writeStringField(3, text)                      // value
        }.toByteArray()
        val editInfo = ProtoWriter().apply {
            writeVarintField(1, 1)       // insert
            writeMessageField(2, obj)
        }.toByteArray()
        val batch = ProtoWriter().apply {
            writeVarintField(1, imeCounter)
            writeVarintField(2, fieldCounter)
            writeMessageField(3, editInfo)
        }.toByteArray()
        return ProtoWriter().apply { writeMessageField(FIELD_REMOTE_IME_BATCH_EDIT, batch) }.toByteArray()
    }

    fun voiceEnd(sessionId: Int): ByteArray {
        val end = ProtoWriter().apply { writeVarintField(1, sessionId) }.toByteArray()
        return ProtoWriter().apply { writeMessageField(FIELD_REMOTE_VOICE_END, end) }.toByteArray()
    }

    fun keyInject(keyCode: Int, direction: Int = DIRECTION_SHORT): ByteArray {
        val inject = ProtoWriter().apply {
            writeVarintField(1, keyCode)
            writeVarintField(2, direction)
        }.toByteArray()
        return ProtoWriter().apply { writeMessageField(FIELD_REMOTE_KEY_INJECT, inject) }.toByteArray()
    }

    /** [appLink] can be a package name (e.g. "org.jellyfin.androidtv") or a deep-link URL
     * (e.g. "https://www.youtube.com") - the TV resolves either into launching the matching app. */
    fun appLinkLaunchRequest(appLink: String): ByteArray {
        val request = ProtoWriter().apply { writeStringField(1, appLink) }.toByteArray()
        return ProtoWriter().apply { writeMessageField(FIELD_REMOTE_APP_LINK_LAUNCH_REQUEST, request) }.toByteArray()
    }
}
