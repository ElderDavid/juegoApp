package com.elderdavid.quientienelarazon.ui.local

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elderdavid.quientienelarazon.core.game.Bet
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.game.PlayerId
import com.elderdavid.quientienelarazon.core.game.PlayerOutcome
import com.elderdavid.quientienelarazon.core.game.RoundType
import com.elderdavid.quientienelarazon.core.local.LocalMatch
import com.elderdavid.quientienelarazon.core.local.LocalStage
import com.elderdavid.quientienelarazon.core.questions.Category
import com.elderdavid.quientienelarazon.core.questions.Question
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import com.elderdavid.quientienelarazon.core.questions.QuestionKind
import com.elderdavid.quientienelarazon.local.FeedbackEvent
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.components.NeonTitle
import com.elderdavid.quientienelarazon.ui.components.PlayerAvatar
import com.elderdavid.quientienelarazon.ui.theme.ArcadeCyan
import com.elderdavid.quientienelarazon.ui.theme.ArcadeError
import com.elderdavid.quientienelarazon.ui.theme.ArcadeMagenta
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOrange
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOutline
import com.elderdavid.quientienelarazon.ui.theme.ArcadeSuccess
import com.elderdavid.quientienelarazon.ui.theme.ArcadeText
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted
import com.elderdavid.quientienelarazon.ui.theme.ArcadeViolet
import com.elderdavid.quientienelarazon.ui.theme.PlayerColors
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
)

@Composable
fun LocalGameScreen(
    match: LocalMatch,
    bank: QuestionBank,
    rouletteCategories: List<Category>,
    reducedMotion: Boolean,
    actions: LocalGameActions,
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
                TextButton(onClick = { confirmExit = false; actions.changePlayers() }) { Text("Abandonar", color = ArcadeError) }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Seguir jugando", color = ArcadeCyan) } },
        )
    }

    val names = match.players.associate { it.id to it.name }
    val colorIndex = match.players.mapIndexed { i, p -> p.id to i }.toMap()

    ArcadeScreen(title = null) {
        when (stage) {
            is LocalStage.Roulette -> RouletteView(stage, bank, rouletteCategories, reducedMotion, actions)
            is LocalStage.Handoff -> HandoffView(stage, names.getValue(stage.playerId), colorIndex.getValue(stage.playerId), match.state, actions)
            is LocalStage.Betting -> BettingView(stage, names.getValue(stage.playerId), match.state, bank, actions)
            is LocalStage.Answering -> AnsweringView(stage, names.getValue(stage.playerId), match.state, bank, actions)
            is LocalStage.Reveal -> RevealView(stage, match.state, bank, names, colorIndex, actions)
            is LocalStage.RoundSummary -> RoundSummaryView(stage, match.state, names, colorIndex, actions)
            is LocalStage.TiebreakReveal -> TiebreakRevealView(stage, bank, names, actions)
            is LocalStage.Finished -> PodiumView(stage, match.state, names, colorIndex, actions)
        }
    }
}

// ---------------------------------------------------------------- Ruleta

