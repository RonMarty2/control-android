package com.rnd.remoto.ir

/** A ready-to-transmit infrared signal: carrier frequency + alternating on/off durations (µs). */
class IrSignal(val frequencyHz: Int, val pattern: IntArray)

/**
 * Turns a stored code string into an [IrSignal]. Supported formats:
 * - legacy NEC: "AA,CC" (8-bit address/command, complements derived) or "LL,HH,CC" (extended
 *   address bytes sent as given), all hexadecimal;
 * - "nec:A0A1C0C1" and "samsung:A0A1C0C1": the four frame bytes in transmission order;
 * - "sirc:BITS:CMD:ADDR" (Sony, BITS = 12/15/20), "rc5:ADDR:CMD", "rc6:ADDR:CMD", "jvc:ADDR:CMD"
 *   (hexadecimal values);
 * - "raw:FREQ:d1,d2,..." (decimal µs, starting with a mark).
 */
object IrCodec {

    fun encode(code: String): Result<IrSignal> = runCatching {
        val text = code.trim()
        require(text.isNotEmpty()) { "Código vacío" }
        if (':' !in text) return@runCatching encodeLegacy(text)

        val parts = text.split(":")
        when (parts[0].lowercase()) {
            "nec" -> {
                val b = bytes4(parts)
                IrSignal(38000, NecEncoder.encodeBytes(b[0], b[1], b[2], b[3]))
            }
            "samsung" -> {
                val b = bytes4(parts)
                IrSignal(38000, samsung(b))
            }
            "sirc" -> {
                require(parts.size == 4) { "Formato SIRC inválido: $text" }
                val bits = parts[1].toInt()
                require(bits == 12 || bits == 15 || bits == 20) { "SIRC de $bits bits no soportado" }
                IrSignal(40000, sirc(bits, parts[2].toInt(16), parts[3].toInt(16)))
            }
            "rc5" -> {
                require(parts.size == 3) { "Formato RC5 inválido: $text" }
                IrSignal(36000, rc5(parts[1].toInt(16), parts[2].toInt(16)))
            }
            "rc6" -> {
                require(parts.size == 3) { "Formato RC6 inválido: $text" }
                IrSignal(36000, rc6(parts[1].toInt(16), parts[2].toInt(16)))
            }
            "jvc" -> {
                require(parts.size == 3) { "Formato JVC inválido: $text" }
                IrSignal(38000, jvc(parts[1].toInt(16), parts[2].toInt(16)))
            }
            "raw" -> {
                require(parts.size == 3) { "Formato raw inválido" }
                val pattern = parts[2].split(",").map { it.trim().toInt() }.toIntArray()
                require(pattern.isNotEmpty() && pattern.all { it > 0 }) { "Señal raw inválida" }
                IrSignal(parts[1].toInt(), pattern)
            }
            else -> error("Protocolo IR desconocido: ${parts[0]}")
        }
    }

    /** Canonical "nec:" form of a legacy "AA,CC"/"LL,HH,CC" code, or null if it isn't one. */
    fun legacyToNec(code: String): String? {
        val p = code.split(",").map { it.trim().removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: return null }
        fun hx(v: Int) = "%02x".format(v and 0xFF)
        return when (p.size) {
            2 -> "nec:" + hx(p[0]) + hx(p[0].inv()) + hx(p[1]) + hx(p[1].inv())
            3 -> "nec:" + hx(p[0]) + hx(p[1]) + hx(p[2]) + hx(p[2].inv())
            else -> null
        }
    }

    private fun encodeLegacy(text: String): IrSignal {
        val parts = text.split(",").map {
            it.trim().removePrefix("0x").removePrefix("0X").toIntOrNull(16)
                ?: throw IllegalArgumentException("Valor hexadecimal inválido: $it")
        }
        return when (parts.size) {
            2 -> IrSignal(38000, NecEncoder.encode(parts[0], parts[1]))
            3 -> IrSignal(38000, NecEncoder.encodeExtended(parts[0], parts[1], parts[2]))
            else -> throw IllegalArgumentException("Formato inválido: $text")
        }
    }

