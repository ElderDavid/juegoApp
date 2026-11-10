package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.runtime.getValue
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elderdavid.quientienelarazon.billing.Product
import com.elderdavid.quientienelarazon.core.economy.Cosmetic
import com.elderdavid.quientienelarazon.core.economy.CosmeticKind
import com.elderdavid.quientienelarazon.core.economy.Cosmetics
import com.elderdavid.quientienelarazon.core.economy.EconomyRules
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.economy.EconomyUiState
import com.elderdavid.quientienelarazon.economy.EconomyViewModel
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.theme.Mango
import com.elderdavid.quientienelarazon.ui.theme.Rojo
import com.elderdavid.quientienelarazon.ui.theme.Chicle
import com.elderdavid.quientienelarazon.ui.theme.Naranja
import com.elderdavid.quientienelarazon.ui.theme.Linea
import com.elderdavid.quientienelarazon.ui.theme.Menta
import com.elderdavid.quientienelarazon.ui.theme.PapelSoft
import com.elderdavid.quientienelarazon.ui.theme.Cielo

/** Monedas, racha diaria, videos, compras y cosméticos. */
@Composable
fun ShopScreen(viewModel: EconomyViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    ArcadeScreen(title = "Tienda y premios", onBack = onBack) {
        state.message?.let { Text(it, color = Mango, style = MaterialTheme.typography.bodyLarge) }
        if (!state.available) {
            ArcadeCard(accent = Linea) {
                Text("Tienda no disponible", style = MaterialTheme.typography.titleMedium)
                Text("Esta versión no tiene conexión con el servidor. Puedes jugar sin límites igualmente.", color = PapelSoft)
            }
            return@ArcadeScreen
        }
        val snapshot = state.snapshot
        if (snapshot == null) {
            if (state.loadFailed) {
                ArcadeCard(accent = Rojo) {
                    Text("Sin conexión. Tus monedas te esperan en el servidor.", color = PapelSoft)
                    ArcadeOutlinedButton(text = "Reintentar", onClick = viewModel::refresh, color = Mango)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = Mango) }
            }
            return@ArcadeScreen
        }

        ArcadeCard(accent = Naranja) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tus monedas", style = MaterialTheme.typography.titleMedium)
                Text("🪙 ${snapshot.coins}", style = MaterialTheme.typography.headlineMedium, color = Naranja)
            }
            Text(
                "Úsalas en comodines, en modos de juego extra y en marcos, títulos, temas y sonidos. Las preguntas generales, «De aquí somos», el modo niños y el solitario son gratis.",
                color = PapelSoft,
            )
        }

        DailyCard(state, viewModel)

        ArcadeCard(accent = Cielo) {
            Text("Ver un video", style = MaterialTheme.typography.titleMedium, color = Cielo)
            Text(
                "+${EconomyRules.VIDEO_REWARD} monedas por video completo. Te quedan ${snapshot.videosLeft} hoy.",
                color = PapelSoft,
            )
            ArcadeButton(
                text = when {
                    snapshot.videosLeft == 0 -> "Vuelve mañana"
                    !state.rewardedReady -> "Cargando video…"
                    else -> "Ver video: +${EconomyRules.VIDEO_REWARD}"
                },
                onClick = { activity?.let(viewModel::watchVideo) },
                enabled = snapshot.videosLeft > 0 && state.rewardedReady && !state.busy && activity != null,
                color = Cielo,
            )
        }

        if (state.billingAvailable) {
            ArcadeCard(accent = Chicle) {
                Text("Quitar anuncios", style = MaterialTheme.typography.titleMedium, color = Chicle)
                if (state.removeAds) {
                    Text("Ya no ves anuncios. ¡Gracias por apoyar el juego!", color = Menta)
                } else {
                    Text(
                        "Un solo pago, para siempre: sin anuncios al terminar las partidas ni banners. Los videos con monedas siguen disponibles si quieres.",
                        color = PapelSoft,
                    )
                    ArcadeButton(
                        text = state.prices[Product.REMOVE_ADS]?.let { "Quitar anuncios · $it" } ?: "Quitar anuncios",
                        onClick = { activity?.let { viewModel.buy(it, Product.REMOVE_ADS) } },
                        enabled = !state.busy && activity != null,
                        color = Chicle,
                    )
                }
            }
            ArcadeCard(accent = Mango) {
                Text("Paquetes de monedas", style = MaterialTheme.typography.titleMedium, color = Mango)
                Product.coinPacks.forEach { pack ->
                    ArcadeOutlinedButton(
                        text = "🪙 ${pack.coins}" + (state.prices[pack]?.let { " · $it" } ?: ""),
                        onClick = { activity?.let { viewModel.buy(it, pack) } },
                        enabled = !state.busy && activity != null,
                        color = Mango,
                    )
                }
                Text("El cobro lo hace Google Play. Contenido fijo: sin cajas sorpresa.", color = PapelSoft, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Primero los modos de juego, después lo decorativo.
        (listOf(CosmeticKind.MODE) + CosmeticKind.entries.filter { it != CosmeticKind.MODE })
            .forEach { kind -> CosmeticsCard(kind, state, viewModel) }
    }
}

@Composable
private fun DailyCard(state: EconomyUiState, viewModel: EconomyViewModel) {
    val s = state.snapshot ?: return
    ArcadeCard(accent = Menta) {
        Text("Racha diaria", style = MaterialTheme.typography.titleMedium, color = Menta)
        Text(
            if (s.dailyClaimed) "Día ${s.streakDay} reclamado. Vuelve mañana para el día ${s.streakDay + 1}."
            else "Día ${s.nextStreakDay}: +${EconomyRules.streakReward(s.nextStreakDay)} monedas. Si un día no entras, vuelves al día 1 sin perder nada.",
            color = PapelSoft,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EconomyRules.STREAK_REWARDS.forEachIndexed { i, coins ->
                val day = i + 1
                val done = if (s.dailyClaimed) day <= s.streakDay else day < s.nextStreakDay
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("D$day", style = MaterialTheme.typography.labelMedium, color = if (done) Menta else PapelSoft)
                    Text("$coins", style = MaterialTheme.typography.labelMedium, color = if (done) Menta else PapelSoft)
                }
            }
        }
        ArcadeButton(
            text = if (s.dailyClaimed) "Reclamado" else "Reclamar +${EconomyRules.streakReward(s.nextStreakDay)}",
            onClick = viewModel::claimDaily,
            enabled = !s.dailyClaimed && !state.busy,
            color = Menta,
        )
    }
}

