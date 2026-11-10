package com.elderdavid.quientienelarazon.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta «letrero de chiva»: colores planos y saturados, contorno de tinta y sombras duras,
// como los letreros pintados a mano de los buses y las tiendas de barrio.
//
// Reglas de contraste (WCAG AA, revisadas con la fórmula de luminancia):
// - Sobre el fondo añil solo va texto papel, suave, mango, menta o cielo (≥ 4,5:1).
//   Chicle y naranja solo en títulos grandes (≥ 3:1). El rojo de error nunca va directo sobre el fondo.
// - Sobre los paneles de tinta pasa cualquier color de la paleta.
// - Sobre los colores de relleno (botones, fichas) el texto siempre es tinta (≥ 5,5:1).

/** Fondo de las pantallas. */
val Anil = Color(0xFF2A39B8)
val AnilDeep = Color(0xFF2231A3)

/** Contornos, sombras duras y texto sobre colores claros. */
val Tinta = Color(0xFF150F3D)

/** Paneles y tarjetas. */
val Panel = Color(0xFF1B1550)
val PanelHigh = Color(0xFF282070)

/** Bordes de elementos apagados o neutros dentro de un panel. */
val Linea = Color(0xFF4A4E9E)

/** Papel: texto principal sobre el fondo y los paneles, y fondo de la pregunta. */
val Papel = Color(0xFFFFF4D6)
val PapelSoft = Color(0xFFCDD2F7)

/** Texto secundario sobre papel. */
val TintaSoft = Color(0xFF4B4473)

val Mango = Color(0xFFFFC21A)
val Chicle = Color(0xFFFF7DC0)
val Menta = Color(0xFF29D391)
val Cielo = Color(0xFF63C7FF)
val Naranja = Color(0xFFFF7A2F)
val Rojo = Color(0xFFFF4D5E)

/** Colores de jugador: distintos en tono y luminancia; el avatar además lleva la inicial. */
val PlayerColors = listOf(Mango, Chicle, Menta, Cielo)
