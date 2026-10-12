package com.elderdavid.quientienelarazon.core.profile

import com.elderdavid.quientienelarazon.core.game.Driver
import com.elderdavid.quientienelarazon.core.game.GameConfig
import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.game.Move
import com.elderdavid.quientienelarazon.core.game.RIGHT
import com.elderdavid.quientienelarazon.core.game.RoundType
import com.elderdavid.quientienelarazon.core.game.WRONG
import com.elderdavid.quientienelarazon.core.game.betFor
import com.elderdavid.quientienelarazon.core.game.players
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalHistoryTest {

    private fun finishedGame(seed: Long, config: GameConfig = GameConfig()): GameState {
        val driver = Driver()
        val ps = players(3)
        val winner = ps[0].id
        val s = driver.engine.newGame(ps, config, seed = seed)
        return driver.playMain(s) { _, id, st ->
            val choice = if (id == winner) RIGHT else WRONG
            if (st.round == RoundType.STRATEGY) Move(choice, bet = betFor(st, id)) else Move(choice)
        }
    }

    @Test
    fun recordsFinishedGameWithNamesScoresAndWinner() {
        val game = finishedGame(seed = 1)
        val history = LocalHistory().record(game, finishedAtMillis = 1_000)
        assertEquals(1, history.gamesFinished)
        val summary = history.recent.single()
        assertEquals(listOf("Jugador 1", "Jugador 2", "Jugador 3"), summary.players.map { it.name })
        assertEquals(listOf("Jugador 1"), summary.winners)
        assertNull(summary.regionId)
        assertEquals(game.scores.values.max(), history.bestScore)
        assertEquals("Jugador 1", history.bestScoreName)
    }

    @Test
    fun keepsOnlyTheMostRecentGamesNewestFirst() {
        var history = LocalHistory()
        repeat(LocalHistory.MAX_RECENT + 3) { i -> history = history.record(finishedGame(seed = i.toLong()), finishedAtMillis = i.toLong()) }
        assertEquals(LocalHistory.MAX_RECENT + 3, history.gamesFinished)
        assertEquals(LocalHistory.MAX_RECENT, history.recent.size)
        assertEquals((LocalHistory.MAX_RECENT + 2).toLong(), history.recent.first().finishedAtMillis)
    }

    @Test
    fun remembersRegion() {
        val game = finishedGame(seed = 2, config = GameConfig(mode = ContentMode.Regional("cesar")))
        assertEquals("cesar", LocalHistory().record(game, 0).recent.single().regionId)
    }

    @Test
    fun rejectsUnfinishedGames() {
        val driver = Driver()
        val started = driver.engine.newGame(players(2), GameConfig(), seed = 1)
        assertThrows(IllegalArgumentException::class.java) { LocalHistory().record(started, 0) }
    }

    @Test
    fun survivesEncodingAndCorruptedData() {
        val history = LocalHistory().record(finishedGame(seed = 4), 77)
        assertEquals(history, LocalHistory.decode(history.encode()))
        assertEquals(LocalHistory(), LocalHistory.decode(null))
        assertEquals(LocalHistory(), LocalHistory.decode("{no es json"))
        assertEquals(LocalHistory(), LocalHistory.decode("""{"games":"muchas"}"""))
    }
}
