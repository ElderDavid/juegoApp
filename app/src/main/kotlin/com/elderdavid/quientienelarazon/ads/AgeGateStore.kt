package com.elderdavid.quientienelarazon.ads

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.elderdavid.quientienelarazon.core.ads.AgeGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

/** Estado de la pregunta de edad. [Loading] mientras se lee el almacenamiento del teléfono. */
sealed interface AgeStatus {
    data object Loading : AgeStatus
    data object Unknown : AgeStatus
    data class Known(val group: AgeGroup) : AgeStatus
}

/**
 * Respuesta a la pregunta de edad, guardada en el teléfono. Solo se guarda el grupo (niño o no),
 * nunca el año. No se puede cambiar desde la app: para volver a preguntar hay que borrar los datos.
 */
class AgeGateStore(private val dataStore: DataStore<Preferences>, scope: CoroutineScope) {
    private val key = stringPreferencesKey("age_group")

    val status: StateFlow<AgeStatus> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            prefs[key]?.let { name -> AgeGroup.entries.firstOrNull { it.name == name } }
                ?.let { AgeStatus.Known(it) } ?: AgeStatus.Unknown
        }
        .stateIn(scope, SharingStarted.Eagerly, AgeStatus.Loading)

    suspend fun save(group: AgeGroup) {
        dataStore.edit { it[key] = group.name }
    }
}
