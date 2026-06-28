package com.jujidaw.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jujidaw.model.MidiTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * A single MIDI CC → parameter mapping.
 *
 * New-style mappings store the destination in [target].
 * Legacy mappings only stored [paramId]; when [target] is `null` and
 * [paramId] >= 0 the effective target is [MidiTarget.SynthParam].
 *
 * @property ccNumber MIDI controller number (0-127).
 * @property target The mixer / synth / effect target (nullable for legacy compat).
 * @property paramId Legacy synth-param id; used only when [target] is null.
 * @property minValue Minimum output value (default 0.0).
 * @property maxValue Maximum output value (default 1.0).
 */
@Serializable
data class MidiMapping(
    val ccNumber: Int,
    val target: MidiTarget? = null,
    val paramId: Int = -1,
    val minValue: Float = 0f,
    val maxValue: Float = 1f
) {
    /** Resolve the effective target, migrating legacy [paramId] values. */
    fun effectiveTarget(): MidiTarget = target
        ?: if (paramId >= 0) MidiTarget.SynthParam(paramId)
        else MidiTarget.SynthParam(0)
}

/**
 * Application-level [DataStore] for MIDI mapping persistence.
 *
 * All mappings are serialised as a single JSON array stored under one
 * preferences key.  This keeps the DataStore schema simple and avoids
 * migration complexity as the mapping list grows.
 */
class MidiMappingStore(private val context: Context) {

    companion object {
        private val MAPPINGS_KEY = stringPreferencesKey("mappings")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        // Allow serialising sealed-class hierarchies without explicit @SerialName
        // by using the class discriminator.
        classDiscriminator = "type"
    }

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

    /** Remove every mapping that targets the given [target]. */
    suspend fun removeMappingByTarget(target: MidiTarget) {
        val current = loadMappings().toMutableList()
        current.removeAll {
            // Compare structural equality for equal-value data classes
            it.effectiveTarget() == target
        }
        saveMappings(current)
    }

    /** Remove every mapping for the given [ccNumber]. */
    suspend fun removeMapping(ccNumber: Int) {
        val current = loadMappings().toMutableList()
        current.removeAll { it.ccNumber == ccNumber }
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

/** Application-scoped [DataStore] for MIDI mappings. */
private val Context.mappingStore: DataStore<Preferences> by preferencesDataStore(name = "midi_mappings")
