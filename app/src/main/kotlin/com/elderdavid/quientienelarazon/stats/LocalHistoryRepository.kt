package com.elderdavid.quientienelarazon.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.elderdavid.quientienelarazon.core.game.GameState
import com.elderdavid.quientienelarazon.core.profile.LocalHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Partidas locales jugadas en este teléfono. Solo vive aquí: no se sube al servidor ni se mezcla
 * con las estadísticas de la cuenta (ver [LocalHistory]).
 */
interface LocalHistoryRepository {
    val history: Flow<LocalHistory>
    suspend fun record(finished: GameState)
    suspend fun clear()
}

/** Archivo propio, separado de los ajustes, para poder borrarlo sin tocar las preferencias. */
val Context.localHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "local_history")

class DataStoreLocalHistoryRepository(
    private val dataStore: DataStore<Preferences>,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : LocalHistoryRepository {

    private val key = stringPreferencesKey("history_v1")

    override val history: Flow<LocalHistory> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { LocalHistory.decode(it[key]) }

    override suspend fun record(finished: GameState) {
        // edit es atómico: dos partidas seguidas no pueden pisarse el registro.
        dataStore.edit { prefs ->
            prefs[key] = LocalHistory.decode(prefs[key]).record(finished, wallClock()).encode()
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }
}
