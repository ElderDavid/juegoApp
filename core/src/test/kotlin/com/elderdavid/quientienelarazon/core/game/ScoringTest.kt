package com.elderdavid.quientienelarazon.core.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {
    private val limit = 8_000L

    @Test fun speedBonusIsMaxForInstantAnswer() = assertEquals(50, Scoring.speedBonus(0, limit))
    @Test fun speedBonusIsZeroAtTimeLimit() = assertEquals(0, Scoring.speedBonus(limit, limit))
    @Test fun speedBonusAtHalfTimeIsHalf() = assertEquals(25, Scoring.speedBonus(4_000, limit))
    @Test fun speedBonusUsesIntegerDivision() = assertEquals(31, Scoring.speedBonus(3_000, limit)) // 50*5000/8000 = 31.25

    @Test
    fun speedBonusIsClampedOutsideRange() {
        assertEquals(50, Scoring.speedBonus(-500, limit))
        assertEquals(0, Scoring.speedBonus(20_000, limit))
    }

    @Test
    fun speedBonusIsMonotonicAndBounded() {
        var previous = Int.MAX_VALUE
        for (t in 0..limit step 100) {
            val b = Scoring.speedBonus(t, limit)
            assertTrue(b in 0..50)
            assertTrue(b <= previous)
            previous = b
        }
    }

    @Test
    fun allowedBetsDependOnBalance() {
        assertEquals(emptyList<Int>(), Scoring.allowedBets(0))
        assertEquals(emptyList<Int>(), Scoring.allowedBets(49))
        assertEquals(listOf(50), Scoring.allowedBets(50))
        assertEquals(listOf(50), Scoring.allowedBets(99))
        assertEquals(listOf(50, 100), Scoring.allowedBets(100))
    }

    @Test
    fun defaultBetIsLowestAllowedOrFree() {
        assertEquals(Bet.Free, Scoring.defaultBet(30))
        assertEquals(Bet.Amount(50), Scoring.defaultBet(400))
    }

    @Test
    fun betDeltas() {
        assertEquals(200, Scoring.betDelta(Bet.Amount(100), won = true))
        assertEquals(19, Scoring.heistAmount(99))
        assertEquals(0, Scoring.heistAmount(0))
        assertEquals(-50, Scoring.betDelta(Bet.Amount(50), won = false))
        assertEquals(GameRules.FREE_BET_REWARD, Scoring.betDelta(Bet.Free, won = true))
        assertEquals(0, Scoring.betDelta(Bet.Free, won = false))
    }
}
