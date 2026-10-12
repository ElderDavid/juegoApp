package com.elderdavid.quientienelarazon.account

import com.elderdavid.quientienelarazon.core.profile.PlayerStats
import com.elderdavid.quientienelarazon.core.profile.ProfileNameIssue
import kotlinx.coroutines.flow.StateFlow

/** Estado de la cuenta del jugador. El modo local funciona en cualquiera de ellos. */
sealed interface AccountState {
    /** Faltan la URL o la clave pública de Supabase en la compilación. */
    data object NotConfigured : AccountState
    data object Loading : AccountState
    data object SignedOut : AccountState

    /** Hay sesión guardada pero no se pudo renovar (normalmente, sin conexión). */
    data object Offline : AccountState

    /** Invitado anónimo: su progreso se pierde si desinstala la app sin vincular Google. */
    data class Guest(val userId: String) : AccountState
    data class Google(val userId: String, val email: String?) : AccountState
}

val AccountState.userId: String?
    get() = when (this) {
        is AccountState.Guest -> userId
        is AccountState.Google -> userId
        else -> null
    }

/** Token de Google y el nonce sin cifrar con el que se pidió (Supabase lo comprueba). */
class GoogleToken(val idToken: String, val rawNonce: String) {
    // Nunca se imprime el token, ni siquiera en registros de depuración.
    override fun toString() = "GoogleToken(***)"
}

sealed interface GoogleCredentialResult {
    data class Success(val token: GoogleToken) : GoogleCredentialResult
    data object Cancelled : GoogleCredentialResult
    data class Unavailable(val reason: String) : GoogleCredentialResult
}

sealed interface LinkResult {
    data object Linked : LinkResult

    /** Esa cuenta de Google ya pertenece a otro jugador: hay que elegir qué cuenta usar. */
    data object IdentityInUse : LinkResult
    data class Failed(val message: String) : LinkResult
}

data class Profile(val userId: String, val displayName: String, val avatarId: Int)

sealed interface ProfileSaveResult {
    data class Saved(val profile: Profile) : ProfileSaveResult
    data class InvalidName(val issue: ProfileNameIssue) : ProfileSaveResult
    data class Failed(val message: String) : ProfileSaveResult
}

interface AccountRepository {
    val state: StateFlow<AccountState>

    /** Entra como invitado si no hay sesión. No hace nada si ya la hay. */
    suspend fun ensureSignedIn(): Result<Unit>

    /** Añade Google a la cuenta actual conservando el mismo ID de usuario. */
    suspend fun linkGoogle(token: GoogleToken): LinkResult

    /** Cambia a la cuenta que ya tiene esa identidad de Google. El progreso del invitado no se fusiona. */
    suspend fun switchToGoogle(token: GoogleToken): LinkResult

    suspend fun signOut()
}

interface ProfileRepository {
    suspend fun load(userId: String): Result<Pair<Profile, PlayerStats>>
    suspend fun save(displayName: String, avatarId: Int): ProfileSaveResult
}