    private fun bytes4(parts: List<String>): IntArray {
        require(parts.size == 2 && parts[1].length == 8) { "Se esperaban 4 bytes en hexadecimal" }
        return IntArray(4) { parts[1].substring(it * 2, it * 2 + 2).toInt(16) }
    }

    // ---- protocol encoders ----

    private fun samsung(b: IntArray): IntArray {
        val out = ArrayList<Int>(70)
        out.add(4500); out.add(4500)
        for (byte in b) for (i in 0 until 8) {
            out.add(560)
            out.add(if (((byte shr i) and 1) == 1) 1690 else 560)
        }
        out.add(560)
        return out.toIntArray()
    }

    /** Sony SIRC: 40 kHz, 7 command bits then address bits (LSB first), frame sent 3 times at 45 ms. */
    private fun sirc(bits: Int, command: Int, address: Int): IntArray {
        val frame = ArrayList<Int>()
        frame.add(2400); frame.add(600)
        fun bit(b: Int) { frame.add(if (b == 1) 1200 else 600); frame.add(600) }
        for (i in 0 until 7) bit((command shr i) and 1)
        for (i in 0 until bits - 7) bit((address shr i) and 1)
        val gap = (45000 - frame.sum()).coerceAtLeast(0)
        frame[frame.size - 1] += gap
        val out = ArrayList<Int>(frame.size * 3)
        repeat(3) { out.addAll(frame) }
        return out.toIntArray()
    }

    /** RC5/RC6 repeat presses must flip a "toggle" bit or the receiver treats them as key-hold. */
    private var rcToggle = 0

    /** Builds a pattern from (level, duration) half-bits: merges equal neighbours, starts on a mark. */
    private fun fromLevels(levels: List<Pair<Boolean, Int>>): IntArray {
        val out = ArrayList<Int>()
        var current: Boolean? = null
        for ((mark, duration) in levels) {
            if (current == null) {
                if (!mark) continue // leading idle space carries no information
                current = true
                out.add(duration)
            } else if (current == mark) {
                out[out.size - 1] = out[out.size - 1] + duration
            } else {
                current = mark
                out.add(duration)
            }
        }
        if (out.size % 2 == 0) out.removeAt(out.size - 1) // end on a mark
        return out.toIntArray()
    }

    private fun rc5(address: Int, command: Int): IntArray {
        rcToggle = rcToggle xor 1
        val t = 889
        val bits = ArrayList<Int>()
        bits.add(1); bits.add(1); bits.add(rcToggle)
        for (i in 4 downTo 0) bits.add((address shr i) and 1)
        for (i in 5 downTo 0) bits.add((command shr i) and 1)
        val levels = ArrayList<Pair<Boolean, Int>>()
        for (b in bits) {
            if (b == 1) { levels.add(false to t); levels.add(true to t) }
            else { levels.add(true to t); levels.add(false to t) }
        }
        val frame = fromLevels(levels)
        val gap = (114000 - frame.sum()).coerceAtLeast(10000)
        val out = ArrayList<Int>()
        out.addAll(frame.toList()); out.add(gap); out.addAll(frame.toList())
        return out.toIntArray()
    }

    private fun rc6(address: Int, command: Int): IntArray {
        rcToggle = rcToggle xor 1
        val t = 444
        val levels = ArrayList<Pair<Boolean, Int>>()
        levels.add(true to 6 * t); levels.add(false to 2 * t) // leader
        fun bit(b: Int, width: Int = t) {
            if (b == 1) { levels.add(true to width); levels.add(false to width) }
            else { levels.add(false to width); levels.add(true to width) }
        }
        bit(1)                       // start bit
        bit(0); bit(0); bit(0)       // mode 0
        bit(rcToggle, 2 * t)         // trailer / toggle bit (double width)
        for (i in 7 downTo 0) bit((address shr i) and 1)
        for (i in 7 downTo 0) bit((command shr i) and 1)
        return fromLevels(levels)
    }

    private fun jvc(address: Int, command: Int): IntArray {
        val out = ArrayList<Int>(40)
        out.add(8400); out.add(4200)
        for (byte in intArrayOf(address, command)) for (i in 0 until 8) {
            out.add(526)
            out.add(if (((byte shr i) and 1) == 1) 1574 else 526)
        }
        out.add(526)
        return out.toIntArray()
    }
}
