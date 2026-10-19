package com.rnd.remoto.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class IrCodecTest {

    private fun encode(code: String): IrSignal = IrCodec.encode(code).getOrElse { fail("No codifica $code: ${it.message}"); throw it }

    /** Every pattern must alternate mark/space starting and ending on a mark, all durations > 0. */
    private fun assertWellFormed(signal: IrSignal) {
        assertTrue(signal.pattern.isNotEmpty())
        assertTrue(signal.pattern.all { it > 0 })
    }

    @Test
    fun legacyNecPairMatchesTheCanonicalForm() {
        val legacy = encode("07,02")
        val canonical = encode("nec:07f802fd")
        assertEquals(canonical.pattern.toList(), legacy.pattern.toList())
        assertEquals("nec:07f802fd", IrCodec.legacyToNec("07,02"))
        assertEquals("nec:00df1ce3", IrCodec.legacyToNec("00,df,1c"))
    }

    @Test
    fun necFrameHasLeaderThirtyTwoBitsAndStopMark() {
        val s = encode("nec:00df1ce3")
        assertEquals(38000, s.frequencyHz)
        assertEquals(2 + 32 * 2 + 1, s.pattern.size)
        assertEquals(9000, s.pattern[0])
        assertEquals(4500, s.pattern[1])
    }

    @Test
    fun necBitsAreSentLsbFirst() {
        // First byte 0x01: first bit is 1 (long space), the next seven are 0 (short space).
        val s = encode("nec:01000000")
        assertEquals(1687, s.pattern[3])
        for (bit in 1 until 8) assertEquals(562, s.pattern[3 + bit * 2])
    }

    @Test
    fun samsungUsesItsOwnLeader() {
        val s = encode("samsung:070702fd")
        assertEquals(4500, s.pattern[0])
        assertEquals(4500, s.pattern[1])
        assertEquals(2 + 32 * 2 + 1, s.pattern.size)
    }

    @Test
    fun sircSendsThreeFramesAt40kHz() {
        val s = encode("sirc:12:15:1")
        assertEquals(40000, s.frequencyHz)
        assertEquals(3 * (2 + 12 * 2), s.pattern.size)
        assertEquals(2400, s.pattern[0])
        assertWellFormed(s)
    }

    @Test
    fun rc5EndsOnMarkAndIsAtMostFourteenBitsTwice() {
        val s = encode("rc5:00:0c")
        assertEquals(36000, s.frequencyHz)
        assertEquals(1, s.pattern.size % 2) // starts and ends on a mark
        assertTrue(s.pattern.size <= 2 * 28 + 1)
        assertWellFormed(s)
    }

    @Test
    fun rc6HasLeaderAndEndsOnMark() {
        val s = encode("rc6:00:0c")
        assertEquals(2664, s.pattern[0])
        assertEquals(888, s.pattern[1])
        assertEquals(1, s.pattern.size % 2)
        assertWellFormed(s)
    }

    @Test
    fun jvcHasHeaderAndSixteenBits() {
        val s = encode("jvc:03:17")
        assertEquals(8400, s.pattern[0])
        assertEquals(2 + 16 * 2 + 1, s.pattern.size)
    }

    @Test
    fun rawIsPassedThrough() {
        val s = encode("raw:38000:9000,4500,560,560,560")
        assertEquals(38000, s.frequencyHz)
        assertEquals(listOf(9000, 4500, 560, 560, 560), s.pattern.toList())
    }

    @Test
    fun invalidCodesFailInsteadOfCrashing() {
        for (bad in listOf("", "zz,01", "07", "nec:123", "foo:1:2", "sirc:9:1:1", "raw:38000:abc")) {
            assertTrue("debería fallar: '$bad'", IrCodec.encode(bad).isFailure)
        }
    }
}
