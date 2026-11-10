package com.elderdavid.quientienelarazon.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.elderdavid.quientienelarazon.ads.NoAds
import com.elderdavid.quientienelarazon.core.game.GameEngine
import com.elderdavid.quientienelarazon.core.game.Player
import com.elderdavid.quientienelarazon.core.game.PlayerId
import com.elderdavid.quientienelarazon.core.local.LocalMatch
import com.elderdavid.quientienelarazon.core.local.LocalStage
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import com.elderdavid.quientienelarazon.core.questions.QuestionBankLoader
import com.elderdavid.quientienelarazon.local.LocalUiState
import com.elderdavid.quientienelarazon.ui.local.LocalGameActions
import com.elderdavid.quientienelarazon.ui.local.LocalGameScreen
import com.elderdavid.quientienelarazon.ui.local.LocalSetupScreen
import com.elderdavid.quientienelarazon.ui.screens.HomeScreen
import com.elderdavid.quientienelarazon.ui.screens.MainMenuScreen
import com.elderdavid.quientienelarazon.ui.screens.RulesScreen
import com.elderdavid.quientienelarazon.ui.theme.QtrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas de las pantallas principales para revisar el diseño sin emulador.
 * Solo se ejecuta con `./gradlew :app:testDebugUnitTest -Pcapturas` (ver el flujo «Capturas»);
 * las imágenes quedan en app/build/capturas/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class CapturasTest {
    @get:Rule val compose = createComposeRule()

    private val bank = QuestionBankLoader.fromResources()
    private val engine = GameEngine(bank)
    private val out = File(System.getProperty("capturas.dir") ?: "build/capturas").apply { mkdirs() }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { QtrTheme { content() } }
        compose.mainClock.advanceTimeBy(500)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val players = listOf("Ana", "Luis", "Sofía").mapIndexed { i, n -> Player(PlayerId("p$i"), n) }

    /** Juega una partida completa y devuelve el primer momento de cada etapa. */
    private fun stages(): Map<String, Pair<LocalMatch, Long>> {
        var m = LocalMatch.start(engine, players, ContentMode.General, seed = 7)
        var now = 10_000L
        val seen = linkedMapOf<String, Pair<LocalMatch, Long>>()
        repeat(400) {
            val s = m.stage
            val key = s::class.simpleName!!
            if (key !in seen) seen[key] = m to now
            m = when (s) {
                is LocalStage.Roulette -> m.rouletteDone()
                is LocalStage.Handoff -> m.ready(now)
                is LocalStage.Betting -> m.bet(s.allowedBets.firstOrNull() ?: 0, now)
                is LocalStage.Answering -> {
                    // Ana acierta siempre; los demás eligen la primera opción.
                    val q = bank.question(s.questionId)
                    val pick = if (s.playerId == players[0].id) q.correctIndex ?: 0 else 0
                    m.answer(pick, now + 3_000)
                }
                is LocalStage.Reveal, is LocalStage.RoundSummary, is LocalStage.TiebreakReveal -> m.next()
                is LocalStage.Finished -> return seen
            }
            now += 5_000
        }
        return seen
    }

    private fun actions(now: Long) = LocalGameActions(
        rouletteDone = {}, ready = {}, bet = {}, answer = {}, next = {}, rematch = {}, changePlayers = {}, exitToMenu = {},
        now = { now }, feedback = {},
    )

    @Test fun inicio() = shot("01-inicio") { HomeScreen(onStart = {}) }

    @Test fun menu() = shot("02-menu") {
        MainMenuScreen(NoAds, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun preparar() = shot("03-preparar") {
        LocalSetupScreen(
            state = LocalUiState(loading = false, names = listOf("Ana", "Luis", "Sofía"), regions = bank.regions, countries = bank.countries),
            onNameChange = { _, _ -> }, onAddPlayer = {}, onRemovePlayer = {}, onModeChange = {}, onLengthChange = {},
            onFastChange = {}, onSoloChange = {}, onStart = {}, onBack = {},
        )
    }

    @Test fun reglas() = shot("09-reglas") { RulesScreen(NoAds, onBack = {}) }

    private fun game(name: String, stage: String, timeOffset: Long = 0) {
        val (match, now) = stages().getValue(stage)
        val at = when (val s = match.stage) {
            is LocalStage.Answering -> s.startedAt + 4_000
            else -> now + timeOffset
        }
        shot(name) {
            LocalGameScreen(match, bank, bank.categories, reducedMotion = true, actions = actions(at))
        }
    }

    @Test fun ruleta() = game("04-ruleta", "Roulette")
    @Test fun turno() = game("05-pasa-el-telefono", "Handoff")
    @Test fun pregunta() = game("06-pregunta", "Answering")
    @Test fun respuesta() = game("07-respuesta", "Reveal")
    @Test fun apuesta() = game("08-apuesta", "Betting")
    @Test fun ronda() = game("10-fin-de-ronda", "RoundSummary")
    @Test fun podio() = game("11-podio", "Finished")
}
