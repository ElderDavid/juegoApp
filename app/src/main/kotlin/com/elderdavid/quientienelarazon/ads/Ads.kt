package com.elderdavid.quientienelarazon.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.elderdavid.quientienelarazon.billing.BillingRepository
import com.elderdavid.quientienelarazon.core.ads.AgeGroup
import com.elderdavid.quientienelarazon.core.ads.InterstitialPacing
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Anuncios de la app. Dónde aparecen:
 * - Intersticial: solo al salir de la pantalla final de una partida, con el límite de
 *   [InterstitialPacing]. Nunca durante una pregunta ni en la configuración de la partida.
 * - Banner: en pantallas secundarias (Reglas y Estadísticas) y uno pequeño al final del menú
 *   principal, debajo de «Ajustes». Nunca durante una partida.
 * - Video con recompensa: solo cuando el jugador pulsa «Ver video» en la tienda. Las monedas las
 *   paga el servidor cuando AdMob le confirma el video (verificación del lado del servidor).
 * «Quitar anuncios» quita el intersticial y los banners; el video sigue disponible porque lo elige
 * el jugador.
 */
interface AdsController {
    /** true si en este momento se pueden mostrar banners. */
    val bannersEnabled: StateFlow<Boolean>
    val bannerUnitId: String

    /**
     * Se llama al salir de la pantalla final de una partida. [gameKey] identifica la partida para
     * contarla una sola vez. [proceed] se ejecuta siempre: enseguida si no toca anuncio, o al
     * cerrarse el anuncio.
     */
    fun afterGame(activity: Activity, gameKey: String, proceed: () -> Unit)

    /** true si hay un video con recompensa cargado y listo. */
    val rewardedReady: StateFlow<Boolean>

    /**
     * Muestra el video con recompensa. [userId] viaja a AdMob y vuelve firmado al servidor, que
     * paga a esa cuenta. [onClosed] recibe true si el jugador lo vio hasta ganar la recompensa.
     */
    fun showRewarded(activity: Activity, userId: String, onClosed: (earned: Boolean) -> Unit)
}

/** Versión sin anuncios (release sin identificadores reales, o compra de «quitar anuncios»). */
object NoAds : AdsController {
    override val bannersEnabled: StateFlow<Boolean> = MutableStateFlow(false)
    override val bannerUnitId: String = ""
    override fun afterGame(activity: Activity, gameKey: String, proceed: () -> Unit) = proceed()
    override val rewardedReady: StateFlow<Boolean> = MutableStateFlow(false)
    override fun showRewarded(activity: Activity, userId: String, onClosed: (earned: Boolean) -> Unit) = onClosed(false)
}

class AdMobAdsController(
    context: Context,
    private val consent: AdsConsent,
    /** No se pide ningún anuncio hasta saber si quien juega es menor de 13. */
    private val ageStatus: StateFlow<AgeStatus>,
    billing: BillingRepository,
    private val scope: CoroutineScope,
    private val interstitialUnitId: String,
    override val bannerUnitId: String,
    /** Vacío = sin videos con recompensa en esta compilación. */
    private val rewardedUnitId: String,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : AdsController {
    private val appContext = context.applicationContext
    private val adsRemoved = billing.entitlements
    private val initialized = MutableStateFlow(false)
    private var initStarted = false

    private var pacing = InterstitialPacing()
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var showing = false

    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false
    private val _rewardedReady = MutableStateFlow(false)
    override val rewardedReady: StateFlow<Boolean> = _rewardedReady

    override val bannersEnabled: StateFlow<Boolean> =
        combine(initialized, consent.canRequestAds, adsRemoved) { init, can, removed -> init && can && !removed.removeAds }
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // Si el consentimiento ya estaba resuelto de una sesión anterior, se puede empezar ya.
        scope.launch {
            combine(consent.canRequestAds, ageStatus) { can, age -> can && age is AgeStatus.Known }
                .collect { if (it) initialize() }
        }
    }

    /** Inicializa el SDK una sola vez, fuera del hilo principal, y precarga el primer intersticial. */
    private fun initialize() {
        if (initStarted) return
        initStarted = true
        MobileAds.setRequestConfiguration(requestConfiguration(child))
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(appContext) {}
            withContext(Dispatchers.Main) {
                initialized.value = true
                loadInterstitial()
                loadRewarded()
            }
        }
    }

    private fun loadInterstitial() {
        if (!initialized.value || loading || interstitial != null || adsRemoved.value.removeAds) return
        if (!consent.canRequestAds.value) return
        loading = true
        InterstitialAd.load(
            appContext,
            interstitialUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    // Sin reintentos en bucle: se vuelve a intentar tras la próxima partida.
                    Log.i(TAG, "Intersticial no disponible (código ${error.code})")
                }
            },
        )
    }

    private fun loadRewarded() {
        if (rewardedUnitId.isEmpty() || child || !initialized.value || rewardedLoading || rewarded != null) return
        if (!consent.canRequestAds.value) return
        rewardedLoading = true
        RewardedAd.load(
            appContext,
            rewardedUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    rewarded = ad
                    _rewardedReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                    Log.i(TAG, "Video con recompensa no disponible (código ${error.code})")
                }
            },
        )
    }

    override fun showRewarded(activity: Activity, userId: String, onClosed: (earned: Boolean) -> Unit) {
        val ad = rewarded
        if (ad == null || showing) {
            loadRewarded()
            onClosed(false)
            return
        }
        rewarded = null
        _rewardedReady.value = false
        showing = true
        var earned = false
        ad.setServerSideVerificationOptions(ServerSideVerificationOptions.Builder().setUserId(userId).build())
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finish()

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.i(TAG, "No se pudo mostrar el video (código ${error.code})")
                finish()
            }

            private fun finish() {
                showing = false
                loadRewarded()
                onClosed(earned)
            }
        }
        ad.show(activity) { earned = true }
    }

    override fun afterGame(activity: Activity, gameKey: String, proceed: () -> Unit) {
        pacing = pacing.gameFinished(gameKey)
        val ad = interstitial
        val now = clock()
        if (showing || ad == null || adsRemoved.value.removeAds || !consent.canRequestAds.value || !pacing.canShow(now)) {
            loadInterstitial()
            proceed()
            return
        }
        interstitial = null
        showing = true
        pacing = pacing.shown(now)
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finish()

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.i(TAG, "No se pudo mostrar el intersticial (código ${error.code})")
                finish()
            }

            private fun finish() {
                showing = false
                loadInterstitial()
                proceed()
            }
        }
        ad.show(activity)
    }

    /** Menor de 13 según la pregunta de edad. */
    private val child: Boolean get() = (ageStatus.value as? AgeStatus.Known)?.group == AgeGroup.CHILD

    private companion object {
        const val TAG = "Ads"
    }
}

/**
 * Configuración de AdMob según la edad. Para menores de 13 (política de Familias y COPPA): anuncios
 * dirigidos a niños, sin personalizar ni identificador de publicidad, y solo contenido para todos.
 * Para el resto: contenido apto para todos los públicos con supervisión, como antes.
 */
internal fun requestConfiguration(child: Boolean): RequestConfiguration {
    val builder = RequestConfiguration.Builder()
    if (child) {
        builder
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
            .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
    } else {
        builder.setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
    }
    return builder.build()
}
