package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elderdavid.quientienelarazon.account.AccountState
import com.elderdavid.quientienelarazon.account.ProfileUiState
import com.elderdavid.quientienelarazon.account.ProfileViewModel
import com.elderdavid.quientienelarazon.account.requestGoogleToken
import com.elderdavid.quientienelarazon.core.profile.Avatars
import com.elderdavid.quientienelarazon.core.profile.Levels
import com.elderdavid.quientienelarazon.core.profile.PlayerStats
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.theme.ArcadeCyan
import com.elderdavid.quientienelarazon.ui.theme.ArcadeError
import com.elderdavid.quientienelarazon.ui.theme.ArcadeMagenta
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOrange
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOutline
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted
import com.elderdavid.quientienelarazon.ui.theme.ArcadeViolet
import kotlinx.coroutines.launch

/** Avatares predefinidos: fauna colombiana. El índice es el avatar_id que guarda el servidor. */
val AvatarEmojis = listOf("🦜", "🐆", "🦊", "🐢", "🐬", "🦉", "🐸", "🦋", "🐝", "🦁", "🐙", "🦩")

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, googleWebClientId: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val askGoogle: (Boolean) -> Unit = { switching ->
        scope.launch { viewModel.onGoogleCredential(requestGoogleToken(context, googleWebClientId), switching) }
    }

    if (state.conflict) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConflict,
            title = { Text("Esa cuenta de Google ya tiene un perfil") },
            text = {
                Text(
                    "Puedes seguir como invitado o cambiar a la cuenta que ya existe. " +
                        "Si cambias, el progreso de este invitado no se suma a esa cuenta.",
                )
            },
            confirmButton = { TextButton(onClick = { askGoogle(true) }) { Text("Cambiar de cuenta", color = ArcadeOrange) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConflict) { Text("Seguir como invitado", color = ArcadeCyan) } },
        )
    }

    ArcadeScreen(title = "Perfil", onBack = onBack) {
        state.message?.let { Text(it, color = ArcadeOrange, style = MaterialTheme.typography.bodyLarge) }
        when (val account = state.account) {
            AccountState.NotConfigured -> ArcadeCard(accent = ArcadeOutline) {
                Text("Cuentas aún no disponibles", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Esta versión no tiene conexión con el servidor de cuentas. Puedes jugar en este teléfono sin cuenta.",
                    color = ArcadeTextMuted,
                )
            }
            AccountState.Loading, AccountState.SignedOut -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = ArcadeCyan)
            }
            AccountState.Offline -> ArcadeCard(accent = ArcadeError) {
                Text("Sin conexión", style = MaterialTheme.typography.titleMedium)
                Text("No se pudo contactar con el servidor. El juego local sigue disponible.", color = ArcadeTextMuted)
                ArcadeOutlinedButton(text = "Reintentar", onClick = viewModel::retry, color = ArcadeCyan)
            }
            is AccountState.Guest, is AccountState.Google -> {
                AccountCard(account, state, onLink = { askGoogle(false) }, onSignOut = viewModel::signOut)
                if (state.loadFailed) {
                    ArcadeCard(accent = ArcadeError) {
                        Text("No se pudo cargar tu perfil.", color = ArcadeTextMuted)
                        ArcadeOutlinedButton(text = "Reintentar", onClick = viewModel::retry, color = ArcadeCyan)
                    }
                }
                if (state.profile != null) {
                    EditCard(state, viewModel)
                    state.stats?.let { StatsCard(it) }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(account: AccountState, state: ProfileUiState, onLink: () -> Unit, onSignOut: () -> Unit) {
    ArcadeCard(accent = ArcadeViolet) {
        when (account) {
            is AccountState.Guest -> {
                Text("Juegas como invitado", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Vincula tu cuenta de Google para no perder tu progreso si cambias de teléfono. Mantienes el mismo perfil.",
                    color = ArcadeTextMuted,
                )
                if (state.googleAvailable) {
                    ArcadeButton(text = if (state.linking) "Vinculando…" else "Vincular con Google", onClick = onLink, enabled = !state.linking, color = ArcadeCyan)
                } else {
                    Text("La vinculación con Google aún no está configurada en esta versión.", color = ArcadeTextMuted)
                }
            }
            is AccountState.Google -> {
                Text("Cuenta de Google vinculada", style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
                account.email?.let { Text(it, color = ArcadeTextMuted) }
                ArcadeOutlinedButton(text = "Cerrar sesión", onClick = onSignOut)
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditCard(state: ProfileUiState, viewModel: ProfileViewModel) {
    ArcadeCard(accent = ArcadeCyan) {
        Text("Nombre y avatar", style = MaterialTheme.typography.titleMedium)
        val issue = state.nameIssue
        OutlinedTextField(
            value = state.nameDraft,
            onValueChange = viewModel::setName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombre público") },
            singleLine = true,
            isError = issue != null,
            supportingText = if (issue != null) { { Text(ProfileViewModel.issueMessage(issue)) } } else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ArcadeCyan, unfocusedBorderColor = ArcadeOutline, cursorColor = ArcadeCyan),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (id in 0 until Avatars.COUNT) {
                val selected = id == state.avatarDraft
                OutlinedButton(
                    onClick = { viewModel.setAvatar(id) },
                    modifier = Modifier.size(56.dp).semantics {
                        this.selected = selected
                        contentDescription = "Avatar ${id + 1}"
                    },
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) ArcadeMagenta else ArcadeOutline),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Text(AvatarEmojis[id], style = MaterialTheme.typography.headlineMedium) }
            }
        }
        ArcadeButton(text = if (state.saving) "Guardando…" else "Guardar", onClick = viewModel::save, enabled = state.canSave, color = ArcadeMagenta)
    }
}

@Composable
private fun StatsCard(stats: PlayerStats) {
    ArcadeCard(accent = ArcadeOrange) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Nivel ${stats.level}", style = MaterialTheme.typography.headlineMedium, color = ArcadeOrange)
            Text("${stats.xp} XP", color = ArcadeTextMuted)
        }
        Text("Partidas online: ${stats.gamesPlayed}", color = ArcadeTextMuted)
        Text("Victorias: ${stats.wins} · Derrotas: ${stats.losses}", color = ArcadeTextMuted)
        Text("Aciertos: ${stats.correctAnswers} de ${stats.objectiveAnswered} (${stats.accuracyPercent} %)", color = ArcadeTextMuted)
        Text("Mejor puntuación: ${stats.bestScore}", color = ArcadeTextMuted)
        Text(
            "Ganas ${Levels.XP_PER_GAME} XP por partida, ${Levels.XP_PER_CORRECT} por acierto y ${Levels.XP_PER_WIN} por victoria.",
            style = MaterialTheme.typography.bodyMedium,
            color = ArcadeTextMuted,
        )
    }
}
