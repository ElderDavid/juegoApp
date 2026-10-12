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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
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
            else -> SetupContent(state, onNameChange, onAddPlayer, onRemovePlayer, onModeChange, onStart)
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
    onStart: () -> Unit,
) {
    Text(
        "De 2 a 4 jugadores en este teléfono. Os lo iréis pasando en cada turno.",
        style = MaterialTheme.typography.bodyLarge,
        color = ArcadeTextMuted,
    )
    val issues = state.nameIssues
    ArcadeCard(accent = ArcadeCyan) {
        Text("Jugadores", style = MaterialTheme.typography.titleLarge)
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
        if (state.canAddPlayer) {
            ArcadeOutlinedButton(text = "Añadir jugador", onClick = onAddPlayer, color = ArcadeCyan)
        }
    }

    ModeCard(state, onModeChange)

    ArcadeButton(text = "¡A jugar!", onClick = onStart, enabled = state.canStart, color = ArcadeMagenta)
    if (!state.canStart) {
        Text(
            "Escribe un nombre distinto para cada jugador.",
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
        }
        Text("De aquí somos: preguntas de un departamento", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.regions.forEach { region ->
                val mode = ContentMode.Regional(region.id)
                ModeChip(region.name, state.mode == mode) { onModeChange(mode) }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ArcadeOrange,
            selectedLabelColor = ArcadeNight,
        ),
    )
}

private fun issueText(issue: NameIssue?, name: String): String? = when (issue) {
    null -> null
    NameIssue.EMPTY -> if (name.isEmpty()) null else "Escribe un nombre"
    NameIssue.TOO_LONG -> "Máximo ${LocalSetup.MAX_NAME_LENGTH} caracteres"
    NameIssue.DUPLICATE -> "Ese nombre ya está en uso"
}
