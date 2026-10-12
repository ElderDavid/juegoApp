package com.elderdavid.quientienelarazon.core.local

import com.elderdavid.quientienelarazon.core.game.EngineResult
import com.elderdavid.quientienelarazon.core.game.GameConfig
import com.elderdavid.quientienelarazon.core.game.GameEngine
import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.game.Phase
import com.elderdavid.quientienelarazon.core.game.Player
import com.elderdavid.quientienelarazon.core.game.PlayerId
import com.elderdavid.quientienelarazon.core.game.QuestionResult
import com.elderdavid.quientienelarazon.core.game.RoundType
import com.elderdavid.quientienelarazon.core.game.Scoring
import com.elderdavid.quientienelarazon.core.game.TiebreakResult
import com.elderdavid.quientienelarazon.core.questions.ContentMode

/** Lo que debe mostrar la pantalla en cada momento de una partida local. */
sealed interface LocalStage {
    /** La ruleta anima hacia una categoría ya decidida. */
    data class Roulette(val categoryId: String, val round: RoundType, val questionNumber: Int) : LocalStage

    /** Pantalla de «pasa el teléfono»: oculta la pregunta hasta que el jugador indicado está listo. */
    data class Handoff(val playerId: PlayerId, val tiebreak: Boolean) : LocalStage

    /** Ronda de estrategia: el jugador apuesta antes de ver la pregunta. Lista vacía = apuesta libre. */
    data class Betting(val playerId: PlayerId, val allowedBets: List<Int>, val score: Int) : LocalStage

    /** El jugador ve la pregunta; el tiempo corre desde [startedAt] hasta [deadline]. */
    data class Answering(
        val playerId: PlayerId,
        val questionId: String,
        val startedAt: Long,
        val deadline: Long,
        val tiebreak: Boolean,
    ) : LocalStage

    data class Reveal(val result: QuestionResult) : LocalStage
    data class RoundSummary(val finishedRound: RoundType) : LocalStage
    data class TiebreakReveal(val result: TiebreakResult) : LocalStage
    data class Finished(val winners: List<PlayerId>) : LocalStage
}

/**
 * Partida local en un solo teléfono. Envuelve el motor y añade el traspaso del teléfono entre
 * jugadores, de modo que nadie ve la pregunta ni las respuestas de otro antes de su turno.
 *
 * Es inmutable y no lee el reloj: cada acción recibe `now` (milisegundos de un reloj monótono).
 * Las acciones que no corresponden a la etapa actual devuelven la misma instancia, así un doble
 * toque no puede responder dos veces ni puntuar dos veces.
 */
