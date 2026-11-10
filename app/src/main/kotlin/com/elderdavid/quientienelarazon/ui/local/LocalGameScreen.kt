package com.elderdavid.quientienelarazon.ui.local

import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elderdavid.quientienelarazon.core.economy.EconomyRules
import com.elderdavid.quientienelarazon.core.game.Bet
import com.elderdavid.quientienelarazon.core.game.GameLength
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.game.PlayerId
import com.elderdavid.quientienelarazon.core.game.PlayerOutcome
import com.elderdavid.quientienelarazon.core.game.RoundType
import com.elderdavid.quientienelarazon.core.local.LocalMatch
import com.elderdavid.quientienelarazon.core.local.LocalStage
import com.elderdavid.quientienelarazon.core.local.Wildcard
import com.elderdavid.quientienelarazon.core.questions.Category
import com.elderdavid.quientienelarazon.core.questions.Question
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import com.elderdavid.quientienelarazon.core.questions.QuestionKind
import com.elderdavid.quientienelarazon.economy.LocalGameReward
import com.elderdavid.quientienelarazon.local.FeedbackEvent
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.components.MeterBar
import com.elderdavid.quientienelarazon.ui.components.NeonTitle
import com.elderdavid.quientienelarazon.ui.components.PlayerAvatar
import com.elderdavid.quientienelarazon.ui.components.StickerButton
import com.elderdavid.quientienelarazon.ui.components.Tag
import com.elderdavid.quientienelarazon.ui.components.sticker
import com.elderdavid.quientienelarazon.ui.theme.AnilDeep
import com.elderdavid.quientienelarazon.ui.theme.Chicle
import com.elderdavid.quientienelarazon.ui.theme.Cielo
import com.elderdavid.quientienelarazon.ui.theme.Letrero
import com.elderdavid.quientienelarazon.ui.theme.Linea
import com.elderdavid.quientienelarazon.ui.theme.Mango
import com.elderdavid.quientienelarazon.ui.theme.Menta
import com.elderdavid.quientienelarazon.ui.theme.Naranja
import com.elderdavid.quientienelarazon.ui.theme.Panel
import com.elderdavid.quientienelarazon.ui.theme.Papel
import com.elderdavid.quientienelarazon.ui.theme.PapelSoft
import com.elderdavid.quientienelarazon.ui.theme.PlayerColors
import com.elderdavid.quientienelarazon.ui.theme.Rojo
import com.elderdavid.quientienelarazon.ui.theme.Tinta
import com.elderdavid.quientienelarazon.ui.theme.TintaSoft
import com.elderdavid.quientienelarazon.ui.theme.inkShadow
import kotlinx.coroutines.delay

/** Acciones que la partida necesita de la capa superior. */
class LocalGameActions(
    val rouletteDone: () -> Unit,
    val ready: () -> Unit,
    val bet: (Int) -> Unit,
    val answer: (Int) -> Unit,
    val next: () -> Unit,
    val rematch: () -> Unit,
    val changePlayers: () -> Unit,
    val exitToMenu: () -> Unit,
    val now: () -> Long,
    val feedback: (FeedbackEvent) -> Unit,
    val useWildcard: (Wildcard) -> Unit = {},
    val buyMatchPack: () -> Unit = {},
)

/** Lo que rodea a la partida: monedas del dueño, avisos, premio de la cuenta y cosméticos. */
data class LocalGameExtras(
    /** Monedas del dueño del teléfono; null sin cuenta (entonces no se ofrece el paquete). */
    val coins: Int? = null,
    val buyingPack: Boolean = false,
    val notice: String? = null,
    val reward: LocalGameReward? = null,
    /** Animación de victoria equipada. */
    val victoryId: String? = null,
)

