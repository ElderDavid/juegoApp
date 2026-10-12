package com.elderdavid.quientienelarazon.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elderdavid.quientienelarazon.billing.Product
import com.elderdavid.quientienelarazon.core.economy.Cosmetic
import com.elderdavid.quientienelarazon.core.economy.CosmeticKind
import com.elderdavid.quientienelarazon.core.economy.Cosmetics
import com.elderdavid.quientienelarazon.core.economy.EconomyRules
import com.elderdavid.quientienelarazon.economy.EconomyUiState
import com.elderdavid.quientienelarazon.economy.EconomyViewModel
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.theme.ArcadeCyan
import com.elderdavid.quientienelarazon.ui.theme.ArcadeError
import com.elderdavid.quientienelarazon.ui.theme.ArcadeMagenta
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOrange
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOutline
import com.elderdavid.quientienelarazon.ui.theme.ArcadeSuccess
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted
import com.elderdavid.quientienelarazon.ui.theme.ArcadeViolet

/** Monedas, racha diaria, videos, compras y cosméticos. */
@Composable
fun ShopScreen(viewModel: EconomyViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    ArcadeScreen(title = "Tienda y premios", onBack = onBack) {
        state.message?.let { Text(it, color = ArcadeOrange, style = MaterialTheme.typography.bodyLarge) }
        if (!state.available) {
            ArcadeCard(accent = ArcadeOutline) {
                Text("Tienda no disponible", style = MaterialTheme.typography.titleMedium)
                Text("Esta versión no tiene conexión con el servidor. Puedes jugar sin límites igualmente.", color = ArcadeTextMuted)
            }
            return@ArcadeScreen
        }
        val snapshot = state.snapshot
        if (snapshot == null) {
            if (state.loadFailed) {
                ArcadeCard(accent = ArcadeError) {
                    Text("Sin conexión. Tus monedas te esperan en el servidor.", color = ArcadeTextMuted)
                    ArcadeOutlinedButton(text = "Reintentar", onClick = viewModel::refresh, color = ArcadeCyan)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = ArcadeCyan) }
            }
            return@ArcadeScreen
        }

        ArcadeCard(accent = ArcadeOrange) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tus monedas", style = MaterialTheme.typography.titleMedium)
                Text("🪙 ${snapshot.coins}", style = MaterialTheme.typography.headlineMedium, color = ArcadeOrange)
            }
            Text(
                "Úsalas en comodines para tus partidas y en marcos, títulos, temas y sonidos. Todas las preguntas y modos son gratis.",
                color = ArcadeTextMuted,
            )
        }

        DailyCard(state, viewModel)

        ArcadeCard(accent = ArcadeViolet) {
            Text("Ver un video", style = MaterialTheme.typography.titleMedium, color = ArcadeViolet)
            Text(
                "+${EconomyRules.VIDEO_REWARD} monedas por video completo. Te quedan ${snapshot.videosLeft} hoy.",
                color = ArcadeTextMuted,
            )
            ArcadeButton(
                text = when {
                    snapshot.videosLeft == 0 -> "Vuelve mañana"
                    !state.rewardedReady -> "Cargando video…"
                    else -> "Ver video: +${EconomyRules.VIDEO_REWARD}"
                },
                onClick = { activity?.let(viewModel::watchVideo) },
                enabled = snapshot.videosLeft > 0 && state.rewardedReady && !state.busy && activity != null,
                color = ArcadeViolet,
            )
        }

        if (state.billingAvailable) {
            ArcadeCard(accent = ArcadeMagenta) {
                Text("Quitar anuncios", style = MaterialTheme.typography.titleMedium, color = ArcadeMagenta)
                if (state.removeAds) {
                    Text("Ya no ves anuncios. ¡Gracias por apoyar el juego!", color = ArcadeSuccess)
                } else {
                    Text(
                        "Un solo pago, para siempre: sin anuncios al terminar las partidas ni banners. Los videos con monedas siguen disponibles si quieres.",
                        color = ArcadeTextMuted,
                    )
                    ArcadeButton(
                        text = state.prices[Product.REMOVE_ADS]?.let { "Quitar anuncios · $it" } ?: "Quitar anuncios",
                        onClick = { activity?.let { viewModel.buy(it, Product.REMOVE_ADS) } },
                        enabled = !state.busy && activity != null,
                        color = ArcadeMagenta,
                    )
                }
            }
            ArcadeCard(accent = ArcadeCyan) {
                Text("Paquetes de monedas", style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
                Product.coinPacks.forEach { pack ->
                    ArcadeOutlinedButton(
                        text = "🪙 ${pack.coins}" + (state.prices[pack]?.let { " · $it" } ?: ""),
                        onClick = { activity?.let { viewModel.buy(it, pack) } },
                        enabled = !state.busy && activity != null,
                        color = ArcadeCyan,
                    )
                }
                Text("El cobro lo hace Google Play. Contenido fijo: sin cajas sorpresa.", color = ArcadeTextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        CosmeticKind.entries.forEach { kind -> CosmeticsCard(kind, state, viewModel) }
    }
}

@Composable
private fun DailyCard(state: EconomyUiState, viewModel: EconomyViewModel) {
    val s = state.snapshot ?: return
    ArcadeCard(accent = ArcadeSuccess) {
        Text("Racha diaria", style = MaterialTheme.typography.titleMedium, color = ArcadeSuccess)
        Text(
            if (s.dailyClaimed) "Día ${s.streakDay} reclamado. Vuelve mañana para el día ${s.streakDay + 1}."
            else "Día ${s.nextStreakDay}: +${EconomyRules.streakReward(s.nextStreakDay)} monedas. Si un día no entras, vuelves al día 1 sin perder nada.",
            color = ArcadeTextMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EconomyRules.STREAK_REWARDS.forEachIndexed { i, coins ->
                val day = i + 1
                val done = if (s.dailyClaimed) day <= s.streakDay else day < s.nextStreakDay
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("D$day", style = MaterialTheme.typography.labelMedium, color = if (done) ArcadeSuccess else ArcadeTextMuted)
                    Text("$coins", style = MaterialTheme.typography.labelMedium, color = if (done) ArcadeSuccess else ArcadeTextMuted)
                }
            }
        }
        ArcadeButton(
            text = if (s.dailyClaimed) "Reclamado" else "Reclamar +${EconomyRules.streakReward(s.nextStreakDay)}",
            onClick = viewModel::claimDaily,
            enabled = !s.dailyClaimed && !state.busy,
            color = ArcadeSuccess,
        )
    }
}

@Composable
private fun CosmeticsCard(kind: CosmeticKind, state: EconomyUiState, viewModel: EconomyViewModel) {
    val items = Cosmetics.all.filter { it.kind == kind }
    val level = state.snapshot?.level ?: 1
    ArcadeCard {
        Text(kindTitle(kind), style = MaterialTheme.typography.titleMedium, color = ArcadeCyan)
        items.forEach { c -> CosmeticRow(c, level, state, viewModel) }
    }
}

@Composable
private fun CosmeticRow(c: Cosmetic, level: Int, state: EconomyUiState, viewModel: EconomyViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.bodyLarge)
            val detail = when {
                state.isEquipped(c) -> "Equipado"
                state.owns(c) -> "Tuyo"
                c.unlockLevel != null -> "Se gana en el nivel ${c.unlockLevel} (vas en el $level)"
                else -> "🪙 ${c.price}"
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (state.isEquipped(c)) ArcadeSuccess else ArcadeTextMuted)
        }
        val modifier = Modifier.widthIn(max = 150.dp)
        when {
            state.owns(c) -> ArcadeOutlinedButton(
                text = if (state.isEquipped(c)) "Quitar" else "Usar",
                onClick = { viewModel.toggleEquip(c) },
                enabled = !state.busy,
                color = ArcadeCyan,
                modifier = modifier,
            )
            c.price != null -> ArcadeOutlinedButton(
                text = "Comprar",
                onClick = { viewModel.buyCosmetic(c) },
                enabled = !state.busy && (state.snapshot?.coins ?: 0) >= c.price,
                color = ArcadeOrange,
                modifier = modifier,
            )
        }
    }
}

private fun kindTitle(kind: CosmeticKind) = when (kind) {
    CosmeticKind.FRAME -> "Marcos de avatar"
    CosmeticKind.TITLE -> "Títulos"
    CosmeticKind.THEME -> "Temas de color"
    CosmeticKind.ROULETTE_SOUND -> "Sonidos de la ruleta"
    CosmeticKind.VICTORY -> "Animaciones de victoria"
}