@Composable
private fun RouletteView(
    stage: LocalStage.Roulette,
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

    RoundHeader(stage.round, stage.questionNumber)
    if (stage.questionNumber % GameRules.QUESTIONS_PER_ROUND == 1) {
        ArcadeCard(accent = ArcadeViolet) {
            Text("Ronda ${stage.round.number}: ${stage.round.title}", style = MaterialTheme.typography.titleLarge, color = ArcadeViolet)
            Text(roundRule(stage.round), color = ArcadeTextMuted)
        }
    }
    ArcadeCard(accent = if (landed) ArcadeOrange else ArcadeOutline, modifier = Modifier.heightIn(min = 160.dp)) {
        Text("Categoría", style = MaterialTheme.typography.labelMedium, color = ArcadeTextMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(
            text = shown.name,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.displayMedium,
            color = if (landed) ArcadeOrange else ArcadeText,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun roundRule(round: RoundType): String = when (round) {
    RoundType.KNOWLEDGE -> "Acierto: +${GameRules.KNOWLEDGE_POINTS} puntos."
    RoundType.SPEED -> "Acierto: +${GameRules.SPEED_POINTS} y hasta +${GameRules.SPEED_BONUS_MAX} extra cuanto más rápido respondas."
    RoundType.STRATEGY -> "Antes de ver la pregunta apuestas ${GameRules.ALLOWED_BETS.joinToString(" o ")} puntos. Aciertas: los ganas. Fallas: los pierdes."
    RoundType.FINAL -> "Preguntas más difíciles. Acierto: +${GameRules.FINAL_POINTS} y bono por rapidez."
}

@Composable
internal fun RoundHeader(round: RoundType, questionNumber: Int) {
    Text(
        "RONDA ${round.number} · ${round.title.uppercase()}   ·   PREGUNTA $questionNumber/${GameRules.TOTAL_QUESTIONS}",
        style = MaterialTheme.typography.labelMedium,
        color = ArcadeCyan,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

// ---------------------------------------------------------------- Traspaso

@Composable
private fun HandoffView(stage: LocalStage.Handoff, name: String, colorIndex: Int, state: GameState, actions: LocalGameActions) {
    if (stage.tiebreak) {
        NeonTitle("¡Desempate!", color = ArcadeMagenta)
        Text(
            "Gana quien acierte más rápido. Sin apuestas y sin cambios en las estadísticas.",
            color = ArcadeTextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        RoundHeader(state.round, state.questionIndex + 1)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pasa el teléfono a", style = MaterialTheme.typography.titleMedium, color = ArcadeTextMuted)
        PlayerAvatar(name = name, playerIndex = colorIndex, size = 96)
        Text(name, style = MaterialTheme.typography.displayMedium, color = PlayerColors[colorIndex % PlayerColors.size], textAlign = TextAlign.Center)
        Text("Que nadie más mire la pantalla.", color = ArcadeTextMuted)
    }
    ArcadeButton(text = "Soy $name, estoy listo", onClick = actions.ready, color = PlayerColors[colorIndex % PlayerColors.size])
}

// ---------------------------------------------------------------- Apuesta

@Composable
private fun BettingView(stage: LocalStage.Betting, name: String, state: GameState, bank: QuestionBank, actions: LocalGameActions) {
    RoundHeader(state.round, state.questionIndex + 1)
    NeonTitle("$name, apuesta", color = ArcadeOrange)
    ArcadeCard(accent = ArcadeOrange) {
        Text("Categoría: ${bank.category(state.currentPlanned!!.categoryId).name}", style = MaterialTheme.typography.titleMedium)
        Text("Tienes ${stage.score} puntos.", color = ArcadeTextMuted)
        Text("Tras apostar verás la pregunta y empezará tu tiempo.", color = ArcadeTextMuted)
    }
    if (stage.allowedBets.isEmpty()) {
        ArcadeCard {
            Text("Apuesta libre", style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
            Text(
                "No te alcanza para la apuesta mínima. Si aciertas ganas ${GameRules.FREE_BET_REWARD} puntos y si fallas no pierdes nada.",
                color = ArcadeTextMuted,
            )
        }
        ArcadeButton(text = "Jugar apuesta libre", onClick = { actions.feedback(FeedbackEvent.TAP); actions.bet(0) })
    } else {
        stage.allowedBets.forEach { amount ->
            ArcadeButton(text = "Apostar $amount", onClick = { actions.feedback(FeedbackEvent.TAP); actions.bet(amount) }, color = if (amount == stage.allowedBets.max()) ArcadeMagenta else ArcadeCyan)
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

    if (!stage.tiebreak) RoundHeader(state.round, state.questionIndex + 1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.titleLarge, color = ArcadeCyan)
        Text("$seconds s", style = MaterialTheme.typography.headlineMedium, color = if (seconds <= 3) ArcadeError else ArcadeText)
    }
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().height(10.dp),
        color = if (seconds <= 3) ArcadeError else ArcadeCyan,
        trackColor = ArcadeOutline,
    )
    val bet = state.bets[stage.playerId]
    if (bet != null) Text(betLabel(bet), color = ArcadeOrange, style = MaterialTheme.typography.labelMedium)
    QuestionCard(question, bank)
    question.options.forEachIndexed { index, option ->
        OptionButton(text = option, letter = optionLetter(index), onClick = { actions.feedback(FeedbackEvent.TAP); actions.answer(index) })
    }
}

@Composable
private fun QuestionCard(question: Question, bank: QuestionBank) {
    ArcadeCard(accent = ArcadeViolet) {
        Text(bank.category(question.categoryId).name.uppercase(), style = MaterialTheme.typography.labelMedium, color = ArcadeViolet)
        when (question.kind) {
            QuestionKind.OPINION -> Text("Opinión: gana la opción más votada", style = MaterialTheme.typography.labelMedium, color = ArcadeOrange)
            QuestionKind.DEBATE -> Text(
                if (question.isVote) "Debate: gana la opción más votada" else "Debate: se resuelve con la regla del juego",
                style = MaterialTheme.typography.labelMedium,
                color = ArcadeOrange,
            )
            QuestionKind.OBJECTIVE -> Unit
        }
        Text(question.text, style = MaterialTheme.typography.headlineMedium, color = ArcadeText)
    }
}

@Composable
internal fun OptionButton(text: String, letter: String, onClick: () -> Unit, highlight: Color? = null, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, highlight ?: ArcadeCyan),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ArcadeText, disabledContentColor = ArcadeText),
    ) {
        Text("$letter  ", fontWeight = FontWeight.Black, color = highlight ?: ArcadeCyan)
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

internal fun optionLetter(index: Int) = ('A' + index).toString()

private fun betLabel(bet: Bet) = when (bet) {
    is Bet.Amount -> "APUESTA: ${bet.value}"
    Bet.Free -> "APUESTA LIBRE"
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
    RoundHeader(result.round, result.index + 1)
    QuestionCard(question, bank)
    question.options.forEachIndexed { index, option ->
        val winning = index in result.winningOptions
        val votes = result.voteCounts[index]
        OptionButton(
            text = if (question.isVote) "$option  ·  ${votes ?: 0} voto${if (votes == 1) "" else "s"}" else option,
            letter = optionLetter(index),
            onClick = {},
            enabled = false,
            highlight = if (winning) ArcadeSuccess else ArcadeOutline,
        )
    }
    if (question.isVote && !result.scored) {
        Text("Menos de 2 votos válidos: esta pregunta no da puntos.", color = ArcadeOrange)
    }
    ArcadeCard {
        Text(if (question.isVote) "Resultado de la votación" else "Respuesta", style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
        Text(question.explanation, color = ArcadeTextMuted)
        question.source?.let { SourceLink(it) }
    }
    ArcadeCard(accent = ArcadeMagenta) {
        result.outcomes.forEach { o -> OutcomeRow(o, question, names.getValue(o.playerId), colorIndex.getValue(o.playerId), state.scores.getValue(o.playerId)) }
    }
    val lastOfGame = result.index == GameRules.TOTAL_QUESTIONS - 1
    ArcadeButton(text = if (lastOfGame) "Ver resultados" else "Continuar", onClick = actions.next)
}

@Composable
internal fun SourceLink(url: String) {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
        Text("Fuente: ${url.substringAfter("://").substringBefore("/")}", color = ArcadeCyan, style = MaterialTheme.typography.labelMedium)
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
            Text(answer + bet, color = ArcadeTextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                signed(o.pointsDelta),
                style = MaterialTheme.typography.titleLarge,
                color = when {
                    o.pointsDelta > 0 -> ArcadeSuccess
                    o.pointsDelta < 0 -> ArcadeError
                    else -> ArcadeTextMuted
                },
            )
            Text("$total pts", color = ArcadeTextMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
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
    NeonTitle("Fin de la ronda ${stage.finishedRound.number}")
    Scoreboard(state, names, colorIndex)
    val nextRound = RoundType.entries.getOrNull(stage.finishedRound.ordinal + 1)
    if (nextRound != null) {
        ArcadeCard(accent = ArcadeViolet) {
            Text("Siguiente: ronda ${nextRound.number}, ${nextRound.title}", style = MaterialTheme.typography.titleMedium, color = ArcadeViolet)
            Text(roundRule(nextRound), color = ArcadeTextMuted)
        }
    }
    ArcadeButton(text = "Siguiente ronda", onClick = actions.next)
}

@Composable
private fun Scoreboard(state: GameState, names: Map<PlayerId, String>, colorIndex: Map<PlayerId, Int>) {
    ArcadeCard(accent = ArcadeCyan) {
        state.ranking.forEachIndexed { position, (id, score) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${position + 1}.", style = MaterialTheme.typography.titleLarge, color = ArcadeTextMuted)
                PlayerAvatar(name = names.getValue(id), playerIndex = colorIndex.getValue(id), size = 40)
                Text(names.getValue(id), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("$score", style = MaterialTheme.typography.headlineMedium, color = PlayerColors[colorIndex.getValue(id) % PlayerColors.size])
            }
        }
    }
}

// ---------------------------------------------------------------- Desempate

@Composable
private fun TiebreakRevealView(stage: LocalStage.TiebreakReveal, bank: QuestionBank, names: Map<PlayerId, String>, actions: LocalGameActions) {
    val result = stage.result
    val question = bank.question(result.questionId)
    NeonTitle("Desempate", color = ArcadeMagenta)
    ArcadeCard {
        Text(question.text, style = MaterialTheme.typography.titleMedium)
        Text("Respuesta: ${question.options[question.correctIndex!!]}", color = ArcadeSuccess)
        Text(question.explanation, color = ArcadeTextMuted)
    }
    ArcadeCard(accent = ArcadeMagenta) {
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
        style = MaterialTheme.typography.headlineMedium,
        color = ArcadeOrange,
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
) {
    LaunchedEffect(Unit) { actions.feedback(FeedbackEvent.CORRECT) }
    val winners = stage.winners.map { names.getValue(it) }
    NeonTitle(if (winners.size == 1) "¡${winners.single()} tiene la razón!" else "¡Victoria compartida!", color = ArcadeOrange)
    if (winners.size > 1) {
        Text(winners.joinToString(" y "), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Scoreboard(state, names, colorIndex)
    ArcadeCard {
        Text("Aciertos", style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
        state.players.forEach { p ->
            val right = state.results.count { r -> r.outcomes.any { it.playerId == p.id && it.won == true } }
            Text("${p.name}: $right de ${GameRules.TOTAL_QUESTIONS}", color = ArcadeTextMuted)
        }
    }
    ArcadeButton(text = "Revancha", onClick = actions.rematch, color = ArcadeMagenta)
    ArcadeOutlinedButton(text = "Cambiar jugadores", onClick = actions.changePlayers, color = ArcadeCyan)
    ArcadeOutlinedButton(text = "Menú principal", onClick = actions.exitToMenu)
}
