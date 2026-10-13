package com.elderdavid.quientienelarazon.core.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSetupTest {
    @Test
    fun validNamesCanStart() {
        assertTrue(LocalSetup.canStart(listOf("Ana", "Luis")))
        assertEquals(listOf("Ana", "Luis María"), LocalSetup.players(listOf(" Ana ", "Luis   María")).map { it.name })
    }

    @Test
    fun rejectsEmptyLongAndDuplicateNames() {
        val issues = LocalSetup.issues(listOf("Ana", "  ", "ANA", "x".repeat(17)))
        assertEquals(mapOf(1 to NameIssue.EMPTY, 2 to NameIssue.DUPLICATE, 3 to NameIssue.TOO_LONG), issues)
    }

    @Test
    fun playerCountMustBeOneToFour() {
        assertTrue(LocalSetup.canStart(listOf("Ana")))
        assertFalse(LocalSetup.canStart(emptyList()))
        assertFalse(LocalSetup.canStart(listOf("A", "B", "C", "D", "E")))
        assertTrue(LocalSetup.canStart(listOf("A", "B", "C", "D")))
    }
}
