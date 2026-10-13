package com.elderdavid.quientienelarazon.ui.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.elderdavid.quientienelarazon.core.game.GameLength
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.local.LocalSetup
import com.elderdavid.quientienelarazon.core.local.NameIssue
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import com.elderdavid.quientienelarazon.local.LocalUiState
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.components.PlayerAvatar
import com.elderdavid.quientienelarazon.ui.theme.ArcadeCyan
import com.elderdavid.quientienelarazon.ui.theme.ArcadeError
import com.elderdavid.quientienelarazon.ui.theme.ArcadeMagenta
import com.elderdavid.quientienelarazon.ui.theme.ArcadeNight
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOrange
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOutline
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted
import com.elderdavid.quientienelarazon.ui.theme.PlayerColors

@Composable
fun LocalSetupScreen(
    state: LocalUiState,
    onNameChange: (Int, String) -> Unit,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Int) -> Unit,
    onModeChange: (ContentMode) -> Unit,
    onLengthChange: (GameLength) -> Unit,
    onFastChange: (Boolean) -> Unit,
    onSoloChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    ArcadeScreen(title = "Partida local", onBack = onBack) {
        when {
            state.loading -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = ArcadeCyan)
            }
            state.loadError -> ArcadeCard(accent = ArcadeError) {
                Text("No se pudieron cargar las preguntas.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Es un problema de la instalación de la app, no de tu conexión. Reinstalarla debería solucionarlo.",
                    color = ArcadeTextMuted,
                )
            }
            else -> SetupContent(
                state, onNameChange, onAddPlayer, onRemovePlayer, onModeChange, onLengthChange, onFastChange, onSoloChange, onStart,
            )
        }
    }
}

