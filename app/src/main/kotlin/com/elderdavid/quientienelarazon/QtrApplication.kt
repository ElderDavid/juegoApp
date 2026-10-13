package com.elderdavid.quientienelarazon

import android.app.Application
import com.elderdavid.quientienelarazon.account.AccountRepository
import com.elderdavid.quientienelarazon.account.ProfileRepository
import com.elderdavid.quientienelarazon.account.SupabaseAccountRepository
import com.elderdavid.quientienelarazon.account.SupabaseProfileRepository
import com.elderdavid.quientienelarazon.account.SupabaseSettings
import com.elderdavid.quientienelarazon.ads.AdMobAdsController
import com.elderdavid.quientienelarazon.ads.AgeGateStore
import com.elderdavid.quientienelarazon.ads.AdsConsent
import com.elderdavid.quientienelarazon.ads.AdsController
import com.elderdavid.quientienelarazon.ads.NoAds
import com.elderdavid.quientienelarazon.billing.BillingRepository
import com.elderdavid.quientienelarazon.billing.PlayBillingRepository
import com.elderdavid.quientienelarazon.billing.UnavailableBillingRepository
import com.elderdavid.quientienelarazon.economy.EconomyCache
import com.elderdavid.quientienelarazon.economy.EconomyRepository
import com.elderdavid.quientienelarazon.economy.SupabaseEconomyRepository
import com.elderdavid.quientienelarazon.economy.economyCacheDataStore
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import com.elderdavid.quientienelarazon.core.questions.QuestionBankLoader
import com.elderdavid.quientienelarazon.online.OnlineRepository
import com.elderdavid.quientienelarazon.online.SupabaseOnlineRepository
import com.elderdavid.quientienelarazon.settings.DataStoreSettingsRepository
import com.elderdavid.quientienelarazon.settings.SettingsRepository
import com.elderdavid.quientienelarazon.settings.settingsDataStore
import com.elderdavid.quientienelarazon.stats.DataStoreLocalHistoryRepository
import com.elderdavid.quientienelarazon.stats.LeaderboardRepository
import com.elderdavid.quientienelarazon.stats.LocalHistoryRepository
import com.elderdavid.quientienelarazon.stats.SupabaseLeaderboardRepository
import com.elderdavid.quientienelarazon.stats.localHistoryDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Contenedor de dependencias manual: suficiente para el MVP y evita añadir Hilt.
 * El modo local no depende de nada que use la red: si Supabase no está configurado o no hay
 * conexión, solo las funciones online quedan desactivadas.
 */
class AppContainer(application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val supabaseSettings = SupabaseSettings(
        url = BuildConfig.SUPABASE_URL,
        publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
    )

    private val supabase by lazy { if (supabaseSettings.isConfigured) supabaseSettings.createClient() else null }

    /** null cuando la compilación no tiene configuración de Supabase. */
    val accountRepository: AccountRepository? by lazy { supabase?.let { SupabaseAccountRepository(it, appScope) } }
    val profileRepository: ProfileRepository? by lazy { supabase?.let { SupabaseProfileRepository(it) } }
    val onlineRepository: OnlineRepository? by lazy {
        val client = supabase ?: return@lazy null
        accountRepository?.let { SupabaseOnlineRepository(client, it) }
    }

    val leaderboardRepository: LeaderboardRepository? by lazy { supabase?.let { SupabaseLeaderboardRepository(it) } }

    val settingsRepository: SettingsRepository =
        DataStoreSettingsRepository(application.settingsDataStore)

    /** Respuesta a la pregunta de edad (niño o no). Decide cómo se piden los anuncios. */
    val ageGate = AgeGateStore(application.settingsDataStore, appScope)

    /** Partidas locales de este teléfono. Fuente de verdad separada de las estadísticas de la cuenta. */
    val localHistoryRepository: LocalHistoryRepository =
        DataStoreLocalHistoryRepository(application.localHistoryDataStore)

    /** Monedas, premios y cosméticos. null sin configuración de Supabase. */
    val economyRepository: EconomyRepository? by lazy {
        val client = supabase ?: return@lazy null
        accountRepository?.let { SupabaseEconomyRepository(client, it) }
    }

    /** Copia local de lo equipado y de «Quitar anuncios», para que funcionen sin conexión. */
    val economyCache = EconomyCache(application.economyCacheDataStore)

    /**
     * Facturación de Google Play. Sin servidor no se puede verificar una compra, y una compra
     * sin verificar no se debe reconocer: entonces no se vende nada.
     */
    val billingRepository: BillingRepository = economyRepository?.let { economy ->
        PlayBillingRepository(
            context = application,
            scope = appScope,
            verify = economy::verifyPurchase,
            accountId = economy::userId,
            loadCachedRemoveAds = { economyCache.removeAds.first() },
            saveRemoveAds = economyCache::saveRemoveAds,
        )
    } ?: UnavailableBillingRepository()

    /** null si esta compilación no muestra anuncios (release sin identificadores de AdMob). */
    val adsConsent: AdsConsent? = if (BuildConfig.ADS_ENABLED) AdsConsent(application) else null

    val ads: AdsController = adsConsent?.let { consent ->
        AdMobAdsController(
            context = application,
            consent = consent,
            ageStatus = ageGate.status,
            billing = billingRepository,
            // Hilo principal: el SDK de anuncios exige cargar y mostrar desde él.
            scope = MainScope(),
            interstitialUnitId = BuildConfig.ADMOB_INTERSTITIAL_ID,
            bannerUnitId = BuildConfig.ADMOB_BANNER_ID,
            // Sin servidor no hay quién pague la recompensa: entonces no se ofrecen videos.
            rewardedUnitId = if (supabaseSettings.isConfigured) BuildConfig.ADMOB_REWARDED_ID else "",
        )
    } ?: NoAds

    init {
        // Lo equipado se guarda en el teléfono cada vez que llega del servidor.
        appScope.launch {
            economyRepository?.snapshot?.filterNotNull()?.collect { economyCache.saveEquipped(it.equipped) }
        }
    }

    private val bankLock = Mutex()
    private var bank: QuestionBank? = null

    /** Banco de preguntas incluido en la app. Se lee una sola vez (lo valida QuestionBankContentTest al compilar), fuera del hilo principal. */
    suspend fun questionBank(): QuestionBank = bankLock.withLock {
        bank ?: withContext(Dispatchers.Default) { QuestionBankLoader.fromResources() }.also { bank = it }
    }
}

class QtrApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
