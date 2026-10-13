package com.elderdavid.quientienelarazon.core.local

import com.elderdavid.quientienelarazon.core.TestBank
import com.elderdavid.quientienelarazon.core.game.Bet
import com.elderdavid.quientienelarazon.core.game.GameConfig
import com.elderdavid.quientienelarazon.core.game.GameEngine
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.Phase
import com.elderdavid.quientienelarazon.core.game.RIGHT
import com.elderdavid.quientienelarazon.core.game.RoundType
import com.elderdavid.quientienelarazon.core.game.Scoring
import com.elderdavid.quientienelarazon.core.game.WRONG
import com.elderdavid.quientienelarazon.core.game.players
import com.elderdavid.quientienelarazon.core.profile.LocalHistory
import com.elderdavid.quientienelarazon.core.questions.Audience
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsModeTest {
    /** Banco de prueba en el que todas las preguntas generales son para niños. */
    private val kidsBank: QuestionBank = TestBank.standard().let { b ->
        QuestionBank(b.questions.filter { it.regionId == null }.map { it.copy(audience = Audience.KIDS) }, b.categories, b.regions)
    }
    private val engine = GameEngine(kidsBank)

    @Test
    fun kidsModeGivesOneAndAHalfTimesTheTime() {
        assertEquals(GameRules.QUESTION_TIME_MILLIS * 3 / 2, GameRules.KIDS_QUESTION_TIME_MILLIS)
        assertEquals(GameRules.KIDS_QUESTION_TIME_MILLIS, GameConfig(mode = ContentMode.Kids).questionTimeMillis)
        assertEquals(GameRules.QUESTION_TIME_MILLIS, GameConfig(mode = ContentMode.General).questionTimeMillis)
    }

    @Test
    fun kidsPoolOnlyHasKidsQuestionsAndOtherModesNever() {
        val mixed = TestBank.standard().let { b ->
            QuestionBank(b.questions + kidsBank.questions.map { it.copy(id = "k-" + it.id) }, b.categories, b.regions)
        }
        assertTrue(mixed.pool(ContentMode.Kids).all { it.audience == Audience.KIDS })
        assertTrue(mixed.pool(ContentMode.General).none { it.audience == Audience.KIDS })
        assertTrue(mixed.pool(ContentMode.Regional("cesar")).none { it.audience == Audience.KIDS })
    }

    @Test
    fun answeringDeadlineUsesKidsTime() {
        val m = LocalMatch.start(engine, players(2), ContentMode.Kids, seed = 3).rouletteDone().ready(now = 1_000)
        val answering = m.stage as LocalStage.Answering
        assertEquals(1_000 + GameRules.KIDS_QUESTION_TIME_MILLIS, answering.deadline)
        // A los 10 s todavía vale la respuesta (en el modo normal ya se habría agotado).
        val answered = m.answer(RIGHT, now = 11_000)
        assertEquals(RIGHT, answered.state.answers.values.single().choice)
    }

    @Test
    fun wrongBetDoesNotSubtractInKidsMode() {
        var m = LocalMatch.start(engine, players(2), ContentMode.Kids, seed = 5)
        var now = 0L
        // Todos aciertan hasta la ronda de estrategia para tener saldo con el que apostar.
        while (!(m.state.round == RoundType.STRATEGY && m.stage is LocalStage.Roulette)) {
            m = step(m, RIGHT, now).also { now += 1_000 }
        }
        val before = m.state.scores
        m = m.rouletteDone()
        while (m.state.phase == Phase.QUESTION) {
            m = m.ready(now)
            val betting = m.stage as LocalStage.Betting
            m = m.bet(betting.allowedBets.max(), now).answer(WRONG, now + 500)
        }
        val result = m.state.lastResult!!
        assertTrue(result.outcomes.all { it.bet is Bet.Amount && it.won == false && it.pointsDelta == 0 })
        assertEquals(before, m.state.scores)
    }

    @Test
    fun wrongBetStillSubtractsOutsideKidsMode() {
        assertEquals(-100, Scoring.betDelta(Bet.Amount(100), won = false))
        assertEquals(0, Scoring.betDelta(Bet.Amount(100), won = false, noLoss = true))
        assertEquals(200, Scoring.betDelta(Bet.Amount(100), won = true, noLoss = true))
    }

    @Test
    fun noMatchPackPurchasesInKidsMode() {
        val m = LocalMatch.start(engine, players(2), ContentMode.Kids, seed = 4)
        assertFalse(m.canBuyMatchPack)
        assertEquals(m, m.addMatchPack())
    }

    @Test
    fun rematchKeepsKidsMode() {
        val m = LocalMatch.start(engine, players(2), ContentMode.Kids, seed = 1)
        val again = m.rematch(seed = 2)
        assertTrue(again.state.config.kids)
        assertEquals(GameRules.KIDS_QUESTION_TIME_MILLIS, again.state.config.questionTimeMillis)
    }

    @Test
    fun historyRemembersKidsGames() {
        var m = LocalMatch.start(engine, players(2), ContentMode.Kids, seed = 9)
        var now = 0L
        while (m.stage !is LocalStage.Finished) m = step(m, RIGHT, now).also { now += 1_000 }
        val summary = LocalHistory().record(m.state, finishedAtMillis = 1).recent.single()
        assertTrue(summary.kids)
        assertEquals(null, summary.regionId)
        assertFalse(LocalHistory.decode(LocalHistory().encode()).recent.any { it.kids })
        assertTrue(LocalHistory.decode(LocalHistory().record(m.state, 1).encode()).recent.single().kids)
    }

    /** Avanza una etapa: todos responden [choice] (apostando lo mínimo) y se pasa la revelación. */
    private fun step(match: LocalMatch, choice: Int, now: Long): LocalMatch = when (val s = match.stage) {
        is LocalStage.Roulette -> match.rouletteDone()
        is LocalStage.Handoff -> match.ready(now)
        is LocalStage.Betting -> match.bet(s.allowedBets.minOrNull() ?: 0, now)
        is LocalStage.Answering -> match.answer(choice, now + 500)
        else -> match.next()
    }
}
