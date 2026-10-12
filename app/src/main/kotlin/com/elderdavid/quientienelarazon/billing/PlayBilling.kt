package com.elderdavid.quientienelarazon.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.elderdavid.quientienelarazon.economy.VerifyResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Google Play Billing. Flujo de cada compra:
 * 1. Google Play cobra y avisa a la app.
 * 2. La app pide al servidor que la verifique ([verify]); el servidor acredita las monedas o
 *    registra «Quitar anuncios».
 * 3. Solo entonces la app consume (monedas) o reconoce («Quitar anuncios») la compra. Si algo
 *    falla en medio, [refresh] lo retoma al volver a abrir la app; Google devuelve el dinero de las
 *    compras que no se reconocen en 3 días.
 */
class PlayBillingRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val verify: suspend (productId: String, purchaseToken: String) -> VerifyResult,
    /** Id de la cuenta del juego: Google lo devuelve con la compra y el servidor lo compara. */
    private val accountId: suspend () -> String?,
    /** Último estado conocido de «Quitar anuncios», para no mostrar anuncios al abrir sin conexión. */
    private val loadCachedRemoveAds: suspend () -> Boolean,
    private val saveRemoveAds: suspend (Boolean) -> Unit,
) : BillingRepository, PurchasesUpdatedListener {

    override val available: Boolean = true

    private val _entitlements = MutableStateFlow(Entitlements())
    override val entitlements: StateFlow<Entitlements> = _entitlements.asStateFlow()

    private val _prices = MutableStateFlow<Map<Product, String>>(emptyMap())
    override val prices: StateFlow<Map<Product, String>> = _prices.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val connectLock = Mutex()
    private val processLock = Mutex()
    private var details: Map<Product, ProductDetails> = emptyMap()
    private var pendingFlow: CompletableDeferred<PurchaseResult>? = null

    init {
        scope.launch {
            if (loadCachedRemoveAds()) _entitlements.value = Entitlements(removeAds = true)
            refresh()
        }
    }

    private suspend fun connect(): Boolean = connectLock.withLock {
        if (client.isReady) return true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    override suspend fun refresh() {
        if (!connect()) return
        loadDetails()
        val purchases = suspendCancellableCoroutine<List<Purchase>?> { cont ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            ) { result, list ->
                cont.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) list else null)
            }
        } ?: return
        val ownsRemoveAds = purchases.any { p ->
            Product.REMOVE_ADS.playId in p.products && p.purchaseState == Purchase.PurchaseState.PURCHASED && p.isAcknowledged
        }
        setRemoveAds(ownsRemoveAds)
        // Compras a medias (pagadas pero sin verificar, reconocer o consumir): se terminan ahora.
        purchases.forEach { process(it) }
    }

    private suspend fun loadDetails() {
        if (details.size == Product.entries.size) return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Product.entries.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it.playId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        val list = suspendCancellableCoroutine<List<ProductDetails>> { cont ->
            client.queryProductDetailsAsync(params) { result, queried ->
                cont.resume(if (result.responseCode == BillingClient.BillingResponseCode.OK) queried.productDetailsList else emptyList())
            }
        }
        details = list.mapNotNull { d -> Product.byPlayId(d.productId)?.let { it to d } }.toMap()
        _prices.value = details.mapNotNull { (product, d) -> d.oneTimePurchaseOfferDetails?.formattedPrice?.let { product to it } }.toMap()
    }

    override suspend fun purchase(activity: Activity, product: Product): PurchaseResult {
        if (product == Product.REMOVE_ADS && _entitlements.value.removeAds) return PurchaseResult.AlreadyOwned
        if (!connect()) return PurchaseResult.NotAvailable("Google Play no está disponible en este momento.")
        loadDetails()
        val productDetails = details[product]
            ?: return PurchaseResult.NotAvailable("Este producto aún no está a la venta.")
        val account = accountId()
            ?: return PurchaseResult.NotAvailable("Necesitas conexión para comprar.")
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetails).build()))
            .setObfuscatedAccountId(account)
            .build()
        val deferred = CompletableDeferred<PurchaseResult>()
        pendingFlow = deferred
        val launch = client.launchBillingFlow(activity, flow)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingFlow = null
            return resultFor(launch.responseCode)
        }
        return deferred.await()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val flow = pendingFlow
        pendingFlow = null
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) {
            flow?.complete(resultFor(result.responseCode))
            return
        }
        scope.launch {
            val outcomes = purchases.map { process(it) }
            flow?.complete(outcomes.firstOrNull { it != PurchaseResult.Purchased } ?: PurchaseResult.Purchased)
        }
    }

    /** Verifica en el servidor y luego consume o reconoce. Seguro de repetir. */
    private suspend fun process(purchase: Purchase): PurchaseResult = processLock.withLock {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) return PurchaseResult.Pending
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return PurchaseResult.Failed("estado ${purchase.purchaseState}")
        val product = purchase.products.firstNotNullOfOrNull(Product::byPlayId) ?: return PurchaseResult.Failed("producto desconocido")
        if (!product.consumable && purchase.isAcknowledged) {
            setRemoveAds(true)
            return PurchaseResult.Purchased
        }
        when (val verified = verify(product.playId, purchase.purchaseToken)) {
            VerifyResult.Verified -> Unit
            VerifyResult.Retry -> return PurchaseResult.Failed("Pago recibido. Lo terminaremos cuando haya conexión.")
            is VerifyResult.Rejected -> {
                Log.w(TAG, "Compra rechazada por el servidor: ${verified.reason}")
                return PurchaseResult.Failed("Google Play no confirmó la compra.")
            }
        }
        if (product.consumable) {
            consume(purchase.purchaseToken)
        } else {
            if (acknowledge(purchase.purchaseToken)) setRemoveAds(true)
        }
        PurchaseResult.Purchased
    }

    private suspend fun consume(token: String): Boolean = suspendCancellableCoroutine { cont ->
        client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build()) { result, _ ->
            cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
        }
    }

    private suspend fun acknowledge(token: String): Boolean = suspendCancellableCoroutine { cont ->
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
            cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
        }
    }

    private suspend fun setRemoveAds(removed: Boolean) {
        if (_entitlements.value.removeAds == removed) return
        _entitlements.value = Entitlements(removeAds = removed)
        saveRemoveAds(removed)
    }

    private fun resultFor(code: Int): PurchaseResult = when (code) {
        BillingClient.BillingResponseCode.OK -> PurchaseResult.Purchased
        BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseResult.AlreadyOwned
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
        -> PurchaseResult.NotAvailable("Google Play no está disponible en este momento.")
        else -> PurchaseResult.Failed("Error de Google Play ($code).")
    }

    private companion object {
        const val TAG = "Billing"
    }
}