data class LocalMatch(
    val state: GameState,
    private val engine: GameEngine,
    /** Jugador que confirmó tener el teléfono en el turno actual. */
    private val readyPlayer: PlayerId? = null,
    /** Momento en que se mostró la pregunta al jugador actual. */
    private val turnStartedAt: Long? = null,
) {
    val players: List<Player> get() = state.players

    val stage: LocalStage
        get() = when (state.phase) {
            Phase.ROULETTE -> state.currentPlanned!!.let {
                LocalStage.Roulette(it.categoryId, it.round, it.index + 1)
            }
            Phase.QUESTION, Phase.TIEBREAK_QUESTION -> turnStage()
            Phase.REVEAL -> LocalStage.Reveal(state.lastResult!!)
            Phase.ROUND_SUMMARY -> LocalStage.RoundSummary(state.round)
            Phase.TIEBREAK_REVEAL -> LocalStage.TiebreakReveal(state.tiebreakResults.last())
            Phase.FINISHED -> LocalStage.Finished(state.winners)
        }

    private val tiebreak: Boolean get() = state.phase == Phase.TIEBREAK_QUESTION

    private fun turnStage(): LocalStage {
        val player = state.currentTurn!!
        if (readyPlayer != player) return LocalStage.Handoff(player, tiebreak)
        if (needsBet(player)) {
            val score = state.scores.getValue(player)
            return LocalStage.Betting(player, Scoring.allowedBets(score), score)
        }
        val start = turnStartedAt!!
        return LocalStage.Answering(player, currentQuestionId(), start, start + state.config.questionTimeMillis, tiebreak)
    }

    private fun needsBet(player: PlayerId) =
        state.phase == Phase.QUESTION && state.round == RoundType.STRATEGY && player !in state.bets

    private fun currentQuestionId(): String =
        if (tiebreak) state.plan.tiebreakQueue[state.tiebreakIndex] else state.currentPlanned!!.questionId

    /** La animación de la ruleta terminó. */
    fun rouletteDone(): LocalMatch {
        if (stage !is LocalStage.Roulette) return this
        return withEngine(engine.startQuestion(state))
    }

    /** El jugador del turno tiene el teléfono. Si no hay apuesta pendiente, empieza su tiempo. */
    fun ready(now: Long): LocalMatch {
        val handoff = stage as? LocalStage.Handoff ?: return this
        val startsNow = !needsBet(handoff.playerId)
        return copy(readyPlayer = handoff.playerId, turnStartedAt = if (startsNow) now else null)
    }

    /** Apuesta de la ronda de estrategia. [amount] 0 = apuesta libre cuando no alcanza el saldo. */
    fun bet(amount: Int, now: Long): LocalMatch {
        val betting = stage as? LocalStage.Betting ?: return this
        return when (val r = engine.placeBet(state, betting.playerId, amount)) {
            is EngineResult.Ok -> copy(state = r.state, turnStartedAt = now)
            is EngineResult.Rejected -> this
        }
    }

    /**
     * Respuesta del jugador del turno. Si llega después del límite se registra como tiempo agotado:
     * nunca se acepta una respuesta tardía.
     */
    fun answer(choice: Int, now: Long): LocalMatch {
        val answering = stage as? LocalStage.Answering ?: return this
        val elapsed = (now - answering.startedAt).coerceAtLeast(0)
        val late = elapsed > state.config.questionTimeMillis
        return submit(answering.playerId, if (late) null else choice, elapsed)
    }

    /** Se agotó el tiempo. Se ignora si el reloj indica que aún queda tiempo. */
    fun expire(now: Long): LocalMatch {
        val answering = stage as? LocalStage.Answering ?: return this
        if (now < answering.deadline) return this
        return submit(answering.playerId, null, state.config.questionTimeMillis)
    }

    /** Continúa tras una revelación o un marcador. */
    fun next(): LocalMatch = when (stage) {
        is LocalStage.Reveal, is LocalStage.RoundSummary, is LocalStage.TiebreakReveal -> withEngine(engine.advance(state))
        else -> this
    }

    /** Nueva partida con los mismos jugadores y modo, evitando en lo posible las preguntas ya vistas. */
    fun rematch(seed: Long): LocalMatch {
        val seen = state.plan.questions.map { it.questionId }.toSet() + state.plan.tiebreakQueue
        return start(engine, state.players, state.config.mode, seed, seen)
    }

    private fun submit(player: PlayerId, choice: Int?, elapsed: Long): LocalMatch =
        when (val r = engine.submitAnswer(state, player, choice, elapsed)) {
            is EngineResult.Ok -> copy(state = r.state, readyPlayer = null, turnStartedAt = null)
            is EngineResult.Rejected -> this
        }

    private fun withEngine(result: EngineResult): LocalMatch = when (result) {
        is EngineResult.Ok -> copy(state = result.state, readyPlayer = null, turnStartedAt = null)
        is EngineResult.Rejected -> this
    }

    companion object {
        fun start(
            engine: GameEngine,
            players: List<Player>,
            mode: ContentMode,
            seed: Long,
            avoidQuestionIds: Set<String> = emptySet(),
        ): LocalMatch = LocalMatch(
            state = engine.newGame(players, GameConfig(mode = mode, sequentialTurns = true), seed, avoidQuestionIds),
            engine = engine,
        )
    }
}
