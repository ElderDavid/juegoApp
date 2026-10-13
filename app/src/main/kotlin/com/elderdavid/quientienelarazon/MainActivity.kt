package com.elderdavid.quientienelarazon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elderdavid.quientienelarazon.core.economy.CosmeticKind
import com.elderdavid.quientienelarazon.economy.EquippedCosmetics
import com.elderdavid.quientienelarazon.ui.navigation.QtrNavHost
import com.elderdavid.quientienelarazon.ui.theme.QtrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as QtrApplication).container
        // Pide el consentimiento de anuncios si la ley lo exige. El juego no espera a que termine:
        // los anuncios simplemente no se cargan hasta que se pueda.
        if (savedInstanceState == null) container.adsConsent?.gather(this) {}
        setContent {
            val equipped by container.economyCache.equipped.collectAsStateWithLifecycle(initialValue = EquippedCosmetics())
            QtrTheme(themeId = equipped[CosmeticKind.THEME]) {
                QtrNavHost(container = container)
            }
        }
    }
}
