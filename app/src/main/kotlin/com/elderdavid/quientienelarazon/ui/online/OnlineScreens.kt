package com.elderdavid.quientienelarazon.ui.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.elderdavid.quientienelarazon.online.OnlineAudience
import com.elderdavid.quientienelarazon.online.OnlineBusy
import com.elderdavid.quientienelarazon.online.OnlineError
import com.elderdavid.quientienelarazon.online.OnlineUiState
import com.elderdavid.quientienelarazon.online.RoomPlayer
import com.elderdavid.quientienelarazon.online.RoomView
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.components.ChoiceChip
import com.elderdavid.quientienelarazon.ui.components.NeonTitle
import com.elderdavid.quientienelarazon.ui.components.PlayerAvatar
import com.elderdavid.quientienelarazon.ui.components.Tag
import com.elderdavid.quientienelarazon.ui.components.sticker
import com.elderdavid.quientienelarazon.ui.local.GameLengthCard
import com.elderdavid.quientienelarazon.ui.local.OptionButton
import com.elderdavid.quientienelarazon.ui.local.QuestionSign
import com.elderdavid.quientienelarazon.ui.local.RoundHeader
import com.elderdavid.quientienelarazon.ui.local.SourceLink
import com.elderdavid.quientienelarazon.ui.local.TimerBar
import com.elderdavid.quientienelarazon.ui.local.heistLabel
import com.elderdavid.quientienelarazon.ui.local.optionLetter
import com.elderdavid.quientienelarazon.ui.local.roundRule
import com.elderdavid.quientienelarazon.ui.local.signed
import com.elderdavid.quientienelarazon.ui.screens.AvatarEmojis
import com.elderdavid.quientienelarazon.ui.theme.Chicle
import com.elderdavid.quientienelarazon.ui.theme.Cielo
import com.elderdavid.quientienelarazon.ui.theme.Linea
import com.elderdavid.quientienelarazon.ui.theme.Mango
import com.elderdavid.quientienelarazon.ui.theme.Menta
import com.elderdavid.quientienelarazon.ui.theme.Naranja
import com.elderdavid.quientienelarazon.ui.theme.Papel
import com.elderdavid.quientienelarazon.ui.theme.PapelSoft
import com.elderdavid.quientienelarazon.ui.theme.PlayerColors
import com.elderdavid.quientienelarazon.ui.theme.Rojo
import com.elderdavid.quientienelarazon.ui.theme.Tinta
import com.elderdavid.quientienelarazon.ui.theme.inkShadow

/** Acciones de la pantalla online. */
class OnlineActions(
    val setMode: (String) -> Unit,
    val setLength: (GameLength) -> Unit,
    val setAudience: (OnlineAudience) -> Unit,
    val setKidsLevel: (Boolean) -> Unit,
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
    OnlineError.INVALID_AUDIENCE -> "Las salas de niños y mixtas solo tienen preguntas generales."
    OnlineError.WRONG_STATUS -> "La partida ya empezó: el nivel ya no se puede cambiar."
    OnlineError.NETWORK -> "Sin conexión con el servidor. Comprueba tu internet e inténtalo otra vez."
    OnlineError.UNKNOWN -> "Algo salió mal en el servidor. Inténtalo otra vez."
}

@Composable
private fun ErrorCard(state: OnlineUiState, actions: OnlineActions) {
    val error = state.error ?: return
    ArcadeCard(accent = Rojo) {
        Text(errorMessage(error), color = Papel)
        TextButton(onClick = actions.dismissError) { Text("Entendido", color = Mango) }
    }
}

