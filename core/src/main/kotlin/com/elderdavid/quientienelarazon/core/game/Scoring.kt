package com.elderdavid.quientienelarazon.core.game

/** Cálculos de puntuación puros y deterministas. */
object Scoring {

    /**
     * Bonificación por rapidez: lineal entre [GameRules.SPEED_BONUS_MAX] (respuesta inmediata)
     * y 0 (al vencer el tiempo), con división entera. Nunca es negativa ni supera el máximo.
     */
    fun speedBonus(elapsedMillis: Long, timeLimitMillis: Long, max: Int = GameRules.SPEED_BONUS_MAX): Int {
        require(timeLimitMillis > 0) { "El tiempo límite debe ser positivo" }
        val remaining = (timeLimitMillis - elapsedMillis).coerceIn(0, timeLimitMillis)
        return ((max.toLong() * remaining) / timeLimitMillis).toInt()
    }

    /** Puntos base por acierto en cada ronda (la ronda de estrategia usa la apuesta). */
    fun basePoints(round: RoundType): Int = when (round) {
        RoundType.KNOWLEDGE -> GameRules.KNOWLEDGE_POINTS
        RoundType.SPEED -> GameRules.SPEED_POINTS
        RoundType.STRATEGY -> 0
        RoundType.FINAL -> GameRules.FINAL_POINTS
    }

    fun hasSpeedBonus(round: RoundType): Boolean = round == RoundType.SPEED || round == RoundType.FINAL

    /**
     * Apuestas que un jugador puede hacer con su saldo. Lista vacía = no puede apostar:
     * juega con [Bet.Free] (regla de la sección 5, ronda 3).
     */
    fun allowedBets(score: Int): List<Int> = GameRules.ALLOWED_BETS.filter { it <= score }

    /** Apuesta automática cuando el jugador no elige (p. ej. se le acaba el tiempo): la mínima posible. */
    fun defaultBet(score: Int): Bet = allowedBets(score).minOrNull()?.let { Bet.Amount(it) } ?: Bet.Free

    /** Variación de puntos en la ronda de estrategia. Nunca deja el saldo en negativo. */
    fun betDelta(bet: Bet, won: Boolean): Int = when (bet) {
        is Bet.Amount -> if (won) bet.value else -bet.value
        Bet.Free -> if (won) GameRules.FREE_BET_REWARD else 0
    }
}

/**
 * Apuesta de la ronda de estrategia.
 * [Free]: quien tiene menos de la apuesta mínima juega sin riesgo; si acierta gana
 * [GameRules.FREE_BET_REWARD] y si falla no pierde nada.
 */
sealed interface Bet {
    data class Amount(val value: Int) : Bet
    data object Free : Bet
}
