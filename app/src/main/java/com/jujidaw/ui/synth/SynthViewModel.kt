package com.jujidaw.ui.synth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jujidaw.JujiDawApp
import com.jujidaw.audio.SynthEngine
import com.jujidaw.data.MidiMappingStore
import com.jujidaw.data.PresetDao
import com.jujidaw.data.PresetEntity
import com.jujidaw.model.MidiLearnMode
import com.jujidaw.model.MidiLearnState
import com.jujidaw.model.ModulationRoute
import com.jujidaw.model.SynthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * UI state for the synth screen.
 */
data class SynthUiState(
    val synthState: SynthState = SynthState(),
    val selectedTrack: Int = 0,
    val midiLearnState: MidiLearnState = MidiLearnState(),
    val showPresetBrowser: Boolean = false,
    val showSaveDialog: Boolean = false,
    val savePresetName: String = "",
    val savePresetCategory: String = "Leads",
    val presetCategory: String = "All",
    val toastMessage: String? = null
)

/**
 * ViewModel for the synth screen.
 *
 * Owns the synthesizer parameter state and track selection.
 * All tracks currently share a single [SynthState]; per-track parameter
 * isolation is reserved for a future update.
 *
 * ### Public API
 * - [uiState] – observable screen state
 * - [selectTrack] – switch active synth track (0..15)
 * - [updateSynthState] – apply a new parameter snapshot
 * - [loadPreset], [savePreset] – preset I/O via Room
 * - [toggleMidiLearn], [selectParamForLearn], [clearMidiLearn] – MIDI learn flow
 */
