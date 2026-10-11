package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.components.ComingSoonButton
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOrange
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted

@Composable
fun MainMenuScreen(onRules: () -> Unit, onSettings: () -> Unit) {
    ArcadeScreen(title = "Menú principal") {
        Text(
            "Elige cómo quieres jugar.",
            style = MaterialTheme.typography.bodyLarge,
            color = ArcadeTextMuted,
        )
        // Fase 4: partida local; Fase 6: online; Fase 5: perfil.
        ComingSoonButton("Jugar en este teléfono")
        ComingSoonButton("Jugar online")
        ComingSoonButton("Perfil y estadísticas")
        ArcadeButton(text = "Reglas del juego", onClick = onRules, color = ArcadeOrange)
        ArcadeOutlinedButton(text = "Ajustes", onClick = onSettings)
    }
}
