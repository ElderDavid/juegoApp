package com.elderdavid.quientienelarazon.core.online

/**
 * Reloj de una fase online. El servidor manda cuánto le queda a la fase; el teléfono descuenta
 * desde que recibió el dato con su propio reloj monótono, así la diferencia entre relojes no importa.
 */
data class PhaseClock(val remainingAtReceipt: Long, val receivedAt: Long, val totalMillis: Long) {
    fun remaining(now: Long): Long = (remainingAtReceipt - (now - receivedAt)).coerceIn(0, totalMillis.coerceAtLeast(0))

    fun fraction(now: Long): Float = if (totalMillis <= 0) 0f else remaining(now).toFloat() / totalMillis

    /** Segundos que se muestran: 8, 7… 1 y 0 solo cuando se acabó. */
    fun secondsShown(now: Long): Int = ((remaining(now) + 999) / 1000).toInt()
}

/**
 * Tiempo que el jugador tardó desde que VIO la pregunta. El servidor solo lo acepta si es
 * coherente con lo que él midió (como mucho 1,5 s menos), así que no da ventaja mentir.
 */
fun clientElapsed(questionShownAt: Long, now: Long): Long = (now - questionShownAt).coerceAtLeast(0)
