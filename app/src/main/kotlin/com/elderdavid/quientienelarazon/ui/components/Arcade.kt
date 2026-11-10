package com.elderdavid.quientienelarazon.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elderdavid.quientienelarazon.ui.theme.AnilDeep
import com.elderdavid.quientienelarazon.ui.theme.Letrero
import com.elderdavid.quientienelarazon.ui.theme.Linea
import com.elderdavid.quientienelarazon.ui.theme.LocalArcadeStyle
import com.elderdavid.quientienelarazon.ui.theme.Mango
import com.elderdavid.quientienelarazon.ui.theme.Panel
import com.elderdavid.quientienelarazon.ui.theme.Papel
import com.elderdavid.quientienelarazon.ui.theme.PapelSoft
import com.elderdavid.quientienelarazon.ui.theme.PlayerColors
import com.elderdavid.quientienelarazon.ui.theme.Tinta
import com.elderdavid.quientienelarazon.ui.theme.inkShadow

/** Grosor del contorno de tinta de botones y tarjetas. */
val InkStroke = 3.dp

/**
 * Pieza «calcomanía»: relleno plano, contorno de tinta y una sombra dura desplazada hacia abajo a la
 * derecha. Reserva siempre el espacio de la sombra, para que nada salte al pulsar o al habilitarse.
 * Con [pressed] la pieza baja hasta tapar su sombra, como un botón físico.
 */
fun Modifier.sticker(
    fill: Color,
    shape: Shape,
    depth: Dp = 4.dp,
    raised: Boolean = true,
    pressed: Boolean = false,
    outline: Color = Tinta,
): Modifier = this
    .padding(end = depth, bottom = depth)
    .drawBehind {
        if (raised) {
            val o = depth.toPx()
            val shadow = shape.createOutline(size, layoutDirection, this)
            translate(o, o) { drawOutline(shadow, Tinta) }
        }
    }
    .offset { if (pressed && raised) IntOffset(depth.roundToPx(), depth.roundToPx()) else IntOffset.Zero }
    .clip(shape)
    .background(fill)
    .border(InkStroke, outline, shape)

/** Fondo de color plano (o el del tema equipado); no anima para no gastar batería. */
@Composable
fun ArcadeBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val style = LocalArcadeStyle.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(style.backgroundTop, style.backgroundBottom))),
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
    /** Contenido fijo bajo la zona desplazable (por ejemplo, un banner). */
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ArcadeBackground(modifier) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (onBack != null) BackChip(onBack)
                    if (title != null) NeonTitle(title)
                    content()
                }
            }
            bottomBar?.invoke()
        }
    }
}

/** «Volver»: pequeña pieza de tinta arriba a la izquierda. */
@Composable
private fun BackChip(onBack: () -> Unit) {
    StickerButton(
        onClick = onBack,
        fill = Panel,
        contentColor = Mango,
        depth = 3.dp,
        minHeight = 44.dp,
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text("‹  Volver", style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Título de pantalla con letras de letrero y sombra de tinta. Alineado a la izquierda en las
 * pantallas de menú; las de partida lo centran con [align].
 */
@Composable
fun NeonTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalArcadeStyle.current.title,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.headlineLarge.copy(shadow = inkShadow()),
        color = color,
        textAlign = align,
    )
}

/** Base de todos los botones: calcomanía pulsable con altura mínima cómoda. */
@Composable
fun StickerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Mango,
    contentColor: Color = Tinta,
    enabled: Boolean = true,
    depth: Dp = 4.dp,
    minHeight: Dp = 56.dp,
    shape: Shape = MaterialTheme.shapes.medium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .sticker(
                fill = if (enabled) fill else Panel,
                shape = shape,
                depth = depth,
                raised = enabled,
                pressed = pressed,
                outline = if (enabled) Tinta else Linea,
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = minHeight)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) contentColor else PapelSoft) { content() }
    }
}