@Composable
private fun NotAvailableScreen(onBack: () -> Unit) {
    ArcadeScreen(title = "Jugar online", onBack = onBack) {
        ArcadeCard {
            Text("El juego online no está disponible en esta versión", style = MaterialTheme.typography.titleMedium)
            Text("Falta la conexión con el servidor. Puedes jugar en este teléfono sin conexión.", color = PapelSoft)
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
        ArcadeCard(accent = Cielo) {
            Text("Preguntas", style = MaterialTheme.typography.titleMedium, color = Cielo)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.modes.forEach { m ->
                    ChoiceChip(
                        label = if (m.id == "general") "General" else "De aquí somos: ${m.name}",
                        selected = m.id == state.mode,
                        onClick = { actions.setMode(m.id) },
                        color = Cielo,
                    )
                }
            }
        }
        AudienceCard(state, actions)
        GameLengthCard(state.length, actions.setLength)

        ArcadeCard(accent = Chicle) {
            Text("Con amigos", style = MaterialTheme.typography.titleMedium, color = Chicle)
            Text(
                "Crea una sala y comparte el código. De 2 a 4 jugadores, cada uno en su teléfono. " +
                    "Al entrar con código, el tipo de sala lo decide quien la creó.",
                color = PapelSoft,
            )
            ArcadeButton(
                text = if (busy == OnlineBusy.CREATING) "Creando sala…" else "Crear sala privada",
                onClick = actions.createPrivate,
                enabled = busy == null,
                color = Chicle,
            )
            OutlinedTextField(
                value = state.codeInput,
                onValueChange = actions.setCode,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Código de sala") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Chicle, unfocusedBorderColor = Linea, cursorColor = Chicle),
            )
            ArcadeOutlinedButton(
                text = if (busy == OnlineBusy.JOINING) "Entrando…" else "Entrar con código",
                onClick = actions.joinByCode,
                enabled = busy == null && state.codeValid,
                color = Chicle,
            )
        }

        ArcadeCard(accent = Mango) {
            Text("Partida pública", style = MaterialTheme.typography.titleMedium, color = Mango)
            Text("Te unimos con otros jugadores que eligieron las mismas preguntas, el mismo tipo de sala y la misma duración.", color = PapelSoft)
            ArcadeButton(
                text = if (busy == OnlineBusy.SEARCHING) "Buscando…" else "Buscar partida",
                onClick = actions.findPublic,
                enabled = busy == null,
                color = Mango,
            )
        }
    }
}

fun audienceName(audience: OnlineAudience): String = when (audience) {
    OnlineAudience.STANDARD -> "Normal"
    OnlineAudience.KIDS -> "Niños"
    OnlineAudience.MIXED -> "Mixta"
}

