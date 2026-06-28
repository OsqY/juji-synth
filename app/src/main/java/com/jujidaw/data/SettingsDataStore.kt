package com.jujidaw.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val sampleRate: Int = 44100,
    val bufferSize: Int = 256,
    val outputMode: String = "stereo"
)

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "audio_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val SAMPLE_RATE = intPreferencesKey("sample_rate")
        private val BUFFER_SIZE = intPreferencesKey("buffer_size")
        private val OUTPUT_MODE = stringPreferencesKey("output_mode")
        private val LAST_PROJECT = stringPreferencesKey("last_project")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            sampleRate = preferences[SAMPLE_RATE] ?: 44100,
            bufferSize = preferences[BUFFER_SIZE] ?: 256,
            outputMode = preferences[OUTPUT_MODE] ?: "stereo"
        )
    }

    suspend fun getLastProjectName(): String? {
        return context.settingsDataStore.data.first()[LAST_PROJECT]
    }

    suspend fun setLastProjectName(name: String?) {
        context.settingsDataStore.edit { preferences ->
            if (name == null) preferences.remove(LAST_PROJECT) else preferences[LAST_PROJECT] = name
        }
    }

    suspend fun saveSettings(sampleRate: Int, bufferSize: Int, outputMode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[SAMPLE_RATE] = sampleRate
            preferences[BUFFER_SIZE] = bufferSize
            preferences[OUTPUT_MODE] = outputMode
        }
    }

    suspend fun getSettings(): AppSettings {
        val preferences = context.settingsDataStore.data.first()
        return AppSettings(
            sampleRate = preferences[SAMPLE_RATE] ?: 44100,
            bufferSize = preferences[BUFFER_SIZE] ?: 256,
            outputMode = preferences[OUTPUT_MODE] ?: "stereo"
        )
    }
}
