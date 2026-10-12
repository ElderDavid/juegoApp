package com.elderdavid.quientienelarazon.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elderdavid.quientienelarazon.ui.theme.ArcadeCyan
import com.elderdavid.quientienelarazon.ui.theme.ArcadeDeep
import com.elderdavid.quientienelarazon.ui.theme.ArcadeMagenta
import com.elderdavid.quientienelarazon.ui.theme.ArcadeNight
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOutline
import com.elderdavid.quientienelarazon.ui.theme.ArcadePanel
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted
import com.elderdavid.quientienelarazon.ui.theme.PlayerColors

/** Fondo arcade con degradado sutil; no anima para no gastar batería. */
@Composable
fun ArcadeBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ArcadeDeep, ArcadeNight, ArcadeNight))),
    ) { content() }
}

/**
 * Pantalla estándar: respeta las áreas seguras (barras del sistema, recortes de cámara),
 * limita el ancho en tablets y permite desplazamiento en pantallas pequeñas.
 */
@Composable
fun ArcadeScreen(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ArcadeBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (onBack != null || title != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) {
                            TextButton(onClick = onBack) { Text("‹ Volver", color = ArcadeCyan) }
                        }
                    }
                }
                if (title != null) {
                    NeonTitle(title)
                }
                content()
            }
        }
    }
}

@Composable
fun NeonTitle(text: String, modifier: Modifier = Modifier, color: Color = ArcadeCyan) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
        color = color,
        textAlign = TextAlign.Center,
    )
}

/** Botón principal. Altura mínima de 56 dp para pulsaciones cómodas. */
@Composable
fun ArcadeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = ArcadeCyan,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = ArcadeNight,
            disabledContainerColor = ArcadePanel,
            disabledContentColor = ArcadeTextMuted,
        ),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
    }
}

/** Botón secundario con borde neón. */
@Composable
fun ArcadeOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = ArcadeMagenta,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, if (enabled) color else ArcadeOutline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = ArcadeTextMuted,
        ),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Opción del menú que aún no está disponible en esta fase. Se muestra deshabilitada y lo dice
 * explícitamente, para no aparentar una función que no existe.
 */
@Composable
fun ComingSoonButton(text: String, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, ArcadeOutline),
    ) {
        Text(
            "${text.uppercase()}  ·  PRÓXIMAMENTE",
            style = MaterialTheme.typography.labelMedium,
            color = ArcadeTextMuted,
        )
    }
}

@Composable
fun ArcadeCard(
    modifier: Modifier = Modifier,
    accent: Color = ArcadeOutline,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = ArcadePanel),
        border = BorderStroke(1.5.dp, accent),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/**
 * Avatar circular con el color del jugador y su inicial (o el emoji de su perfil en online);
 * distinguible también sin color.
 */
@Composable
fun PlayerAvatar(name: String, playerIndex: Int, modifier: Modifier = Modifier, size: Int = 48, emoji: String? = null) {
    val color = PlayerColors[playerIndex.mod(PlayerColors.size)]
    Box(
        modifier = modifier
            .size(size.dp)
            .border(3.dp, color, CircleShape)
            .background(ArcadePanel, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji ?: name.trim().firstOrNull()?.uppercase() ?: "?",
            color = color,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
