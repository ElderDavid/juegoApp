package com.elderdavid.quientienelarazon.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Detalles visuales que cambian con los cosméticos equipados: el color del fondo y el de los títulos
 * (tema) y el borde del avatar (marco). Todos los fondos son lo bastante oscuros para que el texto
 * papel, suave y mango cumpla el contraste 4,5:1; el resto de la paleta no cambia.
 */
data class ArcadeStyle(
    val backgroundTop: Color = Anil,
    val backgroundBottom: Color = AnilDeep,
    val title: Color = Papel,
) {
    companion object {
        fun forTheme(themeId: String?): ArcadeStyle = when (themeId) {
            "theme_neon_caribe" -> ArcadeStyle(backgroundTop = Color(0xFF0A5560), backgroundBottom = Color(0xFF07444D), title = Color(0xFF8AF5DA))
            "theme_atardecer" -> ArcadeStyle(backgroundTop = Color(0xFF7E2440), backgroundBottom = Color(0xFF661B33), title = Color(0xFFFFD27A))
            "theme_selva" -> ArcadeStyle(backgroundTop = Color(0xFF175A30), backgroundBottom = Color(0xFF104724), title = Color(0xFFD2F78A))
            "theme_galaxia" -> ArcadeStyle(backgroundTop = Color(0xFF4C2288), backgroundBottom = Color(0xFF3B1A6C), title = Color(0xFFFFC2F2))
            else -> ArcadeStyle()
        }
    }
}

val LocalArcadeStyle = staticCompositionLocalOf { ArcadeStyle() }

/** Color del marco de avatar equipado, o null si no hay. */
fun frameColor(frameId: String?): Color? = when (frameId) {
    "frame_bronze" -> Color(0xFFCD7F32)
    "frame_oro" -> Color(0xFFFFD54F)
    "frame_costa" -> Color(0xFF00C2D1)
    "frame_paisa" -> Color(0xFF7CD957)
    "frame_llanero" -> Color(0xFFE6A23C)
    "frame_pacifico" -> Color(0xFF2E9BFF)
    else -> null
}
