package com.elderdavid.quientienelarazon.core.game

import com.elderdavid.quientienelarazon.core.questions.QuestionBank

/** Motivos por los que el motor rechaza una acción. El estado no cambia cuando se rechaza. */
enum class Rejection {
    WRONG_PHASE,
    UNKNOWN_PLAYER,
    NOT_YOUR_TURN,
    ALREADY_ANSWERED,
    ALREADY_BET,
    BET_NOT_ALLOWED_THIS_ROUND,
    INVALID_BET,
    BET_REQUIRED,
    INVALID_OPTION,
    INVALID_TIME,
    TIME_EXPIRED,
    NOT_A_TIEBREAK_PLAYER,
}

sealed interface EngineResult {
    data class Ok(val state: GameState) : EngineResult
    data class Rejected(val reason: Rejection) : EngineResult
}

/**
 * Motor de reglas único para los modos local y online. Es puro y determinista: recibe un estado
 * y una acción y devuelve el nuevo estado. Los tiempos llegan medidos desde fuera (en online los
 * mide el servidor) y se validan aquí.
 */
class GameEngine(private val bank: QuestionBank) {

    fun newGame(players: List<Player>, config: GameConfig, seed: Long, avoidQuestionIds: Set<String> = emptySet()): GameState {
        require(players.size in GameRules.MIN_PLAYERS..GameRules.MAX_PLAYERS) {
            "Se necesitan entre ${GameRules.MIN_PLAYERS} y ${GameRules.MAX_PLAYERS} jugadores"
        }
        require(players.map { it.id }.toSet().size == players.size) { "IDs de jugador repetidos" }
        require(players.all { it.name.isNotBlank() }) { "Todos los jugadores necesitan nombre" }
        val plan = GamePlanner.plan(bank, config.mode, players.size, seed, avoidQuestionIds)
        return GameState(config = config, players = players, plan = plan, scores = players.associate { it.id to 0 })
    }

    /** La ruleta terminó de mostrar la categoría: empieza la pregunta. */
    fun startQuestion(state: GameState): EngineResult {
        if (state.phase != Phase.ROULETTE) return EngineResult.Rejected(Rejection.WRONG_PHASE)
        return ok(state.copy(phase = Phase.QUESTION, bets = emptyMap(), answers = emptyMap()))
    }

    fun placeBet(state: GameState, playerId: PlayerId, amount: Int): EngineResult {
        if (state.phase != Phase.QUESTION) return EngineResult.Rejected(Rejection.WRONG_PHASE)
        if (state.round != RoundType.STRATEGY) return EngineResult.Rejected(Rejection.BET_NOT_ALLOWED_THIS_ROUND)
        checkTurn(state, playerId)?.let { return EngineResult.Rejected(it) }
        if (playerId in state.bets) return EngineResult.Rejected(Rejection.ALREADY_BET)
        val score = state.scores.getValue(playerId)
        val allowed = Scoring.allowedBets(score)
        val bet = when {
            allowed.isEmpty() && amount == 0 -> Bet.Free
            amount in allowed -> Bet.Amount(amount)
            else -> return EngineResult.Rejected(Rejection.INVALID_BET)
        }
        return ok(state.copy(bets = state.bets + (playerId to bet)))
    }

    /**
     * Registra una respuesta. [choice] null = se agotó el tiempo sin responder.
     * Cuando todos respondieron se puntúa la pregunta una sola vez y pasa a revelación.
     */
    fun submitAnswer(state: GameState, playerId: PlayerId, choice: Int?, elapsedMillis: Long): EngineResult {
        val tiebreak = state.phase == Phase.TIEBREAK_QUESTION
        if (state.phase != Phase.QUESTION && !tiebreak) return EngineResult.Rejected(Rejection.WRONG_PHASE)
        if (tiebreak && playerId !in state.tiebreakPlayers) {
            return EngineResult.Rejected(
                if (state.players.none { it.id == playerId }) Rejection.UNKNOWN_PLAYER else Rejection.NOT_A_TIEBREAK_PLAYER,
            )
        }
        checkTurn(state, playerId)?.let { return EngineResult.Rejected(it) }
        if (elapsedMillis < 0) return EngineResult.Rejected(Rejection.INVALID_TIME)
        if (choice != null && elapsedMillis > state.config.questionTimeMillis) return EngineResult.Rejected(Rejection.TIME_EXPIRED)

        val questionId = if (tiebreak) state.plan.tiebreakQueue[state.tiebreakIndex] else state.currentPlanned!!.questionId
        val question = bank.question(questionId)
        if (choice != null && choice !in question.options.indices) return EngineResult.Rejected(Rejection.INVALID_OPTION)

        var bets = state.bets
        if (!tiebreak && state.round == RoundType.STRATEGY && playerId !in bets) {
            // Sin apuesta solo se acepta el agotamiento del tiempo; entonces se aplica la mínima.
            if (choice != null) return EngineResult.Rejected(Rejection.BET_REQUIRED)
            bets = bets + (playerId to Scoring.defaultBet(state.scores.getValue(playerId)))
        }

        val answer = Answer(choice, elapsedMillis.coerceAtMost(state.config.questionTimeMillis))
        val next = state.copy(bets = bets, answers = state.answers + (playerId to answer))
        val everyone = next.turnOrder
        if (!everyone.all { it in next.answers }) return ok(next)
        return ok(if (tiebreak) closeTiebreak(next) else closeQuestion(next))
    }

