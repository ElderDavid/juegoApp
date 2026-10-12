package com.elderdavid.quientienelarazon.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Productos que la app podría vender. Los identificadores deben coincidir con los que se creen en
 * Google Play Console; hoy no existe ninguno, así que nada se puede comprar.
 */
enum class Product(val playId: String) {
    /** Compra única que quita los anuncios. */
    REMOVE_ADS("remove_ads"),
}

/** Lo que el usuario tiene comprado. Solo se debe poner a true tras verificar la compra. */
data class Entitlements(val removeAds: Boolean = false)

sealed interface PurchaseResult {
    data object Purchased : PurchaseResult
    data object Cancelled : PurchaseResult

    /** Pago pendiente (por ejemplo, efectivo en tienda): aún no da derecho a nada. */
    data object Pending : PurchaseResult

    /** La facturación no está disponible en esta versión o en este teléfono. */
    data class NotAvailable(val reason: String) : PurchaseResult
    data class Failed(val reason: String) : PurchaseResult
}

/**
 * Facturación de Google Play. Por ahora solo existe la interfaz: no hay ninguna implementación que
 * cobre ni ningún botón de compra en la app. Lo que falta para activarla está en docs/ANUNCIOS.md.
 *
 * Reglas para la implementación futura:
 * - [entitlements] solo cambia con compras verificadas (idealmente en el servidor) y reconocidas.
 * - Nunca se guardan datos de pago en la app: Google Play gestiona el cobro.
 */
interface BillingRepository {
    /** false si esta versión no puede vender nada. La interfaz no debe ofrecer compras en ese caso. */
    val available: Boolean
    val entitlements: StateFlow<Entitlements>

    /** Vuelve a consultar las compras del usuario (al abrir la app o tras reinstalar). */
    suspend fun refresh()
    suspend fun purchase(activity: Activity, product: Product): PurchaseResult
}

/** La única implementación actual: no vende nada y nunca simula una compra. */
class UnavailableBillingRepository : BillingRepository {
    override val available: Boolean = false
    private val _entitlements = MutableStateFlow(Entitlements())
    override val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun purchase(activity: Activity, product: Product): PurchaseResult =
        PurchaseResult.NotAvailable("Las compras aún no están disponibles en esta versión.")
}
