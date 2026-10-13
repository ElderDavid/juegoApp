package com.elderdavid.quientienelarazon.core.profile

import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.game.Phase
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Registro de las partidas locales jugadas en este teléfono.
 *
 * Es independiente de las estadísticas de la cuenta: en una partida local juegan varias personas
 * con un solo teléfono y ninguna tiene por qué ser el dueño de la cuenta, así que esos resultados
 * no se suman al perfil online. La fuente de verdad de cada dato es única:
 * - Estadísticas de la cuenta (nivel, XP, victorias online): el servidor.
 * - Este registro: el almacenamiento local del teléfono. Se pierde al desinstalar o borrar datos.
 */
@Serializable
data class LocalHistory(
    @SerialName("games") val gamesFinished: Int = 0,
    @SerialName("best") val bestScore: Int = 0,
    @SerialName("best_name") val bestScoreName: String? = null,
    /** Partidas más recientes primero, como máximo [MAX_RECENT]. */
    val recent: List<LocalGameSummary> = emptyList(),
) {
    /** Añade una partida terminada. Llamar una sola vez por partida. */
    fun record(state: GameState, finishedAtMillis: Long): LocalHistory {
        require(state.phase == Phase.FINISHED) { "La partida no ha terminado" }
        val players = state.players.map { LocalPlayerScore(it.name, state.scores.getValue(it.id)) }
        val winners = state.players.filter { it.id in state.winners }.map { it.name }
        val summary = LocalGameSummary(
            finishedAtMillis = finishedAtMillis,
            regionId = (state.config.mode as? ContentMode.Regional)?.regionId,
            kids = state.config.kids,
            players = players,
            winners = winners,
        )
        val top = players.maxBy { it.score }
        val newBest = top.score > bestScore || bestScoreName == null
        return copy(
            gamesFinished = gamesFinished + 1,
            bestScore = if (newBest) top.score else bestScore,
            bestScoreName = if (newBest) top.name else bestScoreName,
            recent = (listOf(summary) + recent).take(MAX_RECENT),
        )
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val MAX_RECENT = 10

        private val json = Json { ignoreUnknownKeys = true }

        /** Lee el registro guardado. Si falta o está dañado, empieza de cero en lugar de fallar. */
        fun decode(text: String?): LocalHistory {
            if (text.isNullOrBlank()) return LocalHistory()
            return try {
                json.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                LocalHistory()
            } catch (e: IllegalArgumentException) {
                LocalHistory()
            }
        }
    }
}

@Serializable
data class LocalGameSummary(
    @SerialName("at") val finishedAtMillis: Long,
    /** null = modo general. */
    @SerialName("region") val regionId: String? = null,
    val players: List<LocalPlayerScore>,
    val winners: List<String>,
    /** true si se jugó en modo niños. */
    val kids: Boolean = false,
)

@Serializable
data class LocalPlayerScore(val name: String, val score: Int)
