package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elderdavid.quientienelarazon.ui.components.ArcadeBackground
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.sticker
import com.elderdavid.quientienelarazon.ui.theme.Mango
import com.elderdavid.quientienelarazon.ui.theme.Papel
import com.elderdavid.quientienelarazon.ui.theme.Tinta
import com.elderdavid.quientienelarazon.ui.theme.inkShadow

@Composable
fun HomeScreen(onStart: () -> Unit) {
    ArcadeBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.widthIn(max = 480.dp)) {
                TitleSign()
                Spacer(Modifier.height(36.dp))
                Text(
                    "Cultura general para 2 a 4 jugadores. Responde, apuesta y demuestra quién sabe más.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Papel,
                )
            }
            ArcadeButton(text = "Empezar", onClick = onStart, modifier = Modifier.widthIn(max = 480.dp))
        }
    }
}

/** El nombre del juego como un letrero pintado: tres renglones inclinados y una franja mango en medio. */
@Composable
private fun TitleSign() {
    val big = MaterialTheme.typography.displayLarge.copy(shadow = inkShadow(10f))
    Column(
        Modifier
            .graphicsLayer { rotationZ = -5f }
            .semantics(mergeDescendants = true) { heading(); contentDescription = "¿Quién tiene la razón?" },
    ) {
        Text("¿QUIÉN", style = big, color = Papel, modifier = Modifier.clearAndSetSemantics { })
        Box(Modifier.padding(vertical = 6.dp).sticker(Mango, MaterialTheme.shapes.extraSmall, depth = 6.dp)) {
            Text(
                "TIENE LA",
                style = MaterialTheme.typography.displayMedium,
                color = Tinta,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp).clearAndSetSemantics { },
            )
        }
        Text("RAZÓN?", style = big, color = Papel, modifier = Modifier.clearAndSetSemantics { })
    }
}
