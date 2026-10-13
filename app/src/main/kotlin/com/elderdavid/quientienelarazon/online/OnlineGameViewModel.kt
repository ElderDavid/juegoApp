package com.elderdavid.quientienelarazon.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elderdavid.quientienelarazon.core.game.GameLength
import com.elderdavid.quientienelarazon.core.online.PhaseClock
import com.elderdavid.quientienelarazon.core.online.RoomCode
import com.elderdavid.quientienelarazon.core.online.clientElapsed
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Modalidad elegible: «general» o el id de un departamento. */
data class OnlineMode(val id: String, val name: String)

enum class OnlineBusy { CREATING, JOINING, SEARCHING, STARTING, LEAVING, LEVEL }

data class OnlineUiState(
    /** false si la compilación no tiene Supabase configurado. */
    val available: Boolean = true,
    val modes: List<OnlineMode> = listOf(OnlineMode("general", "General")),
    val mode: String = "general",
    val length: GameLength = GameLength.DEFAULT,
    /** Tipo de sala al crear o buscar. Las de niños y mixtas solo con preguntas generales. */
    val audience: OnlineAudience = OnlineAudience.STANDARD,
    /** Nivel del jugador en salas mixtas: true = preguntas para niños con 12 s. */
    val kidsLevel: Boolean = false,
    val codeInput: String = "",
    val busy: OnlineBusy? = null,
    val view: RoomView? = null,
    val clock: PhaseClock? = null,
    /** Hay sala pero la última consulta falló: se reintenta solo. */
    val reconnecting: Boolean = false,
    val error: OnlineError? = null,
    /** Respuesta o apuesta enviada que el servidor aún no confirmó: evita dobles toques. */
    val sending: Boolean = false,
) {
    val codeValid: Boolean get() = RoomCode.isValid(codeInput)
    val modeName: String get() = modes.firstOrNull { it.id == (view?.room?.mode ?: mode) }?.name ?: mode
}

/**
 * Partida online. El servidor decide todo; aquí solo se consulta su estado (cada segundo durante
 * la partida, también como latido de conexión), se envían las acciones del jugador y se cuenta el
 * tiempo localmente a partir de lo que dice el servidor.
 *
 * [clock] debe ser monótono (`SystemClock.elapsedRealtime` en la app).
 * [childPlayer]: quien juega dijo tener menos de 13 años; empieza con salas de niños y nivel niños.
 */
