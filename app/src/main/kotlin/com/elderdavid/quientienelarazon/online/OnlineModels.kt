package com.elderdavid.quientienelarazon.online

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lo que el servidor deja ver a un jugador (función room_state de supabase/migrations/…_salas_online.sql).
 * La app solo dibuja esto: puntos, tiempos y resultados los decide el servidor.
 */
@Serializable
data class RoomView(
    val room: RoomInfo,
    val me: String,
    val players: List<RoomPlayer>,
    val current: CurrentQuestion? = null,
    val result: QuestionOutcome? = null,
    val final: List<FinalEntry>? = null,
) {
    val myPlayer: RoomPlayer? get() = players.firstOrNull { it.userId == me }
    val isHost: Boolean get() = room.hostId == me
    fun player(id: String): RoomPlayer? = players.firstOrNull { it.userId == id }

    /** Jugadores ordenados por puntos; ante empate, primero los ganadores y luego por asiento. */
    val ranking: List<RoomPlayer>
        get() = players.sortedWith(
            compareByDescending<RoomPlayer> { it.score }
                .thenBy { if (it.userId in room.winners) 0 else 1 }
                .thenBy { it.seat },
        )
}

@Serializable
data class RoomInfo(
    val id: String,
    val code: String,
    val visibility: String,
    val mode: String,
    val status: String,
    val phase: String? = null,
    @SerialName("host_id") val hostId: String,
    @SerialName("question_index") val questionIndex: Int = 0,
    @SerialName("tiebreak_index") val tiebreakIndex: Int = 0,
    @SerialName("phase_total_ms") val phaseTotalMs: Long? = null,
    @SerialName("phase_remaining_ms") val phaseRemainingMs: Long? = null,
    @SerialName("autostart_remaining_ms") val autostartRemainingMs: Long? = null,
    val winners: List<String> = emptyList(),
    @SerialName("cancel_reason") val cancelReason: String? = null,
) {
    val isPublic: Boolean get() = visibility == "public"
    val inLobby: Boolean get() = status == "waiting" || status == "ready"
    val isOver: Boolean get() = status == "finished" || status == "cancelled"
}

@Serializable
data class RoomPlayer(
    @SerialName("user_id") val userId: String,
    val seat: Int,
    val name: String,
    @SerialName("avatar_id") val avatarId: Int,
    val score: Int,
    val connected: Boolean,
    val left: Boolean,
    @SerialName("in_tiebreak") val inTiebreak: Boolean = false,
    val answered: Boolean = false,
    @SerialName("bet_placed") val betPlaced: Boolean = false,
)

@Serializable
data class CurrentQuestion(
    val index: Int,
    @SerialName("is_tiebreak") val isTiebreak: Boolean,
    /** 1–4; 0 en el desempate. */
    val round: Int,
    @SerialName("category_id") val categoryId: String,
    /** null mientras gira la ruleta o se apuesta: la pregunta no sale del servidor antes de tiempo. */
    val question: QuestionText? = null,
    @SerialName("my_bet") val myBet: Int? = null,
    @SerialName("my_choice") val myChoice: Int? = null,
    @SerialName("allowed_bets") val allowedBets: List<Int>? = null,
)

@Serializable
data class QuestionText(
    val text: String,
    val options: List<String>,
    val kind: String,
    val format: String,
    @SerialName("is_vote") val isVote: Boolean,
)

@Serializable
data class QuestionOutcome(
    @SerialName("winning_options") val winningOptions: List<Int>,
    @SerialName("vote_counts") val voteCounts: Map<String, Int> = emptyMap(),
    val scored: Boolean,
    @SerialName("tiebreak_winner") val tiebreakWinner: String? = null,
    val explanation: String,
    val source: String? = null,
    @SerialName("game_rule") val gameRule: String? = null,
    val outcomes: List<PlayerAnswer>,
)

@Serializable
data class PlayerAnswer(
    @SerialName("user_id") val userId: String,
    val choice: Int? = null,
    @SerialName("elapsed_ms") val elapsedMs: Long? = null,
    val won: Boolean? = null,
    val points: Int,
    val bet: Int? = null,
)

@Serializable
data class FinalEntry(@SerialName("user_id") val userId: String, val correct: Int)

/** Errores que la app sabe explicar. Los códigos los lanza el servidor. */
enum class OnlineError(val code: String) {
    NOT_SIGNED_IN("not_authenticated"),
    ROOM_NOT_FOUND("room_not_found"),
    ROOM_FULL("room_full"),
    ROOM_IN_PROGRESS("room_in_progress"),
    ROOM_CLOSED("room_closed"),
    ROOM_LEFT("room_left"),
    NOT_IN_ROOM("not_in_room"),
    NOT_HOST("not_host"),
    NOT_ENOUGH_PLAYERS("not_enough_players"),
    NOT_ENOUGH_QUESTIONS("not_enough_questions"),
    RATE_LIMITED("rate_limited"),
    TIME_EXPIRED("time_expired"),
    ALREADY_ANSWERED("already_answered"),
    ALREADY_BET("already_bet"),
    WRONG_PHASE("wrong_phase"),
    WRONG_QUESTION("wrong_question"),
    INVALID_BET("invalid_bet"),
    INVALID_OPTION("invalid_option"),
    INVALID_MODE("invalid_mode"),
    NETWORK("network"),
    UNKNOWN("unknown"),
    ;

    companion object {
        /** Busca un código conocido dentro del mensaje de error del servidor. */
        fun from(message: String?): OnlineError =
            entries.firstOrNull { it != NETWORK && it != UNKNOWN && message != null && it.code in message } ?: UNKNOWN
    }
}

sealed interface OnlineResult<out T> {
    data class Ok<T>(val value: T) : OnlineResult<T>
    data class Failed(val error: OnlineError) : OnlineResult<Nothing>
}

/** Acceso a las salas. La implementación real llama a las funciones del servidor en Supabase. */
interface OnlineRepository {
    suspend fun ensureSignedIn(): OnlineResult<Unit>

    /** Sala en la que el jugador sigue dentro (para reconectar al volver). null si no hay. */
    suspend fun currentRoom(): OnlineResult<RoomView?>
    suspend fun createRoom(publicRoom: Boolean, mode: String): OnlineResult<RoomView>
    suspend fun joinByCode(code: String): OnlineResult<RoomView>
    suspend fun findPublic(mode: String): OnlineResult<RoomView>
    suspend fun state(roomId: String): OnlineResult<RoomView>
    suspend fun start(roomId: String): OnlineResult<RoomView>
    suspend fun leave(roomId: String): OnlineResult<Unit>
    suspend fun bet(roomId: String, index: Int, amount: Int): OnlineResult<RoomView>
    suspend fun answer(roomId: String, index: Int, choice: Int, clientElapsedMs: Long): OnlineResult<RoomView>
}
