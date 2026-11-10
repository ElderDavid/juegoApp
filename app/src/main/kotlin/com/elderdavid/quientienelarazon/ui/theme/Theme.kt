package com.elderdavid.quientienelarazon.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val ArcadeColorScheme = darkColorScheme(
    primary = Mango,
    onPrimary = Tinta,
    primaryContainer = PanelHigh,
    onPrimaryContainer = Papel,
    secondary = Chicle,
    onSecondary = Tinta,
    secondaryContainer = PanelHigh,
    onSecondaryContainer = Papel,
    tertiary = Menta,
    onTertiary = Tinta,
    background = Anil,
    onBackground = Papel,
    surface = Panel,
    onSurface = Papel,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = PapelSoft,
    surfaceContainer = Panel,
    surfaceContainerHigh = Panel,
    surfaceContainerHighest = PanelHigh,
    outline = Linea,
    outlineVariant = Linea,
    error = Rojo,
    onError = Tinta,
)

private val ArcadeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

/**
 * El juego siempre usa su propio tema, independientemente del tema del sistema.
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
