package com.elderdavid.quientienelarazon.ui.local

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elderdavid.quientienelarazon.ads.AdsController
import com.elderdavid.quientienelarazon.core.local.LocalStage
import com.elderdavid.quientienelarazon.local.GameFeedback
import com.elderdavid.quientienelarazon.local.LocalGameViewModel
import com.elderdavid.quientienelarazon.settings.AppSettings
import com.elderdavid.quientienelarazon.settings.SettingsRepository

/** Conecta la partida local con su ViewModel, los ajustes y los sonidos. */
@Composable
fun LocalGameRoute(
    viewModel: LocalGameViewModel,
    settingsRepository: SettingsRepository,
    ads: AdsController,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val context = LocalContext.current
    val feedback = remember { GameFeedback(context.applicationContext) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }

    val match = state.match
    val bank = viewModel.questionBank()
    if (match == null || bank == null) {
        LocalSetupScreen(
            state = state,
            onNameChange = viewModel::setName,
            onAddPlayer = viewModel::addPlayer,
            onRemovePlayer = viewModel::removePlayer,
            onModeChange = viewModel::setMode,
            onLengthChange = viewModel::setLength,
            onStart = viewModel::start,
            onBack = onExit,
        )
    } else {
        // Los botones de la pantalla final pasan antes por el posible anuncio; los demás, no.
        val activity = LocalActivity.current
        val finished = match.stage is LocalStage.Finished
        val gameKey = "local:${System.identityHashCode(match.state)}"
        fun afterGame(action: () -> Unit): () -> Unit =
            if (finished && activity != null) ({ ads.afterGame(activity, gameKey, action) }) else action
        LocalGameScreen(
            match = match,
            bank = bank,
            rouletteCategories = remember(match.state.config.mode) { viewModel.rouletteCategories() },
            reducedMotion = settings.reducedMotion,
            actions = LocalGameActions(
                rouletteDone = viewModel::rouletteDone,
                ready = viewModel::ready,
                bet = viewModel::bet,
                answer = viewModel::answer,
                next = viewModel::next,
                rematch = afterGame(viewModel::rematch),
                changePlayers = afterGame(viewModel::quit),
                exitToMenu = afterGame { viewModel.quit(); onExit() },
                now = viewModel::now,
                feedback = { event -> feedback.play(event, settings) },
            ),
        )
    }
}
