package com.elderdavid.quientienelarazon.core.local

import com.elderdavid.quientienelarazon.core.game.EngineResult
import com.elderdavid.quientienelarazon.core.game.GameConfig
import com.elderdavid.quientienelarazon.core.game.GameEngine
import com.elderdavid.quientienelarazon.core.game.GameLength
import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.game.Phase
import com.elderdavid.quientienelarazon.core.game.Player
import com.elderdavid.quientienelarazon.core.game.PlayerId
import com.elderdavid.quientienelarazon.core.game.QuestionResult
import com.elderdavid.quientienelarazon.core.game.RoundType
import com.elderdavid.quientienelarazon.core.game.Scoring
import com.elderdavid.quientienelarazon.core.game.TiebreakResult
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import kotlin.random.Random

/** Lo que debe mostrar la pantalla en cada momento de una partida local. */
sealed interface LocalStage {
    /** La ruleta anima hacia una categoría ya decidida. */
    data class Roulette(val categoryId: String, val round: RoundType, val questionNumber: Int) : LocalStage

    /** Pantalla de «pasa el teléfono»: oculta la pregunta hasta que el jugador indicado está listo. */
    data class Handoff(val playerId: PlayerId, val tiebreak: Boolean) : LocalStage

    /** Ronda de estrategia: el jugador apuesta antes de ver la pregunta. Lista vacía = apuesta libre. */
    data class Betting(val playerId: PlayerId, val allowedBets: List<Int>, val score: Int) : LocalStage

    /**
     * El jugador ve la pregunta; el tiempo corre desde [startedAt] hasta [deadline].
     * [hiddenOptions]: opciones quitadas por el comodín 50/50. [wildcards]: comodines que le
     * quedan y [usableWildcards]: los que puede usar ahora mismo.
     */
    data class Answering(
        val playerId: PlayerId,
        val questionId: String,
        val startedAt: Long,
        val deadline: Long,
        val tiebreak: Boolean,
        val hiddenOptions: Set<Int> = emptySet(),
        val wildcards: Int = 0,
        val usableWildcards: Set<Wildcard> = emptySet(),
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
    /** Comodines que le quedan a cada jugador en esta partida. */
    val wildcards: Map<PlayerId, Int> = emptyMap(),
    /** true si ya se compró el paquete de partida (uno por partida). */
    val matchPackUsed: Boolean = false,
    /** Opciones ocultas por el 50/50 en el turno actual. */
    private val hiddenOptions: Set<Int> = emptySet(),
    /** Como mucho un comodín por jugador y pregunta. */
    private val wildcardUsedThisTurn: Boolean = false,
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
        return LocalStage.Answering(
            playerId = player,
            questionId = currentQuestionId(),
            startedAt = start,
            deadline = start + state.timeLimitFor(player),
            tiebreak = tiebreak,
            hiddenOptions = hiddenOptions,
            wildcards = wildcards[player] ?: 0,
            usableWildcards = usableWildcards(player),
        )
    }

    private fun usableWildcards(player: PlayerId): Set<Wildcard> {
        if (tiebreak || wildcardUsedThisTurn || (wildcards[player] ?: 0) <= 0) return emptySet()
        val question = engine.question(currentQuestionId())
        return buildSet {
            add(Wildcard.EXTRA_TIME)
            if (question.correctIndex != null && question.options.size >= 4) add(Wildcard.FIFTY_FIFTY)
            if (state.answers.isEmpty() && engine.replacementFor(state) != null) add(Wildcard.CHANGE_QUESTION)
        }
    }