@Composable
fun LocalGameScreen(
    match: LocalMatch,
    bank: QuestionBank,
    rouletteCategories: List<Category>,
    reducedMotion: Boolean,
    actions: LocalGameActions,
    extras: LocalGameExtras = LocalGameExtras(),
) {
    var confirmExit by remember { mutableStateOf(false) }
    val stage = match.stage
    BackHandler(enabled = stage !is LocalStage.Finished) { confirmExit = true }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("¿Abandonar la partida?") },
            text = { Text("Se perderá el progreso de esta partida.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; actions.changePlayers() }) { Text("Abandonar", color = Rojo) }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Seguir jugando", color = Mango) } },
        )
    }

    val names = match.players.associate { it.id to it.name }
    val colorIndex = match.players.mapIndexed { i, p -> p.id to i }.toMap()

    ArcadeScreen(title = null) {
        when (stage) {
            is LocalStage.Roulette -> RouletteView(stage, match.state.config.length, match.state.config.kids, match.state.players.size >= 2 && !match.state.config.kids, bank, rouletteCategories, reducedMotion, actions)
            is LocalStage.Handoff -> {
                HandoffView(stage, names.getValue(stage.playerId), colorIndex.getValue(stage.playerId), match.state, actions)
                if (!stage.tiebreak) MatchPackOffer(match, extras, actions)
            }
            is LocalStage.Betting -> BettingView(stage, names.getValue(stage.playerId), match.state, bank, actions)
            is LocalStage.Answering -> AnsweringView(stage, names.getValue(stage.playerId), match.state, bank, actions)
            is LocalStage.Reveal -> RevealView(stage, match.state, bank, names, colorIndex, actions)
            is LocalStage.RoundSummary -> RoundSummaryView(stage, match.state, names, colorIndex, actions)
            is LocalStage.TiebreakReveal -> TiebreakRevealView(stage, bank, names, actions)
            is LocalStage.Finished -> PodiumView(stage, match.state, names, colorIndex, actions, extras, reducedMotion)
        }
    }
}

// ---------------------------------------------------------------- Ruleta

@Composable
private fun RouletteView(
    stage: LocalStage.Roulette,
    length: GameLength,
    kids: Boolean,
    /** La partida tiene pregunta robo (2 o más jugadores y no es modo niños). */
    heistGame: Boolean,
    bank: QuestionBank,
    categories: List<Category>,
    reducedMotion: Boolean,
    actions: LocalGameActions,
) {
    val target = bank.category(stage.categoryId)
    var shown by remember(stage) { mutableStateOf(if (reducedMotion) target else categories.firstOrNull() ?: target) }
    var landed by remember(stage) { mutableStateOf(reducedMotion) }

    // La categoría ya está decidida: la animación solo recorre la lista y se detiene en ella.
    LaunchedEffect(stage) {
        if (!reducedMotion && categories.size > 1) {
            val start = categories.indexOf(shown).coerceAtLeast(0)
            val end = categories.indexOf(target).let { if (it < 0) 0 else it }
            val steps = 2 * categories.size + (end - start).mod(categories.size)
            for (step in 1..steps) {
                shown = categories[(start + step) % categories.size]
                actions.feedback(FeedbackEvent.TICK)
                // Desaceleración: cada paso tarda un poco más que el anterior.
                delay(40L + (220L * step * step) / (steps.toLong() * steps))
            }
            shown = target
        }
        landed = true
        delay(if (reducedMotion) 900L else 1_100L)
        actions.rouletteDone()
    }

    RoundHeader(stage.round, stage.questionNumber, length.questions, heistGame && length.isHeist(stage.questionNumber - 1))
    if (length.isFirstOfRound(stage.questionNumber - 1)) {
        ArcadeCard(accent = Cielo) {
            Text("Ronda ${stage.round.number}: ${stage.round.title}", style = MaterialTheme.typography.titleLarge, color = Cielo)
            Text(roundRule(stage.round, kids, heistGame), color = PapelSoft)
        }
    }
    // Al detenerse, el panel se vuelve un letrero mango: es el único momento animado de la ruleta.
    Column(
        Modifier.fillMaxWidth().heightIn(min = 180.dp).sticker(if (landed) Mango else Panel, MaterialTheme.shapes.extraLarge, depth = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (landed) "Te toca" else "Girando la ruleta…",
            style = MaterialTheme.typography.titleMedium,
            color = if (landed) Tinta else PapelSoft,
            modifier = Modifier.padding(top = 22.dp),
        )
        Text(
            text = shown.name,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.displayMedium,
            color = if (landed) Tinta else Papel,
            textAlign = TextAlign.Center,
        )
    }
}

