package com.elderdavid.quientienelarazon.core.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameRulesTest {
    @Test
    fun standardGameHasTwelveQuestionsInFourRoundsOfThree() {
        assertEquals(12, GameRules.TOTAL_QUESTIONS)
        assertEquals(4, RoundType.entries.size)
    }

    @Test
    fun questionIndexMapsToRoundInOrder() {
        val rounds = (0 until GameRules.TOTAL_QUESTIONS).map { RoundType.forQuestionIndex(it) }
        assertEquals(List(3) { RoundType.KNOWLEDGE } + List(3) { RoundType.SPEED } +
            List(3) { RoundType.STRATEGY } + List(3) { RoundType.FINAL }, rounds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun indexOutOfRangeIsRejected() {
        RoundType.forQuestionIndex(GameRules.TOTAL_QUESTIONS)
    }
}