@Composable
private fun CosmeticsCard(kind: CosmeticKind, state: EconomyUiState, viewModel: EconomyViewModel) {
    val items = Cosmetics.all.filter { it.kind == kind }
    ArcadeCard(accent = if (kind == CosmeticKind.MODE) Naranja else Linea) {
        Text(kindTitle(kind), style = MaterialTheme.typography.titleMedium, color = Mango)
        if (kind == CosmeticKind.MODE) {
            Text(
                "Muy rápido: ${GameRules.FAST_QUESTION_TIME_MILLIS / 1000} segundos por pregunta en vez de ${GameRules.QUESTION_TIME_MILLIS / 1000}. " +
                    "Países: preguntas de ese país. Se eligen al preparar una partida en este teléfono.",
                color = PapelSoft,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items.forEach { c -> CosmeticRow(c, state, viewModel) }
    }
}

@Composable
private fun CosmeticRow(c: Cosmetic, state: EconomyUiState, viewModel: EconomyViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.bodyLarge)
            val detail = when {
                state.isEquipped(c) -> "Equipado"
                state.owns(c) && c.kind == CosmeticKind.MODE -> "Desbloqueado"
                state.owns(c) -> "Tuyo"
                // No se dice en qué nivel: es un premio sorpresa.
                c.unlockLevel != null -> "Se gana subiendo de nivel"
                else -> "🪙 ${c.price}"
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (state.isEquipped(c)) Menta else PapelSoft)
        }
        val modifier = Modifier.widthIn(max = 150.dp)
        when {
            state.owns(c) && c.kind == CosmeticKind.MODE -> Unit
            state.owns(c) -> ArcadeOutlinedButton(
                text = if (state.isEquipped(c)) "Quitar" else "Usar",
                onClick = { viewModel.toggleEquip(c) },
                enabled = !state.busy,
                color = Mango,
                modifier = modifier,
            )
            c.price != null -> ArcadeOutlinedButton(
                text = "Comprar",
                onClick = { viewModel.buyCosmetic(c) },
                enabled = !state.busy && (state.snapshot?.coins ?: 0) >= (c.price ?: 0),
                color = Naranja,
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
    CosmeticKind.MODE -> "Modos de juego"
}
