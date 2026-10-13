package com.elderdavid.quientienelarazon.core.game

/**
 * Constantes de reglas compartidas por el modo local, el modo online y la pantalla de reglas.
 * Cualquier cambio aquí afecta a todos los modos a la vez: hay un único motor de reglas.
 */
object GameRules {
    /** Mínimo de jugadores en una sala online. */
    const val MIN_PLAYERS = 2

    /** En el teléfono también se puede jugar solo (modo solitario). */
    const val MIN_LOCAL_PLAYERS = 1
    const val MAX_PLAYERS = 4

    const val ROUNDS = 4

    const val QUESTION_TIME_MILLIS = 8_000L

    /** Modo niños: 1,5 veces el tiempo normal para leer y responder con calma. */
    const val KIDS_QUESTION_TIME_MILLIS = 12_000L

    /** Modo «muy rápido» (se compra con monedas): menos tiempo para responder. */
    const val FAST_QUESTION_TIME_MILLIS = 5_000L

    const val KNOWLEDGE_POINTS = 100
    const val SPEED_POINTS = 100
    const val SPEED_BONUS_MAX = 50
    const val FINAL_POINTS = 150
    const val MAJORITY_VOTE_POINTS = 100

    /** Apuestas permitidas en la ronda de estrategia. */
    val ALLOWED_BETS: List<Int> = listOf(50, 100)

    /** Acertar una apuesta paga el doble de lo apostado (apuestas 100, ganas 200). Fallar resta lo apostado. */
    const val BET_WIN_MULTIPLIER = 2

    /**
     * Pregunta robo: la última de la ronda de velocidad, a mitad de partida. Quien acierta más rápido
     * le quita este porcentaje de sus puntos a cada rival. No hay robo en solitario ni en modo niños.
     */
    const val HEIST_PERCENT = 20

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

/**
 * Duración de la partida elegida antes de jugar. Siempre son las cuatro rondas; cambia cuántas
 * preguntas tiene cada una. En la de 10, conocimiento y final llevan una más que las del medio.
 */
enum class GameLength(val questions: Int, val perRound: List<Int>) {
    SHORT(8, listOf(2, 2, 2, 2)),
    MEDIUM(10, listOf(3, 2, 2, 3)),
    STANDARD(12, listOf(3, 3, 3, 3)),
    ;

    init {
        check(perRound.size == GameRules.ROUNDS && perRound.sum() == questions)
    }

    /** Índice de la primera pregunta de cada ronda, más el total al final. */
    private val starts: List<Int> = perRound.runningFold(0) { acc, n -> acc + n }

    fun roundFor(index: Int): RoundType {
        require(index in 0 until questions) { "Índice de pregunta fuera de rango: $index" }
        return RoundType.entries[starts.indexOfLast { it <= index }]
    }

    /** true si [index] es la primera pregunta de su ronda (ahí se anuncia la ronda). */
    fun isFirstOfRound(index: Int): Boolean = index in starts.dropLast(1)

    /** true si [index] cierra su ronda (después viene el marcador o el final). */
    fun isLastOfRound(index: Int): Boolean = index + 1 in starts.drop(1)

    /** true si [index] es la pregunta robo: la última de la ronda de velocidad. */
    fun isHeist(index: Int): Boolean = roundFor(index) == RoundType.SPEED && isLastOfRound(index)

    companion object {
        val DEFAULT = STANDARD

        fun fromQuestions(questions: Int): GameLength? = entries.firstOrNull { it.questions == questions }
    }
}

/** Las cuatro rondas de una partida, en orden. */
enum class RoundType(val number: Int, val title: String) {
    KNOWLEDGE(1, "Conocimiento"),
    SPEED(2, "Velocidad"),
    STRATEGY(3, "Estrategia"),
    FINAL(4, "Final"),
}
