package com.elderdavid.quientienelarazon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val ArcadeColorScheme = darkColorScheme(
    primary = ArcadeCyan,
    onPrimary = ArcadeNight,
    primaryContainer = ArcadePanelHigh,
    onPrimaryContainer = ArcadeText,
    secondary = ArcadeMagenta,
    onSecondary = ArcadeNight,
    tertiary = ArcadeOrange,
    onTertiary = ArcadeNight,
    background = ArcadeNight,
    onBackground = ArcadeText,
    surface = ArcadePanel,
    onSurface = ArcadeText,
    surfaceVariant = ArcadePanelHigh,
    onSurfaceVariant = ArcadeTextMuted,
    outline = ArcadeOutline,
    error = ArcadeError,
    onError = ArcadeNight,
)

private val ArcadeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/**
 * El juego siempre usa el tema oscuro arcade, independientemente del tema del sistema.
 * [themeId]: tema de color equipado (cosmético), que solo cambia el fondo y los títulos.
 */
@Composable
fun QtrTheme(themeId: String? = null, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalArcadeStyle provides ArcadeStyle.forTheme(themeId)) {
        MaterialTheme(
            colorScheme = ArcadeColorScheme,
            typography = QtrTypography,
            shapes = ArcadeShapes,
            content = content,
        )
    }
}
