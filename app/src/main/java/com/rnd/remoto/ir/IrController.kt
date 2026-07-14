package com.rnd.remoto.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log

private const val TAG = "IrController"

class IrController(context: Context) {

    private val manager: ConsumerIrManager? =
        context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    init {
        Log.d(TAG, "ConsumerIrManager available: ${manager != null}, hasIrEmitter: ${manager?.hasIrEmitter()}")
    }

    fun hasIrEmitter(): Boolean = manager?.hasIrEmitter() == true

    fun sendNec(address: Int, command: Int): Result<Unit> = runCatching {
        val mgr = manager ?: error("El sistema no expone un ConsumerIrManager en este celular")
        if (!mgr.hasIrEmitter()) error("hasIrEmitter() devolvió false al momento de transmitir")
        val pattern = NecEncoder.encode(address, command)
        Log.d(
            TAG,
            "Transmitiendo NEC addr=0x${address.toString(16)} cmd=0x${command.toString(16)} " +
                "freq=${NecEncoder.CARRIER_FREQUENCY_HZ} pattern(${pattern.size})=${pattern.joinToString(",")}"
        )
        mgr.transmit(NecEncoder.CARRIER_FREQUENCY_HZ, pattern)
        Log.d(TAG, "transmit() retornó sin excepción")
    }.onFailure {
        Log.e(TAG, "Falló el envío IR", it)
    }

    /** codeHex format: "AA,CC" hex address and command, e.g. "07,02" or "0x07,0x02" */
    fun sendHexPair(codeHex: String): Result<Unit> {
        val parts = codeHex.split(",")
        if (parts.size != 2) return Result.failure(IllegalArgumentException("Formato inválido: $codeHex"))
        val address = parts[0].trim().removePrefix("0x").removePrefix("0X").toIntOrNull(16)
            ?: return Result.failure(IllegalArgumentException("Dirección hexadecimal inválida: ${parts[0]}"))
        val command = parts[1].trim().removePrefix("0x").removePrefix("0X").toIntOrNull(16)
            ?: return Result.failure(IllegalArgumentException("Comando hexadecimal inválido: ${parts[1]}"))
        return sendNec(address, command)
    }
}
