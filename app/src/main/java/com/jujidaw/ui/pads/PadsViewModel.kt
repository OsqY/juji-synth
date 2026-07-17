package com.jujidaw.ui.pads

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jujidaw.audio.AudioConverter
import com.jujidaw.audio.SynthEngine
import com.jujidaw.audio.TimeStretchListener
import com.jujidaw.project.PadParamValues
import com.jujidaw.project.PadSessionStore
import com.jujidaw.project.PadSettings
import com.jujidaw.project.PadSynthSessionStore
import com.jujidaw.project.PadSelectionStore
import com.jujidaw.JujiDawApp
import kotlinx.serialization.json.Json
import com.jujidaw.model.defaultTrackSynthState
import com.jujidaw.model.toParamsArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private const val NUM_PADS = 32

/**
 * Pad parameter IDs (must match JniBridge.cpp nativeSetPadParam switch).
 */
object PadParamIds {
    const val PITCH = 0
    const val PAN = 1
    const val VOLUME = 2
    const val ATTACK = 3
    const val RELEASE = 4
    const val FILTER_CUTOFF = 5
    const val FILTER_RESONANCE = 6
    const val REVERSE = 7
    const val LOOP = 8
    const val ONE_SHOT = 9
    const val USE_FILTER = 10
    const val SYNTH_MODE = 11
    const val SYNTH_ROOT_NOTE = 12

    // TODO: Engine does not yet support slice start/end or choke group.
    // Add these constants (and corresponding JNI/C++ fields) when available.
    // const val SLICE_START = 13
    // const val SLICE_END = 14
    // const val CHOKE_GROUP = 15
}

/**
 * Cached per-pad parameters kept in the ViewModel because the engine has no getters.
 */
data class PadParams(
    val pitch: Float = 0f, // semitones
    val pan: Float = 0f, // -1..1
    val volume: Float = 1f, // 0..1
    val attack: Float = 0f, // 0..1
    val release: Float = 0f, // 0..1
    val filterCutoff: Float = 1f, // 0..1
    val filterResonance: Float = 0f, // 0..1
    val reverse: Boolean = false,
    val loop: Boolean = false,
    val oneShot: Boolean = true,
    val useFilter: Boolean = false,
    val synthMode: Boolean = false,
    val synthRootNote: Int = 60, // C4
    val sliceStart: Float = 0f, // TODO: engine support
    val sliceEnd: Float = 1f, // TODO: engine support
    val chokeGroup: Int = 0, // TODO: engine support
)

data class PadsUiState(
    val currentBank: Int = 0, // 0 = Bank A, 1 = Bank B
    val selectedPad: Int = 0, // 0..15 within current bank
    val activePads: Set<Int> = emptySet(), // 0..15 within current bank (visually held)
    val padLoaded: List<Boolean> = List(32) { false },
    val padNames: List<String> = List(32) { "Pad ${it + 1}" },
    val synthPresetNames: List<String> = List(32) { "" },
    val showEditSheet: Boolean = false,
    val showTimeStretchDialog: Boolean = false,
    val timeStretchBpm: String = "120",
    val timeStretchOriginalBpm: String = "120",
    val isTimeStretching: Boolean = false,
    val toastMessage: String? = null,
    val padParams: List<PadParams> = List(32) { PadParams() },
)

