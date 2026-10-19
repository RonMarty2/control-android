package com.rnd.remoto.ir

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.util.Log

private const val TAG = "IrController"

/** Whether this phone can transmit infrared, and why not when it can't. */
class IrStatus(val available: Boolean, val message: String)

class IrController(context: Context) {

    private val appContext = context.applicationContext
    private val manager: ConsumerIrManager? =
        appContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    init {
        Log.d(TAG, "ConsumerIrManager available: ${manager != null}, hasIrEmitter: ${hasIrEmitter()}")
    }

    /** Pure query: asking the system never makes the emitter fire. */
    fun hasIrEmitter(): Boolean = try {
        manager?.hasIrEmitter() == true
    } catch (e: Exception) {
        Log.e(TAG, "hasIrEmitter() falló", e)
        false
    }

    fun status(): IrStatus {
        if (hasIrEmitter()) return IrStatus(true, "Emisor infrarrojo detectado en este celular")
        val declared = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)
        return if (manager == null && !declared) {
            IrStatus(false, "Este celular no tiene emisor infrarrojo (la mayoría de los modelos actuales no lo traen).")
        } else {
            IrStatus(false, "El sistema no expone el emisor infrarrojo de este celular, así que la app no puede usarlo.")
        }
    }

    /** Pattern frequency adjusted to what the emitter hardware accepts (some only cover a range). */
    private fun supportedFrequency(requested: Int): Int {
        val ranges = try { manager?.carrierFrequencies } catch (e: Exception) { null }
        if (ranges.isNullOrEmpty()) return requested
        if (ranges.any { requested in it.minFrequency..it.maxFrequency }) return requested
        return ranges
            .flatMap { listOf(it.minFrequency, it.maxFrequency) }
            .minByOrNull { kotlin.math.abs(it - requested) } ?: requested
    }

    /** Sends any code understood by [IrCodec] (see its doc for formats). */
    fun sendCode(code: String): Result<Unit> = IrCodec.encode(code).mapCatching { signal ->
        val mgr = manager ?: error("El sistema no expone un ConsumerIrManager en este celular")
        if (!mgr.hasIrEmitter()) error("Este celular no tiene emisor infrarrojo")
        val frequency = supportedFrequency(signal.frequencyHz)
        Log.d(TAG, "Transmitiendo $code freq=$frequency pattern(${signal.pattern.size})")
        mgr.transmit(frequency, signal.pattern)
    }.onFailure {
        Log.e(TAG, "Falló el envío IR de $code", it)
    }

    fun sendNec(address: Int, command: Int): Result<Unit> =
        sendCode("%02x,%02x".format(address and 0xFF, command and 0xFF))

    /** Kept for callers that predate multi-protocol support; accepts every format of [sendCode]. */
    fun sendHexPair(codeHex: String): Result<Unit> = sendCode(codeHex)
}
