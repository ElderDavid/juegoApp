package com.elderdavid.quientienelarazon.ui.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elderdavid.quientienelarazon.local.GameFeedback
import com.elderdavid.quientienelarazon.local.LocalGameViewModel
import com.elderdavid.quientienelarazon.settings.AppSettings
import com.elderdavid.quientienelarazon.settings.SettingsRepository

/** Conecta la partida local con su ViewModel, los ajustes y los sonidos. */
@Composable
fun LocalGameRoute(viewModel: LocalGameViewModel, settingsRepository: SettingsRepository, onExit: () -> Unit) {
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
            onStart = viewModel::start,
            onBack = onExit,
        )
    } else {
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
                rematch = viewModel::rematch,
                changePlayers = viewModel::quit,
                exitToMenu = { viewModel.quit(); onExit() },
                now = viewModel::now,
                feedback = { event -> feedback.play(event, settings) },
            ),
        )
    }
}
