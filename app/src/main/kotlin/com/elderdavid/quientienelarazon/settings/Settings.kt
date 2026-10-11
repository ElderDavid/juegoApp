package com.elderdavid.quientienelarazon.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Preferencias locales del dispositivo. No contienen datos de cuenta ni estadísticas. */
data class AppSettings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setVibrationEnabled(enabled: Boolean)
    suspend fun setReducedMotion(enabled: Boolean)
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    }

    override val settings: Flow<AppSettings> = dataStore.data
        // Un archivo dañado no debe impedir jugar: se usan los valores por defecto.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                soundEnabled = prefs[Keys.SOUND] ?: defaults.soundEnabled,
                vibrationEnabled = prefs[Keys.VIBRATION] ?: defaults.vibrationEnabled,
                reducedMotion = prefs[Keys.REDUCED_MOTION] ?: defaults.reducedMotion,
            )
        }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SOUND] = enabled }
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIBRATION] = enabled }
    }

    override suspend fun setReducedMotion(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
    }
}
