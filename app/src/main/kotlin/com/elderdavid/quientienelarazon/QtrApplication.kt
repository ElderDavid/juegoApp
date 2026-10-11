package com.elderdavid.quientienelarazon

import android.app.Application
import com.elderdavid.quientienelarazon.core.questions.QuestionBank
import com.elderdavid.quientienelarazon.core.questions.QuestionBankLoader
import com.elderdavid.quientienelarazon.settings.DataStoreSettingsRepository
import com.elderdavid.quientienelarazon.settings.SettingsRepository
import com.elderdavid.quientienelarazon.settings.settingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Contenedor de dependencias manual: suficiente para el MVP y evita añadir Hilt.
 * Nada aquí requiere red, por lo que el modo local nunca queda bloqueado.
 */
class AppContainer(application: Application) {
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
