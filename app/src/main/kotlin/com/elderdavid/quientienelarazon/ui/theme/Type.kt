package com.elderdavid.quientienelarazon.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tipografía del sistema (sin fuentes descargables): funciona sin conexión y no aumenta el tamaño del APK.
private val Display = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black)
private val Body = TextStyle(fontFamily = FontFamily.SansSerif)

val QtrTypography = Typography(
    displayLarge = Display.copy(fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp),
    displayMedium = Display.copy(fontSize = 36.sp, lineHeight = 40.sp),
    headlineLarge = Display.copy(fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = Display.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = Body.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = Body.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = Body.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = Body.copy(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = Body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 1.sp),
    labelMedium = Body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.8.sp),
)