class PadsViewModel :
    ViewModel(),
    TimeStretchListener {
    private val _uiState = MutableStateFlow(PadsUiState())
    val uiState: StateFlow<PadsUiState> = _uiState

    init {
        SynthEngine.setTimeStretchListener(this)
        // Reconcile with any pad state restored by ProjectAutosave.autoLoad.
        // autoLoad loads samples into the engine and publishes paths/params to
        // PadSessionStore; this collector syncs UI and loads samples for pads
        // the store knows about that are not yet active here (handles both
        // orders: VM created before or after autoLoad runs).
        viewModelScope.launch {
            PadSessionStore.state.collect { pads ->
                reconcilePadStore(pads)
            }
        }
    }

    private fun reconcilePadStore(pads: List<PadSettings>) {
        val cur = _uiState.value
        var changed = false
        val loaded = cur.padLoaded.toMutableList()
        val names = cur.padNames.toMutableList()
        val presetNames = cur.synthPresetNames.toMutableList()
        val params = cur.padParams.toMutableList()
        for (i in 0 until NUM_PADS) {
            val s = pads.getOrNull(i) ?: PadSettings()
            if (s.samplePath.isNotEmpty()) {
                if (!loaded[i]) {
                    SynthEngine.loadSampleToPad(s.samplePath, i)
                    loaded[i] = true
                    names[i] = s.name.ifEmpty { "Pad ${i + 1}" }
                    changed = true
                } else if (names[i] != s.name && s.name.isNotEmpty()) {
                    names[i] = s.name
                    changed = true
                }
            } else if (loaded[i] && s.name.isEmpty()) {
                // Persisted as unloaded but UI thinks loaded: keep UI (user may
                // have imported during this session without a store write yet).
            }
            if (presetNames[i] != s.synthPresetName) {
                presetNames[i] = s.synthPresetName
                changed = true
            }
            val newParams = fromValues(s.params)
            if (params[i] != newParams) {
                params[i] = newParams
                changed = true
            }
        }
        if (changed) {
            _uiState.update { it.copy(padLoaded = loaded, padNames = names, synthPresetNames = presetNames, padParams = params) }
        }
    }

    /** Publish the current UI pad state (path + params) for one pad to the store. */
    private fun publishPad(globalIndex: Int) {
        val cur = _uiState.value
        val existing = PadSessionStore.snapshot().getOrNull(globalIndex) ?: PadSettings()
        val params = cur.padParams.getOrNull(globalIndex) ?: PadParams()
        val name = cur.padNames.getOrNull(globalIndex) ?: "Pad ${globalIndex + 1}"
        PadSessionStore.setPad(
            globalIndex,
            PadSettings(existing.samplePath, name, toValues(params), existing.synthPresetName),
        )
    }

    private fun toValues(p: PadParams): PadParamValues =
        PadParamValues(
            pitch = p.pitch,
            pan = p.pan,
            volume = p.volume,
            attack = p.attack,
            release = p.release,
            filterCutoff = p.filterCutoff,
            filterResonance = p.filterResonance,
            reverse = p.reverse,
            loop = p.loop,
            oneShot = p.oneShot,
            useFilter = p.useFilter,
            synthMode = p.synthMode,
            synthRootNote = p.synthRootNote,
            sliceStart = p.sliceStart,
            sliceEnd = p.sliceEnd,
            chokeGroup = p.chokeGroup,
        )

    private fun fromValues(v: PadParamValues): PadParams =
        PadParams(
            pitch = v.pitch,
            pan = v.pan,
            volume = v.volume,
            attack = v.attack,
            release = v.release,
            filterCutoff = v.filterCutoff,
            filterResonance = v.filterResonance,
            reverse = v.reverse,
            loop = v.loop,
            oneShot = v.oneShot,
            useFilter = v.useFilter,
            synthMode = v.synthMode,
            synthRootNote = v.synthRootNote,
            sliceStart = v.sliceStart,
            sliceEnd = v.sliceEnd,
            chokeGroup = v.chokeGroup,
        )

    override fun onCleared() {
        SynthEngine.setTimeStretchListener(null)
        super.onCleared()
    }

    override fun onTimeStretchComplete(
        padIndex: Int,
        success: Boolean,
    ) {
        _uiState.update { it.copy(isTimeStretching = false) }
        if (success) {
            showToast("Time-stretch complete")
        } else {
            showToast("Time-stretch failed")
        }
    }

    /** Switch sampler bank (0=A, 1=B) and sync to engine. */
    fun setBank(bank: Int) {
        val b = bank.coerceIn(0, 1)
        SynthEngine.setSamplerBank(b)
        _uiState.update {
            val selected = it.selectedPad.coerceIn(0, 15)
            PadSelectionStore.select(b * 16 + selected)
            // A held pointer from the previous bank must not leave stale
            // highlights in the newly visible grid.
            it.copy(currentBank = b, selectedPad = selected, activePads = emptySet())
        }
    }

    /** Select a pad (0..15) within the current bank. */
    fun selectPad(padIndex: Int) {
        val localIndex = padIndex.coerceIn(0, 15)
        val globalIndex = _uiState.value.currentBank * 16 + localIndex
        PadSelectionStore.select(globalIndex)
        _uiState.update { it.copy(selectedPad = localIndex) }
    }

    /** Trigger a pad with the given velocity (1..127). */
    fun triggerPad(
        padIndex: Int,
        velocity: Int,
    ) {
        val localIndex = padIndex.coerceIn(0, 15)
        val globalIndex = _uiState.value.currentBank * 16 + localIndex
        SynthEngine.triggerPad(globalIndex, velocity.coerceIn(1, 127))
        _uiState.update { it.copy(activePads = it.activePads + localIndex) }
    }

    /** Release a pad. */
    fun releasePad(padIndex: Int) {
        val localIndex = padIndex.coerceIn(0, 15)
        val globalIndex = _uiState.value.currentBank * 16 + localIndex
        SynthEngine.releasePad(globalIndex)
        _uiState.update { it.copy(activePads = it.activePads - localIndex) }
    }

    /** Toggle the selected pad between sampler and synth mode from the main toolbar. */
    fun toggleSelectedSynthMode() {
        val state = _uiState.value
        val current = state.padParams[state.currentBank * 16 + state.selectedPad].synthMode
        setPadParam(state.selectedPad, PadParamIds.SYNTH_MODE, if (current) 0f else 1f)
        if (!current) showEditSheet()
    }

    /**
     * Pad touch down with Y-position for velocity.
     * @param normalizedY 0.0 = top, 1.0 = bottom
     */
    fun onPadDown(
        padIndex: Int,
        normalizedY: Float,
    ) {
        val velocity = ((1f - normalizedY.coerceIn(0f, 1f)) * 127).toInt().coerceIn(1, 127)
        triggerPad(padIndex, velocity)
    }

    /** Import an audio file from a content URI into the selected pad. */
    fun importSample(
        context: Context,
        uri: Uri?,
    ) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                val globalIndex = _uiState.value.currentBank * 16 + _uiState.value.selectedPad
                val samplesDir =
                    (context.getExternalFilesDir(null) ?: context.filesDir)
                        .resolve("samples")
                        .apply { mkdirs() }

                val wavFile = File(samplesDir, "pad_${globalIndex}_${System.currentTimeMillis()}.wav")
                val converted = AudioConverter.convertToWav(context, uri, wavFile.absolutePath)
                if (!converted) {
                    showToast("Failed to decode audio format")
                    return@launch
                }

                val ok = SynthEngine.loadSampleToPad(wavFile.absolutePath, globalIndex)
                if (ok) {
                    val dispName = wavFile.nameWithoutExtension
                    _uiState.update { state ->
                        val loaded = state.padLoaded.toMutableList().apply { set(globalIndex, true) }
                        val names =
                            state.padNames.toMutableList().apply {
                                set(globalIndex, dispName)
                            }
                        val params = state.padParams
                        state.copy(padLoaded = loaded, padNames = names, padParams = params)
                    }
                    // Persist the absolute sample path + params so the pad
                    // survives an app restart via ProjectAutosave.
                    val params0 = _uiState.value.padParams.getOrElse(globalIndex) { PadParams() }
                    val existing = PadSessionStore.snapshot().getOrNull(globalIndex) ?: PadSettings()
                    PadSessionStore.setPad(
                        globalIndex,
                        existing.copy(samplePath = wavFile.absolutePath, name = dispName, params = toValues(params0)),
                    )
                    showToast("Sample loaded")
                } else {
                    showToast("Import failed: file could not be loaded")
                }
            } catch (e: Exception) {
                showToast("Import error: ${e.message}")
            }
        }
    }

    /**
     * Chop the selected pad's sample into 16 equal slices across the current bank.
     * Delegates to the C++ engine's chopSample implementation.
     */
    fun chopSelectedPad() {
        val state = _uiState.value
        val globalIndex = state.currentBank * 16 + state.selectedPad
        if (!state.padLoaded[globalIndex]) {
            showToast("No sample on selected pad")
            return
        }
        val startPad = state.currentBank * 16
        SynthEngine.chopSample(globalIndex, startPad, 16)
        _uiState.update { s ->
            val loaded =
                s.padLoaded.toMutableList().apply {
                    for (i in 0 until 16) set(startPad + i, true)
                }
            s.copy(padLoaded = loaded)
        }
        showToast("Sample chopped into 16 slices")
    }

    /** Show the time-stretch BPM dialog. */
    fun showTimeStretchDialog() {
        _uiState.update { it.copy(showTimeStretchDialog = true) }
    }

    fun dismissTimeStretchDialog() {
        _uiState.update { it.copy(showTimeStretchDialog = false) }
    }

    fun setTimeStretchBpm(bpm: String) {
        _uiState.update { it.copy(timeStretchBpm = bpm) }
    }

    fun setTimeStretchOriginalBpm(bpm: String) {
        _uiState.update { it.copy(timeStretchOriginalBpm = bpm) }
    }

    /** Apply time-stretch to the selected pad using the BPM ratio. */
    fun applyTimeStretch() {
        val state = _uiState.value
        val globalIndex = state.currentBank * 16 + state.selectedPad
        if (!state.padLoaded[globalIndex]) {
            showToast("No sample on selected pad")
            return
        }
        val bpm = state.timeStretchBpm.toFloatOrNull() ?: 120f
        val origBpm = state.timeStretchOriginalBpm.toFloatOrNull() ?: 120f
        if (origBpm <= 0f) {
            showToast("Invalid original BPM")
            return
        }
        val ratio = bpm / origBpm
        val tempoChangePercent = (ratio - 1f) * 100.0
        _uiState.update { it.copy(isTimeStretching = true) }
        // Pitch preserved (0.0 semitones), rate follows tempo.
        SynthEngine.timeStretchPadAsync(globalIndex, tempoChangePercent, 0.0, 0.0)
    }

    /** Open the pad edit bottom sheet. */
    fun showEditSheet() {
        _uiState.update { it.copy(showEditSheet = true) }
    }

    fun dismissEditSheet() {
        _uiState.update { it.copy(showEditSheet = false) }
    }

    /** Set a pad parameter via JNI and mirror it into local state. */
    fun setPadParam(
        padIndex: Int,
        paramId: Int,
        value: Float,
    ) {
        val globalIndex = _uiState.value.currentBank * 16 + padIndex
        SynthEngine.setPadParam(globalIndex, paramId, value)
        _uiState.update { state ->
            val params = state.padParams.toMutableList()
            val old = params[globalIndex]
            params[globalIndex] =
                when (paramId) {
                    PadParamIds.PITCH -> old.copy(pitch = value)
                    PadParamIds.PAN -> old.copy(pan = value)
                    PadParamIds.VOLUME -> old.copy(volume = value)
                    PadParamIds.ATTACK -> old.copy(attack = value)
                    PadParamIds.RELEASE -> old.copy(release = value)
                    PadParamIds.FILTER_CUTOFF -> old.copy(filterCutoff = value)
                    PadParamIds.FILTER_RESONANCE -> old.copy(filterResonance = value)
                    PadParamIds.REVERSE -> old.copy(reverse = value > 0.5f)
                    PadParamIds.LOOP -> old.copy(loop = value > 0.5f)
                    PadParamIds.ONE_SHOT -> old.copy(oneShot = value > 0.5f)
                    PadParamIds.USE_FILTER -> old.copy(useFilter = value > 0.5f)
                    PadParamIds.SYNTH_MODE -> old.copy(synthMode = value > 0.5f)
                    PadParamIds.SYNTH_ROOT_NOTE -> old.copy(synthRootNote = value.toInt())
                    else -> old
                }
            state.copy(padParams = params)
        }

        // Enable/disable per-pad synth engine on mode toggle.
        if (paramId == PadParamIds.SYNTH_MODE) {
            if (value > 0.5f) {
                val state = PadSynthSessionStore.snapshot()[globalIndex] ?: defaultTrackSynthState()
                PadSynthSessionStore.setPadState(globalIndex, state)
                SynthEngine.setPadSynthEnabled(globalIndex, true)
                // Apply default synth params so the pad makes audible sound.
                SynthEngine.applyPadSynthState(globalIndex, state.toParamsArray())
            } else {
                SynthEngine.setPadSynthEnabled(globalIndex, false)
            }
        }
        publishPad(globalIndex)
        // Publish the synth-mode flag before notifying Keys, so a selected
        // synth pad is immediately recognised as a chromatic target.
        if (paramId == PadParamIds.SYNTH_MODE && value > 0.5f) {
            PadSelectionStore.select(globalIndex)
        }
    }

    /** Load and persist the actual Room preset snapshot for this synth pad. */
    fun loadPadPreset(
        padIndex: Int,
        presetName: String,
    ) {
        val globalIndex = _uiState.value.currentBank * 16 + padIndex
        viewModelScope.launch {
            val preset = JujiDawApp.instance.database.presetDao().getPresetByName(presetName)
            if (preset == null) {
                showToast("Preset not found: $presetName")
                return@launch
            }
            val synthState = runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromString(com.jujidaw.model.SynthState.serializer(), preset.parametersJson)
            }.getOrElse {
                showToast("Could not load: $presetName")
                return@launch
            }
            PadSynthSessionStore.setPadState(globalIndex, synthState)
            SynthEngine.setPadSynthEnabled(globalIndex, true)
            SynthEngine.applyPadSynthState(globalIndex, synthState.toParamsArray())
            val existing = PadSessionStore.snapshot().getOrNull(globalIndex) ?: PadSettings()
            PadSessionStore.setPad(globalIndex, existing.copy(synthPresetName = preset.name))
            _uiState.update { state ->
                val names = state.synthPresetNames.toMutableList()
                names[globalIndex] = preset.name
                state.copy(synthPresetNames = names)
            }
            setPadParam(padIndex, PadParamIds.SYNTH_MODE, 1f)
            showToast("Loaded: ${preset.name}")
        }
    }

    /** Set slice start (0..1). TODO: wire to engine when setPadSlice is available. */
    fun setSliceStart(
        padIndex: Int,
        value: Float,
    ) {
        val globalIndex = _uiState.value.currentBank * 16 + padIndex
        _uiState.update { state ->
            val params = state.padParams.toMutableList()
            params[globalIndex] = params[globalIndex].copy(sliceStart = value.coerceIn(0f, 1f))
            state.copy(padParams = params)
        }
        publishPad(globalIndex)
    }

    /** Set slice end (0..1). TODO: wire to engine when setPadSlice is available. */
    fun setSliceEnd(
        padIndex: Int,
        value: Float,
    ) {
        val globalIndex = _uiState.value.currentBank * 16 + padIndex
        _uiState.update { state ->
            val params = state.padParams.toMutableList()
            val old = params[globalIndex]
            params[globalIndex] = old.copy(sliceEnd = value.coerceIn(old.sliceStart, 1f))
            state.copy(padParams = params)
        }
        publishPad(globalIndex)
    }

    /** Set choke group. TODO: wire to engine when choke group support is added. */
    fun setChokeGroup(
        padIndex: Int,
        group: Int,
    ) {
        val globalIndex = _uiState.value.currentBank * 16 + padIndex
        _uiState.update { state ->
            val params = state.padParams.toMutableList()
            params[globalIndex] = params[globalIndex].copy(chokeGroup = group.coerceAtLeast(0))
            state.copy(padParams = params)
        }
        publishPad(globalIndex)
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    companion object {
        /** Factory preset names available for pad synth loading. */
        val FACTORY_PRESETS: List<String> =
            listOf(
                "Saw Lead",
                "Bright Lead",
                "Square Lead",
                "Deep Sub",
                "Saw Bass",
                "Pulse Bass",
                "Soft Pad",
                "Warm Pad",
                "Dark Pad",
                "Resonant Pluck",
                "Acid Lead",
                "Analog Brass",
            )

        /**
         * Reads duration in milliseconds from a standard PCM WAV file header.
         * Returns null if the file is not a valid WAV or cannot be read.
         *
         * TODO: If the engine adds per-pad slice offsets (setPadSlice), this can be
         * used in Kotlin to precompute start/end frames and pass them via JNI.
         */
        fun readWavDurationMs(file: File): Long? {
            return try {
                file.inputStream().use { stream ->
                    val header = ByteArray(44)
                    if (stream.read(header) < 44) return null

                    // RIFF magic
                    if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte()) return null
                    if (header[8] != 'W'.code.toByte() || header[9] != 'A'.code.toByte()) return null

                    val sampleRate = readLEInt(header, 24)
                    val numChannels = readLEShort(header, 22).toInt()
                    val bitsPerSample = readLEShort(header, 34).toInt()
                    val dataSize = readLEInt(header, 40)

                    if (sampleRate <= 0 || bitsPerSample <= 0 || numChannels <= 0) return null
                    val bytesPerFrame = numChannels * (bitsPerSample / 8)
                    val numFrames = dataSize / bytesPerFrame
                    (numFrames * 1000L) / sampleRate
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun readLEInt(
            buf: ByteArray,
            offset: Int,
        ): Int =
            (buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8) or
                ((buf[offset + 2].toInt() and 0xFF) shl 16) or
                ((buf[offset + 3].toInt() and 0xFF) shl 24)

        private fun readLEShort(
            buf: ByteArray,
            offset: Int,
        ): Short =
            (
                (buf[offset].toInt() and 0xFF) or
                    ((buf[offset + 1].toInt() and 0xFF) shl 8)
            ).toShort()
    }
}
