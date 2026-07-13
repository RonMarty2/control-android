package com.rnd.remoto.ir

import android.content.Context
import android.hardware.ConsumerIrManager

class IrController(context: Context) {

    private val manager: ConsumerIrManager? =
        context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    fun hasIrEmitter(): Boolean = manager?.hasIrEmitter() == true

    fun sendNec(address: Int, command: Int) {
        val mgr = manager ?: return
        if (!mgr.hasIrEmitter()) return
        mgr.transmit(NecEncoder.CARRIER_FREQUENCY_HZ, NecEncoder.encode(address, command))
    }

    /** codeHex format: "AA,CC" hex address and command, e.g. "07,02" or "0x07,0x02" */
    fun sendHexPair(codeHex: String) {
        val parts = codeHex.split(",")
        if (parts.size != 2) return
        val address = parts[0].trim().removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: return
        val command = parts[1].trim().removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: return
        sendNec(address, command)
    }
}
