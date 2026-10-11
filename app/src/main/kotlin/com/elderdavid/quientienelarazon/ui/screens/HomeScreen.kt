package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elderdavid.quientienelarazon.ui.components.ArcadeBackground
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.theme.ArcadeCyan
import com.elderdavid.quientienelarazon.ui.theme.ArcadeMagenta
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOrange
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted

@Composable
fun HomeScreen(onStart: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Reverse),
        label = "titleScale",
    )
    ArcadeBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("¿QUIÉN", style = MaterialTheme.typography.displayLarge, color = ArcadeCyan)
            Text(
                "TIENE LA",
                style = MaterialTheme.typography.displayMedium,
                color = ArcadeOrange,
            )
            Text(
                "RAZÓN?",
                style = MaterialTheme.typography.displayLarge,
                color = ArcadeMagenta,
                modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Cultura general para 2 a 4 jugadores",
                style = MaterialTheme.typography.bodyLarge,
                color = ArcadeTextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            ArcadeButton(text = "Empezar", onClick = onStart, modifier = Modifier.widthIn(max = 360.dp))
        }
    }
}