/** [kids]: en el modo niños fallar una apuesta no resta puntos. [heist]: la partida tiene pregunta robo. */
internal fun roundRule(round: RoundType, kids: Boolean = false, heist: Boolean = !kids): String = when (round) {
    RoundType.KNOWLEDGE -> "Acierto: +${GameRules.KNOWLEDGE_POINTS} puntos."
    RoundType.SPEED -> "Acierto: +${GameRules.SPEED_POINTS} y hasta +${GameRules.SPEED_BONUS_MAX} extra cuanto más rápido respondas." +
        if (heist) " La última es la pregunta robo." else ""
    RoundType.STRATEGY -> "Antes de ver la pregunta apuestas ${GameRules.ALLOWED_BETS.joinToString(" o ")} puntos. Aciertas: ganas el doble. " +
        if (kids) "Fallas: no pierdes nada." else "Fallas: los pierdes."
    RoundType.FINAL -> "Preguntas más difíciles. Acierto: +${GameRules.FINAL_POINTS} y bono por rapidez."
}

/** Texto de la pregunta robo, compartido por el modo local y el online. */
internal val HEIST_RULE = "Quien acierte más rápido le quita el ${GameRules.HEIST_PERCENT} % de sus puntos a cada rival."

/** [heist]: la pregunta es la pregunta robo; se anuncia bajo la cabecera. */
@Composable
internal fun RoundHeader(round: RoundType, questionNumber: Int, totalQuestions: Int, heist: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Ronda ${round.number}, ${round.title}. Pregunta $questionNumber de $totalQuestions"
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Ronda ${round.number}: ${round.title}", style = MaterialTheme.typography.titleMedium, color = Mango, modifier = Modifier.weight(1f))
            Text("$questionNumber/$totalQuestions", style = MaterialTheme.typography.headlineMedium, color = Papel)
        }
        QuestionPips(questionNumber, totalQuestions)
    }
    if (heist) {
        ArcadeCard(accent = Chicle) {
            Text("¡Pregunta robo!", style = MaterialTheme.typography.headlineMedium, color = Chicle)
            Text(HEIST_RULE, color = Papel)
        }
    }
}

/** Una marca por pregunta: hechas en mango, la actual en papel y las que faltan vacías. */
@Composable
private fun QuestionPips(current: Int, total: Int) {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            val color = when {
                i + 1 < current -> Mango
                i + 1 == current -> Papel
                else -> AnilDeep
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
                    .border(2.dp, Tinta, RoundedCornerShape(3.dp)),
            )
        }
    }
}

// ---------------------------------------------------------------- Traspaso

@Composable
private fun HandoffView(stage: LocalStage.Handoff, name: String, colorIndex: Int, state: GameState, actions: LocalGameActions) {
    if (stage.tiebreak) {
        NeonTitle("¡Desempate!", color = Chicle, align = TextAlign.Center)
        Text(
            "Gana quien acierte más rápido. Sin apuestas y sin cambios en las estadísticas.",
            color = PapelSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        RoundHeader(state.round, state.questionIndex + 1, state.config.length.questions, state.isHeistQuestion)
    }
    if (state.players.size == 1) {
        // Solitario: no hay que pasar el teléfono, solo una pausa antes de que corra el tiempo.
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayerAvatar(name = name, playerIndex = colorIndex, size = 112)
            Text("$name, ${state.scores.getValue(stage.playerId)} puntos", style = MaterialTheme.typography.titleLarge, color = Papel, textAlign = TextAlign.Center)
            Text("El tiempo empieza al pulsar el botón.", color = PapelSoft)
        }
        ArcadeButton(text = "Siguiente pregunta", onClick = actions.ready, color = PlayerColors[colorIndex % PlayerColors.size])
        return
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pasa el teléfono a", style = MaterialTheme.typography.titleMedium, color = PapelSoft)
        PlayerAvatar(name = name, playerIndex = colorIndex, size = 112)
        Text(
            name,
            style = MaterialTheme.typography.displayMedium.copy(shadow = inkShadow()),
            color = PlayerColors[colorIndex % PlayerColors.size],
            textAlign = TextAlign.Center,
        )
        Text("Que nadie más mire la pantalla.", color = PapelSoft)
    }
    ArcadeButton(text = "Soy $name, estoy listo", onClick = actions.ready, color = PlayerColors[colorIndex % PlayerColors.size])
}

