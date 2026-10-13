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
