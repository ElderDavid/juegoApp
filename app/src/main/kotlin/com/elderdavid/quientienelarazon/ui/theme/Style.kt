package com.elderdavid.quientienelarazon.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Detalles visuales que cambian con los cosméticos equipados: el fondo y el color de los títulos
 * (tema) y el borde del avatar (marco). El resto de la paleta arcade no cambia, para mantener el
 * contraste revisado.
 */
data class ArcadeStyle(
    val backgroundTop: Color = ArcadeDeep,
    val backgroundBottom: Color = ArcadeNight,
    val title: Color = ArcadeCyan,
) {
    companion object {
        fun forTheme(themeId: String?): ArcadeStyle = when (themeId) {
            "theme_neon_caribe" -> ArcadeStyle(backgroundTop = Color(0xFF003B4A), backgroundBottom = ArcadeNight, title = Color(0xFF3DF5C8))
            "theme_atardecer" -> ArcadeStyle(backgroundTop = Color(0xFF4A1530), backgroundBottom = Color(0xFF1A0B20), title = Color(0xFFFFB347))
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
