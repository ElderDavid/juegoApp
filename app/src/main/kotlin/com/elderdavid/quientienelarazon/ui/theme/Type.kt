package com.elderdavid.quientienelarazon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.elderdavid.quientienelarazon.R

// Fuentes incluidas en el APK (unos 150 KB en total, recortadas a los caracteres latinos):
// funcionan sin conexión. Licencia SIL Open Font License (ver docs/FUENTES.md).

/** Bungee: letras de letrero, para títulos, marcadores y la categoría de la ruleta. */
val Letrero = FontFamily(Font(R.font.bungee_regular, FontWeight.Normal))

/** Atkinson Hyperlegible Next: muy legible a distancia, para preguntas, opciones y textos. */
val Lectura = FontFamily(
    Font(R.font.atkinson_next_400, FontWeight.Normal),
    Font(R.font.atkinson_next_600, FontWeight.SemiBold),
    Font(R.font.atkinson_next_800, FontWeight.ExtraBold),
)

/** Sombra dura de tinta bajo los títulos, como la de las letras pintadas. */
fun inkShadow(px: Float = 6f) = Shadow(color = Tinta, offset = Offset(px, px), blurRadius = 0f)

private val Display = TextStyle(fontFamily = Letrero, fontWeight = FontWeight.Normal)
private val Body = TextStyle(fontFamily = Lectura, fontWeight = FontWeight.Normal)

val QtrTypography = Typography(
    displayLarge = Display.copy(fontSize = 44.sp, lineHeight = 44.sp),
    displayMedium = Display.copy(fontSize = 34.sp, lineHeight = 38.sp),
    displaySmall = Display.copy(fontSize = 28.sp, lineHeight = 32.sp),
    headlineLarge = Display.copy(fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = Display.copy(fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = Body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = Body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = Body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = Body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = Body.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = Body.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = Body.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = Body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp),
    labelMedium = Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
)