@Composable
private fun SetupContent(
    state: LocalUiState,
    onNameChange: (Int, String) -> Unit,
    onAddPlayer: () -> Unit,
    onRemovePlayer: (Int) -> Unit,
    onModeChange: (ContentMode) -> Unit,
    onLengthChange: (GameLength) -> Unit,
    onFastChange: (Boolean) -> Unit,
    onSoloChange: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    Text(
        "Juega solo o con hasta 4 personas en este teléfono. En grupo os lo iréis pasando en cada turno.",
        style = MaterialTheme.typography.bodyLarge,
        color = ArcadeTextMuted,
    )
    val issues = state.nameIssues
    ArcadeCard(accent = ArcadeCyan) {
        Text("Jugadores", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Solo", state.solo, ArcadeCyan) { onSoloChange(true) }
            ModeChip("En grupo", !state.solo, ArcadeCyan) { onSoloChange(false) }
        }
        if (state.solo) {
            Text(
                "Modo solitario: preguntas de dificultad media y difícil. ¡Intenta superar tu récord!",
                style = MaterialTheme.typography.bodyMedium,
                color = ArcadeTextMuted,
            )
        }
        state.names.forEachIndexed { index, name ->
            val message = issueText(issues[index], name)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayerAvatar(name = name.ifBlank { "${index + 1}" }, playerIndex = index, size = 44)
                OutlinedTextField(
                    value = name,
                    onValueChange = { onNameChange(index, it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Jugador ${index + 1}") },
                    singleLine = true,
                    // Solo marcamos error cuando el campo ya tiene algo o está repetido, no al abrir.
                    isError = issues[index] != null && (name.isNotEmpty() || issues[index] != NameIssue.EMPTY),
                    supportingText = if (message != null) { { Text(message) } } else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PlayerColors[index % PlayerColors.size],
                        unfocusedBorderColor = ArcadeOutline,
                        cursorColor = ArcadeCyan,
                    ),
                )
                if (state.canRemovePlayer) {
                    TextButton(
                        onClick = { onRemovePlayer(index) },
                        modifier = Modifier.semantics { contentDescription = "Quitar jugador ${index + 1}" },
                    ) { Text("✕", color = ArcadeTextMuted) }
                }
            }
        }
        if (state.canAddPlayer && !state.solo) {
            ArcadeOutlinedButton(text = "Añadir jugador", onClick = onAddPlayer, color = ArcadeCyan)
        }
    }

    ModeCard(state, onModeChange)
    SpeedCard(state, onFastChange)
    GameLengthCard(state.length, onLengthChange)

    ArcadeButton(text = "¡A jugar!", onClick = onStart, enabled = state.canStart, color = ArcadeMagenta)
    if (!state.canStart) {
        Text(
            if (!state.modeUnlocked(state.mode)) "Ese país se desbloquea con monedas en Tienda y premios."
            else if (state.solo) "Escribe tu nombre."
            else "Escribe un nombre distinto para cada jugador.",
            style = MaterialTheme.typography.bodyMedium,
            color = ArcadeTextMuted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeCard(state: LocalUiState, onModeChange: (ContentMode) -> Unit) {
    ArcadeCard(accent = ArcadeOrange) {
        Text("Preguntas", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeChip("General", state.mode == ContentMode.General) { onModeChange(ContentMode.General) }
            ModeChip("Niños", state.mode == ContentMode.Kids) { onModeChange(ContentMode.Kids) }
        }
        if (state.mode == ContentMode.Kids) {
            Text(kidsModeHint(), style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
        }
        Text("De aquí somos: preguntas de un departamento", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.regions.forEach { region ->
                val mode = ContentMode.Regional(region.id)
                ModeChip(region.name, state.mode == mode) { onModeChange(mode) }
            }
        }
        if (state.countries.isNotEmpty()) {
            Text("Países: preguntas de un país", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.countries.forEach { country ->
                    val mode = ContentMode.Country(country.id)
                    val unlocked = state.modeUnlocked(mode)
                    ModeChip(if (unlocked) country.name else "🔒 ${country.name}", state.mode == mode, enabled = unlocked) {
                        onModeChange(mode)
                    }
                }
            }
            if (state.countries.any { !state.modeUnlocked(ContentMode.Country(it.id)) }) {
                Text(
                    "Los países con candado se desbloquean con monedas en Tienda y premios.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArcadeTextMuted,
                )
            }
        }
    }
}

/** Velocidad: normal o «muy rápido», que se compra con monedas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedCard(state: LocalUiState, onFastChange: (Boolean) -> Unit) {
    ArcadeCard(accent = ArcadeMagenta) {
        Text("Velocidad", style = MaterialTheme.typography.titleLarge)
        val kids = state.mode == ContentMode.Kids
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeChip("Normal · ${GameRules.QUESTION_TIME_MILLIS / 1000} s", !state.fastActive, ArcadeMagenta) { onFastChange(false) }
            ModeChip(
                (if (state.ownsFast) "" else "🔒 ") + "Muy rápido · ${GameRules.FAST_QUESTION_TIME_MILLIS / 1000} s",
                state.fastActive,
                ArcadeMagenta,
                enabled = state.ownsFast && !kids,
            ) { onFastChange(true) }
        }
        Text(
            when {
                kids -> "El modo niños siempre tiene ${GameRules.KIDS_QUESTION_TIME_MILLIS / 1000} segundos por pregunta."
                !state.ownsFast -> "«Muy rápido» se desbloquea con monedas en Tienda y premios."
                else -> "Segundos para responder cada pregunta."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = ArcadeTextMuted,
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    color: Color = ArcadeOrange,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = ArcadeNight,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameLengthCard(selected: GameLength, onChange: (GameLength) -> Unit) {
    ArcadeCard(accent = ArcadeCyan) {
        Text("Duración", style = MaterialTheme.typography.titleLarge)
        // FlowRow: en pantallas estrechas el tercer chip baja de línea en vez de aplastarse.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GameLength.entries.forEach { length ->
                FilterChip(
                    selected = length == selected,
                    onClick = { onChange(length) },
                    label = { Text("${length.questions} preguntas", maxLines = 1, softWrap = false) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ArcadeCyan,
                        selectedLabelColor = ArcadeNight,
                    ),
                )
            }
        }
        Text(lengthHint(selected), style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
    }
}

/** Qué cambia en el modo niños. Las cifras salen de [GameRules] para no contradecir al motor. */
fun kidsModeHint(): String =
    "Preguntas más fáciles, ${GameRules.KIDS_QUESTION_TIME_MILLIS / 1000} segundos por pregunta " +
        "y en las apuestas fallar no resta puntos."

/** Reparto por rondas, para que se entienda qué cambia al elegir menos preguntas. */
fun lengthHint(length: GameLength): String =
    "4 rondas: " + length.perRound.joinToString(" + ") + " preguntas"

private fun issueText(issue: NameIssue?, name: String): String? = when (issue) {
    null -> null
    NameIssue.EMPTY -> if (name.isEmpty()) null else "Escribe un nombre"
    NameIssue.TOO_LONG -> "Máximo ${LocalSetup.MAX_NAME_LENGTH} caracteres"
    NameIssue.DUPLICATE -> "Ese nombre ya está en uso"
}
