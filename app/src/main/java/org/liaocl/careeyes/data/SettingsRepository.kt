package org.liaocl.careeyes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import java.io.IOException

internal val Context.careEyesDataStore by preferencesDataStore(name = "careeyes")

internal val careEyesJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

internal val careEyesIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val key = stringPreferencesKey("settings_json")

    val settings: StateFlow<AppSettings> = appContext.careEyesDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences -> decodeSettings(preferences[key]) }
        .stateIn(careEyesIoScope, SharingStarted.Eagerly, AppSettings())

    /** Reads persisted settings directly; unlike [settings], this never returns the initial default. */
    suspend fun load(): AppSettings = try {
        decodeSettings(appContext.careEyesDataStore.data.first()[key])
    } catch (_: IOException) {
        AppSettings()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        appContext.careEyesDataStore.edit { preferences ->
            val current = decodeSettings(preferences[key])
            preferences[key] = careEyesJson.encodeToString(
                AppSettings.serializer(),
                transform(current).normalized(),
            )
        }
    }

    private fun decodeSettings(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings()
        return runCatching { careEyesJson.decodeFromString(AppSettings.serializer(), raw) }
            .getOrDefault(AppSettings())
            .normalized()
    }
}
