package com.elderdavid.quientienelarazon.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elderdavid.quientienelarazon.core.economy.EconomyRules
import com.elderdavid.quientienelarazon.core.economy.LocalGameReport
import com.elderdavid.quientienelarazon.core.economy.PaidModes
import com.elderdavid.quientienelarazon.core.game.GameEngine
import com.elderdavid.quientienelarazon.core.game.GameLength
import com.elderdavid.quientienelarazon.core.game.GameRules
import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.local.LocalMatch
import com.elderdavid.quientienelarazon.core.local.LocalSetup
import com.elderdavid.quientienelarazon.core.local.LocalStage
import com.elderdavid.quientienelarazon.core.local.NameIssue
import com.elderdavid.quientienelarazon.core.local.Wildcard
import com.elderdavid.quientienelarazon.core.questions.Category
import com.elderdavid.quientienelarazon.core.questions.ContentMode
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import com.elderdavid.quientienelarazon.core.questions.Region
import com.elderdavid.quientienelarazon.economy.EconomyRepository
import com.elderdavid.quientienelarazon.economy.LocalGameReward
import com.elderdavid.quientienelarazon.economy.SpendResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Estado de la pantalla de partida local: configuración y, una vez iniciada, la partida. */
data class LocalUiState(
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val names: List<String> = listOf("", ""),
    val mode: ContentMode = ContentMode.General,
    val length: GameLength = GameLength.DEFAULT,
    val regions: List<Region> = emptyList(),
    /** Países del modo países (se compran con monedas). */
    val countries: List<Region> = emptyList(),
    /** Modo «muy rápido» elegido (solo cuenta si se compró). */
    val fast: Boolean = false,
    /** Modos de juego comprados por la cuenta del teléfono (ids de [Cosmetics]). */
    val ownedModes: Set<String> = emptySet(),
    val match: LocalMatch? = null,
    /** Identifica la partida en curso ante el servidor (paquete de partida, XP). Cambia en cada revancha. */
    val gameKey: String? = null,
    /** Monedas del dueño del teléfono; null sin cuenta o sin conexión. */
    val coins: Int? = null,
    val buyingPack: Boolean = false,
    /** Lo que ganó la cuenta del teléfono con la partida terminada. */
    val reward: LocalGameReward? = null,
    val notice: String? = null,
) {
    val nameIssues: Map<Int, NameIssue> get() = LocalSetup.issues(names)
    val canStart: Boolean get() = !loading && !loadError && LocalSetup.canStart(names) && modeUnlocked(mode)
    val canAddPlayer: Boolean get() = names.size < GameRules.MAX_PLAYERS
    val canRemovePlayer: Boolean get() = names.size > GameRules.MIN_LOCAL_PLAYERS

    /** Modo solitario: un solo jugador. */
    val solo: Boolean get() = names.size == 1

    val ownsFast: Boolean get() = PaidModes.FAST in ownedModes

    /** El modo «muy rápido» no se aplica al modo niños, que tiene más tiempo. */
    val fastActive: Boolean get() = fast && ownsFast && mode != ContentMode.Kids

    /** Los países se compran; el resto de modos son gratis. */
    fun modeUnlocked(mode: ContentMode): Boolean =
        mode !is ContentMode.Country || PaidModes.countryItem(mode.countryId) in ownedModes
}

/**
 * Partida local en un solo teléfono. Toda la lógica de reglas vive en [LocalMatch]; aquí solo
 * se carga el banco, se gestiona la configuración y se programa el fin del tiempo de cada turno.
 *
 * [clock] debe ser monótono (en la app, `SystemClock.elapsedRealtime`) para que cambiar la hora
 * del teléfono no altere los tiempos. [recordFinished] guarda cada partida terminada en el
 * historial del teléfono, una sola vez por partida (también tras una revancha).
 */
