package com.elderdavid.quientienelarazon.ui.online

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elderdavid.quientienelarazon.core.game.GameLength
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.RoundType
import com.elderdavid.quientienelarazon.core.online.PhaseClock
import com.elderdavid.quientienelarazon.core.online.RoomCode
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import com.elderdavid.quientienelarazon.local.FeedbackEvent
import com.elderdavid.quientienelarazon.online.OnlineBusy
import com.elderdavid.quientienelarazon.online.OnlineError
import com.elderdavid.quientienelarazon.online.OnlineUiState
import com.elderdavid.quientienelarazon.online.RoomPlayer
import com.elderdavid.quientienelarazon.online.RoomView
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.components.NeonTitle
import com.elderdavid.quientienelarazon.ui.components.PlayerAvatar
import com.elderdavid.quientienelarazon.ui.local.GameLengthCard
import com.elderdavid.quientienelarazon.ui.local.OptionButton
import com.elderdavid.quientienelarazon.ui.local.RoundHeader
import com.elderdavid.quientienelarazon.ui.local.SourceLink
import com.elderdavid.quientienelarazon.ui.local.optionLetter
import com.elderdavid.quientienelarazon.ui.local.roundRule
import com.elderdavid.quientienelarazon.ui.local.signed
import com.elderdavid.quientienelarazon.ui.screens.AvatarEmojis
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

/** Acciones de la pantalla online. */
class OnlineActions(
    val setMode: (String) -> Unit,
    val setLength: (GameLength) -> Unit,
    val setCode: (String) -> Unit,
    val createPrivate: () -> Unit,
    val joinByCode: () -> Unit,
    val findPublic: () -> Unit,
    val start: () -> Unit,
    val bet: (Int) -> Unit,
    val answer: (Int) -> Unit,
    val leave: () -> Unit,
    val dismissError: () -> Unit,
    val exit: () -> Unit,
    val now: () -> Long,
    val feedback: (FeedbackEvent) -> Unit,
)

@Composable
fun OnlineScreen(state: OnlineUiState, bank: QuestionBank?, actions: OnlineActions) {
    val view = state.view
    when {
        !state.available -> NotAvailableScreen(actions.exit)
        view == null -> OnlineMenuScreen(state, actions)
        view.room.inLobby -> LobbyScreen(state, view, actions)
        else -> GameScreen(state, view, bank, actions)
    }
}

fun errorMessage(error: OnlineError): String = when (error) {
    OnlineError.NOT_SIGNED_IN -> "No se pudo iniciar sesión. Revisa tu conexión."
    OnlineError.ROOM_NOT_FOUND -> "No hay ninguna sala abierta con ese código."
    OnlineError.ROOM_FULL -> "La sala está llena (máximo ${GameRules.MAX_PLAYERS} jugadores)."
    OnlineError.ROOM_IN_PROGRESS -> "Esa partida ya empezó. Pide un código nuevo para la siguiente."
    OnlineError.ROOM_CLOSED -> "Esa sala ya se cerró."
    OnlineError.ROOM_LEFT -> "Saliste de esa partida y no se puede volver a entrar."
    OnlineError.NOT_IN_ROOM -> "Ya no estás en la sala."
    OnlineError.NOT_HOST -> "Solo quien creó la sala puede empezar."
    OnlineError.NOT_ENOUGH_PLAYERS -> "Hacen falta al menos ${GameRules.MIN_PLAYERS} jugadores conectados."
    OnlineError.NOT_ENOUGH_QUESTIONS -> "Esta modalidad no tiene preguntas suficientes en el servidor."
    OnlineError.RATE_LIMITED -> "Demasiados intentos seguidos. Espera unos minutos."
    OnlineError.TIME_EXPIRED -> "Se acabó el tiempo: tu respuesta no llegó a tiempo."
    OnlineError.ALREADY_ANSWERED -> "Ya habías respondido."
    OnlineError.ALREADY_BET -> "Ya habías apostado."
    OnlineError.WRONG_PHASE, OnlineError.WRONG_QUESTION -> "La partida ya avanzó. Actualizando…"
    OnlineError.INVALID_BET -> "Esa apuesta no está permitida con tus puntos."
    OnlineError.INVALID_OPTION -> "Opción no válida."
    OnlineError.INVALID_MODE -> "Esa modalidad no existe en el servidor."
    OnlineError.NETWORK -> "Sin conexión con el servidor. Comprueba tu internet e inténtalo otra vez."
    OnlineError.UNKNOWN -> "Algo salió mal en el servidor. Inténtalo otra vez."
}