class SynthViewModel(
    private val presetDao: PresetDao = JujiDawApp.instance.database.presetDao()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SynthUiState())
    val uiState: StateFlow<SynthUiState> = _uiState.asStateFlow()

    private val _presets = MutableStateFlow<List<PresetEntity>>(emptyList())
    val presets: StateFlow<List<PresetEntity>> = _presets.asStateFlow()

    private val presetJson = Json { encodeDefaults = true }

    /** External MIDI mapping store; injected from the UI layer because it needs a [Context]. */
    var midiMappingStore: MidiMappingStore? = null
        private set

    init {
        viewModelScope.launch {
            presetDao.getAllPresets().collect { _presets.value = it }
        }
    }

    fun setMidiMappingStore(store: MidiMappingStore) {
        midiMappingStore = store
    }

    /** Switch the active synth track (0..15). */
    fun selectTrack(index: Int) {
        if (index !in 0..15) return
        _uiState.value = _uiState.value.copy(selectedTrack = index)
        // TODO: load per-track state when multi-timbral support is added
    }

    /** Replace the current synth state (e.g. after parameter changes from panels). */
    fun updateSynthState(newState: SynthState) {
        _uiState.value = _uiState.value.copy(synthState = newState)
    }

    /** Load a preset by name from the database and apply it to the engine. */
    fun loadPreset(name: String) {
        val allPresets = _presets.value
        val preset = allPresets.find { it.name == name }
        if (preset == null || preset.parametersJson == "{}") {
            showToast("Preset not found")
            return
        }
        try {
            val loadedState = presetJson.decodeFromString(SynthState.serializer(), preset.parametersJson)
            _uiState.value = _uiState.value.copy(synthState = loadedState)
            applySynthStateToEngine(loadedState)
            showToast("Loaded: ${preset.name}")
        } catch (_: Exception) {
            showToast("Failed to load preset")
        }
    }

    /** Save the current synth state as a new user preset. */
    fun savePreset(name: String, category: String = "Leads") {
        if (name.isBlank()) return
        val state = _uiState.value.synthState
        val jsonStr = presetJson.encodeToString(SynthState.serializer(), state)
        viewModelScope.launch {
            presetDao.insertPreset(
                PresetEntity(
                    name = name.trim(),
                    category = category,
                    description = "User preset",
                    isFactory = false,
                    parametersJson = jsonStr
                )
            )
        }
        _uiState.value = _uiState.value.copy(showSaveDialog = false, savePresetName = "")
        showToast("Saved: ${name.trim()}")
    }

    /** Toggle the MIDI learn workflow. */
    fun toggleMidiLearn() {
        val current = _uiState.value.midiLearnState
        val nextMode = when (current.mode) {
            MidiLearnMode.IDLE -> MidiLearnMode.LEARN_ACTIVE
            else -> MidiLearnMode.IDLE
        }
        _uiState.value = _uiState.value.copy(
            midiLearnState = MidiLearnState(mode = nextMode, selectedParamId = null)
        )
    }

    /** Mark a parameter as selected and await a MIDI CC message. */
    fun selectParamForLearn(paramId: Int) {
        _uiState.value = _uiState.value.copy(
            midiLearnState = MidiLearnState(
                mode = MidiLearnMode.CONTROL_SELECTED,
                selectedParamId = paramId
            )
        )
    }

    /** Clear MIDI learn state. */
    fun clearMidiLearn() {
        _uiState.value = _uiState.value.copy(
            midiLearnState = MidiLearnState(mode = MidiLearnMode.IDLE, selectedParamId = null)
        )
    }

    /** Persist a MIDI mapping and apply the incoming value. */
    fun confirmMidiLearn(ccNumber: Int, value: Float) {
        val paramId = _uiState.value.midiLearnState.selectedParamId ?: return
        viewModelScope.launch {
            midiMappingStore?.addMapping(
                com.jujidaw.data.MidiMapping(ccNumber = ccNumber, paramId = paramId)
            )
        }
        SynthEngine.setParam(paramId, value)
        _uiState.value = _uiState.value.copy(
            midiLearnState = MidiLearnState(mode = MidiLearnMode.LEARN_ACTIVE, selectedParamId = null)
        )
    }

    /** Update a single modulation route and sync it to the engine. */
    fun updateModulationRoute(index: Int, route: ModulationRoute) {
        val current = _uiState.value.synthState
        val newRoutes = current.modulationRoutes.toMutableList().apply { set(index, route) }
        val newState = current.copy(modulationRoutes = newRoutes)
        _uiState.value = _uiState.value.copy(synthState = newState)
        SynthEngine.setModulationRoute(index, route.source, route.destination, route.amount, route.active)
    }

    // -- UI visibility helpers --

    fun showPresetBrowser(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPresetBrowser = show)
    }

    fun showSaveDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSaveDialog = show)
    }

    fun setSavePresetName(name: String) {
        _uiState.value = _uiState.value.copy(savePresetName = name)
    }

    fun setPresetCategory(category: String) {
        _uiState.value = _uiState.value.copy(presetCategory = category)
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    private fun showToast(message: String) {
        _uiState.value = _uiState.value.copy(toastMessage = message)
    }

    companion object {
        /** Apply a complete [SynthState] to the native engine. */
        fun applySynthStateToEngine(state: SynthState) {
            SynthEngine.resetEffects()
            val params = floatArrayOf(
                // Oscillators (9)
                state.osc1Level, state.osc2Level, state.osc1Waveform.toFloat(), state.osc2Waveform.toFloat(),
                state.oscDetune, state.subOscLevel, state.noiseLevel, state.oscMix,
                if (state.oscSync) 1f else 0f,
                // Filter (4)
                state.filterCutoff, state.filterResonance, state.filterMode.toFloat(), state.filterEnvAmount,
                // Amp Envelope (4)
                state.ampAttack, state.ampDecay, state.ampSustain, state.ampRelease,
                // Filter Envelope (4)
                state.filterAttack, state.filterDecay, state.filterSustain, state.filterRelease,
                // LFO1 (3) + LFO2 (3)
                state.lfo1Rate, state.lfo1Depth, state.lfo1Waveform.toFloat(),
                state.lfo2Rate, state.lfo2Depth, state.lfo2Waveform.toFloat(),
                // Effects: Reverb (2), Delay (3), Distortion (2), Bypass (1), Chorus (3)
                state.reverbMix, state.reverbDecay,
                state.delayMix, state.delayTime, state.delayFeedback,
                state.distortionDrive, state.distortionMix,
                if (state.effectsBypass) 1f else 0f,
                state.chorusRate, state.chorusDepth, state.chorusMix,
                // Master (1)
                state.masterVolume
            )
            require(params.size == 39) { "Synth parameter array must contain exactly 39 entries" }
            SynthEngine.applySynthState(params)
            state.modulationRoutes.forEachIndexed { idx, route ->
                SynthEngine.setModulationRoute(idx, route.source, route.destination, route.amount, route.active)
            }
        }
    }
}
