package com.elderdavid.quientienelarazon.economy

import com.elderdavid.quientienelarazon.core.economy.CosmeticKind
import com.elderdavid.quientienelarazon.core.economy.EconomyRules
import com.elderdavid.quientienelarazon.core.economy.LocalGameReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Economía en memoria con las mismas reglas básicas que el servidor. */
class FakeEconomy(coins: Int = 0, var online: Boolean = true) : EconomyRepository {
    private val _snapshot = MutableStateFlow<EconomySnapshot?>(null)
    override val snapshot: StateFlow<EconomySnapshot?> = _snapshot
    private var state = EconomySnapshot(coins = coins, videosLeft = EconomyRules.VIDEOS_PER_DAY)
    private val packs = mutableSetOf<String>()
    val recorded = mutableListOf<Pair<String, LocalGameReport>>()

    override suspend fun userId(): String? = if (online) "00000000-0000-0000-0000-000000000001" else null

    override suspend fun refresh(): Result<EconomySnapshot> =
        if (online) Result.success(state.also { _snapshot.value = it }) else Result.failure(java.io.IOException("sin red"))

    override suspend fun recordLocalGame(gameKey: String, report: LocalGameReport): Result<LocalGameReward> {
        if (!online) return Result.failure(java.io.IOException("sin red"))
        recorded += gameKey to report
        state = state.copy(coins = state.coins + EconomyRules.GAME_COINS, xp = state.xp + EconomyRules.LOCAL_GAME_XP)
        _snapshot.value = state
        return Result.success(LocalGameReward(EconomyRules.LOCAL_GAME_XP, EconomyRules.GAME_COINS))
    }

    override suspend fun claimDaily(): Result<Int> {
        if (state.dailyClaimed) return Result.failure(IllegalStateException("already_claimed"))
        state = state.copy(coins = state.coins + 10, dailyClaimed = true, streakDay = 1)
        _snapshot.value = state
        return Result.success(10)
    }

    override suspend fun buyMatchPack(gameKey: String): SpendResult {
        if (!online) return SpendResult.Failed("sin red")
        if (gameKey in packs) return SpendResult.AlreadyDone
        if (state.coins < EconomyRules.MATCH_PACK_PRICE) return SpendResult.NotEnoughCoins
        packs += gameKey
        state = state.copy(coins = state.coins - EconomyRules.MATCH_PACK_PRICE)
        _snapshot.value = state
        return SpendResult.Done(state.coins)
    }

    override suspend fun buyCosmetic(id: String): SpendResult = SpendResult.Failed("no usado")
    override suspend fun equip(kind: CosmeticKind, id: String?): Result<Unit> = Result.success(Unit)
    override suspend fun verifyPurchase(productId: String, purchaseToken: String): VerifyResult = VerifyResult.Verified
}
