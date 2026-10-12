package com.elderdavid.quientienelarazon.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.elderdavid.quientienelarazon.ads.AdBanner
import com.elderdavid.quientienelarazon.ads.AdsController
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.RoundType
import com.elderdavid.quientienelarazon.ui.components.ArcadeCard
import com.elderdavid.quientienelarazon.ui.components.ArcadeScreen
import com.elderdavid.quientienelarazon.ui.theme.ArcadeCyan
import com.elderdavid.quientienelarazon.ui.theme.ArcadeMagenta
import com.elderdavid.quientienelarazon.ui.theme.ArcadeOrange
import com.elderdavid.quientienelarazon.ui.theme.ArcadeTextMuted
import com.elderdavid.quientienelarazon.ui.theme.ArcadeViolet

/** Las cifras salen de [GameRules] para que la pantalla nunca contradiga al motor. */
@Composable
fun RulesScreen(ads: AdsController, onBack: () -> Unit) {
    val seconds = GameRules.QUESTION_TIME_MILLIS / 1000
    ArcadeScreen(title = "Reglas", onBack = onBack, bottomBar = { AdBanner(ads) }) {
        Text(
            "${GameRules.MIN_PLAYERS} a ${GameRules.MAX_PLAYERS} jugadores · " +
                "${GameRules.TOTAL_QUESTIONS} preguntas en ${GameRules.ROUNDS} rondas · " +
                "$seconds segundos por pregunta",
            style = MaterialTheme.typography.bodyLarge,
            color = ArcadeTextMuted,
        )
        RoundCard(
            RoundType.KNOWLEDGE, ArcadeCyan,
            "Opción múltiple. Acierto: +${GameRules.KNOWLEDGE_POINTS}. Error: 0.",
        )
        RoundCard(
            RoundType.SPEED, ArcadeMagenta,
            "Preguntas rápidas, sobre todo verdadero o falso. Acierto: +${GameRules.SPEED_POINTS}, " +
                "más hasta +${GameRules.SPEED_BONUS_MAX} si respondes rápido. Error: 0.",
        )
        RoundCard(
            RoundType.STRATEGY, ArcadeOrange,
            "Antes de cada pregunta apuestas ${GameRules.ALLOWED_BETS.joinToString(" o ")} puntos " +
                "(solo lo que tengas). Acierto: ganas lo apostado. Error: lo pierdes.",
        )
        RoundCard(
            RoundType.FINAL, ArcadeViolet,
            "Preguntas más difíciles. Acierto: +${GameRules.FINAL_POINTS} más bonificación por rapidez. Error: 0.",
        )
        ArcadeCard(accent = ArcadeCyan) {
            Text("Desempate", style = MaterialTheme.typography.titleLarge, color = ArcadeCyan)
            Text(
                "Si hay empate al final, los empatados responden una pregunta extra sin apuestas. " +
                    "Gana la respuesta correcta más rápida; si nadie acierta, se repite con otra pregunta.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ArcadeCard(accent = ArcadeMagenta) {
            Text("Preguntas de opinión", style = MaterialTheme.typography.titleLarge, color = ArcadeMagenta)
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
            "Ronda ${round.number} · ${round.title}",
            style = MaterialTheme.typography.titleLarge,
            color = accent,
        )
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}
