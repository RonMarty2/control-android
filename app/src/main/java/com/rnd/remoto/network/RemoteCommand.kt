package com.rnd.remoto.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Cosas que el dispositivo le avisa a la app sin que ella pregunte. */
sealed class ImeEvent {
    /** Se enfocó un cuadro de texto en el dispositivo (o pidió su teclado); [text] es lo que ya tiene escrito. */
    data class TextFieldFocused(val text: String, val label: String?) : ImeEvent()

    /** El dispositivo pide búsqueda por voz (tocaron el micrófono en pantalla). */
    data class VoiceRequested(val sessionId: Int) : ImeEvent()
}

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


    /** Eventos que el dispositivo manda por su cuenta (ej: se enfocó un cuadro de texto). */
    val imeEvents: Flow<ImeEvent> get() = emptyFlow()

    /** Abre la conexión sin mandar ningún comando, para empezar a recibir [imeEvents]. */
    suspend fun connect(): Boolean = false

    /** Reemplaza el contenido del campo de texto enfocado del dispositivo por [text] (edición en vivo). */
    suspend fun setText(text: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Este dispositivo no soporta escribir texto"))

    /** Avisa al dispositivo que la búsqueda por voz ya terminó (para que cierre su pantalla de escucha). */
    suspend fun endVoice(sessionId: Int): Result<Unit> = Result.success(Unit)

    /** Lleva el campo de texto enfocado del dispositivo de [previous] a [current] mandando solo
     * la diferencia (borra lo que sobra y teclea lo nuevo): sirve para espejar un teclado en vivo. */
    suspend fun editText(previous: String, current: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Este dispositivo no soporta escribir texto"))

    fun close() {}
}
