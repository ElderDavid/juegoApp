package com.elderdavid.quientienelarazon

import android.app.Application
import com.elderdavid.quientienelarazon.account.AccountRepository
import com.elderdavid.quientienelarazon.account.ProfileRepository
import com.elderdavid.quientienelarazon.account.SupabaseAccountRepository
import com.elderdavid.quientienelarazon.account.SupabaseProfileRepository
import com.elderdavid.quientienelarazon.account.SupabaseSettings
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import com.elderdavid.quientienelarazon.core.questions.QuestionBankLoader
import com.elderdavid.quientienelarazon.settings.DataStoreSettingsRepository
import com.elderdavid.quientienelarazon.settings.SettingsRepository
import com.elderdavid.quientienelarazon.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    val settingsRepository: SettingsRepository =
        DataStoreSettingsRepository(application.settingsDataStore)

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
