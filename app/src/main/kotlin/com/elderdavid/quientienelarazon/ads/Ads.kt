package com.elderdavid.quientienelarazon.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.elderdavid.quientienelarazon.billing.BillingRepository
import com.elderdavid.quientienelarazon.core.ads.InterstitialPacing
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
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
 * - Banner: solo en pantallas secundarias (Reglas y Estadísticas). Nunca en el juego ni en el menú.
 * No hay anuncios con recompensa: el juego no tiene nada que regalar a cambio.
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
}

/** Versión sin anuncios (release sin identificadores reales, o compra de «quitar anuncios»). */
object NoAds : AdsController {
    override val bannersEnabled: StateFlow<Boolean> = MutableStateFlow(false)
    override val bannerUnitId: String = ""
    override fun afterGame(activity: Activity, gameKey: String, proceed: () -> Unit) = proceed()
}

class AdMobAdsController(
    context: Context,
    private val consent: AdsConsent,
    billing: BillingRepository,
    private val scope: CoroutineScope,
    private val interstitialUnitId: String,
    override val bannerUnitId: String,
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

    override val bannersEnabled: StateFlow<Boolean> =
        combine(initialized, consent.canRequestAds, adsRemoved) { init, can, removed -> init && can && !removed.removeAds }
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // Si el consentimiento ya estaba resuelto de una sesión anterior, se puede empezar ya.
        scope.launch { consent.canRequestAds.collect { if (it) initialize() } }
    }

    /** Inicializa el SDK una sola vez, fuera del hilo principal, y precarga el primer intersticial. */
    private fun initialize() {
        if (initStarted || adsRemoved.value.removeAds) return
        initStarted = true
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                // Juego de preguntas para público general: contenido apto para todos los públicos con supervisión.
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                .build(),
        )
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(appContext) {}
            withContext(Dispatchers.Main) {
                initialized.value = true
                loadInterstitial()
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

    private companion object {
        const val TAG = "Ads"
    }
}
