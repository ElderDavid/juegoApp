package com.elderdavid.quientienelarazon.core.online

import org.junit.Assert.assertEquals
import org.junit.Test

class PhaseClockTest {
    @Test
    fun countsDownFromReceipt() {
        val clock = PhaseClock(remainingAtReceipt = 7_500, receivedAt = 1_000, totalMillis = 8_000)
        assertEquals(7_500, clock.remaining(1_000))
        assertEquals(5_000, clock.remaining(3_500))
        assertEquals(0, clock.remaining(20_000))
        assertEquals(8, clock.secondsShown(1_000))
        assertEquals(5, clock.secondsShown(3_500))
        assertEquals(1, clock.secondsShown(8_499))
        assertEquals(0, clock.secondsShown(8_500))
    }

    @Test
    fun neverExceedsTotal() {
        // Un dato del servidor raro (o un reloj que retrocede) no puede mostrar más tiempo del que hay.
        val clock = PhaseClock(remainingAtReceipt = 9_000, receivedAt = 1_000, totalMillis = 8_000)
        assertEquals(8_000, clock.remaining(1_000))
        assertEquals(8_000, clock.remaining(500))
        assertEquals(1f, clock.fraction(1_000))
    }

    @Test
    fun elapsedIsNeverNegative() {
        assertEquals(1_200, clientElapsed(questionShownAt = 1_000, now = 2_200))
        assertEquals(0, clientElapsed(questionShownAt = 2_000, now = 1_000))
    }
}
