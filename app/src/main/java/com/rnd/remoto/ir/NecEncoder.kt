package com.rnd.remoto.ir

/**
 * Standard 38kHz NEC infrared protocol encoder.
 * Produces the on/off duration pattern (microseconds) expected by
 * android.hardware.ConsumerIrManager#transmit(frequency, pattern).
 */
object NecEncoder {

    const val CARRIER_FREQUENCY_HZ = 38000

    private const val LEADER_MARK = 9000
    private const val LEADER_SPACE = 4500
    private const val BIT_MARK = 562
    private const val ZERO_SPACE = 562
    private const val ONE_SPACE = 1687

    /**
     * @param address 8-bit device address (its complement is derived automatically)
     * @param command 8-bit command code (its complement is derived automatically)
     */
    fun encode(address: Int, command: Int): IntArray {
        val bits = ArrayList<Int>(32)
        fun addByte(byte: Int) {
            for (i in 0 until 8) bits.add((byte shr i) and 1)
        }
        addByte(address and 0xFF)
        addByte(address.inv() and 0xFF)
        addByte(command and 0xFF)
        addByte(command.inv() and 0xFF)

        val pattern = ArrayList<Int>(2 + bits.size * 2 + 1)
        pattern.add(LEADER_MARK)
        pattern.add(LEADER_SPACE)
        for (bit in bits) {
            pattern.add(BIT_MARK)
            pattern.add(if (bit == 1) ONE_SPACE else ZERO_SPACE)
        }
        pattern.add(BIT_MARK)
        return pattern.toIntArray()
    }
}
