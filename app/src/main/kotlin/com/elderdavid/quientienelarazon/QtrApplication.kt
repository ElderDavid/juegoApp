package com.elderdavid.quientienelarazon

import android.app.Application
import com.elderdavid.quientienelarazon.settings.DataStoreSettingsRepository
import com.elderdavid.quientienelarazon.settings.SettingsRepository
import com.elderdavid.quientienelarazon.settings.settingsDataStore

/**
 * Contenedor de dependencias manual: suficiente para el MVP y evita añadir Hilt.
 * Nada aquí requiere red, por lo que el modo local nunca queda bloqueado.
 */
class AppContainer(application: Application) {
    val settingsRepository: SettingsRepository =
        DataStoreSettingsRepository(application.settingsDataStore)
}

class QtrApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
