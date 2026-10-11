package com.elderdavid.quientienelarazon.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elderdavid.quientienelarazon.core.game.GameEngine
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.local.LocalMatch
import com.elderdavid.quientienelarazon.core.local.LocalSetup
import com.elderdavid.quientienelarazon.core.local.LocalStage
import com.elderdavid.quientienelarazon.core.local.NameIssue
import com.elderdavid.quientienelarazon.core.questions.Category
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import com.elderdavid.quientienelarazon.core.questions.Region
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla de partida local: configuración y, una vez iniciada, la partida. */
data class LocalUiState(
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val names: List<String> = listOf("", ""),
    val mode: ContentMode = ContentMode.General,
    val regions: List<Region> = emptyList(),
    val match: LocalMatch? = null,
) {
    val nameIssues: Map<Int, NameIssue> get() = LocalSetup.issues(names)
    val canStart: Boolean get() = !loading && !loadError && LocalSetup.canStart(names)
    val canAddPlayer: Boolean get() = names.size < GameRules.MAX_PLAYERS
    val canRemovePlayer: Boolean get() = names.size > GameRules.MIN_PLAYERS
}

/**
 * Partida local en un solo teléfono. Toda la lógica de reglas vive en [LocalMatch]; aquí solo
 * se carga el banco, se gestiona la configuración y se programa el fin del tiempo de cada turno.
 *
 * [clock] debe ser monótono (en la app, `SystemClock.elapsedRealtime`) para que cambiar la hora
 * del teléfono no altere los tiempos.
 */
class LocalGameViewModel(
    private val loadBank: suspend () -> QuestionBank,
    private val clock: () -> Long,
    private val newSeed: () -> Long,
) : ViewModel() {

    private val _state = MutableStateFlow(LocalUiState())
    val state: StateFlow<LocalUiState> = _state.asStateFlow()

    private var bank: QuestionBank? = null
    private var engine: GameEngine? = null
    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val loaded = loadBank()
                bank = loaded
                engine = GameEngine(loaded)
                _state.update { it.copy(loading = false, regions = loaded.regions) }
            } catch (e: Exception) {
                // El banco va dentro de la app; si falla es un error de empaquetado, no de red.
                _state.update { it.copy(loading = false, loadError = true) }
            }
        }
    }

    fun now(): Long = clock()

    fun questionBank(): QuestionBank? = bank

    /** Categorías que puede mostrar la ruleta en el modo actual. */
    fun rouletteCategories(): List<Category> {
        val b = bank ?: return emptyList()
        val mode = _state.value.match?.state?.config?.mode ?: _state.value.mode
        return b.pool(mode).map { it.categoryId }.distinct().sorted().map(b::category)
    }

    // --- Configuración ---

    fun setName(index: Int, name: String) = _state.update { s ->
        if (index !in s.names.indices) s else s.copy(names = s.names.toMutableList().also { it[index] = name.take(LocalSetup.MAX_NAME_LENGTH + 4) })
    }

    fun addPlayer() = _state.update { if (it.canAddPlayer) it.copy(names = it.names + "") else it }

    fun removePlayer(index: Int) = _state.update {
        if (it.canRemovePlayer && index in it.names.indices) it.copy(names = it.names.filterIndexed { i, _ -> i != index }) else it
    }

    fun setMode(mode: ContentMode) = _state.update { it.copy(mode = mode) }

    fun start() {
        val s = _state.value
        val e = engine ?: return
        if (!s.canStart) return
        setMatch(LocalMatch.start(e, LocalSetup.players(s.names), s.mode, newSeed()))
    }

    // --- Partida ---

    fun rouletteDone() = act { it.rouletteDone() }
    fun ready() = act { it.ready(clock()) }
    fun bet(amount: Int) = act { it.bet(amount, clock()) }
    fun answer(choice: Int) = act { it.answer(choice, clock()) }
    fun next() = act { it.next() }
    fun rematch() = act { it.rematch(newSeed()) }

    /** Abandona la partida y vuelve a la configuración con los mismos nombres. */
    fun quit() = setMatch(null)

    private inline fun act(block: (LocalMatch) -> LocalMatch) {
        val current = _state.value.match ?: return
        val updated = block(current)
        if (updated !== current) setMatch(updated)
    }

    private fun setMatch(match: LocalMatch?) {
        _state.update { it.copy(match = match) }
        scheduleTimeout(match)
    }

    /** Programa el fin del tiempo del turno actual. El motor vuelve a comprobarlo con el reloj. */
    private fun scheduleTimeout(match: LocalMatch?) {
        timerJob?.cancel()
        val answering = match?.stage as? LocalStage.Answering ?: return
        timerJob = viewModelScope.launch {
            while (true) {
                // Si el retardo termina un poco antes que el reloj, se vuelve a esperar lo que falte.
                delay((answering.deadline - clock()).coerceAtLeast(1))
                val current = _state.value.match ?: return@launch
                if (current.stage != answering) return@launch
                val expired = current.expire(clock())
                if (expired !== current) {
                    setMatch(expired)
                    return@launch
                }
            }
        }
    }

    companion object {
        fun factory(loadBank: suspend () -> QuestionBank, clock: () -> Long, newSeed: () -> Long): ViewModelProvider.Factory =
            viewModelFactory { initializer { LocalGameViewModel(loadBank, clock, newSeed) } }
    }
}