// ---------------------------------------------------------------- Apuesta

@Composable
private fun BettingView(stage: LocalStage.Betting, name: String, state: GameState, bank: QuestionBank, actions: LocalGameActions) {
    RoundHeader(state.round, state.questionIndex + 1, state.config.length.questions)
    NeonTitle("$name, apuesta", color = Mango)
    ArcadeCard(accent = Naranja) {
        Tag(bank.category(state.currentPlanned!!.categoryId).name, Cielo)
        Text("Tienes ${stage.score} puntos.", color = PapelSoft)
        Text("Tras apostar verás la pregunta y empezará tu tiempo.", color = PapelSoft)
        if (state.config.kids) Text("Modo niños: si fallas no pierdes lo apostado.", color = Mango)
    }
    if (stage.allowedBets.isEmpty()) {
        ArcadeCard {
            Text("Apuesta libre", style = MaterialTheme.typography.titleMedium, color = Mango)
            Text(
                "No te alcanza para la apuesta mínima. Si aciertas ganas ${GameRules.FREE_BET_REWARD} puntos y si fallas no pierdes nada.",
                color = PapelSoft,
            )
        }
        ArcadeButton(text = "Jugar apuesta libre", onClick = { actions.feedback(FeedbackEvent.TAP); actions.bet(0) })
    } else {
        stage.allowedBets.forEach { amount ->
            ArcadeButton(text = "Apostar $amount · ganas ${amount * GameRules.BET_WIN_MULTIPLIER}", onClick = { actions.feedback(FeedbackEvent.TAP); actions.bet(amount) }, color = if (amount == stage.allowedBets.max()) Naranja else Mango)
        }
    }
}

// ---------------------------------------------------------------- Pregunta

@Composable
private fun AnsweringView(stage: LocalStage.Answering, name: String, state: GameState, bank: QuestionBank, actions: LocalGameActions) {
    val question = bank.question(stage.questionId)
    val limit = (stage.deadline - stage.startedAt).toFloat()
    var remaining by remember(stage) { mutableLongStateOf(stage.deadline - actions.now()) }
    LaunchedEffect(stage) {
        while (remaining > 0) {
            withFrameMillis { }
            remaining = (stage.deadline - actions.now()).coerceAtLeast(0)
        }
    }
    val fraction by animateFloatAsState(targetValue = (remaining / limit).coerceIn(0f, 1f), label = "timer")
    val seconds = ((remaining + 999) / 1000).toInt()

    if (!stage.tiebreak) RoundHeader(state.round, state.questionIndex + 1, state.config.length.questions, state.isHeistQuestion)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(name = name, playerIndex = colorIndexOf(state, stage.playerId), size = 40)
        Text(name, style = MaterialTheme.typography.titleLarge, color = Papel, modifier = Modifier.weight(1f))
        Text(
            "$seconds s",
            style = MaterialTheme.typography.displaySmall.copy(shadow = inkShadow(4f)),
            color = if (seconds <= 3) Chicle else Mango,
        )
    }
    TimerBar(fraction, urgent = seconds <= 3)
    val bet = state.bets[stage.playerId]
    if (bet != null) Tag(betLabel(bet), Naranja)
    QuestionCard(question, bank)
    question.options.forEachIndexed { index, option ->
        if (index in stage.hiddenOptions) {
            OptionButton(text = "—", letter = optionLetter(index), onClick = {}, enabled = false, highlight = Linea)
        } else {
            OptionButton(text = option, letter = optionLetter(index), onClick = { actions.feedback(FeedbackEvent.TAP); actions.answer(index) })
        }
    }
    if (!stage.tiebreak) WildcardBar(stage, actions)
}

private fun colorIndexOf(state: GameState, id: PlayerId): Int = state.players.indexOfFirst { it.id == id }.coerceAtLeast(0)

