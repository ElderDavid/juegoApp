package com.elderdavid.quientienelarazon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            QtrTheme {
                QtrNavHost(container = container)
            }
        }
    }
}
