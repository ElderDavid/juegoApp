package com.elderdavid.quientienelarazon.core.game

/**
 * Constantes de reglas compartidas por el modo local, el modo online y la pantalla de reglas.
 * Cualquier cambio aquí afecta a todos los modos a la vez: hay un único motor de reglas.
 */
object GameRules {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    const val ROUNDS = 4
    const val QUESTIONS_PER_ROUND = 3
    const val TOTAL_QUESTIONS = ROUNDS * QUESTIONS_PER_ROUND

    const val QUESTION_TIME_MILLIS = 8_000L

    const val KNOWLEDGE_POINTS = 100
    const val SPEED_POINTS = 100
    const val SPEED_BONUS_MAX = 50
    const val FINAL_POINTS = 150
    const val MAJORITY_VOTE_POINTS = 100

    /** Apuestas permitidas en la ronda de estrategia. */
    val ALLOWED_BETS: List<Int> = listOf(50, 100)

    /**
     * Regla explícita para quien no tiene saldo para la apuesta mínima: juega una «apuesta libre»
     * que suma esta cantidad si acierta y no resta nada si falla. Así nadie queda en negativo.
     */
    const val FREE_BET_REWARD = 50

    /** Las preguntas de votación solo tienen sentido con 3 o más jugadores (con 2 siempre ganan todos). */
    const val MIN_PLAYERS_FOR_VOTES = 3

    /** Máximo de preguntas de votación (opinión o debate) por partida. */
    const val MAX_VOTE_QUESTIONS = 1

    /** Preguntas de desempate reservadas al planificar la partida. */
    const val TIEBREAK_RESERVE = 5
}

/** Las cuatro rondas de una partida estándar, en orden. */
enum class RoundType(val number: Int, val title: String) {
    KNOWLEDGE(1, "Conocimiento"),
    SPEED(2, "Velocidad"),
    STRATEGY(3, "Estrategia"),
    FINAL(4, "Final"),
    ;

    companion object {
        fun forQuestionIndex(index: Int): RoundType {
            require(index in 0 until GameRules.TOTAL_QUESTIONS) { "Índice de pregunta fuera de rango: $index" }
            return entries[index / GameRules.QUESTIONS_PER_ROUND]
        }
    }
}
