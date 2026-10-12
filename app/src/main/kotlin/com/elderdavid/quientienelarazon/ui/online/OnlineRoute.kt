package com.elderdavid.quientienelarazon.ui.online

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elderdavid.quientienelarazon.ads.AdsController
import com.elderdavid.quientienelarazon.local.GameFeedback
import com.elderdavid.quientienelarazon.online.OnlineGameViewModel
import com.elderdavid.quientienelarazon.settings.AppSettings
import com.elderdavid.quientienelarazon.settings.SettingsRepository

/** Conecta las pantallas online con su ViewModel, los ajustes y los sonidos. */
@Composable
fun OnlineRoute(
    viewModel: OnlineGameViewModel,
    settingsRepository: SettingsRepository,
    ads: AdsController,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val context = LocalContext.current
    val feedback = remember { GameFeedback(context.applicationContext) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }

    // Solo al salir de una partida terminada (no cancelada) puede aparecer un anuncio.
    val activity = LocalActivity.current
    val room = state.view?.room
    val finishedRoomId = room?.takeIf { it.status == "finished" }?.id
    val leave: () -> Unit = if (finishedRoomId != null && activity != null) {
        ({ ads.afterGame(activity, "online:$finishedRoomId", viewModel::leave) })
    } else {
        viewModel::leave
    }

    OnlineScreen(
        state = state,
        bank = viewModel.questionBank(),
        actions = OnlineActions(
            setMode = viewModel::setMode,
            setCode = viewModel::setCode,
            createPrivate = viewModel::createPrivate,
            joinByCode = viewModel::joinByCode,
            findPublic = viewModel::findPublic,
            start = viewModel::start,
            bet = viewModel::bet,
            answer = viewModel::answer,
            leave = leave,
            dismissError = viewModel::dismissError,
            exit = onExit,
            now = viewModel::now,
            feedback = { event -> feedback.play(event, settings) },
        ),
    )
}
