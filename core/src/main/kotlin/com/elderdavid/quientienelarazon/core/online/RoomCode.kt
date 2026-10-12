package com.elderdavid.quientienelarazon.core.online

/**
 * Código de invitación de las salas privadas: 6 caracteres sin letras ni números que se confundan
 * (sin I, O, 0 ni 1). El servidor genera el código y aplica la misma normalización al buscarlo.
 */
object RoomCode {
    const val LENGTH = 6
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Mayúsculas y sin espacios ni guiones, como lo escribe la gente al dictarlo. */
    fun normalize(input: String): String = input.filter { it.isLetterOrDigit() }.uppercase()

    fun isValid(input: String): Boolean {
        val code = normalize(input)
        return code.length == LENGTH && code.all { it in ALPHABET }
    }

    /** Agrupa el código en dos bloques para leerlo en voz alta: «ABC DEF». */
    fun display(code: String): String = normalize(code).let { if (it.length == LENGTH) it.take(3) + " " + it.drop(3) else it }
}
