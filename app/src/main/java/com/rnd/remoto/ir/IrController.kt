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
        Unit
    }.onFailure {
        Log.e(TAG, "Falló el envío IR", it)
    }

    private fun parseHexByte(raw: String): Int? =
        raw.trim().removePrefix("0x").removePrefix("0X").toIntOrNull(16)

    /**
     * codeHex format: either "AA,CC" (8-bit address + command, complement of the address is
     * derived automatically, e.g. "07,02") or "LL,HH,CC" (extended address: the two address
     * bytes are sent exactly as given, for remotes whose address bytes aren't complements of
     * each other, e.g. "00,df,1c").
     */
    fun sendHexPair(codeHex: String): Result<Unit> {
        val parts = codeHex.split(",")
        return when (parts.size) {
            2 -> {
                val address = parseHexByte(parts[0])
                    ?: return Result.failure(IllegalArgumentException("Dirección hexadecimal inválida: ${parts[0]}"))
                val command = parseHexByte(parts[1])
                    ?: return Result.failure(IllegalArgumentException("Comando hexadecimal inválido: ${parts[1]}"))
                sendNec(address, command)
            }
            3 -> {
                val addressLow = parseHexByte(parts[0])
                    ?: return Result.failure(IllegalArgumentException("Dirección (byte bajo) inválida: ${parts[0]}"))
                val addressHigh = parseHexByte(parts[1])
                    ?: return Result.failure(IllegalArgumentException("Dirección (byte alto) inválida: ${parts[1]}"))
                val command = parseHexByte(parts[2])
                    ?: return Result.failure(IllegalArgumentException("Comando hexadecimal inválido: ${parts[2]}"))
                sendNecExtended(addressLow, addressHigh, command)
            }
            else -> Result.failure(IllegalArgumentException("Formato inválido: $codeHex"))
        }
    }

    private fun sendNecExtended(addressLow: Int, addressHigh: Int, command: Int): Result<Unit> = runCatching {
        val mgr = manager ?: error("El sistema no expone un ConsumerIrManager en este celular")
        if (!mgr.hasIrEmitter()) error("hasIrEmitter() devolvió false al momento de transmitir")
        val pattern = NecEncoder.encodeExtended(addressLow, addressHigh, command)
        mgr.transmit(NecEncoder.CARRIER_FREQUENCY_HZ, pattern)
        Unit
    }.onFailure {
        Log.e(TAG, "Falló el envío IR extendido", it)
    }
}
