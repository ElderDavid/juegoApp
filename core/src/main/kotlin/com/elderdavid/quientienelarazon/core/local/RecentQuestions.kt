package com.elderdavid.quientienelarazon.core.local

import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.questions.Question

/**
 * Preguntas que ya salieron en partidas locales de este teléfono, de la más antigua a la más reciente.
 *
 * Dentro de una partida el planificador nunca repite una pregunta. Esta lista sirve para que las
 * partidas siguientes tampoco repitan las de las anteriores mientras queden preguntas nuevas en el
 * modo: cuando se acaban, vuelven a salir primero las que se vieron hace más tiempo.
 */
data class RecentQuestions(val ids: List<String> = emptyList()) {

    /** Añade las preguntas de una partida (las planificadas y las reservadas para desempate). */
    fun plus(state: GameState): RecentQuestions =
        plus(state.plan.questions.map { it.questionId } + state.plan.tiebreakQueue)

    fun plus(played: Collection<String>): RecentQuestions {
        if (played.isEmpty()) return this
        val playedSet = played.toSet()
        return RecentQuestions((ids.filterNot { it in playedSet } + playedSet).takeLast(MAX_IDS))
    }

    /**
     * Preguntas del [pool] que conviene evitar en la próxima partida: las más recientes, dejando sin
     * evitar al menos el doble de las que necesita la partida para que la ruleta tenga dónde elegir.
     */
    fun avoidFor(pool: List<Question>, needed: Int): Set<String> {
        val poolIds = pool.mapTo(HashSet()) { it.id }
        val room = (poolIds.size - 2 * needed).coerceAtLeast(0)
        return ids.filter { it in poolIds }.takeLast(room).toSet()
    }

    fun encode(): String = ids.joinToString("\n")

    companion object {
        /** Más que todo el banco del teléfono junto, para no olvidar ninguna antes de tiempo. */
        const val MAX_IDS = 2000

        fun decode(raw: String?): RecentQuestions =
            RecentQuestions(raw.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct().takeLast(MAX_IDS))
    }
}
