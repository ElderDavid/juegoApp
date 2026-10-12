package com.elderdavid.quientienelarazon.core.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomCodeTest {
    @Test
    fun normalizesWhatPeopleType() {
        assertEquals("ABC234", RoomCode.normalize(" abc-234 "))
        assertEquals("ABC234", RoomCode.normalize("abc 234"))
    }

    @Test
    fun acceptsOnlyServerAlphabet() {
        assertTrue(RoomCode.isValid("ABC234"))
        assertTrue(RoomCode.isValid("xyz 789"))
        assertFalse("sin O", RoomCode.isValid("ABCO23"))
        assertFalse("sin I", RoomCode.isValid("ABCI23"))
        assertFalse("sin 0", RoomCode.isValid("ABC023"))
        assertFalse("sin 1", RoomCode.isValid("ABC123"))
        assertFalse("muy corto", RoomCode.isValid("ABC23"))
        assertFalse("muy largo", RoomCode.isValid("ABC2345"))
    }

    @Test
    fun alphabetMatchesServerCheck() {
        // Mismo conjunto que la restricción de rooms.code en la migración: A-H, J-N, P-Z, 2-9.
        val server = (('A'..'H') + ('J'..'N') + ('P'..'Z') + ('2'..'9')).joinToString("")
        assertEquals(server, RoomCode.ALPHABET)
    }

    @Test
    fun displaysInTwoBlocks() {
        assertEquals("ABC 234", RoomCode.display("abc234"))
    }
}