@Composable
private fun WildcardBar(stage: LocalStage.Answering, actions: LocalGameActions) {
    Text(
        if (stage.wildcards == 1) "Te queda 1 comodín" else "Te quedan ${stage.wildcards} comodines",
        style = MaterialTheme.typography.labelMedium,
        color = PapelSoft,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Wildcard.entries.forEach { w ->
            val enabled = w in stage.usableWildcards
            StickerButton(
                onClick = { actions.feedback(FeedbackEvent.TAP); actions.useWildcard(w) },
                enabled = enabled,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Comodín ${w.label}: ${w.description}" },
                fill = Panel,
                contentColor = Naranja,
                depth = 3.dp,
                minHeight = 48.dp,
                // Con más relleno «Cambiar» no cabe en un tercio de pantalla y se parte.
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text(w.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false) }
        }
    }
}

/** El dueño del teléfono puede pagar con monedas un comodín más para TODOS los jugadores. */
@Composable
private fun MatchPackOffer(match: LocalMatch, extras: LocalGameExtras, actions: LocalGameActions) {
    extras.notice?.let { Text(it, color = Mango, style = MaterialTheme.typography.bodyMedium) }
    val coins = extras.coins ?: return
    if (!match.canBuyMatchPack) return
    ArcadeCard(accent = Linea) {
        Text("Paquete de partida", style = MaterialTheme.typography.titleMedium, color = Naranja)
        Text(
            "Un comodín más para cada jugador, pagado con las monedas de la cuenta de este teléfono (tienes $coins).",
            color = PapelSoft,
            style = MaterialTheme.typography.bodyMedium,
        )
        ArcadeOutlinedButton(
            text = if (extras.buyingPack) "Comprando…" else "Comprar · 🪙 ${EconomyRules.MATCH_PACK_PRICE}",
            onClick = actions.buyMatchPack,
            enabled = !extras.buyingPack && coins >= EconomyRules.MATCH_PACK_PRICE,
            color = Naranja,
        )
    }
}

@Composable
private fun QuestionCard(question: Question, bank: QuestionBank) {
    QuestionSign(
        category = bank.category(question.categoryId).name,
        note = when (question.kind) {
            QuestionKind.OPINION -> "Opinión: gana la opción más votada"
            QuestionKind.DEBATE -> if (question.isVote) "Debate: gana la opción más votada" else "Debate: se resuelve con la regla del juego"
            QuestionKind.OBJECTIVE -> null
        },
        text = question.text,
    )
}

/**
 * La pregunta, impresa en papel: es la pieza más clara de la pantalla para que se lea en voz alta.
 * La comparten el modo local y el online.
 */
@Composable
internal fun QuestionSign(category: String, note: String?, text: String, compact: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().sticker(Papel, MaterialTheme.shapes.extraLarge, depth = 6.dp).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag(category, Cielo) }
        if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, color = TintaSoft)
        Text(
            text,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            color = Tinta,
        )
    }
}

/** Barra de tiempo; se vuelve chicle en los últimos segundos. */
@Composable
internal fun TimerBar(fraction: Float, urgent: Boolean) = MeterBar(fraction, if (urgent) Chicle else Mango)

/**
 * Opción de respuesta. Con [highlight] de color (respuesta correcta, opción elegida) la pieza se
 * rellena de ese color; con [Linea] queda apagada (opción descartada o incorrecta).
 */
@Composable
internal fun OptionButton(text: String, letter: String, onClick: () -> Unit, highlight: Color? = null, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lit = highlight != null && highlight != Linea
    val dimmed = !lit && (highlight == Linea || !enabled)
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sticker(
                fill = if (lit) highlight!! else Panel,
                shape = shape,
                raised = !dimmed,
                pressed = pressed,
                outline = if (dimmed) Linea else Tinta,
            )
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (lit) Tinta else if (dimmed) AnilDeep else Mango)
                .border(2.dp, Tinta, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Letrero),
                color = if (lit) highlight!! else if (dimmed) PapelSoft else Tinta,
            )
        }
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (lit) Tinta else if (dimmed) PapelSoft else Papel,
        )
    }
}

internal fun optionLetter(index: Int) = ('A' + index).toString()

private fun betLabel(bet: Bet) = when (bet) {
    is Bet.Amount -> "Apuesta: ${bet.value}"
    Bet.Free -> "Apuesta libre"
}

// ---------------------------------------------------------------- Revelación

