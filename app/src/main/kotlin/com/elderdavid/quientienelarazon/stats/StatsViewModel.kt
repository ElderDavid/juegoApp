package com.elderdavid.quientienelarazon.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elderdavid.quientienelarazon.account.AccountRepository
import com.elderdavid.quientienelarazon.account.ProfileRepository
import com.elderdavid.quientienelarazon.account.userId
import com.elderdavid.quientienelarazon.core.profile.LocalHistory
import com.elderdavid.quientienelarazon.core.profile.PlayerStats
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estadísticas de la cuenta: su única fuente es el servidor. */
sealed interface OnlineStats {
    /** La compilación no tiene servidor configurado. */
    data object NotConfigured : OnlineStats
    data object Loading : OnlineStats

    /** Sin conexión o error del servidor: no se muestran datos viejos como si fueran actuales. */
    data object Unavailable : OnlineStats

    /** [leaderboard] null si solo falló la clasificación. */
    data class Ready(val stats: PlayerStats, val leaderboard: Leaderboard?) : OnlineStats
}

data class StatsUiState(
    val online: OnlineStats = OnlineStats.Loading,
    val local: LocalHistory = LocalHistory(),
    /** id de departamento → nombre, para el historial local. */
    val regionNames: Map<String, String> = emptyMap(),
    val confirmClear: Boolean = false,
)

/**
 * Pantalla de estadísticas. Muestra por separado lo que guarda el servidor (cuenta y clasificación,
 * solo partidas online) y lo que guarda este teléfono (partidas locales). Nunca se suman.
 */
class StatsViewModel(
    private val accounts: AccountRepository?,
    private val profiles: ProfileRepository?,
    private val leaderboard: LeaderboardRepository?,
    private val localHistory: LocalHistoryRepository,
    private val loadBank: suspend () -> QuestionBank,
) : ViewModel() {

    private val _state = MutableStateFlow(
        StatsUiState(online = if (accounts == null || profiles == null) OnlineStats.NotConfigured else OnlineStats.Loading),
    )
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { localHistory.history.collect { h -> _state.update { it.copy(local = h) } } }
        viewModelScope.launch {
            runCatching { loadBank() }.onSuccess { bank ->
                _state.update { s -> s.copy(regionNames = bank.regions.associate { it.id to it.name }) }
            }
        }
        refreshOnline()
    }

    fun refreshOnline() {
        val accounts = accounts ?: return
        val profiles = profiles ?: return
        _state.update { it.copy(online = OnlineStats.Loading) }
        viewModelScope.launch {
            val userId = if (accounts.ensureSignedIn().isSuccess) accounts.state.value.userId else null
            if (userId == null) {
                _state.update { it.copy(online = OnlineStats.Unavailable) }
                return@launch
            }
            val stats = profiles.load(userId).getOrNull()?.second
            if (stats == null) {
                _state.update { it.copy(online = OnlineStats.Unavailable) }
                return@launch
            }
            val board = leaderboard?.load()?.getOrNull()
            _state.update { it.copy(online = OnlineStats.Ready(stats, board)) }
        }
    }

    fun askClear() = _state.update { it.copy(confirmClear = true) }

    fun dismissClear() = _state.update { it.copy(confirmClear = false) }

    fun clearLocal() {
        _state.update { it.copy(confirmClear = false) }
        viewModelScope.launch { localHistory.clear() }
    }

    companion object {
        fun factory(
            accounts: AccountRepository?,
            profiles: ProfileRepository?,
            leaderboard: LeaderboardRepository?,
            localHistory: LocalHistoryRepository,
            loadBank: suspend () -> QuestionBank,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { StatsViewModel(accounts, profiles, leaderboard, localHistory, loadBank) } }
    }
}
