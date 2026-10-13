package com.elderdavid.quientienelarazon.economy

import com.elderdavid.quientienelarazon.core.economy.CosmeticKind
import com.elderdavid.quientienelarazon.core.economy.LocalGameReport
import kotlinx.coroutines.flow.StateFlow

/** Monedas, premios y cosméticos de la cuenta, tal como los devuelve el servidor. */
data class EconomySnapshot(
    val coins: Int = 0,
    val removeAds: Boolean = false,
    val xp: Int = 0,
    val level: Int = 1,
    val owned: Set<String> = emptySet(),
    val equipped: Map<CosmeticKind, String> = emptyMap(),
    val achievements: List<String> = emptyList(),
    val streakDay: Int = 0,
    val dailyClaimed: Boolean = false,
    val nextStreakDay: Int = 1,
    val videosLeft: Int = 0,
    val localXpGamesLeft: Int = 0,
)

/** Lo que dio una partida local a la cuenta del teléfono. */
data class LocalGameReward(val xp: Int, val coins: Int)

sealed interface SpendResult {
    data class Done(val coinsLeft: Int) : SpendResult
    data object NotEnoughCoins : SpendResult
    data object AlreadyDone : SpendResult
    data class Failed(val message: String) : SpendResult
}

/** Resultado de comprobar una compra de Google Play en el servidor. */
sealed interface VerifyResult {
    data object Verified : VerifyResult

    /** La compra no es válida (cancelada, de otra cuenta…): no se debe reconocer. */
    data class Rejected(val reason: String) : VerifyResult

    /** Sin conexión o error del servidor: se reintenta más tarde. */
    data object Retry : VerifyResult
}

/**
 * Economía del juego. El saldo lo decide siempre el servidor; aquí solo se piden acciones y se
 * guarda la última foto para mostrarla. Sin conexión, lo que cuesta monedas no está disponible.
 */
interface EconomyRepository {
    /** Última foto conocida; null hasta la primera carga (o sin cuenta). */
    val snapshot: StateFlow<EconomySnapshot?>

    /** Id del usuario de la cuenta, para el video recompensado y las compras. Entra como invitado si hace falta. */
    suspend fun userId(): String?

    suspend fun refresh(): Result<EconomySnapshot>
    suspend fun recordLocalGame(gameKey: String, report: LocalGameReport): Result<LocalGameReward>
    suspend fun claimDaily(): Result<Int>
    suspend fun buyMatchPack(gameKey: String): SpendResult
    suspend fun buyCosmetic(id: String): SpendResult
    suspend fun equip(kind: CosmeticKind, id: String?): Result<Unit>
    suspend fun verifyPurchase(productId: String, purchaseToken: String): VerifyResult
}
