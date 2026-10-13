package com.elderdavid.quientienelarazon.core.ads

/** Grupo de edad de quien usa el teléfono, según la pregunta neutral de edad al abrir la app. */
enum class AgeGroup {
    /** Menor de [AgeGate.CHILD_AGE_LIMIT]: anuncios aptos para niños y sin videos con recompensa. */
    CHILD,
    TEEN_OR_ADULT,
}

/**
 * Pregunta neutral de edad (política de Familias de Google Play para apps de público mixto).
 * Se pregunta el año de nacimiento sin sugerir ninguna respuesta. Sin mes ni día, la edad exacta
 * no se conoce: se toma la menor posible, así ante la duda se trata a la persona como niño.
 */
object AgeGate {
    const val CHILD_AGE_LIMIT = 13
    const val MAX_AGE = 120

    /** Grupo para [birthYear] en [currentYear], o null si el año no es válido. */
    fun groupFor(birthYear: Int, currentYear: Int): AgeGroup? {
        if (birthYear > currentYear || birthYear < currentYear - MAX_AGE) return null
        val minimumAge = currentYear - birthYear - 1
        return if (minimumAge < CHILD_AGE_LIMIT) AgeGroup.CHILD else AgeGroup.TEEN_OR_ADULT
    }

    /** Lo que escribe el usuario: solo un año de cuatro cifras válido. */
    fun parse(text: String, currentYear: Int): AgeGroup? {
        val year = text.trim().takeIf { it.length == 4 && it.all(Char::isDigit) }?.toInt() ?: return null
        return groupFor(year, currentYear)
    }
}
