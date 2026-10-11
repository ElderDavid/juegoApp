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
        setContent {
            QtrTheme {
                QtrNavHost(container = container)
            }
        }
    }
}