@Composable
private fun RevealView(
    stage: LocalStage.Reveal,
    state: GameState,
    bank: QuestionBank,
    names: Map<PlayerId, String>,
    colorIndex: Map<PlayerId, Int>,
    actions: LocalGameActions,
) {
    val result = stage.result
    val question = bank.question(result.questionId)
    LaunchedEffect(result.index) {
        actions.feedback(if (result.outcomes.any { it.won == true }) FeedbackEvent.CORRECT else FeedbackEvent.WRONG)
    }
    RoundHeader(result.round, result.index + 1, state.config.length.questions, state.isHeistQuestion)
    QuestionCard(question, bank)
    question.options.forEachIndexed { index, option ->
        val winning = index in result.winningOptions
        val votes = result.voteCounts[index]
        OptionButton(
            text = if (question.isVote) "$option  ·  ${votes ?: 0} voto${if (votes == 1) "" else "s"}" else option,
            letter = optionLetter(index),
            onClick = {},
            enabled = false,
            highlight = if (winning) Menta else Linea,
        )
    }
    if (question.isVote && !result.scored) {
        Text("Menos de 2 votos válidos: esta pregunta no da puntos.", color = Naranja)
    }
    ArcadeCard(accent = Menta) {
        Text(if (question.isVote) "Resultado de la votación" else "Respuesta", style = MaterialTheme.typography.titleMedium, color = Menta)
        Text(question.explanation, color = PapelSoft)
        question.source?.let { SourceLink(it) }
    }
    ArcadeCard(accent = Chicle) {
        result.outcomes.forEach { o -> OutcomeRow(o, question, names.getValue(o.playerId), colorIndex.getValue(o.playerId), state.scores.getValue(o.playerId)) }
    }
    val lastOfGame = result.index == state.config.length.questions - 1
    ArcadeButton(text = if (lastOfGame) "Ver resultados" else "Continuar", onClick = actions.next)
}