@Composable
private fun ErrorCard(state: OnlineUiState, actions: OnlineActions) {
    val error = state.error ?: return
    ArcadeCard(accent = ArcadeError) {
        Text(errorMessage(error), color = ArcadeText)
        TextButton(onClick = actions.dismissError) { Text("Entendido", color = ArcadeCyan) }
    }
}

@Composable
private fun NotAvailableScreen(onBack: () -> Unit) {
    ArcadeScreen(title = "Jugar online", onBack = onBack) {
        ArcadeCard {
            Text("El juego online no está disponible en esta versión", style = MaterialTheme.typography.titleMedium)
            Text("Falta la conexión con el servidor. Puedes jugar en este teléfono sin conexión.", color = ArcadeTextMuted)
        }
    }
}

// ---------------------------------------------------------------- Menú online

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineMenuScreen(state: OnlineUiState, actions: OnlineActions) {
    val busy = state.busy
    ArcadeScreen(title = "Jugar online", onBack = actions.exit) {
        ErrorCard(state, actions)
        ArcadeCard(accent = ArcadeViolet) {
            Text("Preguntas", style = MaterialTheme.typography.titleMedium, color = ArcadeViolet)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.modes.forEach { m ->
                    FilterChip(
                        selected = m.id == state.mode,
                        onClick = { actions.setMode(m.id) },
                        label = { Text(if (m.id == "general") "General" else "De aquí somos: ${m.name}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ArcadeViolet,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
        GameLengthCard(state.length, actions.setLength)

        ArcadeCard(accent = ArcadeMagenta) {
            Text("Con amigos", style = MaterialTheme.typography.titleMedium, color = ArcadeMagenta)
            Text("Crea una sala y comparte el código. De 2 a 4 jugadores, cada uno en su teléfono.", color = ArcadeTextMuted)
            ArcadeButton(
                text = if (busy == OnlineBusy.CREATING) "Creando sala…" else "Crear sala privada",
                onClick = actions.createPrivate,
                enabled = busy == null,
                color = ArcadeMagenta,
            )
            OutlinedTextField(
                value = state.codeInput,
                onValueChange = actions.setCode,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Código de sala") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ArcadeMagenta, unfocusedBorderColor = ArcadeOutline, cursorColor = ArcadeMagenta),
            )
            ArcadeOutlinedButton(
                text = if (busy == OnlineBusy.JOINING) "Entrando…" else "Entrar con código",
                onClick = actions.joinByCode,
                enabled = busy == null && state.codeValid,
                color = ArcadeMagenta,
            )
        }

        ArcadeCard(accent = ArcadeCyan) {
            Text("Partida pública", style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
            Text("Te unimos con otros jugadores que eligieron las mismas preguntas y la misma duración.", color = ArcadeTextMuted)
            ArcadeButton(
                text = if (busy == OnlineBusy.SEARCHING) "Buscando…" else "Buscar partida",
                onClick = actions.findPublic,
                enabled = busy == null,
                color = ArcadeCyan,
            )
        }
    }
}

// ---------------------------------------------------------------- Sala de espera

@Composable
private fun LobbyScreen(state: OnlineUiState, view: RoomView, actions: OnlineActions) {
    val context = LocalContext.current
    BackHandler { actions.leave() }
    ArcadeScreen(title = if (view.room.isPublic) "Buscando rivales" else "Sala privada") {
        ReconnectBanner(state)
        ErrorCard(state, actions)
        Text("Preguntas: ${state.modeName} · ${view.room.questionCount} preguntas", color = ArcadeTextMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        if (!view.room.isPublic) {
            ArcadeCard(accent = ArcadeOrange) {
                Text("Código", style = MaterialTheme.typography.labelMedium, color = ArcadeTextMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(
                    RoomCode.display(view.room.code),
                    style = MaterialTheme.typography.displayMedium,
                    color = ArcadeOrange,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                ArcadeOutlinedButton(
                    text = "Compartir código",
                    color = ArcadeOrange,
                    onClick = {
                        val text = "¡Juega conmigo a ¿Quién tiene la razón? Entra con el código ${RoomCode.display(view.room.code)}"
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, "Compartir código"))
                    },
                )
            }
        }
        ArcadeCard(accent = ArcadeCyan) {
            Text("Jugadores (${view.players.size}/${GameRules.MAX_PLAYERS})", style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
            view.players.forEach { p -> PlayerRow(p, view, trailing = if (p.userId == view.room.hostId) "anfitrión" else null) }
        }
        val players = view.players.size
        when {
            view.room.isPublic -> {
                val clock = state.clock
                if (players >= GameRules.MIN_PLAYERS && clock != null) {
                    val seconds = rememberSeconds(clock, actions.now)
                    Text("La partida empieza en $seconds s o al llegar a ${GameRules.MAX_PLAYERS} jugadores.", color = ArcadeOrange, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = ArcadeCyan, modifier = Modifier.padding(end = 12.dp))
                        Text("Esperando a otro jugador…", color = ArcadeTextMuted)
                    }
                }
            }
            view.isHost -> ArcadeButton(
                text = when {
                    state.busy == OnlineBusy.STARTING -> "Empezando…"
                    players < GameRules.MIN_PLAYERS -> "Esperando jugadores…"
                    else -> "Empezar partida"
                },
                onClick = actions.start,
                enabled = players >= GameRules.MIN_PLAYERS && state.busy == null,
                color = ArcadeMagenta,
            )
            else -> Text("Esperando a que el anfitrión empiece la partida.", color = ArcadeTextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        ArcadeOutlinedButton(text = "Salir de la sala", onClick = actions.leave)
    }
}

@Composable
private fun PlayerRow(p: RoomPlayer, view: RoomView, trailing: String? = null, score: Boolean = false) {
    val color = PlayerColors[p.seat % PlayerColors.size]
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerAvatar(name = p.name, playerIndex = p.seat, size = 40, emoji = AvatarEmojis.getOrNull(p.avatarId))
        Column(Modifier.weight(1f)) {
            Text(p.name + if (p.userId == view.me) " (tú)" else "", style = MaterialTheme.typography.titleMedium, color = ArcadeText)
            val status = when {
                p.left -> "abandonó"
                !p.connected -> "reconectando…"
                else -> trailing
            }
            if (status != null) Text(status, style = MaterialTheme.typography.labelMedium, color = if (p.left || !p.connected) ArcadeOrange else ArcadeTextMuted)
        }
        if (score) Text("${p.score}", style = MaterialTheme.typography.headlineMedium, color = color)
    }
}

@Composable
private fun ReconnectBanner(state: OnlineUiState) {
    if (!state.reconnecting) return
    ArcadeCard(accent = ArcadeOrange) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = ArcadeOrange, modifier = Modifier.size(24.dp))
            Column {
                Text("Conexión perdida. Reconectando…", color = ArcadeOrange)
                Text("Sigues en la partida mientras vuelve la conexión.", color = ArcadeTextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun rememberSeconds(clock: PhaseClock, now: () -> Long): Int {
    var seconds by remember(clock) { mutableIntStateOf(clock.secondsShown(now())) }
    LaunchedEffect(clock) {
        while (true) {
            withFrameMillis { }
            seconds = clock.secondsShown(now())
            if (clock.remaining(now()) == 0L) break
        }
    }
    return seconds
}

// ---------------------------------------------------------------- Partida

@Composable
private fun GameScreen(state: OnlineUiState, view: RoomView, bank: QuestionBank?, actions: OnlineActions) {
    var confirmLeave by remember { mutableStateOf(false) }
    val playing = view.room.status == "playing"
    BackHandler(enabled = playing) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("¿Abandonar la partida?") },
            text = { Text("Contará como derrota y no podrás volver a esta partida. Los demás seguirán jugando.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; actions.leave() }) { Text("Abandonar", color = ArcadeError) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Seguir jugando", color = ArcadeCyan) } },
        )
    }
    val categoryName: (String) -> String = { id -> runCatching { bank?.category(id)?.name }.getOrNull() ?: id }

    ArcadeScreen(title = null) {
        ReconnectBanner(state)
        ErrorCard(state, actions)
        when {
            view.room.status == "cancelled" -> CancelledView(view, actions)
            view.room.status == "finished" -> PodiumView(view, actions)
            else -> when (view.room.phase) {
                "roulette" -> RouletteView(view, categoryName)
                "betting" -> BettingView(state, view, categoryName, actions)
                "question", "tiebreak_question" -> QuestionView(state, view, categoryName, actions)
                "reveal", "tiebreak_reveal" -> RevealView(state, view, categoryName, actions)
                "summary" -> SummaryView(state, view, actions)
                else -> CircularProgressIndicator(color = ArcadeCyan)
            }
        }
        if (playing) {
            TextButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Abandonar partida", color = ArcadeTextMuted)
            }
        }
    }
}

private fun roundOf(view: RoomView): RoundType? = view.current?.round?.takeIf { it in 1..4 }?.let { RoundType.entries[it - 1] }

@Composable
private fun PhaseTimer(state: OnlineUiState, actions: OnlineActions, label: String? = null) {
    val clock = state.clock ?: return
    var remaining by remember(clock) { mutableLongStateOf(clock.remaining(actions.now())) }
    LaunchedEffect(clock) {
        while (remaining > 0) {
            withFrameMillis { }
            remaining = clock.remaining(actions.now())
        }
    }
    val fraction by animateFloatAsState(targetValue = if (clock.totalMillis > 0) remaining.toFloat() / clock.totalMillis else 0f, label = "timer")
    val seconds = ((remaining + 999) / 1000).toInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label ?: "", style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
        Text("$seconds s", style = MaterialTheme.typography.headlineMedium, color = if (seconds <= 3) ArcadeError else ArcadeText)
    }
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(10.dp),
        color = if (seconds <= 3) ArcadeError else ArcadeCyan,
        trackColor = ArcadeOutline,
    )
}

@Composable
private fun WaitingFor(view: RoomView, done: (RoomPlayer) -> Boolean) {
    val active = view.players.filter { !it.left && (view.current?.isTiebreak != true || it.inTiebreak) }
    Text(
        active.joinToString("   ") { (if (done(it)) "✓ " else "… ") + it.name },
        color = ArcadeTextMuted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RouletteView(view: RoomView, categoryName: (String) -> String) {
    val current = view.current ?: return
    val round = roundOf(view) ?: return
    RoundHeader(round, current.index + 1, view.room.questionCount)
    if (view.room.length.isFirstOfRound(current.index)) {
        ArcadeCard(accent = ArcadeViolet) {
            Text("Ronda ${round.number}: ${round.title}", style = MaterialTheme.typography.titleLarge, color = ArcadeViolet)
            Text(roundRule(round), color = ArcadeTextMuted)
        }
    }
    ArcadeCard(accent = ArcadeOrange) {
        Text("La ruleta eligió", style = MaterialTheme.typography.labelMedium, color = ArcadeTextMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(
            categoryName(current.categoryId),
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.displayMedium,
            color = ArcadeOrange,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BettingView(state: OnlineUiState, view: RoomView, categoryName: (String) -> String, actions: OnlineActions) {
    val current = view.current ?: return
    val round = roundOf(view) ?: return
    RoundHeader(round, current.index + 1, view.room.questionCount)
    PhaseTimer(state, actions, "Apuesta")
    val me = view.myPlayer
    ArcadeCard(accent = ArcadeOrange) {
        Text("Categoría: ${categoryName(current.categoryId)}", style = MaterialTheme.typography.titleMedium)
        Text("Tienes ${me?.score ?: 0} puntos. La pregunta aparece cuando termine el tiempo de apuesta.", color = ArcadeTextMuted)
    }
    val myBet = current.myBet
    val allowed = current.allowedBets.orEmpty()
    when {
        me == null || me.left -> Unit
        myBet != null -> Text(
            if (myBet == 0) "Juegas con apuesta libre." else "Apostaste $myBet puntos.",
            style = MaterialTheme.typography.titleLarge,
            color = ArcadeOrange,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        allowed.isEmpty() -> {
            ArcadeCard {
                Text("Apuesta libre", style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
                Text("No te alcanza para la apuesta mínima. Si aciertas ganas ${GameRules.FREE_BET_REWARD} puntos y si fallas no pierdes nada.", color = ArcadeTextMuted)
            }
            ArcadeButton(text = "Jugar apuesta libre", onClick = { actions.feedback(FeedbackEvent.TAP); actions.bet(0) }, enabled = !state.sending)
        }
        else -> {
            allowed.forEach { amount ->
                ArcadeButton(
                    text = "Apostar $amount",
                    onClick = { actions.feedback(FeedbackEvent.TAP); actions.bet(amount) },
                    enabled = !state.sending,
                    color = if (amount == allowed.max()) ArcadeMagenta else ArcadeCyan,
                )
            }
            Text("Si no eliges, se aplica la apuesta mínima.", color = ArcadeTextMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
    WaitingFor(view) { it.betPlaced }
}

@Composable
private fun QuestionView(state: OnlineUiState, view: RoomView, categoryName: (String) -> String, actions: OnlineActions) {
    val current = view.current ?: return
    val question = current.question ?: return
    val me = view.myPlayer
    val tiebreak = current.isTiebreak
    val participating = me != null && !me.left && (!tiebreak || me.inTiebreak)
    if (tiebreak) {
        NeonTitle("¡Desempate!", color = ArcadeMagenta)
        Text("Gana quien acierte más rápido. No cambia las estadísticas.", color = ArcadeTextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    } else {
        roundOf(view)?.let { RoundHeader(it, current.index + 1, view.room.questionCount) }
    }
    PhaseTimer(state, actions, if (participating) "Tu respuesta" else "Mirando")
    current.myBet?.takeIf { !tiebreak }?.let { Text(if (it == 0) "APUESTA LIBRE" else "APUESTA: $it", color = ArcadeOrange, style = MaterialTheme.typography.labelMedium) }
    ArcadeCard(accent = ArcadeViolet) {
        Text(categoryName(current.categoryId).uppercase(), style = MaterialTheme.typography.labelMedium, color = ArcadeViolet)
        when {
            question.kind == "OPINION" -> Text("Opinión: gana la opción más votada", style = MaterialTheme.typography.labelMedium, color = ArcadeOrange)
            question.kind == "DEBATE" -> Text(
                if (question.isVote) "Debate: gana la opción más votada" else "Debate: se resuelve con la regla del juego",
                style = MaterialTheme.typography.labelMedium,
                color = ArcadeOrange,
            )
        }
        Text(question.text, style = MaterialTheme.typography.headlineMedium, color = ArcadeText)
    }
    val chosen = current.myChoice
    question.options.forEachIndexed { index, option ->
        OptionButton(
            text = option,
            letter = optionLetter(index),
            onClick = { actions.feedback(FeedbackEvent.TAP); actions.answer(index) },
            enabled = participating && chosen == null && !state.sending,
            highlight = if (chosen == index) ArcadeMagenta else null,
        )
    }
    when {
        !participating && tiebreak -> Text("Solo juegan los empatados en el primer puesto.", color = ArcadeTextMuted)
        !participating -> Text("Abandonaste la partida: solo puedes mirar.", color = ArcadeTextMuted)
        chosen != null -> Text("Respuesta enviada. Esperando a los demás…", color = ArcadeMagenta, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
    WaitingFor(view) { it.answered }
}

@Composable
private fun RevealView(state: OnlineUiState, view: RoomView, categoryName: (String) -> String, actions: OnlineActions) {
    val current = view.current ?: return
    val question = current.question ?: return
    val result = view.result ?: return
    val mine = result.outcomes.firstOrNull { it.userId == view.me }
    LaunchedEffect(current.isTiebreak, current.index) {
        actions.feedback(if (mine?.won == true) FeedbackEvent.CORRECT else FeedbackEvent.WRONG)
    }
    if (current.isTiebreak) NeonTitle("Desempate", color = ArcadeMagenta) else roundOf(view)?.let { RoundHeader(it, current.index + 1, view.room.questionCount) }
    ArcadeCard(accent = ArcadeViolet) {
        Text(categoryName(current.categoryId).uppercase(), style = MaterialTheme.typography.labelMedium, color = ArcadeViolet)
        Text(question.text, style = MaterialTheme.typography.titleLarge, color = ArcadeText)
    }
    question.options.forEachIndexed { index, option ->
        val votes = result.voteCounts[index.toString()]
        OptionButton(
            text = if (question.isVote) "$option  ·  ${votes ?: 0} voto${if (votes == 1) "" else "s"}" else option,
            letter = optionLetter(index),
            onClick = {},
            enabled = false,
            highlight = if (index in result.winningOptions) ArcadeSuccess else ArcadeOutline,
        )
    }
    if (question.isVote && !result.scored) Text("Menos de 2 votos válidos: esta pregunta no da puntos.", color = ArcadeOrange)
    ArcadeCard {
        Text(if (question.isVote) "Resultado de la votación" else "Respuesta", style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
        result.gameRule?.let { Text("Regla del juego: $it", color = ArcadeOrange) }
        Text(result.explanation, color = ArcadeTextMuted)
        result.source?.let { SourceLink(it) }
    }
    ArcadeCard(accent = ArcadeMagenta) {
        result.outcomes.forEach { o ->
            val p = view.player(o.userId) ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerAvatar(name = p.name, playerIndex = p.seat, size = 36, emoji = AvatarEmojis.getOrNull(p.avatarId))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    val answer = o.choice?.let { "${optionLetter(it)}. ${question.options.getOrElse(it) { "" }}" } ?: "Sin respuesta"
                    val extra = when {
                        current.isTiebreak && o.won == true && o.elapsedMs != null -> " · %.2f s".format(o.elapsedMs / 1000.0)
                        o.bet != null -> if (o.bet == 0) " · apuesta libre" else " · apostó ${o.bet}"
                        else -> ""
                    }
                    Text(answer + extra, color = ArcadeTextMuted, style = MaterialTheme.typography.bodyMedium)
                }
                if (!current.isTiebreak) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            signed(o.points),
                            style = MaterialTheme.typography.titleLarge,
                            color = when {
                                o.points > 0 -> ArcadeSuccess
                                o.points < 0 -> ArcadeError
                                else -> ArcadeTextMuted
                            },
                        )
                        Text("${p.score} pts", color = ArcadeTextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    if (current.isTiebreak) {
        val winner = result.tiebreakWinner?.let { view.player(it)?.name }
        Text(
            if (winner != null) "¡$winner gana el desempate!" else "Sigue el empate.",
            style = MaterialTheme.typography.headlineMedium,
            color = ArcadeOrange,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PhaseTimer(state, actions, "Siguiente")
}

@Composable
private fun SummaryView(state: OnlineUiState, view: RoomView, actions: OnlineActions) {
    val finished = roundOf(view) ?: return
    NeonTitle("Fin de la ronda ${finished.number}")
    Scoreboard(view)
    RoundType.entries.getOrNull(finished.ordinal + 1)?.let { next ->
        ArcadeCard(accent = ArcadeViolet) {
            Text("Siguiente: ronda ${next.number}, ${next.title}", style = MaterialTheme.typography.titleMedium, color = ArcadeViolet)
            Text(roundRule(next), color = ArcadeTextMuted)
        }
    }
    PhaseTimer(state, actions, "Siguiente ronda")
}

@Composable
private fun Scoreboard(view: RoomView) {
    ArcadeCard(accent = ArcadeCyan) {
        view.ranking.forEachIndexed { position, p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${position + 1}.", style = MaterialTheme.typography.titleLarge, color = ArcadeTextMuted, modifier = Modifier.padding(end = 8.dp))
                Column(Modifier.weight(1f)) { PlayerRow(p, view, score = true) }
            }
        }
    }
}

@Composable
private fun PodiumView(view: RoomView, actions: OnlineActions) {
    val iWon = view.me in view.room.winners
    LaunchedEffect(Unit) { actions.feedback(if (iWon) FeedbackEvent.CORRECT else FeedbackEvent.WRONG) }
    val winners = view.room.winners.mapNotNull { view.player(it)?.name }
    NeonTitle(
        when {
            winners.size == 1 -> "¡${winners.single()} tiene la razón!"
            winners.size > 1 -> "¡Victoria compartida!"
            else -> "Partida terminada"
        },
        color = ArcadeOrange,
    )
    if (winners.size > 1) Text(winners.joinToString(" y "), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Scoreboard(view)
    view.final?.let { final ->
        ArcadeCard {
            Text("Aciertos", style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
            final.forEach { f ->
                val name = view.player(f.userId)?.name ?: return@forEach
                Text("$name: ${f.correct}", color = ArcadeTextMuted)
            }
            Text("Tus estadísticas y tu nivel ya se actualizaron en tu perfil.", color = ArcadeTextMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
    ArcadeButton(text = "Jugar otra", onClick = actions.leave, color = ArcadeMagenta)
    ArcadeOutlinedButton(text = "Menú principal", onClick = { actions.leave(); actions.exit() }, color = ArcadeCyan)
}

@Composable
private fun CancelledView(view: RoomView, actions: OnlineActions) {
    NeonTitle("Partida cancelada", color = ArcadeOrange)
    ArcadeCard {
        Text(
            when (view.room.cancelReason) {
                "abandoned" -> "Todos los jugadores se desconectaron o abandonaron. No cuenta para las estadísticas."
                else -> "La sala se quedó sin jugadores."
            },
            color = ArcadeTextMuted,
        )
    }
    ArcadeButton(text = "Volver", onClick = actions.leave, color = ArcadeMagenta)
}
