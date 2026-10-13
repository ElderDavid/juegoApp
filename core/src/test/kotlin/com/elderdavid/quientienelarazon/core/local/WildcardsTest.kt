package com.elderdavid.quientienelarazon.core.local

import com.elderdavid.quientienelarazon.core.TestBank
import com.elderdavid.quientienelarazon.core.game.GameEngine
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.RIGHT
import com.elderdavid.quientienelarazon.core.game.players
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardsTest {
    private val engine = GameEngine(TestBank.standard())
    private fun newMatch(n: Int = 2) = LocalMatch.start(engine, players(n), ContentMode.General, seed = 11)

    private fun toAnswering(m: LocalMatch, now: Long): LocalMatch {
        var match = m
        if (match.stage is LocalStage.Roulette) match = match.rouletteDone()
        match = match.ready(now)
        val stage = match.stage
        if (stage is LocalStage.Betting) match = match.bet(stage.allowedBets.minOrNull() ?: 0, now)
        return match
    }

    private fun answering(m: LocalMatch) = m.stage as LocalStage.Answering

    @Test
    fun everyPlayerStartsWithOneFreeWildcard() {
        val m = newMatch(3)
        assertEquals(m.players.associate { it.id to WildcardRules.FREE_PER_PLAYER }, m.wildcards)
        val stage = answering(toAnswering(m, 0))
        assertEquals(1, stage.wildcards)
        assertTrue(Wildcard.EXTRA_TIME in stage.usableWildcards)
        assertTrue(Wildcard.FIFTY_FIFTY in stage.usableWildcards)
        assertTrue(Wildcard.CHANGE_QUESTION in stage.usableWildcards)
    }

    @Test
    fun extraTimeMovesTheDeadlineAndAcceptsLaterAnswers() {
        val m = toAnswering(newMatch(), 0).useWildcard(Wildcard.EXTRA_TIME, now = 100)
        val stage = answering(m)
        assertEquals(GameRules.QUESTION_TIME_MILLIS + WildcardRules.EXTRA_TIME_MILLIS, stage.deadline)
        assertEquals(0, stage.wildcards)
        assertTrue(stage.usableWildcards.isEmpty())
        // Sin comodín sería tarde; con él la respuesta vale.
        val answered = m.answer(RIGHT, now = GameRules.QUESTION_TIME_MILLIS + 2_000)
        assertEquals(RIGHT, answered.state.answers.values.single().choice)
        // El tiempo extra no se arrastra a la siguiente pregunta.
        assertTrue(answered.state.bonusTimeMillis.isNotEmpty())
        assertSame(m.expire(GameRules.QUESTION_TIME_MILLIS + 1), m)
    }

    @Test
    fun fiftyFiftyHidesTwoWrongOptionsOnlyForThatTurn() {
        val m = toAnswering(newMatch(), 0).useWildcard(Wildcard.FIFTY_FIFTY, now = 100)
        val hidden = answering(m).hiddenOptions
        assertEquals(2, hidden.size)
        assertFalse(RIGHT in hidden)
        val next = toAnswering(m.answer(RIGHT, 500), 1_000)
        assertTrue(answering(next).hiddenOptions.isEmpty())
    }

    @Test
    fun changeQuestionKeepsCategoryAndRestartsTime() {
        val before = toAnswering(newMatch(), 0)
        val old = before.state.currentPlanned!!
        val m = before.useWildcard(Wildcard.CHANGE_QUESTION, now = 3_000)
        val now = m.state.currentPlanned!!
        assertNotEquals(old.questionId, now.questionId)
        assertEquals(old.categoryId, now.categoryId)
        assertEquals(3_000, answering(m).startedAt)
        assertFalse(now.questionId in m.state.plan.questions.filterIndexed { i, _ -> i != m.state.questionIndex }.map { it.questionId })
    }

    @Test
    fun changeQuestionIsNotOfferedOnceSomeoneAnswered() {
        var m = newMatch().let { mm ->
            // El segundo jugador también tiene comodín, pero la pregunta ya la respondió otro.
            toAnswering(mm, 0).answer(RIGHT, 500)
        }
        m = toAnswering(m, 1_000)
        val stage = answering(m)
        assertFalse(Wildcard.CHANGE_QUESTION in stage.usableWildcards)
        assertSame(m, m.useWildcard(Wildcard.CHANGE_QUESTION, 1_100))
    }

    @Test
    fun matchPackGivesOneMoreToEveryoneOncePerMatch() {
        val m = newMatch(4)
        assertTrue(m.canBuyMatchPack)
        val packed = m.addMatchPack()
        assertTrue(packed.wildcards.values.all { it == WildcardRules.FREE_PER_PLAYER + 1 })
        assertFalse(packed.canBuyMatchPack)
        assertSame(packed, packed.addMatchPack())
    }

    @Test
    fun oneWildcardPerQuestionEvenWithMoreLeft() {
        val m = toAnswering(newMatch().addMatchPack(), 0).useWildcard(Wildcard.EXTRA_TIME, 100)
        assertEquals(1, answering(m).wildcards)
        assertTrue(answering(m).usableWildcards.isEmpty())
        assertSame(m, m.useWildcard(Wildcard.FIFTY_FIFTY, 200))
    }

    @Test
    fun rematchResetsWildcards() {
        val m = toAnswering(newMatch().addMatchPack(), 0).useWildcard(Wildcard.EXTRA_TIME, 100)
        val again = m.rematch(seed = 3)
        assertNotNull(again)
        assertFalse(again.matchPackUsed)
        assertTrue(again.wildcards.values.all { it == WildcardRules.FREE_PER_PLAYER })
    }
}
