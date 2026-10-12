package com.elderdavid.quientienelarazon.core.profile

import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.game.Phase
import com.elderdavid.quientienelarazon.core.game.PlayerId

/** Estadísticas acumuladas de un jugador. */
data class PlayerStats(
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    /** Aciertos en preguntas objetivas (las votaciones no cuentan como acierto ni fallo). */
    val correctAnswers: Int = 0,
    val objectiveAnswered: Int = 0,
    val bestScore: Int = 0,
    val xp: Int = 0,
) {
    val level: Int get() = Levels.levelFor(xp)
    val accuracyPercent: Int get() = if (objectiveAnswered == 0) 0 else correctAnswers * 100 / objectiveAnswered

    operator fun plus(game: GameStatsDelta) = PlayerStats(
        gamesPlayed = gamesPlayed + 1,
        wins = wins + if (game.won) 1 else 0,
        losses = losses + if (game.won) 0 else 1,
        correctAnswers = correctAnswers + game.correctAnswers,
        objectiveAnswered = objectiveAnswered + game.objectiveQuestions,
        bestScore = maxOf(bestScore, game.score),
        xp = xp + game.xp,
    )
}

/** Lo que aporta una partida terminada a las estadísticas de un jugador. */
data class GameStatsDelta(
    val won: Boolean,
    val score: Int,
    val correctAnswers: Int,
    val objectiveQuestions: Int,
    val xp: Int,
)

object Levels {
    const val XP_PER_GAME = 20
    const val XP_PER_CORRECT = 5
    const val XP_PER_WIN = 30

    /** Nivel 1 con 0 XP; cada nivel cuesta 100 XP más que el anterior (100, 200, 300…). */
    fun levelFor(xp: Int): Int {
        var level = 1
        var needed = 100
        var remaining = xp
        while (remaining >= needed) {
            remaining -= needed
            level++
            needed += 100
        }
        return level
    }

    /**
     * Resultado de una partida terminada para un jugador. Solo cuentan las 12 preguntas principales:
     * el desempate decide el ganador pero no suma aciertos (sección 5 de la especificación).
     * Una victoria compartida cuenta como victoria para cada ganador.
     */
    fun deltaFor(state: GameState, player: PlayerId): GameStatsDelta {
        require(state.phase == Phase.FINISHED) { "La partida no ha terminado" }
        require(state.players.any { it.id == player }) { "Jugador desconocido" }
        check(state.results.size == GameRules.TOTAL_QUESTIONS) { "Faltan resultados de preguntas" }
        // Las objetivas siempre se puntúan y nunca tienen recuento de votos.
        val objective = state.results.filter { r -> r.scored && r.voteCounts.isEmpty() }
        val correct = objective.count { r -> r.outcomes.any { it.playerId == player && it.won == true } }
        val won = player in state.winners
        val xp = XP_PER_GAME + correct * XP_PER_CORRECT + if (won) XP_PER_WIN else 0
        return GameStatsDelta(won, state.scores.getValue(player), correct, objective.size, xp)
    }
}
