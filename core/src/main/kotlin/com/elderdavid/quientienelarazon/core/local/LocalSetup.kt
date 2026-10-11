package com.elderdavid.quientienelarazon.core.local

import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.Player
import com.elderdavid.quientienelarazon.core.game.PlayerId

/** Problemas con los nombres introducidos en la configuración de la partida local. */
enum class NameIssue { EMPTY, TOO_LONG, DUPLICATE }

/** Validación de jugadores para el modo local. */
object LocalSetup {
    const val MAX_NAME_LENGTH = 16

    fun normalize(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    /** Problema de cada nombre por posición; mapa vacío = todos válidos. */
    fun issues(names: List<String>): Map<Int, NameIssue> {
        val normalized = names.map(::normalize)
        val result = mutableMapOf<Int, NameIssue>()
        normalized.forEachIndexed { i, name ->
            when {
                name.isEmpty() -> result[i] = NameIssue.EMPTY
                name.length > MAX_NAME_LENGTH -> result[i] = NameIssue.TOO_LONG
                normalized.take(i).any { it.equals(name, ignoreCase = true) } -> result[i] = NameIssue.DUPLICATE
            }
        }
        return result
    }

    fun canStart(names: List<String>): Boolean =
        names.size in GameRules.MIN_PLAYERS..GameRules.MAX_PLAYERS && issues(names).isEmpty()

    /** Jugadores locales con IDs estables por posición; el avatar es el índice de color. */
    fun players(names: List<String>): List<Player> {
        require(canStart(names)) { "Configuración de jugadores no válida" }
        return names.mapIndexed { i, name -> Player(PlayerId("local-${i + 1}"), normalize(name), avatarId = i) }
    }
}