    /** Avanza tras una revelación o un marcador. */
    fun advance(state: GameState): EngineResult = when (state.phase) {
        Phase.REVEAL -> {
            val nextIndex = state.questionIndex + 1
            when {
                nextIndex >= GameRules.TOTAL_QUESTIONS -> ok(finishOrTiebreak(state))
                nextIndex % GameRules.QUESTIONS_PER_ROUND == 0 -> ok(state.copy(phase = Phase.ROUND_SUMMARY))
                else -> ok(state.copy(phase = Phase.ROULETTE, questionIndex = nextIndex, bets = emptyMap(), answers = emptyMap()))
            }
        }
        Phase.ROUND_SUMMARY -> ok(
            state.copy(phase = Phase.ROULETTE, questionIndex = state.questionIndex + 1, bets = emptyMap(), answers = emptyMap()),
        )
        Phase.TIEBREAK_REVEAL -> {
            val winner = state.tiebreakResults.last().winner
            when {
                winner != null -> ok(state.copy(phase = Phase.FINISHED, winners = listOf(winner)))
                state.tiebreakIndex + 1 >= state.plan.tiebreakQueue.size ->
                    // Sin más preguntas de desempate: victoria compartida, sin inventar un ganador.
                    ok(state.copy(phase = Phase.FINISHED, winners = state.tiebreakPlayers))
                else -> ok(state.copy(phase = Phase.TIEBREAK_QUESTION, tiebreakIndex = state.tiebreakIndex + 1, answers = emptyMap()))
            }
        }
        else -> EngineResult.Rejected(Rejection.WRONG_PHASE)
    }

    private fun checkTurn(state: GameState, playerId: PlayerId): Rejection? {
        if (state.players.none { it.id == playerId }) return Rejection.UNKNOWN_PLAYER
        if (playerId in state.answers) return Rejection.ALREADY_ANSWERED
        if (state.config.sequentialTurns && state.currentTurn != playerId) return Rejection.NOT_YOUR_TURN
        return null
    }

    private fun closeQuestion(state: GameState): GameState {
        val planned = state.currentPlanned!!
        val question = bank.question(planned.questionId)
        val round = planned.round
        val correct = question.correctIndex

        val voteCounts: Map<Int, Int>
        val winningOptions: Set<Int>
        val scored: Boolean
        if (correct != null) {
            voteCounts = emptyMap()
            winningOptions = setOf(correct)
            scored = true
        } else {
            val valid = state.answers.values.mapNotNull { it.choice }
            voteCounts = valid.groupingBy { it }.eachCount()
            scored = valid.size >= 2
            val max = voteCounts.values.maxOrNull() ?: 0
            winningOptions = if (scored) voteCounts.filterValues { it == max }.keys else emptySet()
        }

        val outcomes = state.turnOrder.map { id ->
            val answer = state.answers.getValue(id)
            val bet = state.bets[id]
            val won: Boolean? = if (!scored) null else answer.choice != null && answer.choice in winningOptions
            val delta = when {
                won == null -> 0 // votación sin votos suficientes: ni premio ni pérdida de apuesta
                round == RoundType.STRATEGY -> Scoring.betDelta(bet ?: Scoring.defaultBet(state.scores.getValue(id)), won)
                question.isVote -> if (won) GameRules.MAJORITY_VOTE_POINTS else 0
                !won -> 0
                else -> Scoring.basePoints(round) +
                    if (Scoring.hasSpeedBonus(round)) Scoring.speedBonus(answer.elapsedMillis, state.config.questionTimeMillis) else 0
            }
            PlayerOutcome(id, answer.choice, answer.elapsedMillis, won, delta, bet)
        }
        val scores = state.scores.mapValues { (id, score) ->
            val updated = score + (outcomes.firstOrNull { it.playerId == id }?.pointsDelta ?: 0)
            check(updated >= 0) { "El saldo no puede ser negativo" }
            updated
        }
        val result = QuestionResult(state.questionIndex, round, question.id, winningOptions, voteCounts, scored, outcomes)
        return state.copy(phase = Phase.REVEAL, scores = scores, results = state.results + result)
    }

    private fun finishOrTiebreak(state: GameState): GameState {
        val top = state.scores.values.max()
        val leaders = state.players.map { it.id }.filter { state.scores[it] == top }
        return when {
            leaders.size == 1 -> state.copy(phase = Phase.FINISHED, winners = leaders)
            state.plan.tiebreakQueue.isEmpty() -> state.copy(phase = Phase.FINISHED, winners = leaders)
            else -> state.copy(
                phase = Phase.TIEBREAK_QUESTION,
                tiebreakPlayers = leaders,
                tiebreakIndex = 0,
                bets = emptyMap(),
                answers = emptyMap(),
            )
        }
    }

    /** Gana la respuesta correcta más rápida. Sin aciertos o con empate exacto de tiempo, se repite. */
    private fun closeTiebreak(state: GameState): GameState {
        val question = bank.question(state.plan.tiebreakQueue[state.tiebreakIndex])
        val correct = question.correctIndex!!
        val outcomes = state.turnOrder.map { id ->
            val a = state.answers.getValue(id)
            PlayerOutcome(id, a.choice, a.elapsedMillis, a.choice == correct, 0, null)
        }
        val right = outcomes.filter { it.won == true }
        val fastest = right.minOfOrNull { it.elapsedMillis }
        val winner = right.filter { it.elapsedMillis == fastest }.singleOrNull()?.playerId
        return state.copy(
            phase = Phase.TIEBREAK_REVEAL,
            tiebreakResults = state.tiebreakResults + TiebreakResult(question.id, outcomes, winner),
        )
    }

    private fun ok(state: GameState) = EngineResult.Ok(state)
}
