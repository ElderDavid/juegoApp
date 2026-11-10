package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.runtime.getValue
import androidx.activity.compose.LocalActivity
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elderdavid.quientienelarazon.ads.AdsConsent
import com.elderdavid.quientienelarazon.economy.EconomyViewModel
import com.elderdavid.quientienelarazon.settings.SettingsViewModel
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeOutlinedButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.theme.Mango
import com.elderdavid.quientienelarazon.ui.theme.Chicle
import com.elderdavid.quientienelarazon.ui.theme.Tinta
import com.elderdavid.quientienelarazon.ui.theme.Naranja
import com.elderdavid.quientienelarazon.ui.theme.PapelSoft

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    economy: EconomyViewModel,
    adsConsent: AdsConsent?,
    onShop: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val shop by economy.state.collectAsStateWithLifecycle()
    // La opción solo aparece donde la ley obliga a ofrecerla (la decide el SDK de consentimiento).
    val privacyRequired = adsConsent?.privacyOptionsRequired?.collectAsStateWithLifecycle()?.value == true
    val activity = LocalActivity.current
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
        if (shop.billingAvailable) {
            ArcadeCard {
                Text("Anuncios y compras", style = MaterialTheme.typography.titleMedium)
                shop.message?.let { Text(it, color = Naranja, style = MaterialTheme.typography.bodyMedium) }
                if (shop.removeAds) {
                    Text("Compraste «Quitar anuncios». ¡Gracias!", style = MaterialTheme.typography.bodyMedium, color = PapelSoft)
                } else {
                    Text("Un solo pago y no vuelves a ver anuncios.", style = MaterialTheme.typography.bodyMedium, color = PapelSoft)
                    ArcadeOutlinedButton(text = "Quitar anuncios", onClick = onShop, color = Chicle)
                }
                ArcadeOutlinedButton(
                    text = if (shop.busy) "Restaurando…" else "Restaurar compras",
                    onClick = economy::restorePurchases,
                    enabled = !shop.busy,
                    color = Mango,
                )
            }
        }
        if (privacyRequired && activity != null) {
            ArcadeCard {
                Text("Privacidad de anuncios", style = MaterialTheme.typography.titleMedium)
                Text("Revisa o cambia tu consentimiento para los anuncios.", style = MaterialTheme.typography.bodyMedium, color = PapelSoft)
                ArcadeOutlinedButton(text = "Opciones de privacidad", onClick = { adsConsent.showPrivacyOptions(activity) }, color = Mango)
            }
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
            Text(description, style = MaterialTheme.typography.bodyMedium, color = PapelSoft)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedThumbColor = Tinta, checkedTrackColor = Mango),
        )
    }
}