class OnlineGameViewModel(
    private val repository: OnlineRepository?,
    private val loadBank: suspend () -> QuestionBank,
    private val clock: () -> Long,
    childPlayer: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(
        OnlineUiState(
            available = repository != null,
            audience = if (childPlayer) OnlineAudience.KIDS else OnlineAudience.STANDARD,
            kidsLevel = childPlayer,
        ),
    )
    val state: StateFlow<OnlineUiState> = _state.asStateFlow()

    private var bank: QuestionBank? = null
    private var pollJob: Job? = null

    /** Cuándo vio el jugador la pregunta actual (para el tiempo que declara al responder). */
    private var shownKey: Pair<Boolean, Int>? = null
    private var shownAt: Long = 0

    /** Solo se aplica una respuesta del servidor si su petición empezó después de la última aplicada. */
    private var requestSeq = 0L
    private var appliedSeq = 0L

    init {
        viewModelScope.launch {
            runCatching { loadBank() }.onSuccess { loaded ->
                bank = loaded
                _state.update { s ->
                    s.copy(modes = listOf(OnlineMode("general", "General")) + loaded.regions.map { OnlineMode(it.id, it.name) })
                }
            }
        }
        resume()
    }

    /** Reconexión: si el jugador sigue dentro de una sala (cerró la app, perdió la red), vuelve a ella. */
    private fun resume() {
        val repo = repository ?: return
        viewModelScope.launch {
            if (repo.ensureSignedIn() !is OnlineResult.Ok) return@launch
            val seq = ++requestSeq
            val result = repo.currentRoom()
            if (result is OnlineResult.Ok && result.value != null && _state.value.view == null) show(result.value, seq)
        }
    }

    fun questionBank(): QuestionBank? = bank

    fun now(): Long = clock()

    /** Las salas de niños y mixtas solo tienen preguntas generales: elegir un departamento vuelve a la sala normal. */
    fun setMode(id: String) = _state.update {
        when {
            it.view != null -> it
            id != GENERAL -> it.copy(mode = id, audience = OnlineAudience.STANDARD)
            else -> it.copy(mode = id)
        }
    }

    fun setAudience(audience: OnlineAudience) = _state.update {
        when {
            it.view != null -> it
            audience != OnlineAudience.STANDARD -> it.copy(audience = audience, mode = GENERAL)
            else -> it.copy(audience = audience)
        }
    }

    /** Nivel en salas mixtas. Antes de entrar se recuerda; en la sala de espera de una mixta se cambia en el servidor. */
    fun setKidsLevel(kids: Boolean) {
        val view = _state.value.view
        if (view == null) {
            _state.update { it.copy(kidsLevel = kids) }
            return
        }
        val repo = repository ?: return
        if (!view.room.inLobby || view.room.roomAudience != OnlineAudience.MIXED || _state.value.busy != null) return
        _state.update { it.copy(busy = OnlineBusy.LEVEL, kidsLevel = kids, error = null) }
        viewModelScope.launch {
            val seq = ++requestSeq
            val result = repo.setKidsLevel(view.room.id, kids)
            _state.update { it.copy(busy = null) }
            handle(result, seq)
        }
    }

    fun setLength(length: GameLength) = _state.update { if (it.view == null) it.copy(length = length) else it }

    fun setCode(input: String) = _state.update { it.copy(codeInput = RoomCode.normalize(input).take(RoomCode.LENGTH), error = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun createPrivate() = enter(OnlineBusy.CREATING) {
        val s = _state.value
        createRoom(publicRoom = false, mode = s.mode, length = s.length, audience = s.audience, kids = s.kidsLevel)
    }

    fun findPublic() = enter(OnlineBusy.SEARCHING) {
        val s = _state.value
        findPublic(s.mode, s.length, s.audience, s.kidsLevel)
    }

    /** El tipo de sala lo puso quien la creó; aquí solo cuenta el nivel, por si es mixta. */
    fun joinByCode() {
        if (!_state.value.codeValid) return
        enter(OnlineBusy.JOINING) { joinByCode(_state.value.codeInput, _state.value.kidsLevel) }
    }

    fun start() {
        val repo = repository ?: return
        val room = _state.value.view?.room ?: return
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = OnlineBusy.STARTING, error = null) }
        viewModelScope.launch {
            val seq = ++requestSeq
            val result = repo.start(room.id)
            _state.update { it.copy(busy = null) }
            handle(result, seq)
        }
    }

    fun bet(amount: Int) = act { repo, view -> repo.bet(view.room.id, view.current!!.index, amount) }

    fun answer(choice: Int) {
        val elapsed = clientElapsed(shownAt, clock())
        act { repo, view -> repo.answer(view.room.id, view.current!!.index, choice, elapsed) }
    }

    /** Sale de la sala (en partida, cuenta como abandono) y vuelve al menú online. */
    fun leave() {
        val repo = repository
        val room = _state.value.view?.room
        pollJob?.cancel()
        shownKey = null
        _state.update { it.copy(view = null, clock = null, reconnecting = false, busy = null, sending = false) }
        if (repo != null && room != null && !room.isOver) {
            viewModelScope.launch { repo.leave(room.id) }
        }
    }

    private fun enter(busy: OnlineBusy, action: suspend OnlineRepository.() -> OnlineResult<RoomView>) {
        val repo = repository ?: return
        if (_state.value.busy != null || _state.value.view != null) return
        _state.update { it.copy(busy = busy, error = null) }
        viewModelScope.launch {
            val signed = repo.ensureSignedIn()
            if (signed is OnlineResult.Failed) {
                _state.update { it.copy(busy = null, error = signed.error) }
                return@launch
            }
            val seq = ++requestSeq
            val result = repo.action()
            _state.update { it.copy(busy = null) }
            handle(result, seq)
        }
    }

    private fun act(call: suspend (OnlineRepository, RoomView) -> OnlineResult<RoomView>) {
        val repo = repository ?: return
        val view = _state.value.view ?: return
        if (_state.value.sending || view.current == null) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val seq = ++requestSeq
            val result = call(repo, view)
            _state.update { it.copy(sending = false) }
            handle(result, seq)
            // Si el servidor rechazó la acción (p. ej. llegó tarde), el estado real se pide enseguida.
            if (result is OnlineResult.Failed) refresh()
        }
    }

    private fun handle(result: OnlineResult<RoomView>, seq: Long) {
        when (result) {
            is OnlineResult.Ok -> show(result.value, seq)
            is OnlineResult.Failed -> when (result.error) {
                OnlineError.NETWORK -> _state.update {
                    if (it.view != null) it.copy(reconnecting = true) else it.copy(error = OnlineError.NETWORK)
                }
                OnlineError.NOT_IN_ROOM, OnlineError.ROOM_LEFT -> {
                    pollJob?.cancel()
                    _state.update { it.copy(view = null, clock = null, reconnecting = false, error = result.error) }
                }
                else -> _state.update { it.copy(error = result.error) }
            }
        }
    }

    private fun show(view: RoomView, seq: Long) {
        if (seq < appliedSeq) return
        appliedSeq = seq
        val now = clock()
        val current = view.current
        if (current?.question != null) {
            val key = current.isTiebreak to current.index
            if (key != shownKey) {
                shownKey = key
                shownAt = now
            }
        }
        val phaseClock = view.room.phaseRemainingMs?.let { PhaseClock(it, now, view.room.phaseTotalMs ?: it) }
            ?: view.room.autostartRemainingMs?.let { PhaseClock(it, now, AUTOSTART_TOTAL_MS) }
        _state.update { it.copy(view = view, clock = phaseClock, reconnecting = false) }
        if (view.room.isOver) pollJob?.cancel() else ensurePolling()
    }

    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var failures = 0
            while (isActive) {
                val s = _state.value
                val view = s.view ?: break
                if (view.room.isOver) break
                val base = if (view.room.inLobby) LOBBY_POLL_MS else GAME_POLL_MS
                // Al terminar una fase se consulta enseguida para no quedarse viendo un reloj en cero.
                val untilPhaseEnd = s.clock?.remaining(clock())?.plus(150) ?: base
                val wait = if (failures > 0) minOf(1000L shl minOf(failures, 3), MAX_BACKOFF_MS) else minOf(base, untilPhaseEnd)
                delay(wait.coerceAtLeast(MIN_POLL_MS))
                failures = if (refreshOnce()) 0 else failures + 1
            }
        }
    }

    private suspend fun refreshOnce(): Boolean {
        val repo = repository ?: return false
        val room = _state.value.view?.room ?: return false
        val seq = ++requestSeq
        val result = repo.state(room.id)
        handle(result, seq)
        return result is OnlineResult.Ok
    }

    private fun refresh() {
        viewModelScope.launch { refreshOnce() }
    }

    override fun onCleared() {
        // Cerrar la pantalla sin «Salir» no abandona la sala: al volver, resume() la recupera si el
        // jugador sigue en ella. En la sala de espera, el servidor lo saca tras 20 s sin noticias.
        pollJob?.cancel()
    }

    companion object {
        const val GENERAL = "general"
        const val GAME_POLL_MS = 1000L
        const val LOBBY_POLL_MS = 1500L
        const val MIN_POLL_MS = 250L
        const val MAX_BACKOFF_MS = 5000L
        const val AUTOSTART_TOTAL_MS = 15_000L

        fun factory(
            repository: OnlineRepository?,
            loadBank: suspend () -> QuestionBank,
            clock: () -> Long,
            childPlayer: Boolean = false,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { OnlineGameViewModel(repository, loadBank, clock, childPlayer) } }
    }
}
