package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.components.ComingSoonButton
import com.elderdavid.quientienelarazon.ui.theme.ArcadeCyan
import com.elderdavid.quientienelarazon.ui.theme.ArcadeMagenta
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOrange
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted

@Composable
fun MainMenuScreen(onLocalGame: () -> Unit, onProfile: () -> Unit, onRules: () -> Unit, onSettings: () -> Unit) {
    ArcadeScreen(title = "Menú principal") {
        Text(
            "Elige cómo quieres jugar.",
            style = MaterialTheme.typography.bodyLarge,
            color = ArcadeTextMuted,
        )
        ArcadeButton(text = "Jugar en este teléfono", onClick = onLocalGame, color = ArcadeMagenta)
        // Fase 6: online.
        ComingSoonButton("Jugar online")
        ArcadeOutlinedButton(text = "Perfil y estadísticas", onClick = onProfile, color = ArcadeCyan)
        ArcadeButton(text = "Reglas del juego", onClick = onRules, color = ArcadeOrange)
        ArcadeOutlinedButton(text = "Ajustes", onClick = onSettings)
    }
}