@Composable
internal fun SourceLink(url: String) {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
        Text("Fuente: ${url.substringAfter("://").substringBefore("/")}", color = Mango, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun OutcomeRow(o: PlayerOutcome, question: Question, name: String, colorIndex: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PlayerAvatar(name = name, playerIndex = colorIndex, size = 36)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            val answer = o.choice?.let { "${optionLetter(it)}. ${question.options[it]}" } ?: "Sin respuesta"
            val bet = o.bet?.let { " · ${betLabel(it).lowercase()}" } ?: ""
            Text(answer + bet, color = PapelSoft, style = MaterialTheme.typography.bodyMedium)
            heistLabel(o.stolen)?.let { Text(it, color = Chicle, style = MaterialTheme.typography.labelMedium) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                signed(o.pointsDelta),
                style = MaterialTheme.typography.headlineMedium,
                color = when {
                    o.pointsDelta > 0 -> Menta
                    o.pointsDelta < 0 -> Rojo
                    else -> PapelSoft
                },
            )
            Text("$total pts", color = PapelSoft, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Línea de la pregunta robo bajo el nombre del jugador; null si no robó ni le robaron. */
internal fun heistLabel(stolen: Int): String? = when {
    stolen > 0 -> "¡Robó $stolen puntos!"
    stolen < 0 -> "Le robaron ${-stolen} puntos"
    else -> null
}

internal fun signed(n: Int) = when {
    n > 0 -> "+$n"
    n < 0 -> "−${-n}"
    else -> "0"
}

// ---------------------------------------------------------------- Marcador

@Composable
private fun RoundSummaryView(
    stage: LocalStage.RoundSummary,
    state: GameState,
    names: Map<PlayerId, String>,
    colorIndex: Map<PlayerId, Int>,
    actions: LocalGameActions,
) {
    NeonTitle("Fin de la ronda ${stage.finishedRound.number}", align = TextAlign.Center)
    Scoreboard(state, names, colorIndex)
    val nextRound = RoundType.entries.getOrNull(stage.finishedRound.ordinal + 1)
    if (nextRound != null) {
        ArcadeCard(accent = Cielo) {
            Text("Siguiente: ronda ${nextRound.number}, ${nextRound.title}", style = MaterialTheme.typography.titleMedium, color = Cielo)
            Text(roundRule(nextRound, state.config.kids, state.players.size >= 2 && !state.config.kids), color = PapelSoft)
        }
    }
    ArcadeButton(text = "Siguiente ronda", onClick = actions.next)
}

@Composable
private fun Scoreboard(state: GameState, names: Map<PlayerId, String>, colorIndex: Map<PlayerId, Int>) {
    ArcadeCard(accent = Mango) {
        state.ranking.forEachIndexed { position, (id, score) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${position + 1}", style = MaterialTheme.typography.headlineMedium, color = if (position == 0) Mango else PapelSoft)
                PlayerAvatar(name = names.getValue(id), playerIndex = colorIndex.getValue(id), size = 40)
                Text(names.getValue(id), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("$score", style = MaterialTheme.typography.headlineLarge, color = PlayerColors[colorIndex.getValue(id) % PlayerColors.size])
            }
        }
    }
}

// ---------------------------------------------------------------- Desempate

@Composable
private fun TiebreakRevealView(stage: LocalStage.TiebreakReveal, bank: QuestionBank, names: Map<PlayerId, String>, actions: LocalGameActions) {
    val result = stage.result
    val question = bank.question(result.questionId)
    NeonTitle("Desempate", color = Chicle, align = TextAlign.Center)
    ArcadeCard {
        Text(question.text, style = MaterialTheme.typography.titleMedium)
        Text("Respuesta: ${question.options[question.correctIndex!!]}", color = Menta, style = MaterialTheme.typography.titleMedium)
        Text(question.explanation, color = PapelSoft)
    }
    ArcadeCard(accent = Chicle) {
        result.outcomes.forEach { o ->
            val verdict = when {
                o.choice == null -> "sin respuesta"
                o.won == true -> "acierto en %.2f s".format(o.elapsedMillis / 1000.0)
                else -> "fallo"
            }
            Text("${names.getValue(o.playerId)}: $verdict")
        }
    }
    val winner = result.winner
    Text(
        if (winner != null) "¡${names.getValue(winner)} gana el desempate!" else "Sigue el empate: otra pregunta.",
        style = MaterialTheme.typography.headlineMedium.copy(shadow = inkShadow(4f)),
        color = Mango,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    ArcadeButton(text = "Continuar", onClick = actions.next)
}

// ---------------------------------------------------------------- Podio

@Composable
private fun PodiumView(
    stage: LocalStage.Finished,
    state: GameState,
    names: Map<PlayerId, String>,
    colorIndex: Map<PlayerId, Int>,
    actions: LocalGameActions,
    extras: LocalGameExtras,
    reducedMotion: Boolean,
) {
    LaunchedEffect(Unit) { actions.feedback(FeedbackEvent.CORRECT) }
    if (extras.victoryId != null && !reducedMotion) VictoryCelebration(extras.victoryId)
    val solo = state.players.size == 1
    val winners = stage.winners.map { names.getValue(it) }
    NeonTitle(
        when {
            solo -> "¡${state.scores.values.single()} puntos!"
            winners.size == 1 -> "¡${winners.single()} tiene la razón!"
            else -> "¡Victoria compartida!"
        },
        color = Mango,
        align = TextAlign.Center,
    )
    if (winners.size > 1) {
        Text(winners.joinToString(" y "), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Scoreboard(state, names, colorIndex)
    ArcadeCard {
        Text("Aciertos", style = MaterialTheme.typography.titleMedium, color = Mango)
        state.players.forEach { p ->
            val right = state.results.count { r -> r.outcomes.any { it.playerId == p.id && it.won == true } }
            Text("${p.name}: $right de ${state.config.length.questions}", color = PapelSoft)
        }
    }
    extras.reward?.takeIf { it.xp > 0 || it.coins > 0 }?.let { r ->
        val parts = listOfNotNull(r.xp.takeIf { it > 0 }?.let { "+$it XP" }, r.coins.takeIf { it > 0 }?.let { "+$it monedas" })
        Text(
            "Cuenta de este teléfono: ${parts.joinToString(" y ")}",
            color = Menta,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ArcadeButton(text = if (solo) "Jugar otra vez" else "Revancha", onClick = actions.rematch)
    ArcadeOutlinedButton(text = if (solo) "Cambiar ajustes" else "Cambiar jugadores", onClick = actions.changePlayers, color = Mango)
    ArcadeOutlinedButton(text = "Menú principal", onClick = actions.exitToMenu)
}
