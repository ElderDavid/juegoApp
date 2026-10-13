package com.elderdavid.quientienelarazon.economy

import com.elderdavid.quientienelarazon.account.AccountRepository
import com.elderdavid.quientienelarazon.account.userId
import com.elderdavid.quientienelarazon.core.economy.CosmeticKind
import com.elderdavid.quientienelarazon.core.economy.LocalGameReport
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SupabaseEconomyRepository(
    private val client: SupabaseClient,
    private val accounts: AccountRepository,
) : EconomyRepository {

    private val _snapshot = MutableStateFlow<EconomySnapshot?>(null)
    override val snapshot: StateFlow<EconomySnapshot?> = _snapshot.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun userId(): String? {
        accounts.state.value.userId?.let { return it }
        accounts.ensureSignedIn()
        return accounts.state.value.userId
    }

    override suspend fun refresh(): Result<EconomySnapshot> = safely {
        requireUser()
        val dto = client.postgrest.rpc("economy_state").decodeAs<EconomyDto>()
        dto.toDomain().also { _snapshot.value = it }
    }

    override suspend fun recordLocalGame(gameKey: String, report: LocalGameReport): Result<LocalGameReward> = safely {
        requireUser()
        val params = buildJsonObject {
            put("p_game_key", gameKey)
            put("p_regional", report.regional)
            put("p_perfect_round", report.perfectRound)
        }
        val result = client.postgrest.rpc("record_local_game", params).decodeAs<JsonObject>()
        refreshQuietly()
        LocalGameReward(
            xp = result["xp"]?.jsonPrimitive?.intOrNull ?: 0,
            coins = result["coins"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    override suspend fun claimDaily(): Result<Int> = safely {
        requireUser()
        val result = client.postgrest.rpc("claim_daily").decodeAs<JsonObject>()
        refreshQuietly()
        result["coins"]?.jsonPrimitive?.intOrNull ?: 0
    }

    override suspend fun buyMatchPack(gameKey: String): SpendResult =
        spend { client.postgrest.rpc("buy_match_pack", buildJsonObject { put("p_game_key", gameKey) }).decodeAs<Int>() }

    override suspend fun buyCosmetic(id: String): SpendResult =
        spend { client.postgrest.rpc("buy_cosmetic", buildJsonObject { put("p_id", id) }).decodeAs<Int>() }

    override suspend fun equip(kind: CosmeticKind, id: String?): Result<Unit> = safely {
        requireUser()
        val params = buildJsonObject {
            put("p_kind", kind.name)
            if (id == null) put("p_id", JsonNull) else put("p_id", id)
        }
        client.postgrest.rpc("equip_cosmetic", params)
        refreshQuietly()
    }

    override suspend fun verifyPurchase(productId: String, purchaseToken: String): VerifyResult = try {
        requireUser()
        val response = client.functions.invoke(
            function = "verify-purchase",
            body = buildJsonObject {
                put("productId", productId)
                put("purchaseToken", purchaseToken)
            },
        )
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        if (body["ok"]?.jsonPrimitive?.boolean == true) {
            refreshQuietly()
            VerifyResult.Verified
        } else {
            VerifyResult.Rejected(body["reason"]?.jsonPrimitive?.content ?: "rejected")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Sin conexión o error del servidor (las compras no válidas llegan con ok=false y 200).
        VerifyResult.Retry
    }

    private suspend fun requireUser() {
        checkNotNull(userId()) { "Sin sesión" }
    }

    private suspend fun refreshQuietly() {
        runCatching {
            _snapshot.value = client.postgrest.rpc("economy_state").decodeAs<EconomyDto>().toDomain()
        }.onFailure { if (it is CancellationException) throw it }
    }

    private suspend fun spend(block: suspend () -> Int): SpendResult = try {
        requireUser()
        val left = block()
        refreshQuietly()
        SpendResult.Done(left)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = e.message.orEmpty()
        when {
            "not_enough_coins" in message -> SpendResult.NotEnoughCoins
            "already_bought" in message || "already_owned" in message -> SpendResult.AlreadyDone
            else -> SpendResult.Failed(e::class.simpleName ?: "error")
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

@Serializable
private data class EconomyDto(
    val coins: Int,
    @SerialName("remove_ads") val removeAds: Boolean,
    val xp: Int,
    val level: Int,
    val owned: List<String>,
    val equipped: Map<String, String>,
    val achievements: List<String>,
    @SerialName("streak_day") val streakDay: Int,
    @SerialName("daily_claimed") val dailyClaimed: Boolean,
    @SerialName("next_streak_day") val nextStreakDay: Int,
    @SerialName("videos_left") val videosLeft: Int,
    @SerialName("local_xp_games_left") val localXpGamesLeft: Int,
) {
    fun toDomain() = EconomySnapshot(
        coins = coins,
        removeAds = removeAds,
        xp = xp,
        level = level,
        owned = owned.toSet(),
        equipped = equipped.mapNotNull { (k, v) -> CosmeticKind.entries.firstOrNull { it.name == k }?.let { it to v } }.toMap(),
        achievements = achievements,
        streakDay = streakDay,
        dailyClaimed = dailyClaimed,
        nextStreakDay = nextStreakDay,
        videosLeft = videosLeft,
        localXpGamesLeft = localXpGamesLeft,
    )
}
