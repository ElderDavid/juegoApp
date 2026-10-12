package com.elderdavid.quientienelarazon.core.local

/**
 * Comodines de la partida local. Para que el juego siga siendo justo en un teléfono compartido:
 * - cada jugador recibe [WildcardRules.FREE_PER_PLAYER] gratis por partida;
 * - el paquete de partida (lo paga el dueño con monedas) da uno más a TODOS los jugadores;
 * - como mucho uno por jugador y pregunta, y nunca en el desempate.
 * En online no hay comodines: nadie gana por gastar más.
 */
enum class Wildcard(val label: String, val description: String) {
    FIFTY_FIFTY("50/50", "Quita dos opciones falsas."),
    EXTRA_TIME("+5 s", "Cinco segundos más para responder."),
    CHANGE_QUESTION("Cambiar", "Otra pregunta de la misma categoría. Solo si nadie ha respondido aún."),
}

object WildcardRules {
    const val FREE_PER_PLAYER = 1
    const val EXTRA_TIME_MILLIS = 5_000L
}
