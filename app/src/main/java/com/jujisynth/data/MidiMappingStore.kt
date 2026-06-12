package com.jujisynth.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** A single MIDI CC → synth parameter mapping. */
@Serializable
data class MidiMapping(
    val ccNumber: Int,
    val paramId: Int,
    val minValue: Float = 0f,
    val maxValue: Float = 1f
)

/** Application-level [DataStore] for MIDI mapping persistence. */
private val Context.mappingStore: DataStore<Preferences> by preferencesDataStore(name = "midi_mappings")

/**
 * Persistent key-value store for MIDI CC-to-parameter mappings.
 *
 * All mappings are serialised as a single JSON array stored under one
 * preferences key.  This keeps the DataStore schema simple and avoids
 * migration complexity as the mapping list grows.
 */
class MidiMappingStore(private val context: Context) {

    companion object {
        private val MAPPINGS_KEY = stringPreferencesKey("mappings")
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Reactive stream of the current mapping list. */
    val mappingsFlow: Flow<List<MidiMapping>> = context.mappingStore.data.map { prefs ->
        val raw = prefs[MAPPINGS_KEY] ?: return@map emptyList()
        try {
            json.decodeFromString<List<MidiMapping>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Replace all mappings. */
    suspend fun saveMappings(mappings: List<MidiMapping>) {
        context.mappingStore.edit { prefs ->
            prefs[MAPPINGS_KEY] = json.encodeToString(mappings)
        }
    }

    /** Remove every mapping. */
    suspend fun clearMappings() {
        context.mappingStore.edit { prefs ->
            prefs.remove(MAPPINGS_KEY)
        }
    }

    /**
     * Add (or overwrite) a single mapping.
     *
     * If a mapping for the same CC number already exists it is replaced.
     */
    suspend fun addMapping(mapping: MidiMapping) {
        val current = loadMappings().toMutableList()
        current.removeAll { it.ccNumber == mapping.ccNumber }
        current.add(mapping)
        saveMappings(current)
    }

    /** Remove every mapping that targets the given [paramId]. */
    suspend fun removeMapping(paramId: Int) {
        val current = loadMappings().toMutableList()
        current.removeAll { it.paramId == paramId }
        saveMappings(current)
    }

    /** Return the active mapping for [ccNumber], or `null`. */
    suspend fun findMapping(ccNumber: Int): MidiMapping? {
        return loadMappings().find { it.ccNumber == ccNumber }
    }

    /** One-shot snapshot of all persisted mappings. */
    suspend fun loadMappings(): List<MidiMapping> {
        val prefs = context.mappingStore.data.first()
        val raw = prefs[MAPPINGS_KEY] ?: return emptyList()
        return try {
            json.decodeFromString<List<MidiMapping>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
