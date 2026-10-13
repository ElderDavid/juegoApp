package com.elderdavid.quientienelarazon.core.local

import com.elderdavid.quientienelarazon.core.TestBank
import com.elderdavid.quientienelarazon.core.game.GameEngine
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.RIGHT
import com.elderdavid.quientienelarazon.core.game.RoundType
import com.elderdavid.quientienelarazon.core.game.WRONG
import com.elderdavid.quientienelarazon.core.game.players
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMatchTest {
    private val engine = GameEngine(TestBank.standard())
    private fun newMatch(n: Int = 2, seed: Long = 7) = LocalMatch.start(engine, players(n), ContentMode.General, seed)

    /** Lleva la partida hasta que el jugador del turno ve la pregunta. */
    private fun toAnswering(m: LocalMatch, now: Long): LocalMatch {
        var match = m
        if (match.stage is LocalStage.Roulette) match = match.rouletteDone()
        match = match.ready(now)
        val stage = match.stage
        if (stage is LocalStage.Betting) match = match.bet(stage.allowedBets.minOrNull() ?: 0, now)
        return match
    }

    @Test
    fun soloGamePlaysToTheEndAndTheOnlyPlayerWins() {
        var m = newMatch(1)
        assertTrue(m.solo)
        var now = 0L
        while (m.stage !is LocalStage.Finished) {
            m = when (m.stage) {
                is LocalStage.Reveal, is LocalStage.RoundSummary -> m.next()
                else -> toAnswering(m, now).answer(RIGHT, now + 500)
            }
            now += 10_000
        }
        assertEquals(listOf(m.players.single().id), (m.stage as LocalStage.Finished).winners)
    }

    @Test
    fun fastModeIsKeptForTheRematch() {
        val m = LocalMatch.start(engine, players(2), ContentMode.General, seed = 1, fast = true)
        val answering = m.rouletteDone().ready(now = 0).stage as LocalStage.Answering
        assertEquals(GameRules.FAST_QUESTION_TIME_MILLIS, answering.deadline)
        assertTrue(m.rematch(seed = 2).state.config.fast)
    }

    @Test
    fun questionStaysHiddenUntilPlayerIsReady() {
        val m = newMatch().rouletteDone()
        val handoff = m.stage as LocalStage.Handoff
        assertEquals(m.state.currentTurn, handoff.playerId)
        // Responder durante el traspaso no hace nada.
        assertSame(m, m.answer(RIGHT, now = 100))
        val answering = m.ready(now = 1_000).stage as LocalStage.Answering
        assertEquals(1_000, answering.startedAt)
        assertEquals(1_000 + GameRules.QUESTION_TIME_MILLIS, answering.deadline)
    }

    @Test
    fun eachPlayerGetsTheirOwnHandoffAndThenReveal() {
        var m = toAnswering(newMatch(3), now = 0)
        val first = (m.stage as LocalStage.Answering).playerId
        m = m.answer(RIGHT, now = 2_000)
        val second = (m.stage as LocalStage.Handoff).playerId
        assertTrue(first != second)
        m = toAnswering(m, now = 10_000).answer(WRONG, now = 11_000)
        m = toAnswering(m, now = 20_000).answer(RIGHT, now = 21_000)
        val reveal = m.stage as LocalStage.Reveal
        assertEquals(3, reveal.result.outcomes.size)
        assertEquals(setOf(RIGHT), reveal.result.winningOptions)
    }

    @Test
    fun doubleTapDoesNotAnswerTwice() {
        var m = toAnswering(newMatch(), now = 0).answer(RIGHT, now = 500)
        val after = m
        m = m.answer(RIGHT, now = 600) // el siguiente jugador aún no tiene el teléfono
        assertSame(after, m)
        assertEquals(1, m.state.answers.size)
    }

    @Test
    fun lateAnswerCountsAsTimeout() {
        val m = toAnswering(newMatch(), now = 0).answer(RIGHT, now = GameRules.QUESTION_TIME_MILLIS + 1)
        val answer = m.state.answers.values.single()
        assertNull(answer.choice)
    }

    @Test
    fun expireIsIgnoredWhileTimeRemains() {
        val m = toAnswering(newMatch(), now = 0)
        assertSame(m, m.expire(now = GameRules.QUESTION_TIME_MILLIS - 1))
        val expired = m.expire(now = GameRules.QUESTION_TIME_MILLIS)
        assertNull(expired.state.answers.values.single().choice)
    }

    @Test
    fun strategyRoundAsksForBetBeforeShowingQuestionAndTimer() {
        var m = newMatch()
        // Juega las 6 primeras preguntas acertando para tener saldo.
        while (m.state.round != RoundType.STRATEGY || m.stage !is LocalStage.Roulette) {
            m = when (m.stage) {
                is LocalStage.Roulette, is LocalStage.Handoff -> toAnswering(m, now = 0)
                is LocalStage.Answering -> m.answer(RIGHT, now = 1_000)
                else -> m.next()
            }
        }
        m = m.rouletteDone().ready(now = 5_000)
        val betting = m.stage as LocalStage.Betting
        assertEquals(GameRules.ALLOWED_BETS, betting.allowedBets)
        assertSame(m, m.bet(75, now = 6_000)) // apuesta no permitida
        m = m.bet(100, now = 9_000)
        val answering = m.stage as LocalStage.Answering
        assertEquals(9_000, answering.startedAt) // el tiempo empieza al cerrar la apuesta
    }

    @Test
    fun fullGameFinishesWithWinnerAndRematchAvoidsSeenQuestions() {
        var m = newMatch(2)
        val leader = m.players.first().id
        var guard = 0
        while (m.stage !is LocalStage.Finished && guard++ < 500) {
            m = when (val s = m.stage) {
                is LocalStage.Roulette, is LocalStage.Handoff -> toAnswering(m, now = 0)
                is LocalStage.Answering -> m.answer(if (s.playerId == leader) RIGHT else WRONG, now = 1_000)
                else -> m.next()
            }
        }
        assertEquals(listOf(leader), (m.stage as LocalStage.Finished).winners)

        val seen = m.state.plan.questions.map { it.questionId }.toSet()
        val rematch = m.rematch(seed = 99)
        assertTrue(rematch.stage is LocalStage.Roulette)
        assertEquals(m.players, rematch.players)
        assertTrue(rematch.state.scores.values.all { it == 0 })
        val repeated = rematch.state.plan.questions.count { it.questionId in seen }
        assertEquals(0, repeated)
    }

    @Test
    fun tieGoesToTiebreakWithHandoffForTiedPlayersOnly() {
        var m = newMatch(3)
        val loser = m.players.last().id
        var guard = 0
        while (m.stage !is LocalStage.Finished && m.state.tiebreakPlayers.isEmpty() && guard++ < 500) {
            m = when (val s = m.stage) {
                is LocalStage.Roulette, is LocalStage.Handoff -> toAnswering(m, now = 0)
                // Mismas respuestas y tiempos para los dos primeros: empatan.
                is LocalStage.Answering -> m.answer(if (s.playerId == loser) WRONG else RIGHT, now = 1_000)
                else -> m.next()
            }
        }
        val handoff = m.stage as LocalStage.Handoff
        assertTrue(handoff.tiebreak)
        assertTrue(loser !in m.state.tiebreakPlayers)
        assertEquals(2, m.state.tiebreakPlayers.size)
    }
}