    /**
     * El jugador del turno usa un comodín mientras ve la pregunta. Al cambiar la pregunta su
     * tiempo vuelve a empezar, porque es una pregunta nueva.
     */
    fun useWildcard(wildcard: Wildcard, now: Long): LocalMatch {
        val answering = stage as? LocalStage.Answering ?: return this
        if (wildcard !in answering.usableWildcards) return this
        val player = answering.playerId
        val spent = copy(wildcards = wildcards + (player to (wildcards.getValue(player) - 1)), wildcardUsedThisTurn = true)
        return when (wildcard) {
            Wildcard.EXTRA_TIME -> when (val r = engine.grantExtraTime(state, player, WildcardRules.EXTRA_TIME_MILLIS)) {
                is EngineResult.Ok -> spent.copy(state = r.state)
                is EngineResult.Rejected -> this
            }
            Wildcard.FIFTY_FIFTY -> {
                val question = engine.question(answering.questionId)
                val correct = question.correctIndex!!
                val random = Random(answering.questionId.hashCode().toLong() * 31 + player.hashCode())
                val hidden = question.options.indices.filter { it != correct }.shuffled(random).take(2).toSet()
                spent.copy(hiddenOptions = hidden)
            }
            Wildcard.CHANGE_QUESTION -> when (val r = engine.swapQuestion(state)) {
                is EngineResult.Ok -> spent.copy(state = r.state, turnStartedAt = now, hiddenOptions = emptySet())
                is EngineResult.Rejected -> this
            }
        }
    }

    /** Paquete de partida: cada jugador recibe un comodín más. Uno por partida. */
    fun addMatchPack(): LocalMatch {
        if (!canBuyMatchPack) return this
        return copy(wildcards = state.players.associate { it.id to (wildcards[it.id] ?: 0) + 1 }, matchPackUsed = true)
    }

    /** En el modo niños no se ofrecen compras dentro de la partida. */
    val canBuyMatchPack: Boolean get() = !matchPackUsed && state.phase != Phase.FINISHED && !state.config.kids

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
        val late = elapsed > state.timeLimitFor(answering.playerId)
        return submit(answering.playerId, if (late) null else choice, elapsed)
    }

    /** Se agotó el tiempo. Se ignora si el reloj indica que aún queda tiempo. */
    fun expire(now: Long): LocalMatch {
        val answering = stage as? LocalStage.Answering ?: return this
        if (now < answering.deadline) return this
        return submit(answering.playerId, null, state.timeLimitFor(answering.playerId))
    }

    /** Continúa tras una revelación o un marcador. */
    fun next(): LocalMatch = when (stage) {
        is LocalStage.Reveal, is LocalStage.RoundSummary, is LocalStage.TiebreakReveal -> withEngine(engine.advance(state))
        else -> this
    }

    /** Nueva partida con los mismos jugadores y modo, evitando en lo posible las preguntas ya vistas. */
    fun rematch(seed: Long): LocalMatch {
        val seen = state.plan.questions.map { it.questionId }.toSet() + state.plan.tiebreakQueue
        return start(engine, state.players, state.config.mode, seed, seen, state.config.length)
    }

    private fun submit(player: PlayerId, choice: Int?, elapsed: Long): LocalMatch =
        when (val r = engine.submitAnswer(state, player, choice, elapsed)) {
            is EngineResult.Ok -> copy(state = r.state, readyPlayer = null, turnStartedAt = null).endTurn()
            is EngineResult.Rejected -> this
        }

    private fun endTurn() = copy(hiddenOptions = emptySet(), wildcardUsedThisTurn = false)

    private fun withEngine(result: EngineResult): LocalMatch = when (result) {
        is EngineResult.Ok -> copy(state = result.state, readyPlayer = null, turnStartedAt = null).endTurn()
        is EngineResult.Rejected -> this
    }

    companion object {
        fun start(
            engine: GameEngine,
            players: List<Player>,
            mode: ContentMode,
            seed: Long,
            avoidQuestionIds: Set<String> = emptySet(),
            length: GameLength = GameLength.DEFAULT,
        ): LocalMatch = LocalMatch(
            state = engine.newGame(players, GameConfig(mode = mode, sequentialTurns = true, length = length), seed, avoidQuestionIds),
            engine = engine,
            wildcards = players.associate { it.id to WildcardRules.FREE_PER_PLAYER },
        )
    }
}
