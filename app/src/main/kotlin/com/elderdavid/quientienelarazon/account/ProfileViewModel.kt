package com.elderdavid.quientienelarazon.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elderdavid.quientienelarazon.core.profile.Avatars
import com.elderdavid.quientienelarazon.core.profile.PlayerStats
import com.elderdavid.quientienelarazon.core.profile.ProfileName
import com.elderdavid.quientienelarazon.core.profile.ProfileNameIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val account: AccountState = AccountState.Loading,
    val profile: Profile? = null,
    val stats: PlayerStats? = null,
    val loadFailed: Boolean = false,
    val nameDraft: String = "",
    val avatarDraft: Int = 0,
    val saving: Boolean = false,
    val linking: Boolean = false,
    val googleAvailable: Boolean = false,
    /** La cuenta de Google ya es de otro jugador: la pantalla pregunta qué hacer. */
    val conflict: Boolean = false,
    val message: String? = null,
) {
    val nameIssue: ProfileNameIssue? get() = if (nameDraft.isEmpty()) null else ProfileName.validate(nameDraft)
    val hasChanges: Boolean
        get() = profile != null && (ProfileName.clean(nameDraft) != profile.displayName || avatarDraft != profile.avatarId)
    val canSave: Boolean get() = hasChanges && !saving && ProfileName.validate(nameDraft) == null
}

/**
 * Perfil y cuenta. Sin configuración de Supabase muestra «no configurado» y no hace peticiones.
 * El token de Google lo obtiene la pantalla (necesita la Activity) y llega aquí como resultado.
 */
class ProfileViewModel(
    private val accounts: AccountRepository?,
    private val profiles: ProfileRepository?,
    googleConfigured: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileUiState(
            account = if (accounts == null) AccountState.NotConfigured else AccountState.Loading,
            googleAvailable = accounts != null && googleConfigured,
        ),
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        if (accounts != null) {
            viewModelScope.launch {
                accounts.state.collect { account -> _state.update { it.copy(account = account) } }
            }
            viewModelScope.launch {
                accounts.state.map { it.userId }.distinctUntilChanged().collect { id -> if (id != null) load(id) }
            }
            viewModelScope.launch {
                if (accounts.ensureSignedIn().isFailure) {
                    _state.update { it.copy(message = "No se pudo conectar. El juego local sigue disponible.") }
                }
            }
        }
    }

    fun retry() {
        val repo = accounts ?: return
        viewModelScope.launch {
            _state.update { it.copy(message = null, loadFailed = false) }
            if (repo.ensureSignedIn().isFailure) {
                _state.update { it.copy(message = "Sigue sin haber conexión.") }
            } else {
                repo.state.value.userId?.let { load(it) }
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(nameDraft = name.take(ProfileName.MAX_LENGTH + 4)) }

    fun setAvatar(id: Int) = _state.update { if (Avatars.isValid(id)) it.copy(avatarDraft = id) else it }

    fun save() {
        val repo = profiles ?: return
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val result = repo.save(s.nameDraft, s.avatarDraft)
            _state.update {
                when (result) {
                    is ProfileSaveResult.Saved -> it.copy(
                        saving = false,
                        profile = result.profile,
                        nameDraft = result.profile.displayName,
                        avatarDraft = result.profile.avatarId,
                        message = "Perfil guardado.",
                    )
                    is ProfileSaveResult.InvalidName -> it.copy(saving = false, message = issueMessage(result.issue))
                    is ProfileSaveResult.Failed -> it.copy(saving = false, message = "No se pudo guardar. Revisa tu conexión.")
                }
            }
        }
    }

    /** Resultado del selector de Google. [switching] = el jugador aceptó cambiar a la cuenta existente. */
    fun onGoogleCredential(result: GoogleCredentialResult, switching: Boolean = false) {
        val repo = accounts ?: return
        when (result) {
            GoogleCredentialResult.Cancelled -> _state.update { it.copy(linking = false) }
            is GoogleCredentialResult.Unavailable -> _state.update { it.copy(linking = false, message = result.reason) }
            is GoogleCredentialResult.Success -> {
                _state.update { it.copy(linking = true, message = null, conflict = false) }
                viewModelScope.launch {
                    val outcome = if (switching) repo.switchToGoogle(result.token) else repo.linkGoogle(result.token)
                    _state.update {
                        when (outcome) {
                            LinkResult.Linked -> it.copy(
                                linking = false,
                                message = if (switching) "Ahora usas tu cuenta de Google." else "Cuenta vinculada con Google. Tu progreso queda guardado.",
                            )
                            LinkResult.IdentityInUse -> it.copy(linking = false, conflict = true)
                            is LinkResult.Failed -> it.copy(linking = false, message = "No se pudo vincular con Google.")
                        }
                    }
                }
            }
        }
    }

    fun dismissConflict() = _state.update { it.copy(conflict = false) }

    fun signOut() {
        val repo = accounts ?: return
        viewModelScope.launch {
            repo.signOut()
            // Sin sesión se vuelve a entrar como invitado nuevo, para que el juego online siga disponible.
            repo.ensureSignedIn()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun load(userId: String) {
        val repo = profiles ?: return
        repo.load(userId)
            .onSuccess { (profile, stats) ->
                _state.update {
                    it.copy(
                        profile = profile,
                        stats = stats,
                        loadFailed = false,
                        nameDraft = profile.displayName,
                        avatarDraft = profile.avatarId,
                    )
                }
            }
            .onFailure { _state.update { it.copy(loadFailed = true) } }
    }

    companion object {
        fun issueMessage(issue: ProfileNameIssue) = when (issue) {
            ProfileNameIssue.TOO_SHORT -> "El nombre debe tener al menos ${ProfileName.MIN_LENGTH} caracteres."
            ProfileNameIssue.TOO_LONG -> "El nombre puede tener como máximo ${ProfileName.MAX_LENGTH} caracteres."
            ProfileNameIssue.INVALID_CHARACTERS -> "Usa solo letras, números, espacios, punto, guion o guion bajo."
            ProfileNameIssue.OFFENSIVE -> "Ese nombre no está permitido. Elige otro."
        }

        fun factory(accounts: AccountRepository?, profiles: ProfileRepository?, googleConfigured: Boolean): ViewModelProvider.Factory =
            viewModelFactory { initializer { ProfileViewModel(accounts, profiles, googleConfigured) } }
    }
}
