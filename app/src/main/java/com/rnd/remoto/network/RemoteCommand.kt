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
    fun close() {}
}
