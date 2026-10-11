package com.elderdavid.quientienelarazon.core.game

import com.elderdavid.quientienelarazon.core.TestBank
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    private val d = Driver()
    private val engine = d.engine
    private fun newGame(n: Int = 2, sequential: Boolean = true, seed: Long = 7) =
        engine.newGame(players(n), GameConfig(sequentialTurns = sequential), seed)

    private val p1 = PlayerId("p1")
    private val p2 = PlayerId("p2")
    private val p3 = PlayerId("p3")

    // --- Ronda 1: conocimiento ---

    @Test
    fun knowledgeRoundGives100ForCorrectAndZeroForWrong() {
        val s = d.play(newGame()) { if (it == p1) Move(RIGHT, elapsed = 7_900) else Move(WRONG, elapsed = 100) }
        assertEquals(Phase.REVEAL, s.phase)
        assertEquals(100, s.scores[p1]) // sin bonificación por rapidez en la ronda 1
        assertEquals(0, s.scores[p2])
    }

    @Test
    fun timeoutCountsAsNoAnswer() {
        val s = d.play(newGame()) { Move(null, elapsed = 8_000) }
        assertEquals(0, s.scores[p1])
        assertEquals(false, s.lastResult!!.outcomes.first().won)
    }

    // --- Turnos y validaciones ---

    @Test
    fun cannotAnswerOutOfTurnInSequentialMode() {
        val s = engine.startQuestion(newGame()).ok()
        val waiting = s.turnOrder[1]
        assertEquals(Rejection.NOT_YOUR_TURN, engine.submitAnswer(s, waiting, RIGHT, 100).rejection())
    }

    @Test
    fun firstTurnRotatesEachQuestion() {
        var s = newGame(3)
        val firsts = mutableListOf<PlayerId>()
        repeat(3) {
            firsts += s.turnOrder.first()
            s = d.next(d.play(s) { Move(RIGHT) })
        }
        assertEquals(3, firsts.toSet().size)
    }

    @Test
    fun simultaneousModeAcceptsAnyOrder() {
        var s = engine.startQuestion(newGame(3, sequential = false)).ok()
        s = engine.submitAnswer(s, p3, RIGHT, 100).ok()
        s = engine.submitAnswer(s, p1, RIGHT, 100).ok()
        assertEquals(Phase.QUESTION, s.phase)
        s = engine.submitAnswer(s, p2, RIGHT, 100).ok()
        assertEquals(Phase.REVEAL, s.phase)
    }

    @Test
    fun cannotAnswerTwiceAndPointsAreNotGrantedTwice() {
        var s = engine.startQuestion(newGame(3, sequential = false)).ok()
        s = engine.submitAnswer(s, p1, RIGHT, 100).ok()
        assertEquals(Rejection.ALREADY_ANSWERED, engine.submitAnswer(s, p1, RIGHT, 100).rejection())
        s = engine.submitAnswer(s, p2, RIGHT, 100).ok()
        s = engine.submitAnswer(s, p3, RIGHT, 100).ok()
        assertEquals(100, s.scores[p1])
        // Tras la revelación ya no se aceptan respuestas.
        assertEquals(Rejection.WRONG_PHASE, engine.submitAnswer(s, p1, RIGHT, 100).rejection())
        assertEquals(100, s.scores[p1])
    }

    @Test
    fun cannotAnswerAfterTimeExpires() {
        val s = engine.startQuestion(newGame()).ok()
        val first = s.currentTurn!!
        assertEquals(Rejection.TIME_EXPIRED, engine.submitAnswer(s, first, RIGHT, 8_001).rejection())
        assertEquals(Rejection.INVALID_TIME, engine.submitAnswer(s, first, RIGHT, -1).rejection())
    }

    @Test
    fun invalidOptionAndUnknownPlayerAreRejected() {
        val s = engine.startQuestion(newGame(sequential = false)).ok()
        assertEquals(Rejection.INVALID_OPTION, engine.submitAnswer(s, p1, 9, 100).rejection())
        assertEquals(Rejection.UNKNOWN_PLAYER, engine.submitAnswer(s, PlayerId("x"), 0, 100).rejection())
    }

    @Test
    fun cannotAnswerDuringRoulette() {
        assertEquals(Rejection.WRONG_PHASE, engine.submitAnswer(newGame(), p1, RIGHT, 100).rejection())
    }

    // --- Ronda 2: velocidad ---

    @Test
    fun speedRoundAddsDeterministicBonus() {
        var s = newGame()
        repeat(3) { s = d.next(d.play(s) { Move(WRONG) }) }
        assertEquals(RoundType.SPEED, s.round)
        s = d.play(s) { if (it == p1) Move(RIGHT, elapsed = 0) else Move(RIGHT, elapsed = 4_000) }
        assertEquals(150, s.scores[p1])
        assertEquals(125, s.scores[p2])
    }

    // --- Ronda 3: estrategia y apuestas ---

    private fun atStrategy(scoreP1: Boolean, scoreP2: Boolean): GameState {
        var s = newGame()
        // Ronda 1: p1 acierta 1 (100), p2 según parámetro.
        repeat(6) { q ->
            s = d.next(
                d.play(s) { id ->
                    val right = q == 0 && ((id == p1 && scoreP1) || (id == p2 && scoreP2))
                    Move(if (right) RIGHT else WRONG)
                },
            )
        }
        assertEquals(RoundType.STRATEGY, s.round)
        return engine.startQuestion(s).ok()
    }

    @Test
    fun correctBetAddsAndWrongBetSubtracts() {
        var s = atStrategy(scoreP1 = true, scoreP2 = true)
        for (id in s.turnOrder) {
            s = engine.placeBet(s, id, 100).ok()
            s = engine.submitAnswer(s, id, if (id == p1) RIGHT else WRONG, 500).ok()
        }
        assertEquals(200, s.scores[p1])
        assertEquals(0, s.scores[p2])
    }

    @Test
    fun cannotBetMoreThanBalanceOrInvalidAmounts() {
        val s = atStrategy(scoreP1 = true, scoreP2 = true) // ambos con 100
        val first = s.currentTurn!!
        assertEquals(Rejection.INVALID_BET, engine.placeBet(s, first, 150).rejection())
        assertEquals(Rejection.INVALID_BET, engine.placeBet(s, first, 75).rejection())
        assertEquals(Rejection.INVALID_BET, engine.placeBet(s, first, 0).rejection())
        val bet = engine.placeBet(s, first, 50).ok()
        assertEquals(Rejection.ALREADY_BET, engine.placeBet(bet, first, 100).rejection())
    }

    @Test
    fun playerWithoutBalanceGetsRiskFreeBetAndNeverGoesNegative() {
        var s = atStrategy(scoreP1 = true, scoreP2 = false) // p2 con 0
        for (id in s.turnOrder) {
            if (id == p2) {
                assertEquals(Rejection.INVALID_BET, engine.placeBet(s, id, 50).rejection())
                s = engine.placeBet(s, id, 0).ok()
                s = engine.submitAnswer(s, id, WRONG, 500).ok()
            } else {
                s = engine.placeBet(s, id, 50).ok()
                s = engine.submitAnswer(s, id, WRONG, 500).ok()
            }
        }
        assertEquals(0, s.scores[p2])
        assertEquals(50, s.scores[p1])
        assertEquals(Bet.Free, s.lastResult!!.outcomes.first { it.playerId == p2 }.bet)
    }

    @Test
    fun freeBetWinsFixedReward() {
        var s = atStrategy(scoreP1 = true, scoreP2 = false)
        for (id in s.turnOrder) {
            s = engine.placeBet(s, id, if (id == p2) 0 else 50).ok()
            s = engine.submitAnswer(s, id, RIGHT, 500).ok()
        }
        assertEquals(GameRules.FREE_BET_REWARD, s.scores[p2])
    }

    @Test
    fun answeringWithoutBetIsRejectedButTimeoutAppliesMinimumBet() {
        var s = atStrategy(scoreP1 = true, scoreP2 = true)
        val first = s.currentTurn!!
        assertEquals(Rejection.BET_REQUIRED, engine.submitAnswer(s, first, RIGHT, 100).rejection())
        s = engine.submitAnswer(s, first, null, 8_000).ok()
        val second = s.currentTurn!!
        s = engine.placeBet(s, second, 50).ok()
        s = engine.submitAnswer(s, second, RIGHT, 100).ok()
        assertEquals(50, s.scores[first])
        assertEquals(Bet.Amount(50), s.lastResult!!.outcomes.first { it.playerId == first }.bet)
    }

    @Test
    fun betsAreRejectedOutsideStrategyRound() {
        val s = engine.startQuestion(newGame()).ok()
        assertEquals(Rejection.BET_NOT_ALLOWED_THIS_ROUND, engine.placeBet(s, s.currentTurn!!, 50).rejection())
    }

    // --- Ronda 4: final ---

    @Test
    fun finalRoundGives150PlusSpeedBonus() {
        var s = newGame()
        repeat(9) { s = d.next(d.play(s) { id -> Move(WRONG, bet = betFor(s, id)) }) }
        assertEquals(RoundType.FINAL, s.round)
        s = d.play(s) { if (it == p1) Move(RIGHT, elapsed = 0) else Move(RIGHT, elapsed = 8_000) }
        assertEquals(200, s.scores[p1])
        assertEquals(150, s.scores[p2])
    }

    // --- Partida completa y desempate ---

    @Test
    fun fullGameWithClearWinnerFinishesWithoutTiebreak() {
        for (n in 2..4) {
            val start = newGame(n)
            val end = d.playMain(start) { _, id, s -> Move(if (id == PlayerId("p1")) RIGHT else WRONG, bet = betFor(s, id)) }
            assertEquals(Phase.FINISHED, end.phase)
            assertEquals(listOf(PlayerId("p1")), end.winners)
            assertEquals(GameRules.TOTAL_QUESTIONS, end.results.size)
            assertEquals(n, end.ranking.size)
        }
    }

    @Test
    fun tieGoesToTiebreakAndFastestCorrectWins() {
        val end = d.playMain(newGame(3)) { _, id, s -> Move(if (id == p3) WRONG else RIGHT, elapsed = 1_000, bet = betFor(s, id)) }
        assertEquals(Phase.TIEBREAK_QUESTION, end.phase)
        assertEquals(setOf(p1, p2), end.tiebreakPlayers.toSet())
        val scoresBefore = end.scores

        // p3 no participa del desempate.
        assertEquals(Rejection.NOT_A_TIEBREAK_PLAYER, d.engine.submitAnswer(end, p3, RIGHT, 100).rejection())
        // No se permiten apuestas en el desempate.
        assertEquals(Rejection.WRONG_PHASE, d.engine.placeBet(end, end.currentTurn!!, 50).rejection())

        var s = d.play(end) { if (it == p2) Move(RIGHT, elapsed = 900) else Move(RIGHT, elapsed = 1_500) }
        assertEquals(Phase.TIEBREAK_REVEAL, s.phase)
        s = d.engine.advance(s).ok()
        assertEquals(Phase.FINISHED, s.phase)
        assertEquals(listOf(p2), s.winners)
        // El desempate no altera puntos ni el historial de las 12 preguntas.
        assertEquals(scoresBefore, s.scores)
        assertEquals(GameRules.TOTAL_QUESTIONS, s.results.size)
        assertEquals(p2, s.ranking.first().first)
    }

    @Test
    fun tiebreakRepeatsWhenNobodyIsRightOrTimesTie() {
        var s = d.playMain(newGame(2)) { _, id, st -> Move(RIGHT, elapsed = 1_000, bet = betFor(st, id)) }
        assertEquals(Phase.TIEBREAK_QUESTION, s.phase)
        s = d.engine.advance(d.play(s) { Move(WRONG) }).ok()
        assertEquals(Phase.TIEBREAK_QUESTION, s.phase)
        assertEquals(1, s.tiebreakIndex)
        s = d.engine.advance(d.play(s) { Move(RIGHT, elapsed = 700) }).ok()
        assertEquals(Phase.TIEBREAK_QUESTION, s.phase)
        assertEquals(2, s.tiebreakIndex)
        // Cada desempate usa una pregunta distinta.
        assertEquals(2, s.tiebreakResults.map { it.questionId }.toSet().size)
    }

    @Test
    fun exhaustedTiebreakQueueEndsInSharedVictory() {
        var s = d.playMain(newGame(2)) { _, id, st -> Move(RIGHT, elapsed = 1_000, bet = betFor(st, id)) }
        repeat(s.plan.tiebreakQueue.size) { s = d.engine.advance(d.play(s) { Move(null, elapsed = 8_000) }).ok() }
        assertEquals(Phase.FINISHED, s.phase)
        assertEquals(setOf(p1, p2), s.winners.toSet())
    }

    @Test
    fun scoresNeverGoNegativeInRandomGames() {
        val rnd = kotlin.random.Random(42)
        repeat(200) { game ->
            val n = 2 + game % 3
            val start = engine.newGame(players(n), GameConfig(sequentialTurns = game % 2 == 0), seed = game.toLong())
            val end = d.playMain(start) { _, id, s ->
                val choice = if (rnd.nextInt(5) == 0) null else rnd.nextInt(2)
                val allowed = Scoring.allowedBets(s.scores.getValue(id))
                val bet = if (s.round == RoundType.STRATEGY && choice != null) (allowed.randomOrNull(rnd) ?: 0) else null
                Move(choice, elapsed = if (choice == null) 8_000 else rnd.nextLong(0, 8_001), bet = bet)
            }
            assertTrue(end.scores.values.all { it >= 0 })
            assertTrue(end.phase == Phase.FINISHED || end.phase == Phase.TIEBREAK_QUESTION)
        }
    }

    // --- Votaciones ---

    private val voteDriver = Driver(TestBank.standard(withVotes = true))

    @Test
    fun voteQuestionsAreOnlyPlannedWithThreeOrMorePlayers() {
        val plans2 = (1L..30L).map { voteDriver.engine.newGame(players(2), GameConfig(), it).plan }
        assertTrue(plans2.all { plan -> plan.questions.none { it.questionId.startsWith("v-") } })
        val plans4 = (1L..30L).map { voteDriver.engine.newGame(players(4), GameConfig(), it).plan }
        assertTrue(plans4.all { plan -> plan.questions.count { it.questionId.startsWith("v-") } <= GameRules.MAX_VOTE_QUESTIONS })
    }

    /** Lleva una partida de 4 jugadores hasta una pregunta de votación de la ronda 1. */
    private fun atVoteInKnowledge(): GameState {
        val seed = (1L..500L).first { seed ->
            val plan = voteDriver.engine.newGame(players(4), GameConfig(sequentialTurns = false), seed).plan
            plan.questions.take(3).any { it.questionId.startsWith("v-") }
        }
        var s = voteDriver.engine.newGame(players(4), GameConfig(sequentialTurns = false), seed)
        while (!s.currentPlanned!!.questionId.startsWith("v-")) s = voteDriver.next(voteDriver.play(s) { Move(WRONG) })
        return s
    }

    @Test
    fun majorityVotersWinAndTiedOptionsAllWin() {
        val s = voteDriver.play(atVoteInKnowledge()) { id ->
            when (id.value) { "p1", "p2" -> Move(0); "p3", "p4" -> Move(1); else -> Move(2) }
        }
        val r = s.lastResult!!
        assertEquals(setOf(0, 1), r.winningOptions)
        assertTrue(s.players.all { s.scores[it.id] == GameRules.MAJORITY_VOTE_POINTS })
    }

    @Test
    fun minorityVotersGetNothing() {
        val s = voteDriver.play(atVoteInKnowledge()) { id -> Move(if (id.value == "p4") 2 else 0) }
        assertEquals(setOf(0), s.lastResult!!.winningOptions)
        assertEquals(0, s.scores[PlayerId("p4")])
        assertEquals(100, s.scores[PlayerId("p1")])
    }

    @Test
    fun voteWithFewerThanTwoValidAnswersIsNotScored() {
        val s = voteDriver.play(atVoteInKnowledge()) { id -> if (id.value == "p1") Move(0) else Move(null, elapsed = 8_000) }
        val r = s.lastResult!!
        assertEquals(false, r.scored)
        assertTrue(r.winningOptions.isEmpty())
        assertTrue(s.scores.values.all { it == 0 })
        assertNull(r.outcomes.first().won)
    }

    @Test
    fun roundTransitionsGoThroughSummary() {
        var s = d.play(newGame()) { Move(RIGHT) }
        repeat(2) { s = d.engine.advance(s).ok(); s = d.play(s) { Move(RIGHT) } }
        s = d.engine.advance(s).ok()
        assertEquals(Phase.ROUND_SUMMARY, s.phase)
        s = d.engine.advance(s).ok()
        assertEquals(Phase.ROULETTE, s.phase)
        assertEquals(RoundType.SPEED, s.round)
        assertNotNull(s.currentPlanned)
    }

    @Test
    fun regionalGameOnlyUsesQuestionsFromThatRegion() {
        val s = engine.newGame(players(2), GameConfig(mode = ContentMode.Regional("cesar")), seed = 3)
        assertTrue(s.plan.questions.all { it.questionId.startsWith("r-cesar-") })
        assertTrue(s.plan.tiebreakQueue.all { it.startsWith("r-cesar-") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOnePlayer() { engine.newGame(players(1), GameConfig(), 1) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFivePlayers() { engine.newGame(players(5), GameConfig(), 1) }
}

/** Apuesta mínima válida en la ronda de estrategia; null en las demás rondas. */
fun betFor(s: GameState, id: PlayerId): Int? =
    if (s.round != RoundType.STRATEGY) null else Scoring.allowedBets(s.scores.getValue(id)).minOrNull() ?: 0
