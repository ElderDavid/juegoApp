package com.elderdavid.quientienelarazon.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.elderdavid.quientienelarazon.core.local.RecentQuestions
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Preguntas que ya salieron en partidas locales de este teléfono, para no repetirlas en las
 * siguientes partidas (ver [RecentQuestions]). Solo vive en el teléfono.
 */
interface RecentQuestionsRepository {
    suspend fun load(): RecentQuestions
    suspend fun save(recent: RecentQuestions)
}

/** Sin almacenamiento: recuerda las preguntas mientras la pantalla de juego esté abierta. */
class InMemoryRecentQuestionsRepository : RecentQuestionsRepository {
    private var recent = RecentQuestions()
    override suspend fun load(): RecentQuestions = recent
    override suspend fun save(recent: RecentQuestions) {
        this.recent = recent
    }
}

val Context.recentQuestionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_questions")

class DataStoreRecentQuestionsRepository(private val dataStore: DataStore<Preferences>) : RecentQuestionsRepository {

    private val key = stringPreferencesKey("recent_v1")

    override suspend fun load(): RecentQuestions = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { RecentQuestions.decode(it[key]) }
        .first()

    override suspend fun save(recent: RecentQuestions) {
        dataStore.edit { it[key] = recent.encode() }
    }
}
