package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.elderdavid.quientienelarazon.core.ads.AgeGate
import com.elderdavid.quientienelarazon.core.ads.AgeGroup
import com.elderdavid.quientienelarazon.ui.components.ArcadeButton
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.theme.ArcadeCyan
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted
import java.util.Calendar

/**
 * Pregunta neutral de edad, una sola vez por teléfono. No sugiere ninguna respuesta ni dice qué
 * cambia según la edad, como pide la política de Familias de Google Play.
 */
@Composable
fun AgeGateScreen(onAnswered: (AgeGroup) -> Unit) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    var text by rememberSaveable { mutableStateOf("") }
    val group = AgeGate.parse(text, currentYear)
    ArcadeScreen(title = "¡Bienvenido!") {
        ArcadeCard(accent = ArcadeCyan) {
            Text("¿En qué año naciste?", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(4) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Año de nacimiento") },
                singleLine = true,
                isError = text.length == 4 && group == null,
                supportingText = if (text.length == 4 && group == null) { { Text("Escribe un año válido") } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            Text("Solo se pregunta una vez en este teléfono.", color = ArcadeTextMuted)
        }
        ArcadeButton(text = "Continuar", onClick = { group?.let(onAnswered) }, enabled = group != null)
    }
}
