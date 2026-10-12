package com.elderdavid.quientienelarazon.account

import com.elderdavid.quientienelarazon.core.profile.PlayerStats
import com.elderdavid.quientienelarazon.core.profile.ProfileName
import com.elderdavid.quientienelarazon.core.profile.ProfileNameIssue
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Valores públicos de cliente leídos de BuildConfig. Vacíos = cuentas desactivadas. */
data class SupabaseSettings(val url: String, val publishableKey: String, val googleWebClientId: String) {
    val isConfigured: Boolean get() = url.startsWith("https://") && publishableKey.isNotBlank()
    val googleConfigured: Boolean get() = googleWebClientId.isNotBlank()

    fun createClient(): SupabaseClient = createSupabaseClient(url, publishableKey) {
        install(Auth)
        install(Postgrest)
    }
}

class SupabaseAccountRepository(private val client: SupabaseClient, scope: CoroutineScope) : AccountRepository {

    override val state: StateFlow<AccountState> = client.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val user = status.session.user
                    when {
                        user == null -> AccountState.Loading
                        user.isAnonymous == true -> AccountState.Guest(user.id)
                        else -> AccountState.Google(user.id, user.email)
                    }
                }
                SessionStatus.Initializing -> AccountState.Loading
                is SessionStatus.NotAuthenticated -> AccountState.SignedOut
                is SessionStatus.RefreshFailure -> AccountState.Offline
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AccountState.Loading)

    override suspend fun ensureSignedIn(): Result<Unit> = runSafely {
        client.auth.awaitInitialization()
        if (client.auth.currentSessionOrNull() == null) client.auth.signInAnonymously()
    }

    override suspend fun linkGoogle(token: GoogleToken): LinkResult = try {
        client.auth.linkIdentityWithIdToken(provider = Google, idToken = token.idToken) { nonce = token.rawNonce }
        // Refresca el usuario para que el estado pase de invitado a Google.
        client.auth.retrieveUserForCurrentSession(updateSession = true)
        LinkResult.Linked
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthRestException) {
        if (e.errorCode == AuthErrorCode.IdentityAlreadyExists) LinkResult.IdentityInUse else LinkResult.Failed(e.error)
    } catch (e: Exception) {
        LinkResult.Failed(e::class.simpleName ?: "error")
    }

    override suspend fun switchToGoogle(token: GoogleToken): LinkResult = try {
        client.auth.signInWith(IDToken) {
            idToken = token.idToken
            provider = Google
            nonce = token.rawNonce
        }
        LinkResult.Linked
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LinkResult.Failed(e::class.simpleName ?: "error")
    }

    override suspend fun signOut() {
        runSafely { client.auth.signOut() }
    }
}

class SupabaseProfileRepository(private val client: SupabaseClient) : ProfileRepository {

    override suspend fun load(userId: String): Result<Pair<Profile, PlayerStats>> = runSafely {
        val profile = client.from("profiles").select { filter { eq("id", userId) } }.decodeSingle<ProfileDto>()
        val stats = client.from("player_stats").select { filter { eq("user_id", userId) } }.decodeSingleOrNull<StatsDto>()
        profile.toDomain() to (stats?.toDomain() ?: PlayerStats())
    }

    override suspend fun save(displayName: String, avatarId: Int): ProfileSaveResult {
        // El servidor repite esta validación; aquí solo se evita una petición inútil.
        ProfileName.validate(displayName)?.let { return ProfileSaveResult.InvalidName(it) }
        return try {
            val params = buildJsonObject {
                put("p_display_name", ProfileName.clean(displayName))
                put("p_avatar_id", avatarId)
            }
            val saved = client.postgrest.rpc("update_profile", params).decodeAs<ProfileDto>()
            ProfileSaveResult.Saved(saved.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            serverNameIssue(e.message)?.let { ProfileSaveResult.InvalidName(it) }
                ?: ProfileSaveResult.Failed(e::class.simpleName ?: "error")
        }
    }

    /** Traduce los códigos que lanza la función update_profile del servidor. */
    private fun serverNameIssue(message: String?): ProfileNameIssue? = when {
        message == null -> null
        "name_too_short" in message -> ProfileNameIssue.TOO_SHORT
        "name_too_long" in message -> ProfileNameIssue.TOO_LONG
        "name_invalid_characters" in message -> ProfileNameIssue.INVALID_CHARACTERS
        "name_offensive" in message -> ProfileNameIssue.OFFENSIVE
        else -> null
    }
}

@Serializable
private data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_id") val avatarId: Int,
) {
    fun toDomain() = Profile(id, displayName, avatarId)
}

@Serializable
private data class StatsDto(
    @SerialName("games_played") val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    @SerialName("correct_answers") val correctAnswers: Int,
    @SerialName("objective_answered") val objectiveAnswered: Int,
    @SerialName("best_score") val bestScore: Int,
    val xp: Int,
) {
    fun toDomain() = PlayerStats(gamesPlayed, wins, losses, correctAnswers, objectiveAnswered, bestScore, xp)
}

private suspend fun <T> runSafely(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
