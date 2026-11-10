package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.elderdavid.quientienelarazon.core.economy.Achievements
import com.elderdavid.quientienelarazon.core.economy.CosmeticKind
import com.elderdavid.quientienelarazon.core.economy.Cosmetics
import com.elderdavid.quientienelarazon.core.economy.EconomyRules
import com.elderdavid.quientienelarazon.core.economy.LevelProgress
import com.elderdavid.quientienelarazon.core.profile.Avatars
import com.elderdavid.quientienelarazon.economy.EconomyUiState
import com.elderdavid.quientienelarazon.economy.EconomyViewModel
import com.elderdavid.quientienelarazon.ui.components.MeterBar
import com.elderdavid.quientienelarazon.ui.components.PlayerAvatar
import com.elderdavid.quientienelarazon.ui.components.StickerButton
import com.elderdavid.quientienelarazon.ui.theme.Panel
import androidx.compose.ui.unit.sp
import com.elderdavid.quientienelarazon.ui.theme.Papel
import com.elderdavid.quientienelarazon.ui.theme.frameColor
import com.elderdavid.quientienelarazon.core.profile.Levels
import com.elderdavid.quientienelarazon.core.profile.PlayerStats
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.theme.Mango
import com.elderdavid.quientienelarazon.ui.theme.Rojo
import com.elderdavid.quientienelarazon.ui.theme.Chicle
import com.elderdavid.quientienelarazon.ui.theme.Naranja
import com.elderdavid.quientienelarazon.ui.theme.Linea
import com.elderdavid.quientienelarazon.ui.theme.PapelSoft
import com.elderdavid.quientienelarazon.ui.theme.Cielo
import kotlinx.coroutines.launch

