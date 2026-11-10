package com.elderdavid.quientienelarazon.core.local

import com.elderdavid.quientienelarazon.core.game.GameConfig
import com.elderdavid.quientienelarazon.core.game.GameEngine
import com.elderdavid.quientienelarazon.core.game.GameLength
import com.elderdavid.quientienelarazon.core.game.GamePlanner
import com.elderdavid.quientienelarazon.core.game.players
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import com.elderdavid.quientienelarazon.core.questions.QuestionBankLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentQuestionsTest {
    private val bank = QuestionBankLoader.fromResources()
    private val engine = GameEngine(bank)

    /** Juega [games] partidas seguidas como lo hace la app y devuelve las preguntas de cada una. */
    private fun playSeries(mode: ContentMode, playerCount: Int, games: Int, length: GameLength = GameLength.DEFAULT): List<List<String>> {
        var recent = RecentQuestions()
        val pool = GamePlanner.candidatePool(bank, mode, playerCount, length)
        return (0 until games).map { seed ->
            val avoid = recent.avoidFor(pool, GamePlanner.questionsNeeded(length))
            val state = engine.newGame(players(playerCount), GameConfig(mode = mode, length = length), seed.toLong(), avoid)
            recent = recent.plus(state)
            state.plan.questions.map { it.questionId } + state.plan.tiebreakQueue
        }
    }

    @Test
    fun normalModeDoesNotRepeatQuestionsAcrossManyGames() {
        for (n in 2..4) {
            val all = playSeries(ContentMode.General, n, games = 15).flatten()
            assertEquals("$n jugadores", all.size, all.toSet().size)
        }
    }

    @Test
    fun soloNormalModeAlsoAvoidsRepeats() {
        val all = playSeries(ContentMode.General, 1, games = 8).flatten()
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun smallModesKeepWorkingWhenTheyRunOutOfNewQuestions() {
        // 40 preguntas por departamento: se evitan solo algunas de las recientes y la partida sigue saliendo completa.
        val games = playSeries(ContentMode.Regional("bogota"), 3, games = 10)
        games.forEach { assertEquals(it.size, it.toSet().size) }
        assertTrue(games.all { it.size == GamePlanner.questionsNeeded(GameLength.DEFAULT) })
    }

    @Test
    fun keepsTheMostRecentLastAndSurvivesEncoding() {
        val recent = RecentQuestions().plus(listOf("a", "b", "c")).plus(listOf("b", "d"))
        assertEquals(listOf("a", "c", "b", "d"), recent.ids)
        assertEquals(recent, RecentQuestions.decode(recent.encode()))
        assertEquals(RecentQuestions(), RecentQuestions.decode(null))
        val many = RecentQuestions().plus((1..RecentQuestions.MAX_IDS + 10).map { "q$it" })
        assertEquals(RecentQuestions.MAX_IDS, many.ids.size)
        assertEquals("q${RecentQuestions.MAX_IDS + 10}", many.ids.last())
    }
}
