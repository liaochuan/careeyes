package org.liaocl.careeyes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

class StatsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val key = stringPreferencesKey("stats_json")

    val stats: StateFlow<StatsData> = appContext.careEyesDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences -> decodeStats(preferences[key]) }
        .stateIn(careEyesIoScope, SharingStarted.Eagerly, StatsData())

    suspend fun addFocusSeconds(seconds: Long) {
        if (seconds <= 0L) return
        appContext.careEyesDataStore.edit { preferences ->
            val current = decodeStats(preferences[key])
            preferences[key] = encode(current.recordFocus(seconds))
        }
    }

    suspend fun recordBreak(completed: Boolean, natural: Boolean = false) {
        appContext.careEyesDataStore.edit { preferences ->
            val current = decodeStats(preferences[key])
            preferences[key] = encode(current.recordBreak(completed = completed, natural = natural))
        }
    }

    private fun encode(stats: StatsData): String =
        careEyesJson.encodeToString(StatsData.serializer(), stats)

    private fun decodeStats(raw: String?): StatsData {
        if (raw.isNullOrBlank()) return StatsData()
        return runCatching { careEyesJson.decodeFromString(StatsData.serializer(), raw) }
            .getOrDefault(StatsData())
    }
}
