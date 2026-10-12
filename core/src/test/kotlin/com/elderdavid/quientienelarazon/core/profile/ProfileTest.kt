package com.elderdavid.quientienelarazon.core.profile

import com.elderdavid.quientienelarazon.core.game.Driver
import com.elderdavid.quientienelarazon.core.game.GameConfig
import com.elderdavid.quientienelarazon.core.game.Move
import com.elderdavid.quientienelarazon.core.game.RIGHT
import com.elderdavid.quientienelarazon.core.game.RoundType
import com.elderdavid.quientienelarazon.core.game.WRONG
import com.elderdavid.quientienelarazon.core.game.betFor
import com.elderdavid.quientienelarazon.core.game.players
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileNameTest {
    @Test
    fun acceptsNormalNames() {
        listOf("Elder", "María José", "Vergara", "Computador", "Ana_23", "Juan-Pablo", "Calculo").forEach {
            assertNull(it, ProfileName.validate(it))
        }
    }

    @Test
    fun rejectsLengthAndCharacters() {
        assertEquals(ProfileNameIssue.TOO_SHORT, ProfileName.validate(" a "))
        assertEquals(ProfileNameIssue.TOO_LONG, ProfileName.validate("x".repeat(17)))
        assertEquals(ProfileNameIssue.INVALID_CHARACTERS, ProfileName.validate("hola<script>"))
    }

    @Test
    fun rejectsOffensiveNamesIncludingDisguisedOnes() {
        listOf("Gonorrea", "malparido10", "HIJUEPUTA", "el pUt0", "m1erda", "Hitler88").forEach {
            assertEquals(it, ProfileNameIssue.OFFENSIVE, ProfileName.validate(it))
        }
    }

    @Test
    fun avatarsAreBounded() {
        assertTrue(Avatars.isValid(0))
        assertFalse(Avatars.isValid(Avatars.COUNT))
    }
}

class PlayerStatsTest {
    @Test
    fun levelsGrowProgressively() {
        assertEquals(1, Levels.levelFor(0))
        assertEquals(1, Levels.levelFor(99))
        assertEquals(2, Levels.levelFor(100))
        assertEquals(3, Levels.levelFor(300))
        assertEquals(4, Levels.levelFor(600))
    }

    @Test
    fun finishedGameProducesDeltaForWinnerAndLoser() {
        val driver = Driver()
        val ps = players(2)
        val winner = ps[0].id
        var s = driver.engine.newGame(ps, GameConfig(), seed = 3)
        s = driver.playMain(s) { _, id, st ->
            val choice = if (id == winner) RIGHT else WRONG
            if (st.round == RoundType.STRATEGY) Move(choice, bet = betFor(st, id)) else Move(choice)
        }
        val w = Levels.deltaFor(s, winner)
        val l = Levels.deltaFor(s, ps[1].id)
        assertTrue(w.won)
        assertFalse(l.won)
        assertEquals(12, w.correctAnswers)
        assertEquals(0, l.correctAnswers)
        assertEquals(Levels.XP_PER_GAME + 12 * Levels.XP_PER_CORRECT + Levels.XP_PER_WIN, w.xp)

        val stats = PlayerStats() + w + l
        assertEquals(2, stats.gamesPlayed)
        assertEquals(1, stats.wins)
        assertEquals(1, stats.losses)
        assertEquals(50, stats.accuracyPercent)
        assertEquals(s.scores.getValue(winner), stats.bestScore)
    }
}