class LocalGameViewModel(
    private val loadBank: suspend () -> QuestionBank,
    private val clock: () -> Long,
    private val newSeed: () -> Long,
    private val recordFinished: suspend (GameState) -> Unit = {},
    /** Monedas y XP de la cuenta del teléfono. null = sin servidor: solo los comodines gratis. */
    private val economy: EconomyRepository? = null,
    private val newGameKey: () -> String = { UUID.randomUUID().toString() },
    /** Modos comprados guardados en el teléfono, para jugarlos sin conexión. */
    private val cachedOwnedModes: Flow<Set<String>> = emptyFlow(),
) : ViewModel() {

    private val _state = MutableStateFlow(LocalUiState())
    val state: StateFlow<LocalUiState> = _state.asStateFlow()

    private var bank: QuestionBank? = null
    private var engine: GameEngine? = null
    private var timerJob: Job? = null

    init {
        if (economy != null) {
            viewModelScope.launch { economy.snapshot.collect { snap -> _state.update { it.copy(coins = snap?.coins) } } }
            viewModelScope.launch { economy.refresh() }
        }
        // Lo comprado puede llegar del servidor o, sin conexión, de la copia del teléfono.
        val serverOwned = economy?.snapshot?.map { it?.owned.orEmpty() } ?: flowOf(emptySet<String>())
        viewModelScope.launch {
            combine(serverOwned, cachedOwnedModes.onStart { emit(emptySet()) }) { server, cached -> server + cached }
                .collect { owned -> _state.update { it.copy(ownedModes = owned) } }
        }
        viewModelScope.launch {
            try {
                val loaded = loadBank()
                bank = loaded
                engine = GameEngine(loaded)
                _state.update { it.copy(loading = false, regions = loaded.regions, countries = loaded.countries) }
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

    fun setLength(length: GameLength) = _state.update { it.copy(length = length) }

    fun setFast(fast: Boolean) = _state.update { it.copy(fast = fast) }

    /** Modo solitario: deja solo al primer jugador; al volver al grupo añade otro hueco. */
    fun setSolo(solo: Boolean) = _state.update {
        when {
            solo -> it.copy(names = it.names.take(1))
            it.names.size == 1 -> it.copy(names = it.names + "")
            else -> it
        }
    }

    fun start() {
        val s = _state.value
        val e = engine ?: return
        if (!s.canStart) return
        _state.update { it.copy(gameKey = newGameKey(), reward = null, notice = null) }
        setMatch(LocalMatch.start(e, LocalSetup.players(s.names), s.mode, newSeed(), length = s.length, fast = s.fastActive))
    }

    // --- Partida ---

    fun rouletteDone() = act { it.rouletteDone() }
    fun ready() = act { it.ready(clock()) }
    fun bet(amount: Int) = act { it.bet(amount, clock()) }
    fun answer(choice: Int) = act { it.answer(choice, clock()) }
    fun next() = act { it.next() }
    fun rematch() {
        _state.update { it.copy(gameKey = newGameKey(), reward = null, notice = null) }
        act { it.rematch(newSeed()) }
    }

    fun useWildcard(wildcard: Wildcard) = act { it.useWildcard(wildcard, clock()) }

    /** El dueño paga el paquete de partida con sus monedas y todos reciben un comodín más. */
    fun buyMatchPack() {
        val repo = economy ?: return
        val s = _state.value
        val match = s.match ?: return
        val key = s.gameKey ?: return
        if (!match.canBuyMatchPack || s.buyingPack) return
        _state.update { it.copy(buyingPack = true, notice = null) }
        viewModelScope.launch {
            val result = repo.buyMatchPack(key)
            val current = _state.value.match
            val notice = when (result) {
                is SpendResult.Done, SpendResult.AlreadyDone -> {
                    // Si la partida cambió mientras tanto (abandono o revancha), no se aplica aquí.
                    if (current != null && _state.value.gameKey == key) setMatch(current.addMatchPack())
                    "¡Todos tienen un comodín más!"
                }
                SpendResult.NotEnoughCoins -> "Faltan monedas: el paquete cuesta ${EconomyRules.MATCH_PACK_PRICE}."
                is SpendResult.Failed -> "No se pudo conectar. Los comodines gratis siguen disponibles."
            }
            _state.update { it.copy(buyingPack = false, notice = notice) }
        }
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    /** Abandona la partida y vuelve a la configuración con los mismos nombres. */
    fun quit() = setMatch(null)

    private inline fun act(block: (LocalMatch) -> LocalMatch) {
        val current = _state.value.match ?: return
        val updated = block(current)
        if (updated !== current) setMatch(updated)
    }

    private fun setMatch(match: LocalMatch?) {
        val previous = _state.value.match
        _state.update { it.copy(match = match) }
        scheduleTimeout(match)
        if (match != null && match.stage is LocalStage.Finished && previous?.stage !is LocalStage.Finished) {
            viewModelScope.launch {
                try {
                    recordFinished(match.state)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Si no se puede guardar el historial, la partida sigue igual: no es un error de juego.
                }
            }
            val key = _state.value.gameKey
            if (economy != null && key != null) {
                viewModelScope.launch {
                    // Sin conexión no hay XP para esta partida: el juego sigue igual.
                    economy.recordLocalGame(key, LocalGameReport.of(match.state)).onSuccess { reward ->
                        _state.update { if (it.gameKey == key) it.copy(reward = reward) else it }
                    }
                }
            }
        }
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
        fun factory(
            loadBank: suspend () -> QuestionBank,
            clock: () -> Long,
            newSeed: () -> Long,
            recordFinished: suspend (GameState) -> Unit,
            economy: EconomyRepository?,
            cachedOwnedModes: Flow<Set<String>>,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LocalGameViewModel(loadBank, clock, newSeed, recordFinished, economy, cachedOwnedModes = cachedOwnedModes)
            }
        }
    }
}
