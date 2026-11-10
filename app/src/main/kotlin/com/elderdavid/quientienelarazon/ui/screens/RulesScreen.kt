package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.elderdavid.quientienelarazon.ads.AdBanner
import com.elderdavid.quientienelarazon.ads.AdsController
import com.elderdavid.quientienelarazon.core.game.GameLength
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.RoundType
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.theme.Mango
import com.elderdavid.quientienelarazon.ui.theme.Chicle
import com.elderdavid.quientienelarazon.ui.theme.Naranja
import com.elderdavid.quientienelarazon.ui.theme.PapelSoft
import com.elderdavid.quientienelarazon.ui.theme.Cielo

/** Las cifras salen de [GameRules] para que la pantalla nunca contradiga al motor. */
@Composable
fun RulesScreen(ads: AdsController, onBack: () -> Unit) {
    val seconds = GameRules.QUESTION_TIME_MILLIS / 1000
    val lengths = GameLength.entries.map { it.questions }
    ArcadeScreen(title = "Reglas", onBack = onBack, bottomBar = { AdBanner(ads) }) {
        Text(
            "${GameRules.MIN_LOCAL_PLAYERS} a ${GameRules.MAX_PLAYERS} jugadores en el teléfono (${GameRules.MIN_PLAYERS} a ${GameRules.MAX_PLAYERS} online) · " +
                "${lengths.dropLast(1).joinToString(", ")} o ${lengths.last()} preguntas en ${GameRules.ROUNDS} rondas · " +
                "$seconds segundos por pregunta",
            style = MaterialTheme.typography.bodyLarge,
            color = PapelSoft,
        )
        RoundCard(
            RoundType.KNOWLEDGE, Mango,
            "Opción múltiple. Acierto: +${GameRules.KNOWLEDGE_POINTS}. Error: 0.",
        )
        RoundCard(
            RoundType.SPEED, Chicle,
            "Preguntas rápidas, sobre todo verdadero o falso. Acierto: +${GameRules.SPEED_POINTS}, " +
                "más hasta +${GameRules.SPEED_BONUS_MAX} si respondes rápido. Error: 0. " +
                "La última es la pregunta robo: quien acierte más rápido le quita el " +
                "${GameRules.HEIST_PERCENT} % de sus puntos a cada rival. Si todos fallan, no hay robo.",
        )
        RoundCard(
            RoundType.STRATEGY, Naranja,
            "Antes de cada pregunta apuestas ${GameRules.ALLOWED_BETS.joinToString(" o ")} puntos " +
                "(solo lo que tengas). Acierto: ganas el doble de lo apostado. Error: lo pierdes.",
        )
        RoundCard(
            RoundType.FINAL, Cielo,
            "Preguntas más difíciles. Acierto: +${GameRules.FINAL_POINTS} más bonificación por rapidez. Error: 0.",
        )
        ArcadeCard(accent = Mango) {
            Text("Desempate", style = MaterialTheme.typography.titleLarge, color = Mango)
            Text(
                "Si hay empate al final, los empatados responden una pregunta extra sin apuestas. " +
                    "Gana la respuesta correcta más rápida; si nadie acierta, se repite con otra pregunta.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ArcadeCard(accent = Naranja) {
            Text("Modo niños", style = MaterialTheme.typography.titleLarge, color = Naranja)
            Text(
                "Para jugar en familia en el modo local: preguntas más fáciles pensadas para niños, " +
                    "${GameRules.KIDS_QUESTION_TIME_MILLIS / 1000} segundos por pregunta y, en la ronda de estrategia, " +
                    "fallar no resta lo apostado y no hay pregunta robo.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ArcadeCard(accent = Mango) {
            Text("Solitario y modos extra", style = MaterialTheme.typography.titleLarge, color = Mango)
            Text(
                "Solo: si juegas sin nadie más, las preguntas son de dificultad media y difícil. " +
                    "Con monedas se desbloquean «Muy rápido» (${GameRules.FAST_QUESTION_TIME_MILLIS / 1000} segundos por pregunta) " +
                    "y el modo países, con preguntas de un país.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ArcadeCard(accent = Chicle) {
            Text("Preguntas de opinión", style = MaterialTheme.typography.titleLarge, color = Chicle)
            Text(
                "No tienen respuesta correcta. Cada jugador vota en secreto y quienes eligen la opción " +
                    "más votada ganan +${GameRules.MAJORITY_VOTE_POINTS}. Si hay empate, todas las opciones " +
                    "empatadas ganan. Con menos de dos votos válidos no se reparten puntos.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RoundCard(round: RoundType, accent: Color, description: String) {
    ArcadeCard(accent = accent) {
        Text(
            "Ronda ${round.number}: ${round.title}",
            style = MaterialTheme.typography.titleLarge,
            color = accent,
        )
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}
