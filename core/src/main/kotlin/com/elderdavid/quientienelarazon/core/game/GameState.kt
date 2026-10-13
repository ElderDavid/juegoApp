package com.elderdavid.quientienelarazon.core.game

import com.elderdavid.quientienelarazon.core.questions.ContentMode

data class GameConfig(
    val mode: ContentMode = ContentMode.General,
    /** true en el modo local: un solo teléfono, los jugadores responden por turnos. */
    val sequentialTurns: Boolean = true,
    val questionTimeMillis: Long = if (mode == ContentMode.Kids) GameRules.KIDS_QUESTION_TIME_MILLIS else GameRules.QUESTION_TIME_MILLIS,
    val length: GameLength = GameLength.DEFAULT,
) {
    /** Modo niños: en la ronda de estrategia fallar no resta la apuesta. */
    val kids: Boolean get() = mode == ContentMode.Kids
}

enum class Phase {
    /** La ruleta muestra la categoría ya decidida de la pregunta actual. */
    ROULETTE,

    /** Pregunta en curso: se reciben apuestas (ronda 3) y respuestas. */
    QUESTION,

    /** Respuestas reveladas y puntos aplicados. */
    REVEAL,

    /** Marcador entre rondas. */
    ROUND_SUMMARY,

    /** Pregunta de desempate en curso (sin apuestas). */
    TIEBREAK_QUESTION,

    /** Resultado de una pregunta de desempate. */
    TIEBREAK_REVEAL,

    FINISHED,
}

/** Respuesta registrada. [choice] null = no respondió a tiempo. */
data class Answer(val choice: Int?, val elapsedMillis: Long)

data class PlayerOutcome(
    val playerId: PlayerId,
    val choice: Int?,
    val elapsedMillis: Long,
    /** null en votaciones que no se puntuaron por falta de votos válidos. */
    val won: Boolean?,
    val pointsDelta: Int,
    val bet: Bet?,
)

data class QuestionResult(
    val index: Int,
    val round: RoundType,
    val questionId: String,
    /** Opción correcta (objetivas o regla de juego) u opciones más votadas (votación). */
    val winningOptions: Set<Int>,
    /** Votos por opción, solo en votaciones. */
    val voteCounts: Map<Int, Int>,
    /** false si una votación no tuvo al menos dos votos válidos. */
    val scored: Boolean,
    val outcomes: List<PlayerOutcome>,
)

data class TiebreakResult(
    val questionId: String,
    val outcomes: List<PlayerOutcome>,
    /** Ganador del desempate o null si hay que repetir. */
    val winner: PlayerId?,
)

data class GameState(
    val config: GameConfig,
    val players: List<Player>,
    val plan: GamePlan,
    val scores: Map<PlayerId, Int>,
    val questionIndex: Int = 0,
    val phase: Phase = Phase.ROULETTE,
    val bets: Map<PlayerId, Bet> = emptyMap(),
    val answers: Map<PlayerId, Answer> = emptyMap(),
    /** Historial de las preguntas principales (sin desempate): fuente para las estadísticas. */
    val results: List<QuestionResult> = emptyList(),
    /** Jugadores empatados en el primer puesto, durante el desempate. */
    val tiebreakPlayers: List<PlayerId> = emptyList(),
    val tiebreakIndex: Int = 0,
    val tiebreakResults: List<TiebreakResult> = emptyList(),
    /** Ganadores al terminar. Más de uno solo si se agotaron las preguntas de desempate. */
    val winners: List<PlayerId> = emptyList(),
    /** Tiempo extra de cada jugador en la pregunta actual (comodín de tiempo). Se borra al empezar otra. */
    val bonusTimeMillis: Map<PlayerId, Long> = emptyMap(),
) {
    val currentPlanned: PlannedQuestion? get() = plan.questions.getOrNull(questionIndex)
    val round: RoundType get() = config.length.roundFor(questionIndex.coerceAtMost(config.length.questions - 1))
    val lastResult: QuestionResult? get() = results.lastOrNull()

    /** Jugadores que participan en la pregunta actual, en orden de turno. */
    val turnOrder: List<PlayerId>
        get() {
            val ids = if (phase == Phase.TIEBREAK_QUESTION || phase == Phase.TIEBREAK_REVEAL) {
                tiebreakPlayers
            } else {
                players.map { it.id }
            }
            if (ids.isEmpty()) return ids
            // El primer turno rota en cada pregunta para que nadie empiece siempre.
            val shift = (questionIndex + tiebreakIndex) % ids.size
            return ids.drop(shift) + ids.take(shift)
        }

    /** Tiempo total que tiene [playerId] para la pregunta actual. */
    fun timeLimitFor(playerId: PlayerId): Long = config.questionTimeMillis + (bonusTimeMillis[playerId] ?: 0L)

    /** Jugador que debe responder ahora en modo por turnos; null si ya respondieron todos. */
    val currentTurn: PlayerId? get() = turnOrder.firstOrNull { it !in answers }

    /** Clasificación por puntos. El desempate solo decide el primer puesto. */
    val ranking: List<Pair<PlayerId, Int>>
        get() = players.map { it.id to (scores[it.id] ?: 0) }
            .sortedWith(compareByDescending<Pair<PlayerId, Int>> { it.second }.thenBy { if (it.first in winners) 0 else 1 })
}