/** Botón principal: relleno de color con texto de tinta. */
@Composable
fun ArcadeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Mango,
    enabled: Boolean = true,
) {
    StickerButton(onClick = onClick, modifier = modifier.fillMaxWidth(), fill = color, enabled = enabled) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Botón secundario: panel de tinta con el texto en el color indicado. */
@Composable
fun ArcadeOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Papel,
    enabled: Boolean = true,
) {
    StickerButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        fill = Panel,
        contentColor = color,
        enabled = enabled,
        depth = 3.dp,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/**
 * Opción del menú que aún no está disponible en esta fase. Se muestra deshabilitada y lo dice
 * explícitamente, para no aparentar una función que no existe.
 */
@Composable
fun ComingSoonButton(text: String, modifier: Modifier = Modifier) {
    StickerButton(onClick = {}, modifier = modifier.fillMaxWidth(), enabled = false) {
        Text("$text (próximamente)", style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Tarjeta: panel de tinta con contorno y sombra dura. Si [accent] no es neutro, una franja de ese
 * color arriba indica de qué trata (ronda, robo, apuesta…).
 */
@Composable
fun ArcadeCard(
    modifier: Modifier = Modifier,
    accent: Color = Linea,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().sticker(Panel, MaterialTheme.shapes.large, depth = 5.dp)) {
        if (accent != Linea) Box(Modifier.fillMaxWidth().height(8.dp).background(accent))
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Etiqueta de color con texto de tinta: categoría, tipo de pregunta, aviso de ronda. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .border(2.dp, Tinta, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = Tinta,
    )
}

/** Opción grande del menú principal: color de relleno, nombre en letras de letrero y una línea de ayuda. */
@Composable
fun MenuTile(title: String, hint: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    StickerButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        fill = color,
        depth = 5.dp,
        minHeight = 88.dp,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(hint, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Lista de opciones secundarias dentro de un mismo panel, separadas por líneas finas. */
@Composable
fun MenuList(items: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().sticker(Panel, MaterialTheme.shapes.large, depth = 4.dp)) {
        items.forEachIndexed { i, (label, onClick) ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 1.5.dp, color = Linea)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onClick)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = Papel, modifier = Modifier.weight(1f))
                Text("›", style = MaterialTheme.typography.headlineMedium, color = Mango)
            }
        }
    }
}

/**
 * Avatar redondo relleno con el color del jugador y su inicial (o el emoji de su perfil en online);
 * distinguible también sin color.
 */
@Composable
fun PlayerAvatar(
    name: String,
    playerIndex: Int,
    modifier: Modifier = Modifier,
    size: Int = 48,
    emoji: String? = null,
    /** Marco equipado (cosmético): un segundo anillo por fuera. */
    frame: Color? = null,
) {
    val color = PlayerColors[playerIndex.mod(PlayerColors.size)]
    val framed = if (frame != null) modifier.border(4.dp, frame, CircleShape).padding(5.dp) else modifier
    Box(
        modifier = framed
            .size(size.dp)
            .background(color, CircleShape)
            .border(InkStroke, Tinta, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji ?: name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Tinta,
            fontFamily = if (emoji == null) Letrero else null,
            fontSize = (size * if (emoji == null) 0.42f else 0.5f).sp,
        )
    }
}

/**
 * Ficha de elección (modo, duración, velocidad…). Elegida: rellena de [color] y en relieve.
 * Sin elegir: panel con borde fino. Deshabilitada: atenuada.
 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Mango,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(10.dp)
    val isSelected = selected
    Box(
        modifier = modifier
            .sticker(
                fill = if (selected) color else Panel,
                shape = shape,
                depth = 3.dp,
                raised = selected || (enabled && pressed),
                pressed = pressed,
                outline = if (selected) Tinta else Linea,
            )
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = isSelected }
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = when {
                selected -> Tinta
                enabled -> Papel
                else -> PapelSoft.copy(alpha = 0.7f)
            },
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Barra gruesa con contorno de tinta (tiempo, experiencia). Decorativa: el dato va en un texto al lado. */
@Composable
fun MeterBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(shape)
            .background(AnilDeep)
            .border(InkStroke, Tinta, shape)
            .clearAndSetSemantics { },
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(16.dp)
                .clip(shape)
                .background(color)
                .border(InkStroke, Tinta, shape),
        )
    }
}
