package com.elderdavid.quientienelarazon.economy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.elderdavid.quientienelarazon.core.economy.CosmeticKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Cosméticos equipados, por tipo. Se guardan en el teléfono para que funcionen sin conexión. */
data class EquippedCosmetics(val byKind: Map<CosmeticKind, String> = emptyMap()) {
    operator fun get(kind: CosmeticKind): String? = byKind[kind]
}

val Context.economyCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "economy_cache")

/**
 * Copia local de lo que el servidor dice que está equipado, y de si «Quitar anuncios» ya se
 * verificó. No es la fuente de verdad: el servidor y Google Play lo son. Solo evita que el
 * tema o los anuncios cambien al abrir la app sin conexión.
 */
class EconomyCache(private val dataStore: DataStore<Preferences>) {
    private fun key(kind: CosmeticKind) = stringPreferencesKey("equipped_${kind.name.lowercase()}")
    private val removeAdsKey = booleanPreferencesKey("remove_ads")

    private val prefs: Flow<Preferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    val equipped: Flow<EquippedCosmetics> = prefs.map { p ->
        EquippedCosmetics(CosmeticKind.entries.mapNotNull { kind -> p[key(kind)]?.let { kind to it } }.toMap())
    }

    val removeAds: Flow<Boolean> = prefs.map { it[removeAdsKey] ?: false }

    suspend fun saveEquipped(equipped: Map<CosmeticKind, String>) {
        dataStore.edit { p ->
            CosmeticKind.entries.forEach { kind ->
                val id = equipped[kind]
                if (id == null) p.remove(key(kind)) else p[key(kind)] = id
            }
        }
    }

    suspend fun saveRemoveAds(removed: Boolean) {
        dataStore.edit { it[removeAdsKey] = removed }
    }
}
