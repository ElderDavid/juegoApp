package com.elderdavid.quientienelarazon.core.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {
    @Test
    fun lengthsAre8_10And12InFourRounds() {
        assertEquals(listOf(8, 10, 12), GameLength.entries.map { it.questions })
        assertEquals(GameLength.STANDARD, GameLength.DEFAULT)
        GameLength.entries.forEach { assertEquals(GameRules.ROUNDS, it.perRound.size) }
        assertEquals(4, RoundType.entries.size)
    }

    @Test
    fun standardGameHasFourRoundsOfThree() {
        val rounds = (0 until 12).map { GameLength.STANDARD.roundFor(it) }
        assertEquals(List(3) { RoundType.KNOWLEDGE } + List(3) { RoundType.SPEED } +
            List(3) { RoundType.STRATEGY } + List(3) { RoundType.FINAL }, rounds)
    }

    @Test
    fun shortGameHasFourRoundsOfTwo() {
        val rounds = (0 until 8).map { GameLength.SHORT.roundFor(it) }
        assertEquals(List(2) { RoundType.KNOWLEDGE } + List(2) { RoundType.SPEED } +
            List(2) { RoundType.STRATEGY } + List(2) { RoundType.FINAL }, rounds)
    }

    @Test
    fun mediumGameIsThreeTwoTwoThree() {
        val rounds = (0 until 10).map { GameLength.MEDIUM.roundFor(it) }
        assertEquals(List(3) { RoundType.KNOWLEDGE } + List(2) { RoundType.SPEED } +
            List(2) { RoundType.STRATEGY } + List(3) { RoundType.FINAL }, rounds)
    }

    @Test
    fun roundBoundaries() {
        val m = GameLength.MEDIUM
        assertEquals(listOf(0, 3, 5, 7), (0 until 10).filter { m.isFirstOfRound(it) })
        assertEquals(listOf(2, 4, 6, 9), (0 until 10).filter { m.isLastOfRound(it) })
        assertTrue(GameLength.SHORT.isLastOfRound(1))
        assertFalse(GameLength.STANDARD.isLastOfRound(1))
    }

    @Test
    fun fromQuestionsAcceptsOnlyKnownLengths() {
        assertEquals(GameLength.SHORT, GameLength.fromQuestions(8))
        assertEquals(GameLength.MEDIUM, GameLength.fromQuestions(10))
        assertEquals(GameLength.STANDARD, GameLength.fromQuestions(12))
        assertEquals(null, GameLength.fromQuestions(9))
    }

    @Test(expected = IllegalArgumentException::class)
    fun indexOutOfRangeIsRejected() {
        GameLength.SHORT.roundFor(8)
    }
}
