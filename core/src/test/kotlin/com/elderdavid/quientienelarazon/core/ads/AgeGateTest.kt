package com.elderdavid.quientienelarazon.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgeGateTest {
    private val year = 2026

    @Test
    fun youngerThanThirteenIsAChild() {
        assertEquals(AgeGroup.CHILD, AgeGate.groupFor(2020, year))
        assertEquals(AgeGroup.CHILD, AgeGate.groupFor(2014, year)) // 11 o 12 años
    }

    @Test
    fun whenInDoubtTreatAsChild() {
        // Nacido en 2013: puede tener 12 o 13 años según el mes, así que cuenta como niño.
        assertEquals(AgeGroup.CHILD, AgeGate.groupFor(2013, year))
        assertEquals(AgeGroup.TEEN_OR_ADULT, AgeGate.groupFor(2012, year))
        assertEquals(AgeGroup.TEEN_OR_ADULT, AgeGate.groupFor(1980, year))
    }

    @Test
    fun invalidYearsAreRejected() {
        assertNull(AgeGate.groupFor(2027, year))
        assertNull(AgeGate.groupFor(1900, year))
        assertNull(AgeGate.parse("", year))
        assertNull(AgeGate.parse("85", year))
        assertNull(AgeGate.parse("19a5", year))
        assertNull(AgeGate.parse("19850", year))
    }

    @Test
    fun parsesTypedYear() {
        assertEquals(AgeGroup.TEEN_OR_ADULT, AgeGate.parse(" 1990 ", year))
        assertEquals(AgeGroup.CHILD, AgeGate.parse("2018", year))
    }
}
