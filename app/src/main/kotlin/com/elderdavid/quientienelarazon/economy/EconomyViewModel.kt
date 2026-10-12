package com.elderdavid.quientienelarazon.economy

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elderdavid.quientienelarazon.ads.AdsController
import com.elderdavid.quientienelarazon.billing.BillingRepository
import com.elderdavid.quientienelarazon.billing.Product
import com.elderdavid.quientienelarazon.billing.PurchaseResult
import com.elderdavid.quientienelarazon.core.economy.Cosmetic
import com.elderdavid.quientienelarazon.core.economy.CosmeticKind
import com.elderdavid.quientienelarazon.core.economy.Cosmetics
import com.elderdavid.quientienelarazon.core.economy.EconomyRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EconomyUiState(
    /** false si esta compilación no tiene servidor: no hay monedas ni tienda. */
    val available: Boolean = true,
    val snapshot: EconomySnapshot? = null,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val busy: Boolean = false,
    val removeAds: Boolean = false,
    val billingAvailable: Boolean = false,
    val prices: Map<Product, String> = emptyMap(),
    val rewardedReady: Boolean = false,
    val message: String? = null,
) {
    fun owns(c: Cosmetic) = snapshot?.owned?.contains(c.id) == true
    fun isEquipped(c: Cosmetic) = snapshot?.equipped?.get(c.kind) == c.id
}

/** Tienda y premios: monedas, racha, videos, compras de Google Play y cosméticos. */
class EconomyViewModel(
    private val economy: EconomyRepository?,
    private val billing: BillingRepository,
    private val ads: AdsController,
) : ViewModel() {

    private val _state = MutableStateFlow(EconomyUiState(available = economy != null, billingAvailable = billing.available))
    val state: StateFlow<EconomyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { billing.entitlements.collect { e -> _state.update { it.copy(removeAds = e.removeAds) } } }
        viewModelScope.launch { billing.prices.collect { p -> _state.update { it.copy(prices = p) } } }
        viewModelScope.launch { ads.rewardedReady.collect { r -> _state.update { it.copy(rewardedReady = r) } } }
        if (economy != null) {
            viewModelScope.launch { economy.snapshot.collect { s -> if (s != null) _state.update { it.copy(snapshot = s) } } }
            refresh()
        } else {
            _state.update { it.copy(loading = false) }
        }
    }

    fun refresh() {
        val repo = economy ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, loadFailed = false) }
            val result = repo.refresh()
            _state.update { it.copy(loading = false, loadFailed = result.isFailure) }
        }
    }

    fun claimDaily() = act { repo ->
        repo.claimDaily().fold(
            onSuccess = { "¡+$it monedas por tu racha!" },
            onFailure = { if ("already_claimed" in it.message.orEmpty()) "Ya reclamaste el premio de hoy." else NO_CONNECTION },
        )
    }

    fun buyCosmetic(c: Cosmetic) = act { repo ->
        when (repo.buyCosmetic(c.id)) {
            is SpendResult.Done -> {
                repo.equip(c.kind, c.id)
                "Compraste ${c.name}."
            }
            SpendResult.NotEnoughCoins -> "Te faltan monedas."
            SpendResult.AlreadyDone -> "Ya lo tienes."
            is SpendResult.Failed -> NO_CONNECTION
        }
    }

    /** Equipa [c], o lo quita si ya estaba equipado. */
    fun toggleEquip(c: Cosmetic) = act { repo ->
        val equip = !_state.value.isEquipped(c)
        repo.equip(c.kind, if (equip) c.id else null).fold(
            onSuccess = { if (equip) "${c.name} equipado." else "${c.kind.label}: el de siempre." },
            onFailure = { NO_CONNECTION },
        )
    }

    fun watchVideo(activity: Activity) = act { repo ->
        val user = repo.userId() ?: return@act NO_CONNECTION
        val before = repo.snapshot.value?.coins ?: 0
        val earned = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            ads.showRewarded(activity, user) { if (cont.isActive) cont.resumeWith(Result.success(it)) }
        }
        if (!earned) return@act "El video no se completó: no hay monedas esta vez."
        // AdMob avisa al servidor por su cuenta; se espera un poco a que llegue.
        repeat(5) {
            delay(2_000)
            val now = repo.refresh().getOrNull()?.coins ?: before
            if (now > before) return@act "¡+${now - before} monedas!"
        }
        "Tus monedas llegarán en unos momentos."
    }

    fun buy(activity: Activity, product: Product) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = when (val r = billing.purchase(activity, product)) {
                PurchaseResult.Purchased -> if (product.consumable) "¡+${product.coins} monedas!" else "¡Listo! Ya no verás anuncios."
                PurchaseResult.Pending -> "Pago pendiente. Te avisaremos cuando Google Play lo confirme."
                PurchaseResult.Cancelled -> null
                PurchaseResult.AlreadyOwned -> {
                    billing.refresh()
                    "Ya lo tenías: compra restaurada."
                }
                is PurchaseResult.NotAvailable -> r.reason
                is PurchaseResult.Failed -> r.reason
            }
            economy?.refresh()
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    fun restorePurchases() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            billing.refresh()
            economy?.refresh()
            _state.update {
                it.copy(busy = false, message = if (it.removeAds) "Compras restauradas: sin anuncios." else "No encontramos compras para restaurar.")
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun act(block: suspend (EconomyRepository) -> String?) {
        val repo = economy ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = block(repo)
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    companion object {
        const val NO_CONNECTION = "No se pudo conectar. Inténtalo de nuevo."

        /** Texto del próximo premio por nivel, para el perfil. */
        fun nextRewardText(level: Int): String {
            val next = Cosmetics.nextLevelReward(level)
            val coins = EconomyRules.levelUpBonus(level + 1)
            return if (next == null) {
                "Nivel ${level + 1}: $coins monedas."
            } else if (next.unlockLevel == level + 1) {
                "Nivel ${level + 1}: $coins monedas y ${next.kind.label.lowercase()} «${next.name}»."
            } else {
                "Nivel ${level + 1}: $coins monedas. Nivel ${next.unlockLevel}: ${next.kind.label.lowercase()} «${next.name}»."
            }
        }

        fun cosmeticsOf(kind: CosmeticKind) = Cosmetics.all.filter { it.kind == kind }

        fun factory(economy: EconomyRepository?, billing: BillingRepository, ads: AdsController): ViewModelProvider.Factory =
            viewModelFactory { initializer { EconomyViewModel(economy, billing, ads) } }
    }
}
