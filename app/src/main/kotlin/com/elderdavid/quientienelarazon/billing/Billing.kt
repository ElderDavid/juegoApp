package com.elderdavid.quientienelarazon.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Productos de Google Play. Los identificadores deben coincidir con los creados en Play Console
 * y con la tabla store_products del servidor. [coins] > 0 = paquete de monedas (se consume y se
 * puede volver a comprar); «Quitar anuncios» es una compra única para siempre.
 */
enum class Product(val playId: String, val coins: Int = 0) {
    REMOVE_ADS("remove_ads"),
    COINS_300("coins_300", coins = 300),
    COINS_900("coins_900", coins = 900),
    COINS_2000("coins_2000", coins = 2000),
    ;

    val consumable: Boolean get() = coins > 0

    companion object {
        fun byPlayId(id: String): Product? = entries.firstOrNull { it.playId == id }
        val coinPacks: List<Product> = entries.filter { it.consumable }
    }
}

/** Lo que el usuario tiene comprado. Solo pasa a true con compras verificadas y reconocidas. */
data class Entitlements(val removeAds: Boolean = false)

sealed interface PurchaseResult {
    data object Purchased : PurchaseResult
    data object Cancelled : PurchaseResult

    /** Pago pendiente (por ejemplo, efectivo en tienda): aún no da derecho a nada. */
    data object Pending : PurchaseResult

    /** Ya lo tiene (por ejemplo, «Quitar anuncios» comprado en otro teléfono): basta con restaurar. */
    data object AlreadyOwned : PurchaseResult

    /** La facturación no está disponible en esta versión o en este teléfono. */
    data class NotAvailable(val reason: String) : PurchaseResult
    data class Failed(val reason: String) : PurchaseResult
}

/**
 * Facturación de Google Play.
 * - [entitlements] solo cambia con compras verificadas en el servidor y reconocidas.
 * - Las monedas las acredita el servidor al verificar; la app solo consume la compra después.
 * - Nunca se guardan datos de pago en la app: Google Play gestiona el cobro.
 */
interface BillingRepository {
    /** false si esta versión no puede vender nada. La interfaz no debe ofrecer compras en ese caso. */
    val available: Boolean
    val entitlements: StateFlow<Entitlements>

    /** Precio de cada producto en la moneda del usuario (lo da Google Play). Vacío hasta cargarlo. */
    val prices: StateFlow<Map<Product, String>>

    /** Vuelve a consultar las compras (al abrir la app, tras reinstalar o con «Restaurar compras»). */
    suspend fun refresh()
    suspend fun purchase(activity: Activity, product: Product): PurchaseResult
}

/** Sin facturación (compilación sin servidor para verificar compras). Nunca simula una compra. */
class UnavailableBillingRepository : BillingRepository {
    override val available: Boolean = false
    private val _entitlements = MutableStateFlow(Entitlements())
    override val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()
    override val prices: StateFlow<Map<Product, String>> = MutableStateFlow(emptyMap())

    override suspend fun refresh() = Unit

    override suspend fun purchase(activity: Activity, product: Product): PurchaseResult =
        PurchaseResult.NotAvailable("Las compras no están disponibles en esta versión.")
}
