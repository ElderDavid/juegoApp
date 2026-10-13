package com.elderdavid.quientienelarazon.ui.local

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.elderdavid.quientienelarazon.ui.theme.PlayerColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val DURATION_MILLIS = 4_000L

/**
 * Animación de victoria equipada (cosmético). Dura 4 segundos, es decorativa (los lectores de
 * pantalla la ignoran) y no se muestra con «Reducir animaciones».
 */
@Composable
internal fun VictoryCelebration(victoryId: String) {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(victoryId) {
        val start = withFrameMillis { it }
        while (elapsed < DURATION_MILLIS) {
            elapsed = withFrameMillis { it } - start
        }
    }
    if (elapsed >= DURATION_MILLIS) return
    val seeds = remember(victoryId) { List(60) { Random(it * 7919 + victoryId.hashCode()) } .map { r -> FloatArray(4) { r.nextFloat() } } }
    Canvas(Modifier.fillMaxWidth().height(140.dp).clearAndSetSemantics { }) {
        val t = elapsed / 1000f
        when (victoryId) {
            "victory_fireworks" -> seeds.forEachIndexed { i, p ->
                val burst = i % 4
                val center = Offset(size.width * (0.2f + 0.2f * burst), size.height * (0.3f + 0.2f * (burst % 2)))
                val local = (t - burst * 0.5f).coerceAtLeast(0f)
                if (local <= 0f || local > 1.6f) return@forEachIndexed
                val angle = p[0] * 2 * Math.PI.toFloat()
                val radius = local * 90f * (0.6f + 0.4f * p[1])
                drawCircle(
                    color = PlayerColors[i % PlayerColors.size].copy(alpha = (1f - local / 1.6f).coerceIn(0f, 1f)),
                    radius = 5f,
                    center = center + Offset(cos(angle) * radius, sin(angle) * radius),
                )
            }
            "victory_trumpets" -> {
                // Dos trompetas doradas, una a cada lado, que soplan notas hacia el centro.
                val blow = 1f + 0.06f * sin(t * 12f)
                drawTrumpet(blow)
                scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2, size.height / 2)) { drawTrumpet(blow) }
                seeds.take(24).forEachIndexed { i, p ->
                    val local = (t - (i / 2) * 0.3f) % 1.8f
                    if (local < 0f) return@forEachIndexed
                    val fromLeft = i % 2 == 0
                    val startX = size.width * if (fromLeft) 0.38f else 0.62f
                    val dx = local * (40f + 50f * p[0]) * if (fromLeft) 1f else -1f
                    val x = startX + dx + sin(local * 6f + p[1] * 6f) * 10f
                    val y = size.height * 0.5f - local * size.height * 0.28f
                    drawNote(Offset(x, y), PlayerColors[i % PlayerColors.size].copy(alpha = (1f - local / 1.8f).coerceIn(0f, 1f)))
                }
            }
            else -> seeds.forEachIndexed { i, p ->
                // Confeti: cae con un vaivén lateral.
                val x = size.width * p[0] + sin(t * 3 + p[1] * 6) * 18f
                val y = ((p[2] * size.height) + t * (60f + 80f * p[3])) % size.height
                drawRect(
                    color = PlayerColors[i % PlayerColors.size],
                    topLeft = Offset(x, y),
                    size = Size(10f, 6f),
                )
            }
        }
    }
}

private val TrumpetGold = Color(0xFFFFC94D)

/** Trompeta que apunta a la derecha desde el borde izquierdo. [blow] hace vibrar la campana. */
private fun DrawScope.drawTrumpet(blow: Float) {
    val y = size.height * 0.5f
    val start = size.width * 0.04f
    val bellStart = size.width * 0.26f
    val bellEnd = size.width * 0.38f
    // Boquilla y tubo.
    drawCircle(TrumpetGold, radius = 6f, center = Offset(start, y))
    drawRect(TrumpetGold, topLeft = Offset(start, y - 4f), size = Size(bellStart - start, 8f))
    // Pistones.
    for (k in 0 until 3) {
        drawRect(TrumpetGold, topLeft = Offset(start + (bellStart - start) * (0.35f + 0.15f * k), y - 18f), size = Size(6f, 14f))
    }
    // Campana.
    val half = 24f * blow
    val bell = Path().apply {
        moveTo(bellStart, y - 4f)
        lineTo(bellEnd, y - half)
        lineTo(bellEnd, y + half)
        lineTo(bellStart, y + 4f)
        close()
    }
    drawPath(bell, TrumpetGold)
}

/** Nota musical sencilla: cabeza, plica y corchete. */
private fun DrawScope.drawNote(at: Offset, color: Color) {
    drawCircle(color, radius = 7f, center = at)
    drawRect(color, topLeft = Offset(at.x + 5f, at.y - 26f), size = Size(3f, 26f))
    drawRect(color, topLeft = Offset(at.x + 5f, at.y - 26f), size = Size(12f, 4f))
}