/** Avatares predefinidos: fauna colombiana. El índice es el avatar_id que guarda el servidor. */
val AvatarEmojis = listOf("🦜", "🐆", "🦊", "🐢", "🐬", "🦉", "🐸", "🦋", "🐝", "🦁", "🐙", "🦩")

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    economy: EconomyViewModel,
    googleWebClientId: String,
    onShop: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val economyState by economy.state.collectAsStateWithLifecycle()
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
            confirmButton = { TextButton(onClick = { askGoogle(true) }) { Text("Cambiar de cuenta", color = Naranja) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConflict) { Text("Seguir como invitado", color = Mango) } },
        )
    }

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("¿Borrar tu cuenta?") },
            text = {
                Text(
                    "Se borran para siempre tu perfil, tu nivel, tus estadísticas online y tu puesto en la clasificación. " +
                        "No se puede deshacer. El historial de partidas locales de este teléfono no cambia.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::deleteAccount) { Text("Borrar", color = Rojo) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Cancelar", color = Mango) } },
        )
    }

    ArcadeScreen(title = "Perfil", onBack = onBack) {
        state.message?.let { Text(it, color = Mango, style = MaterialTheme.typography.bodyLarge) }
        when (val account = state.account) {
            AccountState.NotConfigured -> ArcadeCard(accent = Linea) {
                Text("Cuentas aún no disponibles", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Esta versión no tiene conexión con el servidor de cuentas. Puedes jugar en este teléfono sin cuenta.",
                    color = PapelSoft,
                )
            }
            AccountState.Loading, AccountState.SignedOut -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Mango)
            }
            AccountState.Offline -> ArcadeCard(accent = Rojo) {
                Text("Sin conexión", style = MaterialTheme.typography.titleMedium)
                Text("No se pudo contactar con el servidor. El juego local sigue disponible.", color = PapelSoft)
                ArcadeOutlinedButton(text = "Reintentar", onClick = viewModel::retry, color = Mango)
            }
            is AccountState.Guest, is AccountState.Google -> {
                AccountCard(account, state, onLink = { askGoogle(false) }, onSignOut = viewModel::signOut)
                if (state.loadFailed) {
                    ArcadeCard(accent = Rojo) {
                        Text("No se pudo cargar tu perfil.", color = PapelSoft)
                        ArcadeOutlinedButton(text = "Reintentar", onClick = viewModel::retry, color = Mango)
                    }
                }
                if (state.profile != null) {
                    ProgressCard(state, economyState, onShop)
                    EditCard(state, viewModel)
                    state.stats?.let { StatsCard(it) }
                    economyState.snapshot?.let { AchievementsCard(it.achievements) }
                    ArcadeCard(accent = Linea) {
                        Text("Borrar cuenta", style = MaterialTheme.typography.titleMedium)
                        Text("Borra tu perfil y todo tu progreso online del servidor.", color = PapelSoft)
                        ArcadeOutlinedButton(
                            text = if (state.deleting) "Borrando…" else "Borrar mi cuenta",
                            onClick = viewModel::askDelete,
                            enabled = !state.deleting,
                            color = Rojo,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(account: AccountState, state: ProfileUiState, onLink: () -> Unit, onSignOut: () -> Unit) {
    ArcadeCard(accent = Cielo) {
        when (account) {
            is AccountState.Guest -> {
                Text("Juegas como invitado", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Vincula tu cuenta de Google para no perder tu progreso si cambias de teléfono. Mantienes el mismo perfil.",
                    color = PapelSoft,
                )
                if (state.googleAvailable) {
                    ArcadeButton(text = if (state.linking) "Vinculando…" else "Vincular con Google", onClick = onLink, enabled = !state.linking, color = Mango)
                } else {
                    Text("La vinculación con Google aún no está configurada en esta versión.", color = PapelSoft)
                }
            }
            is AccountState.Google -> {
                Text("Cuenta de Google vinculada", style = MaterialTheme.typography.titleMedium, color = Mango)
                account.email?.let { Text(it, color = PapelSoft) }
                ArcadeOutlinedButton(text = "Cerrar sesión", onClick = onSignOut)
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditCard(state: ProfileUiState, viewModel: ProfileViewModel) {
    ArcadeCard(accent = Mango) {
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
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mango, unfocusedBorderColor = Linea, cursorColor = Mango),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (id in 0 until Avatars.COUNT) {
                val selected = id == state.avatarDraft
                StickerButton(
                    onClick = { viewModel.setAvatar(id) },
                    modifier = Modifier.size(60.dp).semantics {
                        this.selected = selected
                        contentDescription = "Avatar ${id + 1}"
                    },
                    fill = if (selected) Chicle else Panel,
                    depth = if (selected) 4.dp else 3.dp,
                    minHeight = 0.dp,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Text(AvatarEmojis[id], fontSize = 26.sp) }
            }
        }
        ArcadeButton(text = if (state.saving) "Guardando…" else "Guardar", onClick = viewModel::save, enabled = state.canSave, color = Chicle)
    }
}

@Composable
private fun StatsCard(stats: PlayerStats) {
    ArcadeCard(accent = Mango) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Online", style = MaterialTheme.typography.titleMedium, color = Mango)
            // La clasificación online solo cuenta el XP de partidas online.
            Text("${stats.xp} XP online", color = PapelSoft)
        }
        Text("Partidas online: ${stats.gamesPlayed}", color = PapelSoft)
        Text("Victorias: ${stats.wins} · Derrotas: ${stats.losses}", color = PapelSoft)
        Text("Aciertos: ${stats.correctAnswers} de ${stats.objectiveAnswered} (${stats.accuracyPercent} %)", color = PapelSoft)
        Text("Mejor puntuación: ${stats.bestScore}", color = PapelSoft)
    }
}

/** Nivel, barra de XP y monedas. Lo que se gana en cada nivel no se anuncia: es sorpresa. */
@Composable
private fun ProgressCard(state: ProfileUiState, economy: EconomyUiState, onShop: () -> Unit) {
    val profile = state.profile ?: return
    val snapshot = economy.snapshot
    val xp = snapshot?.xp ?: state.stats?.xp ?: 0
    val progress = LevelProgress.of(xp)
    val equipped = snapshot?.equipped.orEmpty()
    ArcadeCard(accent = Naranja) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PlayerAvatar(
                name = profile.displayName,
                playerIndex = 2,
                size = 56,
                emoji = AvatarEmojis.getOrNull(profile.avatarId),
                frame = frameColor(equipped[CosmeticKind.FRAME]),
            )
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
                Cosmetics.byId(equipped[CosmeticKind.TITLE])?.let { Text(it.name, color = Cielo, style = MaterialTheme.typography.labelLarge) }
            }
            snapshot?.let { Text("🪙 ${it.coins}", style = MaterialTheme.typography.titleLarge, color = Naranja) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Nivel ${progress.level}", style = MaterialTheme.typography.headlineMedium, color = Naranja)
            Text("${progress.xpIntoLevel} / ${progress.xpForNextLevel} XP", color = PapelSoft)
        }
        MeterBar(progress.fraction, Naranja)
        Text("Cada nivel nuevo te da monedas, y algunos traen un premio sorpresa.", color = Papel)
        Text(
            "Ganas ${Levels.XP_PER_GAME} XP por partida online, ${Levels.XP_PER_CORRECT} por acierto y ${Levels.XP_PER_WIN} por victoria. " +
                "Las partidas en este teléfono dan ${EconomyRules.LOCAL_GAME_XP} XP a tu cuenta (hasta ${EconomyRules.LOCAL_GAMES_XP_PER_DAY} al día).",
            style = MaterialTheme.typography.bodyMedium,
            color = PapelSoft,
        )
        ArcadeButton(text = "Tienda y premios", onClick = onShop, color = Naranja)
    }
}

@Composable
private fun AchievementsCard(unlocked: List<String>) {
    ArcadeCard(accent = Cielo) {
        Text("Logros ${unlocked.size}/${Achievements.all.size}", style = MaterialTheme.typography.titleMedium, color = Cielo)
        Achievements.all.forEach { a ->
            val done = a.id in unlocked
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (done) "★" else "☆", color = if (done) Naranja else PapelSoft, style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, color = if (done) Papel else PapelSoft)
                    Text("${a.description} · 🪙 ${a.coins}", style = MaterialTheme.typography.bodyMedium, color = PapelSoft)
                }
            }
        }
    }
}
