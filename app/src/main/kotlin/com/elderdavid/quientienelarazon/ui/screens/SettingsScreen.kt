package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elderdavid.quientienelarazon.settings.SettingsViewModel
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.theme.ArcadeCyan
import com.elderdavid.quientienelarazon.ui.theme.ArcadeNight
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    ArcadeScreen(title = "Ajustes", onBack = onBack) {
        ArcadeCard {
            SettingRow(
                title = "Sonido",
                description = "Efectos de la ruleta, aciertos y fallos.",
                checked = settings.soundEnabled,
                onCheckedChange = viewModel::setSoundEnabled,
            )
            SettingRow(
                title = "Vibración",
                description = "Vibración breve al responder y al terminar el tiempo.",
                checked = settings.vibrationEnabled,
                onCheckedChange = viewModel::setVibrationEnabled,
            )
            SettingRow(
                title = "Reducir animaciones",
                description = "Ruleta y transiciones más cortas.",
                checked = settings.reducedMotion,
                onCheckedChange = viewModel::setReducedMotion,
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Toda la fila es pulsable y se anuncia como interruptor para lectores de pantalla.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = ArcadeTextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedThumbColor = ArcadeNight, checkedTrackColor = ArcadeCyan),
        )
    }
}
