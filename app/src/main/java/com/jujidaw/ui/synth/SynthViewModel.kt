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
import com.jujidaw.model.defaultTrackSynthState
import com.jujidaw.model.toParamsArray
import com.jujidaw.project.PadSynthSessionStore
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
    /** Index of the pad whose synth is being edited (-1 = global synth on channel 0). */
    val selectedPadIndex: Int = -1,
    val midiLearnState: MidiLearnState = MidiLearnState(),
    val showPresetBrowser: Boolean = false,
    val showSaveDialog: Boolean = false,
    val savePresetName: String = "",
    val savePresetCategory: String = "Leads",
    val presetCategory: String = "All",
    val toastMessage: String? = null,
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
    private val presetDao: PresetDao = JujiDawApp.instance.database.presetDao(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(SynthUiState())
    val uiState: StateFlow<SynthUiState> = _uiState.asStateFlow()

    private val _presets = MutableStateFlow<List<PresetEntity>>(emptyList())
    val presets: StateFlow<List<PresetEntity>> = _presets.asStateFlow()

    private val presetJson = Json { encodeDefaults = true }

    /** Per-track SynthState map for multi-timbral routing. */
    var trackStates: MutableMap<Int, SynthState> = mutableMapOf(0 to defaultTrackSynthState())

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
        val state = trackStates.getOrPut(index) { defaultTrackSynthState() }
        _uiState.value =
            _uiState.value.copy(
                selectedTrack = index,
                selectedPadIndex = -1, // switch back to global synth
                synthState = state,
            )
        applySynthStateToEngine(state)
    }

    /** Select a pad's synth for editing (0..31 across Banks A and B). */
    fun selectPad(padIndex: Int) {
        if (padIndex !in 0..31) return
        val state = PadSynthSessionStore.snapshot()[padIndex] ?: defaultTrackSynthState()
        PadSynthSessionStore.setPadState(padIndex, state)
        _uiState.value =
            _uiState.value.copy(
                selectedPadIndex = padIndex,
                synthState = state,
            )
        // Push the state to the per-pad synth via the full-state API.
        SynthEngine.applyPadSynthState(padIndex, state.toParamsArray())
        SynthEngine.setPadSynthEnabled(padIndex, true)
    }

    /** Replace the current synth state (e.g. after parameter changes from panels). */
    fun updateSynthState(newState: SynthState) {
        val padIdx = _uiState.value.selectedPadIndex
        _uiState.value = _uiState.value.copy(synthState = newState)
        if (padIdx >= 0) {
            // Route to per-pad synth
            PadSynthSessionStore.setPadState(padIdx, newState)
            SynthEngine.applyPadSynthState(padIdx, newState.toParamsArray())
        } else {
            // Route to global synth (channel 0)
            val track = _uiState.value.selectedTrack
            trackStates[track] = newState
            applySynthStateToEngine(newState)
        }
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
            // updateSynthState routes the snapshot to the selected pad when
            // editing pad-synth mode, otherwise to the global synth.
            updateSynthState(loadedState)
            showToast("Loaded: ${preset.name}")
        } catch (_: Exception) {
            showToast("Failed to load preset")
        }
    }

    /** Save the current synth state as a new user preset. */
    fun savePreset(
        name: String,
        category: String = "Leads",
    ) {
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
                    parametersJson = jsonStr,
                ),
            )
        }
        _uiState.value = _uiState.value.copy(showSaveDialog = false, savePresetName = "")
        showToast("Saved: ${name.trim()}")
    }

    /** Toggle the MIDI learn workflow. */
    fun toggleMidiLearn() {
        val current = _uiState.value.midiLearnState
        val nextMode =
            when (current.mode) {
                MidiLearnMode.IDLE -> MidiLearnMode.LEARN_ACTIVE
                else -> MidiLearnMode.IDLE
            }
        _uiState.value =
            _uiState.value.copy(
                midiLearnState = MidiLearnState(mode = nextMode, selectedParamId = null),
            )
    }

    /** Mark a parameter as selected and await a MIDI CC message. */
    fun selectParamForLearn(paramId: Int) {
        _uiState.value =
            _uiState.value.copy(
                midiLearnState =
                    MidiLearnState(
                        mode = MidiLearnMode.CONTROL_SELECTED,
                        selectedParamId = paramId,
                    ),
            )
    }

    /** Clear MIDI learn state. */
    fun clearMidiLearn() {
        _uiState.value =
            _uiState.value.copy(
                midiLearnState = MidiLearnState(mode = MidiLearnMode.IDLE, selectedParamId = null),
            )
    }

    /** Persist a MIDI mapping and apply the incoming value. */
    fun confirmMidiLearn(
        ccNumber: Int,
        value: Float,
    ) {
        val paramId = _uiState.value.midiLearnState.selectedParamId ?: return
        viewModelScope.launch {
            midiMappingStore?.addMapping(
                com.jujidaw.data.MidiMapping(ccNumber = ccNumber, paramId = paramId),
            )
        }
        val padIdx = _uiState.value.selectedPadIndex
        if (padIdx >= 0) {
            SynthEngine.setPadSynthParam(padIdx, paramId, value)
        } else {
            SynthEngine.setParam(paramId, value)
        }
        _uiState.value =
            _uiState.value.copy(
                midiLearnState = MidiLearnState(mode = MidiLearnMode.LEARN_ACTIVE, selectedParamId = null),
            )
    }

    /** Update a single modulation route and sync it to the engine. */
    fun updateModulationRoute(
        index: Int,
        route: ModulationRoute,
    ) {
        val current = _uiState.value.synthState
        val newRoutes = current.modulationRoutes.toMutableList().apply { set(index, route) }
        val newState = current.copy(modulationRoutes = newRoutes)
        _uiState.value = _uiState.value.copy(synthState = newState)
        val padIdx = _uiState.value.selectedPadIndex
        if (padIdx >= 0) {
            PadSynthSessionStore.setPadState(padIdx, newState)
            SynthEngine.applyPadSynthState(padIdx, newState.toParamsArray())
        } else {
            SynthEngine.setModulationRoute(index, route.source, route.destination, route.amount, route.active)
        }
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
            SynthEngine.applySynthState(state.toParamsArray())
            state.modulationRoutes.forEachIndexed { idx, route ->
                SynthEngine.setModulationRoute(idx, route.source, route.destination, route.amount, route.active)
            }
        }
    }
}
