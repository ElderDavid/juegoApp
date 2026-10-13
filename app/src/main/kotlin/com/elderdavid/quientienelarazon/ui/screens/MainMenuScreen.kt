package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.elderdavid.quientienelarazon.ads.AdsController
import com.elderdavid.quientienelarazon.ads.SmallAdBanner
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.theme.ArcadeCyan
import com.elderdavid.quientienelarazon.ui.theme.ArcadeMagenta
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOrange
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted

@Composable
fun MainMenuScreen(
    ads: AdsController,
    onLocalGame: () -> Unit,
    onOnline: () -> Unit,
    onProfile: () -> Unit,
    onShop: () -> Unit,
    onStats: () -> Unit,
    onRules: () -> Unit,
    onSettings: () -> Unit,
) {
    ArcadeScreen(title = "Menú principal") {
        Text(
            "Elige cómo quieres jugar.",
            style = MaterialTheme.typography.bodyLarge,
            color = ArcadeTextMuted,
        )
        ArcadeButton(text = "Jugar en este teléfono", onClick = onLocalGame, color = ArcadeMagenta)
        ArcadeButton(text = "Jugar online", onClick = onOnline, color = ArcadeCyan)
        ArcadeOutlinedButton(text = "Perfil y cuenta", onClick = onProfile, color = ArcadeCyan)
        ArcadeOutlinedButton(text = "Tienda y premios", onClick = onShop, color = ArcadeMagenta)
        ArcadeOutlinedButton(text = "Estadísticas", onClick = onStats, color = ArcadeOrange)
        ArcadeButton(text = "Reglas del juego", onClick = onRules, color = ArcadeOrange)
        ArcadeOutlinedButton(text = "Ajustes", onClick = onSettings)
        // Banner pequeño al final del menú, nunca entre los botones.
        SmallAdBanner(ads)
    }
}
