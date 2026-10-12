package com.elderdavid.quientienelarazon.core.ads

/**
 * Cuándo se puede mostrar un anuncio a pantalla completa. Solo se consulta al salir de la pantalla
 * final de una partida (nunca durante una pregunta) y limita la frecuencia para no cansar:
 * - como mucho uno cada [minGamesBetween] partidas terminadas,
 * - y nunca dos con menos de [minIntervalMillis] de diferencia.
 *
 * Es inmutable: cada paso devuelve un estado nuevo. El tiempo es monotónico (elapsedRealtime) y
 * el estado vive solo mientras la app está abierta.
 */
data class InterstitialPacing(
    val minGamesBetween: Int = DEFAULT_MIN_GAMES_BETWEEN,
    val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    /** Partidas terminadas desde el último anuncio (o desde que se abrió la app). */
    val gamesSinceLastAd: Int = 0,
    val lastShownAtMillis: Long? = null,
    /** Clave de la última partida contada, para no contar dos veces la misma. */
    val lastGameKey: String? = null,
) {
    init {
        require(minGamesBetween >= 1) { "minGamesBetween debe ser al menos 1" }
        require(minIntervalMillis >= 0) { "minIntervalMillis no puede ser negativo" }
    }

    /** Anota una partida terminada. Llamar varias veces con la misma [gameKey] cuenta una sola. */
    fun gameFinished(gameKey: String): InterstitialPacing =
        if (gameKey == lastGameKey) this else copy(gamesSinceLastAd = gamesSinceLastAd + 1, lastGameKey = gameKey)

    fun canShow(nowMillis: Long): Boolean {
        if (gamesSinceLastAd < minGamesBetween) return false
        val last = lastShownAtMillis ?: return true
        return nowMillis - last >= minIntervalMillis
    }

    fun shown(nowMillis: Long): InterstitialPacing = copy(gamesSinceLastAd = 0, lastShownAtMillis = nowMillis)

    companion object {
        const val DEFAULT_MIN_GAMES_BETWEEN = 2
        const val DEFAULT_MIN_INTERVAL_MILLIS = 3 * 60 * 1000L
    }
}
