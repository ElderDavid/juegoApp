package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elderdavid.quientienelarazon.core.profile.Avatars
import com.elderdavid.quientienelarazon.core.profile.LocalGameSummary
import com.elderdavid.quientienelarazon.core.profile.LocalHistory
import com.elderdavid.quientienelarazon.core.profile.Levels
import com.elderdavid.quientienelarazon.core.profile.PlayerStats
import com.elderdavid.quientienelarazon.stats.Leaderboard
import com.elderdavid.quientienelarazon.stats.LeaderboardEntry
import com.elderdavid.quientienelarazon.stats.OnlineStats
import com.elderdavid.quientienelarazon.stats.StatsViewModel
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
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.confirmClear) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClear,
            title = { Text("¿Borrar el historial de este teléfono?") },
            text = { Text("Se borran las partidas locales guardadas aquí. Tus estadísticas online no cambian.") },
            confirmButton = { TextButton(onClick = viewModel::clearLocal) { Text("Borrar", color = ArcadeError) } },
            dismissButton = { TextButton(onClick = viewModel::dismissClear) { Text("Cancelar", color = ArcadeCyan) } },
        )
    }

    ArcadeScreen(title = "Estadísticas", onBack = onBack) {
        SectionHeading("Online · tu cuenta")
        when (val online = state.online) {
            OnlineStats.NotConfigured -> ArcadeCard(accent = ArcadeOutline) {
                Text("Esta versión no tiene conexión con el servidor, así que no hay estadísticas online.", color = ArcadeTextMuted)
            }
            OnlineStats.Loading -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = ArcadeCyan)
            }
            OnlineStats.Unavailable -> ArcadeCard(accent = ArcadeError) {
                Text("No se pudieron cargar tus estadísticas online. Revisa tu conexión.", color = ArcadeTextMuted)
                ArcadeOutlinedButton(text = "Reintentar", onClick = viewModel::refreshOnline, color = ArcadeCyan)
            }
            is OnlineStats.Ready -> {
                OnlineStatsCard(online.stats)
                LeaderboardCard(online.leaderboard, onRetry = viewModel::refreshOnline)
            }
        }

        SectionHeading("En este teléfono · partidas locales")
        LocalHistoryCard(state.local, state.regionNames, onClear = viewModel::askClear)
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = ArcadeCyan,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun OnlineStatsCard(stats: PlayerStats) {
    ArcadeCard(accent = ArcadeOrange) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Nivel ${stats.level}", style = MaterialTheme.typography.headlineMedium, color = ArcadeOrange)
            Text("${stats.xp} XP", color = ArcadeTextMuted)
        }
        Text("Partidas: ${stats.gamesPlayed} · Victorias: ${stats.wins} · Derrotas: ${stats.losses}", color = ArcadeTextMuted)
        Text("Aciertos: ${stats.correctAnswers} de ${stats.objectiveAnswered} (${stats.accuracyPercent} %)", color = ArcadeTextMuted)
        Text("Mejor puntuación: ${stats.bestScore}", color = ArcadeTextMuted)
        Text(
            "Se guardan en el servidor y se recuperan al entrar con tu cuenta en otro teléfono. " +
                "Ganas ${Levels.XP_PER_GAME} XP por partida, ${Levels.XP_PER_CORRECT} por acierto y ${Levels.XP_PER_WIN} por victoria.",
            style = MaterialTheme.typography.bodyMedium,
            color = ArcadeTextMuted,
        )
    }
}

@Composable
private fun LeaderboardCard(board: Leaderboard?, onRetry: () -> Unit) {
    ArcadeCard(accent = ArcadeViolet) {
        Text("Clasificación", style = MaterialTheme.typography.titleMedium)
        when {
            board == null -> {
                Text("No se pudo cargar la clasificación.", color = ArcadeTextMuted)
                ArcadeOutlinedButton(text = "Reintentar", onClick = onRetry, color = ArcadeCyan)
            }
            board.top.isEmpty() -> Text("Aún nadie ha terminado una partida online.", color = ArcadeTextMuted)
            else -> {
                board.top.forEach { LeaderboardRow(it) }
                val me = board.me
                if (me == null) {
                    Text("Juega una partida online para entrar en la clasificación.", color = ArcadeTextMuted)
                } else if (board.top.none { it.me }) {
                    HorizontalDivider(color = ArcadeOutline)
                    LeaderboardRow(me)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    val emoji = AvatarEmojis.getOrElse(entry.avatarId.coerceIn(0, Avatars.COUNT - 1)) { AvatarEmojis.first() }
    val color = if (entry.me) ArcadeMagenta else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("${entry.rank}.", color = color, modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
        Text("$emoji ${entry.name}${if (entry.me) " (tú)" else ""}", color = color, modifier = Modifier.weight(1f))
        Text("Nv ${Levels.levelFor(entry.xp)} · ${entry.wins} V", color = ArcadeTextMuted)
    }
}

@Composable
private fun LocalHistoryCard(history: LocalHistory, regionNames: Map<String, String>, onClear: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.forLanguageTag("es-CO")) }
    ArcadeCard(accent = ArcadeMagenta) {
        if (history.gamesFinished == 0) {
            Text("Todavía no hay partidas locales terminadas en este teléfono.", color = ArcadeTextMuted)
        } else {
            Text("Partidas terminadas: ${history.gamesFinished}", color = ArcadeTextMuted)
            history.bestScoreName?.let { Text("Récord: ${history.bestScore} puntos, de $it", color = ArcadeTextMuted) }
            Text("Últimas partidas", style = MaterialTheme.typography.titleMedium)
            history.recent.forEach { LocalGameRow(it, regionNames, dateFormat) }
        }
        Text(
            "Este historial solo se guarda en este teléfono y no suma a tu cuenta, porque en una partida local " +
                "juegan varias personas con el mismo teléfono. Se pierde si desinstalas la app o borras sus datos.",
            style = MaterialTheme.typography.bodyMedium,
            color = ArcadeTextMuted,
        )
        if (history.gamesFinished > 0) {
            ArcadeOutlinedButton(text = "Borrar historial local", onClick = onClear, color = ArcadeError)
        }
    }
}

@Composable
private fun LocalGameRow(game: LocalGameSummary, regionNames: Map<String, String>, dateFormat: DateFormat) {
    val mode = game.regionId?.let { regionNames[it] ?: it } ?: "General"
    val ranking = game.players.sortedByDescending { it.score }.joinToString(" · ") { "${it.name} ${it.score}" }
    val winners = game.winners.joinToString(" y ")
    HorizontalDivider(color = ArcadeOutline)
    Text("${dateFormat.format(Date(game.finishedAtMillis))} · $mode", style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
    Text(if (game.winners.size > 1) "Ganan $winners" else "Gana $winners", color = ArcadeOrange)
    Text(ranking, style = MaterialTheme.typography.bodyMedium)
}
