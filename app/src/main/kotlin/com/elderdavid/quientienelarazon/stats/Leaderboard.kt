package com.elderdavid.quientienelarazon.stats

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Clasificación online. Solo cuenta partidas online, que el servidor registra al terminar cada sala. */
@Serializable
data class Leaderboard(
    val top: List<LeaderboardEntry> = emptyList(),
    /** Posición propia aunque quede fuera del top; null si aún no ha jugado online. */
    val me: LeaderboardEntry? = null,
)

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    @SerialName("avatar_id") val avatarId: Int,
    val xp: Int,
    val wins: Int,
    val games: Int,
    val me: Boolean = false,
)

interface LeaderboardRepository {
    suspend fun load(limit: Int = DEFAULT_LIMIT): Result<Leaderboard>

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}

class SupabaseLeaderboardRepository(private val client: SupabaseClient) : LeaderboardRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(limit: Int): Result<Leaderboard> = try {
        val raw = client.postgrest.rpc("leaderboard", buildJsonObject { put("p_limit", limit) }).data
        Result.success(json.decodeFromString(Leaderboard.serializer(), raw))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
