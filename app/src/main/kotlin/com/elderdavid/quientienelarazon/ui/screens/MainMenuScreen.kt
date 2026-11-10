package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.elderdavid.quientienelarazon.ads.AdsController
import com.elderdavid.quientienelarazon.ads.SmallAdBanner
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.components.MenuList
import com.elderdavid.quientienelarazon.ui.components.MenuTile
import com.elderdavid.quientienelarazon.ui.theme.Mango
import com.elderdavid.quientienelarazon.ui.theme.Menta
import com.elderdavid.quientienelarazon.ui.theme.PapelSoft

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
    ArcadeScreen(title = "¿A qué jugamos?") {
        Text(
            "Elige cómo quieres jugar.",
            style = MaterialTheme.typography.bodyLarge,
            color = PapelSoft,
        )
        MenuTile(title = "En este teléfono", hint = "De 1 a 4 jugadores, pasándose el celular", color = Mango, onClick = onLocalGame)
        MenuTile(title = "Online", hint = "Crea una sala o únete con un código", color = Menta, onClick = onOnline)
        MenuList(
            listOf(
                "Perfil y cuenta" to onProfile,
                "Tienda y premios" to onShop,
                "Estadísticas" to onStats,
                "Reglas del juego" to onRules,
                "Ajustes" to onSettings,
            ),
        )
        // Banner pequeño al final del menú, nunca entre los botones.
        SmallAdBanner(ads)
    }
}