fun audienceHint(audience: OnlineAudience): String = when (audience) {
    OnlineAudience.STANDARD -> "Preguntas normales y 8 segundos para responder."
    OnlineAudience.KIDS -> "Todos reciben preguntas más fáciles, tienen 12 segundos y fallar una apuesta no resta puntos."
    OnlineAudience.MIXED -> "Niños y mayores juntos: cada uno recibe una pregunta de la misma categoría pero de su nivel. " +
        "Los niños tienen 12 segundos y los mayores 8."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudienceCard(state: OnlineUiState, actions: OnlineActions) {
    ArcadeCard(accent = Naranja) {
        Text("Tipo de sala", style = MaterialTheme.typography.titleMedium, color = Naranja)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OnlineAudience.entries.forEach { a ->
                ChoiceChip(label = audienceName(a), selected = a == state.audience, onClick = { actions.setAudience(a) }, color = Naranja)
            }
        }
        Text(audienceHint(state.audience), color = PapelSoft)
        if (state.audience != OnlineAudience.STANDARD) {
            Text("Solo con preguntas generales.", color = PapelSoft, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.audience == OnlineAudience.MIXED) {
            KidsLevelChips(state.kidsLevel, enabled = true, onChange = actions.setKidsLevel)
        }
    }
}

/** Nivel del jugador en una sala mixta. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KidsLevelChips(kids: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Text("Mis preguntas", style = MaterialTheme.typography.labelMedium, color = PapelSoft)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "Normales (8 s)", true to "De niños (12 s)").forEach { (value, label) ->
            ChoiceChip(label = label, selected = kids == value, onClick = { if (kids != value) onChange(value) }, enabled = enabled)
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
        val audience = view.room.roomAudience
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(state.modeName, Cielo)
            Tag("${view.room.questionCount} preguntas", Papel)
            if (audience != OnlineAudience.STANDARD) Tag("Sala ${audienceName(audience).lowercase()}", Naranja)
        }
        if (audience != OnlineAudience.STANDARD) {
            ArcadeCard(accent = Naranja) {
                Text(audienceHint(audience), color = PapelSoft)
                if (audience == OnlineAudience.MIXED) {
                    KidsLevelChips(view.myKids, enabled = state.busy == null, onChange = actions.setKidsLevel)
                }
            }
        }
        if (!view.room.isPublic) {
            Column(
                Modifier.fillMaxWidth().sticker(Mango, MaterialTheme.shapes.extraLarge, depth = 6.dp).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Código de la sala", style = MaterialTheme.typography.titleMedium, color = Tinta)
                Text(
                    RoomCode.display(view.room.code),
                    style = MaterialTheme.typography.displayLarge,
                    color = Tinta,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                ArcadeOutlinedButton(
                    text = "Compartir código",
                    color = Mango,
                    onClick = {
                        val text = "¡Juega conmigo a ¿Quién tiene la razón? Entra con el código ${RoomCode.display(view.room.code)}"
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, "Compartir código"))
                    },
                )
            }
        }
        ArcadeCard(accent = Mango) {
            Text("Jugadores (${view.players.size}/${GameRules.MAX_PLAYERS})", style = MaterialTheme.typography.titleMedium, color = Mango)
            view.players.forEach { p ->
                val tags = listOfNotNull(
                    "anfitrión".takeIf { p.userId == view.room.hostId },
                    "preguntas de niños".takeIf { p.kids && view.room.roomAudience == OnlineAudience.MIXED },
                )
                PlayerRow(p, view, trailing = tags.joinToString(" · ").ifEmpty { null })
            }
        }
        val players = view.players.size
        when {
            view.room.isPublic -> {
                val clock = state.clock
                if (players >= GameRules.MIN_PLAYERS && clock != null) {
                    val seconds = rememberSeconds(clock, actions.now)
                    Text("La partida empieza en $seconds s o al llegar a ${GameRules.MAX_PLAYERS} jugadores.", color = Mango, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Mango, modifier = Modifier.padding(end = 12.dp))
                        Text("Esperando a otro jugador…", color = PapelSoft)
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
            )
            else -> Text("Esperando a que el anfitrión empiece la partida.", color = PapelSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
            Text(p.name + if (p.userId == view.me) " (tú)" else "", style = MaterialTheme.typography.titleMedium, color = Papel)
            val status = when {
                p.left -> "abandonó"
                !p.connected -> "reconectando…"
                else -> trailing
            }
            if (status != null) Text(status, style = MaterialTheme.typography.labelMedium, color = if (p.left || !p.connected) Naranja else PapelSoft)
        }
        if (score) Text("${p.score}", style = MaterialTheme.typography.headlineMedium, color = color)
    }
}

@Composable
private fun ReconnectBanner(state: OnlineUiState) {
    if (!state.reconnecting) return
    ArcadeCard(accent = Naranja) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = Naranja, modifier = Modifier.size(24.dp))
            Column {
                Text("Conexión perdida. Reconectando…", color = Naranja)
                Text("Sigues en la partida mientras vuelve la conexión.", color = PapelSoft, style = MaterialTheme.typography.bodyMedium)
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
            confirmButton = { TextButton(onClick = { confirmLeave = false; actions.leave() }) { Text("Abandonar", color = Rojo) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Seguir jugando", color = Mango) } },
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
                else -> CircularProgressIndicator(color = Mango)
            }
        }
        if (playing) {
            TextButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Abandonar partida", color = PapelSoft)
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
        Text(label ?: "", style = MaterialTheme.typography.titleLarge, color = Papel)
        Text(
            "$seconds s",
            style = MaterialTheme.typography.displaySmall.copy(shadow = inkShadow(4f)),
            color = if (seconds <= 3) Chicle else Mango,
        )
    }
    TimerBar(fraction, urgent = seconds <= 3)
}

@Composable
private fun WaitingFor(view: RoomView, done: (RoomPlayer) -> Boolean) {
    val active = view.players.filter { !it.left && (view.current?.isTiebreak != true || it.inTiebreak) }
    Text(
        active.joinToString("   ") { (if (done(it)) "✓ " else "… ") + it.name },
        color = PapelSoft,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RouletteView(view: RoomView, categoryName: (String) -> String) {
    val current = view.current ?: return
    val round = roundOf(view) ?: return
    RoundHeader(round, current.index + 1, view.room.questionCount, current.heist)
    if (view.room.length.isFirstOfRound(current.index)) {
        ArcadeCard(accent = Cielo) {
            Text("Ronda ${round.number}: ${round.title}", style = MaterialTheme.typography.titleLarge, color = Cielo)
            Text(roundRule(round, view.myKids, view.room.roomAudience == OnlineAudience.STANDARD), color = PapelSoft)
        }
    }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 180.dp).sticker(Mango, MaterialTheme.shapes.extraLarge, depth = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("La ruleta eligió", style = MaterialTheme.typography.titleMedium, color = Tinta, modifier = Modifier.padding(top = 22.dp))
        Text(
            categoryName(current.categoryId),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.displayMedium,
            color = Tinta,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BettingView(state: OnlineUiState, view: RoomView, categoryName: (String) -> String, actions: OnlineActions) {
    val current = view.current ?: return
    val round = roundOf(view) ?: return
    RoundHeader(round, current.index + 1, view.room.questionCount, current.heist)
    PhaseTimer(state, actions, "Apuesta")
    val me = view.myPlayer
    ArcadeCard(accent = Naranja) {
        Tag(categoryName(current.categoryId), Cielo)
        Text("Tienes ${me?.score ?: 0} puntos. La pregunta aparece cuando termine el tiempo de apuesta.", color = PapelSoft)
        if (view.myKids) Text("Con preguntas de niños, si fallas no pierdes lo apostado.", color = Menta)
    }
    val myBet = current.myBet
    val allowed = current.allowedBets.orEmpty()
    when {
        me == null || me.left -> Unit
        myBet != null -> Text(
            if (myBet == 0) "Juegas con apuesta libre." else "Apostaste $myBet puntos.",
            style = MaterialTheme.typography.titleLarge,
            color = Mango,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        allowed.isEmpty() -> {
            ArcadeCard {
                Text("Apuesta libre", style = MaterialTheme.typography.titleMedium, color = Mango)
                Text("No te alcanza para la apuesta mínima. Si aciertas ganas ${GameRules.FREE_BET_REWARD} puntos y si fallas no pierdes nada.", color = PapelSoft)
            }
            ArcadeButton(text = "Jugar apuesta libre", onClick = { actions.feedback(FeedbackEvent.TAP); actions.bet(0) }, enabled = !state.sending)
        }
        else -> {
            allowed.forEach { amount ->
                ArcadeButton(
                    text = "Apostar $amount · ganas ${amount * GameRules.BET_WIN_MULTIPLIER}",
                    onClick = { actions.feedback(FeedbackEvent.TAP); actions.bet(amount) },
                    enabled = !state.sending,
                    color = if (amount == allowed.max()) Naranja else Mango,
                )
            }
            Text("Si no eliges, se aplica la apuesta mínima.", color = PapelSoft, style = MaterialTheme.typography.bodyMedium)
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
        NeonTitle("¡Desempate!", color = Chicle, align = TextAlign.Center)
        Text("Gana quien acierte más rápido. No cambia las estadísticas.", color = PapelSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    } else {
        roundOf(view)?.let { RoundHeader(it, current.index + 1, view.room.questionCount, current.heist) }
    }
    PhaseTimer(state, actions, if (participating) "Tu respuesta" else "Mirando")
    current.myBet?.takeIf { !tiebreak }?.let { Tag(if (it == 0) "Apuesta libre" else "Apuesta: $it", Naranja) }
    QuestionSign(
        category = categoryName(current.categoryId),
        note = when (question.kind) {
            "OPINION" -> "Opinión: gana la opción más votada"
            "DEBATE" -> if (question.isVote) "Debate: gana la opción más votada" else "Debate: se resuelve con la regla del juego"
            else -> null
        },
        text = question.text,
    )
    val chosen = current.myChoice
    question.options.forEachIndexed { index, option ->
        OptionButton(
            text = option,
            letter = optionLetter(index),
            onClick = { actions.feedback(FeedbackEvent.TAP); actions.answer(index) },
            enabled = participating && chosen == null && !state.sending,
            highlight = if (chosen == index) Chicle else null,
        )
    }
    when {
        !participating && tiebreak -> Text("Solo juegan los empatados en el primer puesto.", color = PapelSoft)
        !participating -> Text("Abandonaste la partida: solo puedes mirar.", color = PapelSoft)
        chosen != null -> Text("Respuesta enviada. Esperando a los demás…", color = Papel, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
    if (current.isTiebreak) NeonTitle("Desempate", color = Chicle, align = TextAlign.Center) else roundOf(view)?.let { RoundHeader(it, current.index + 1, view.room.questionCount, current.heist) }
    QuestionSign(category = categoryName(current.categoryId), note = null, text = question.text, compact = true)
    question.options.forEachIndexed { index, option ->
        val votes = result.voteCounts[index.toString()]
        OptionButton(
            text = if (question.isVote) "$option  ·  ${votes ?: 0} voto${if (votes == 1) "" else "s"}" else option,
            letter = optionLetter(index),
            onClick = {},
            enabled = false,
            highlight = if (index in result.winningOptions) Menta else Linea,
        )
    }
    if (question.isVote && !result.scored) Text("Menos de 2 votos válidos: esta pregunta no da puntos.", color = Mango)
    ArcadeCard(accent = Menta) {
        Text(if (question.isVote) "Resultado de la votación" else "Respuesta", style = MaterialTheme.typography.titleMedium, color = Menta)
        result.gameRule?.let { Text("Regla del juego: $it", color = Naranja) }
        Text(result.explanation, color = PapelSoft)
        result.source?.let { SourceLink(it) }
    }
    ArcadeCard(accent = Chicle) {
        result.outcomes.forEach { o ->
            val p = view.player(o.userId) ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerAvatar(name = p.name, playerIndex = p.seat, size = 36, emoji = AvatarEmojis.getOrNull(p.avatarId))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    // En una sala mixta, quien tiene otro nivel respondió otra pregunta: su opción no es de esta.
                    val answer = when {
                        !o.sameQuestion -> (if (p.kids) "Su pregunta de niños: " else "Su pregunta normal: ") + when (o.won) {
                            true -> "acertó"
                            false -> if (o.choice == null) "sin respuesta" else "falló"
                            null -> if (o.choice == null) "sin respuesta" else "votó"
                        }
                        else -> o.choice?.let { "${optionLetter(it)}. ${question.options.getOrElse(it) { "" }}" } ?: "Sin respuesta"
                    }
                    val extra = when {
                        current.isTiebreak && o.won == true && o.elapsedMs != null -> " · %.2f s".format(o.elapsedMs / 1000.0)
                        o.bet != null -> if (o.bet == 0) " · apuesta libre" else " · apostó ${o.bet}"
                        else -> ""
                    }
                    Text(answer + extra, color = PapelSoft, style = MaterialTheme.typography.bodyMedium)
                    heistLabel(o.stolen)?.let { Text(it, color = Chicle, style = MaterialTheme.typography.labelMedium) }
                }
                if (!current.isTiebreak) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            signed(o.points),
                            style = MaterialTheme.typography.headlineMedium,
                            color = when {
                                o.points > 0 -> Menta
                                o.points < 0 -> Rojo
                                else -> PapelSoft
                            },
                        )
                        Text("${p.score} pts", color = PapelSoft, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    if (current.isTiebreak) {
        val winner = result.tiebreakWinner?.let { view.player(it)?.name }
        Text(
            if (winner != null) "¡$winner gana el desempate!" else "Sigue el empate.",
            style = MaterialTheme.typography.headlineMedium.copy(shadow = inkShadow(4f)),
            color = Mango,
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
        ArcadeCard(accent = Cielo) {
            Text("Siguiente: ronda ${next.number}, ${next.title}", style = MaterialTheme.typography.titleMedium, color = Cielo)
            Text(roundRule(next, view.myKids, view.room.roomAudience == OnlineAudience.STANDARD), color = PapelSoft)
        }
    }
    PhaseTimer(state, actions, "Siguiente ronda")
}

@Composable
private fun Scoreboard(view: RoomView) {
    ArcadeCard(accent = Mango) {
        view.ranking.forEachIndexed { position, p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${position + 1}", style = MaterialTheme.typography.headlineMedium, color = if (position == 0) Mango else PapelSoft, modifier = Modifier.padding(end = 12.dp))
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
        color = Mango,
        align = TextAlign.Center,
    )
    if (winners.size > 1) Text(winners.joinToString(" y "), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Scoreboard(view)
    view.final?.let { final ->
        ArcadeCard {
            Text("Aciertos", style = MaterialTheme.typography.titleMedium, color = Mango)
            final.forEach { f ->
                val name = view.player(f.userId)?.name ?: return@forEach
                Text("$name: ${f.correct}", color = PapelSoft)
            }
            Text("Tus estadísticas y tu nivel ya se actualizaron en tu perfil.", color = PapelSoft, style = MaterialTheme.typography.bodyMedium)
        }
    }
    ArcadeButton(text = "Jugar otra", onClick = actions.leave)
    ArcadeOutlinedButton(text = "Menú principal", onClick = { actions.leave(); actions.exit() }, color = Mango)
}

@Composable
private fun CancelledView(view: RoomView, actions: OnlineActions) {
    NeonTitle("Partida cancelada", align = TextAlign.Center)
    ArcadeCard {
        Text(
            when (view.room.cancelReason) {
                "abandoned" -> "Todos los jugadores se desconectaron o abandonaron. No cuenta para las estadísticas."
                else -> "La sala se quedó sin jugadores."
            },
            color = PapelSoft,
        )
    }
    ArcadeButton(text = "Volver", onClick = actions.leave)
}
