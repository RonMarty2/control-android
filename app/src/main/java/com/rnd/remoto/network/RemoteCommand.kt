package com.rnd.remoto.network

enum class RemoteCommand {
    POWER,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    CHANNEL_UP,
    CHANNEL_DOWN,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    SELECT,
    BACK,
    HOME,
    PLAY_PAUSE
}

interface RemoteController {
    suspend fun send(command: RemoteCommand): Result<Unit>

    /** Launches an app by package name or deep link URL. Only Android TV supports this today. */
    suspend fun launchApp(appLink: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Este dispositivo no soporta abrir apps"))

    /** Types [text] into whatever text field is focused on the device (e.g. a YouTube search
     * box). Only Android TV supports this today. */
    suspend fun sendText(text: String, submit: Boolean = false): Result<Unit> =
        Result.failure(UnsupportedOperationException("Este dispositivo no soporta escribir texto"))

    fun close() {}
}
