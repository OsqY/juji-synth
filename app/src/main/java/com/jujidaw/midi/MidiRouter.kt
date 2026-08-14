package com.jujidaw.midi

import com.jujidaw.audio.SynthEngine
import com.jujidaw.data.MidiMapping
import com.jujidaw.data.MidiMappingStore
import com.jujidaw.model.MidiTarget
import com.jujidaw.model.ParamIds
import com.jujidaw.project.MixerSessionStore
import com.jujidaw.project.TrackSynthSessionStore
import com.jujidaw.ui.keyboard.KeyboardTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central MIDI router — receives note/CC/pitch-bend events from [MidiController]
 * and dispatches them to the correct engine call based on the active
 * [keyboardTarget] and the persisted [mappings].
 *
 * ### Learn mode
 * Call [startLearn] with a [MidiTarget] to capture the next incoming CC.
 * When captured the mapping is persisted and [onLearnCaptured] fires.
 *
 * ### Thread safety
 * All public methods are safe to call from any thread.  State-flow emissions
 * happen on the caller's thread; persistence kicks off on [Dispatchers.IO].
 */
class MidiRouter(
    private val mappingStore: MidiMappingStore,
) {
    // ── scope ──────────────────────────────────────────────────────────────
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Keyboard target ────────────────────────────────────────────────────

    /** Current note-routing target.  Set by [KeyboardViewModel] on change. */
    @Volatile
    var keyboardTarget: KeyboardTarget = KeyboardTarget.Synth

    // ── Mappings ───────────────────────────────────────────────────────────
    private val _mappings = MutableStateFlow<List<MidiMapping>>(emptyList())
    private var mappingsJob: Job? = null

    /** Reactive stream of all active MIDI CC→target mappings. */
    val mappings: StateFlow<List<MidiMapping>> = _mappings.asStateFlow()

    // ── Learn mode ─────────────────────────────────────────────────────────
    @Volatile
    private var _isLearning = false

    /** Whether the router is waiting for the next CC to capture. */
    val isLearning: Boolean get() = _isLearning

    @Volatile
    private var _learnTarget: MidiTarget? = null

    /** The target that will be assigned to the next CC (null when idle). */
    val learnTarget: MidiTarget? get() = _learnTarget

    /**
     * Callback fired when a CC is captured during learn mode.
     * Parameters: (ccNumber, target, normalisedValue 0…1).
     */
    var onLearnCaptured: ((ccNumber: Int, target: MidiTarget, value: Float) -> Unit)? = null

    // ── Default CCs (when no explicit mapping exists) ──────────────────────
    private val defaultCcMappings =
        mapOf(
            1 to DefaultCc(modWheelDest, "Mod Wheel", false),
            7 to DefaultCc(volumeDest, "Volume", false),
            10 to DefaultCc(panDest, "Pan", false),
            64 to DefaultCc(sustainDest, "Sustain Pedal", true),
            74 to DefaultCc(filterCutoffDest, "Filter Cutoff", false),
        )

    private data class DefaultCc(
        val paramId: Int,
        val label: String,
        val isSustain: Boolean,
    )

    companion object {
        // Synth param IDs for default CCs (must match ParamIds constants)
        const val modWheelDest = 52
        const val volumeDest = 50
        const val panDest = -1 // Not yet implemented as a single param
        const val sustainDest = -1 // Handled separately
        const val filterCutoffDest = 10
    }

    // ── Initialisation ─────────────────────────────────────────────────────

    /** Load persisted mappings from [MidiMappingStore] and start observing. */
    fun load() {
        mappingsJob?.cancel()
        mappingsJob = ioScope.launch {
            mappingStore.mappingsFlow.collect { list ->
                _mappings.value = list
            }
        }
    }

    /** Replace mappings while loading a project and persist the new snapshot. */
    fun replaceMappings(mappings: List<MidiMapping>) {
        mappingsJob?.cancel()
        _mappings.value = mappings
        ioScope.launch {
            mappingStore.saveMappings(mappings)
            load()
        }
    }

    // ── Note routing ───────────────────────────────────────────────────────

    /**
     * Route a MIDI Note On to the correct engine call based on
     * [keyboardTarget].
     */
    fun processNoteOn(
        note: Int,
        velocity: Int,
    ) {
        val t = keyboardTarget
        when (t) {
            is KeyboardTarget.Synth -> {
                SynthEngine.noteOn(note, velocity)
            }

            is KeyboardTarget.SamplerA -> {
                SynthEngine.triggerPad(note % 16, velocity)
            }

            is KeyboardTarget.SamplerB -> {
                SynthEngine.triggerPad(16 + (note % 16), velocity)
            }

            is KeyboardTarget.Track -> {
                SynthEngine.scheduleNoteOn(t.index, note, velocity / 127f)
            }

            is KeyboardTarget.SelectedPad -> {
                SynthEngine.synthNoteOn(t.padIndex, note, velocity / 127f)
            }
        }
    }

    /** Route a MIDI Note Off. */
    fun processNoteOff(note: Int) {
        val t = keyboardTarget
        when (t) {
            is KeyboardTarget.Synth -> {
                SynthEngine.noteOff(note)
            }

            is KeyboardTarget.SamplerA, is KeyboardTarget.SamplerB -> {
                // Sampler pads are one-shot; no note-off required
            }

            is KeyboardTarget.Track -> {
                SynthEngine.scheduleNoteOff(t.index, note)
            }

            is KeyboardTarget.SelectedPad -> {
                SynthEngine.synthNoteOff(t.padIndex, note)
            }
        }
    }

    // ── CC routing ─────────────────────────────────────────────────────────

    /**
     * Route a MIDI Control Change.
     *
     * 1. If learn mode is active, capture the CC and persist the mapping.
     * 2. Look up an explicit mapping for [ccNumber].
     * 3. Fall back to built-in default CC behaviour.
     */
    fun processCc(
        ccNumber: Int,
        value: Float,
    ) {
        // ── Learn capture ──────────────────────────────────────────────
        if (_isLearning) {
            val target = _learnTarget ?: return
            _isLearning = false
            _learnTarget = null

            val mapping =
                MidiMapping(
                    ccNumber = ccNumber,
                    target = target,
                    minValue = 0f,
                    maxValue = 1f,
                )
            ioScope.launch {
                mappingStore.addMapping(mapping)
            }

            // Apply the CC value to the target immediately
            applyValueToTarget(target, value)

            // Fire callback so the UI can react (toast, highlight, …)
            onLearnCaptured?.invoke(ccNumber, target, value)
            return
        }

        // ── Explicit mapping lookup ────────────────────────────────────
        val snapshot = _mappings.value
        val mapping = snapshot.find { it.ccNumber == ccNumber }
        if (mapping != null) {
            applyMapping(mapping, value)
            return
        }

        // ── Default CC fallback ────────────────────────────────────────
        val default = defaultCcMappings[ccNumber] ?: return
        if (default.isSustain) {
            // Sustain pedal is handled in MidiController directly
            // (it affects note-off timing); do not send the sentinel -1
            // parameter to the native engine.
        } else if (default.paramId >= 0) {
            applyValueToTarget(MidiTarget.SynthParam(default.paramId), value)
        }
    }

    // ── Pitch bend ─────────────────────────────────────────────────────────

    /** Route MIDI Pitch Bend (-1 … +1). */
    fun processPitchBend(value: Float) {
        val clamped = value.coerceIn(-1f, 1f)
        SynthEngine.setParam(ParamIds.PITCH_BEND, clamped)
        TrackSynthSessionStore.updateParam(0, ParamIds.PITCH_BEND, clamped)
    }

    // ── Learn control ──────────────────────────────────────────────────────

    /** Enter learn mode for the given [target]. */
    fun startLearn(target: MidiTarget) {
        _isLearning = true
        _learnTarget = target
    }

    /** Cancel learn mode without capturing. */
    fun stopLearn() {
        _isLearning = false
        _learnTarget = null
    }

    // ── Mapping helpers ────────────────────────────────────────────────────

    /** Apply a [MidiMapping] (including its range clamping) to the engine. */
    fun applyMapping(
        mapping: MidiMapping,
        rawValue: Float,
    ) {
        val clamped = rawValue.coerceIn(0f, 1f)
        val scaled = mapping.minValue + clamped * (mapping.maxValue - mapping.minValue)
        applyValueToTarget(mapping.effectiveTarget(), scaled)
    }

    /**
     * Apply a normalised [value] (0…1 unless otherwise specified) directly
     * to the given [target] without any mapping table lookup.
     */
    fun applyValueToTarget(
        target: MidiTarget,
        value: Float,
    ) {
        when (target) {
            is MidiTarget.SynthParam -> {
                val clamped = value.coerceIn(0f, 1f)
                SynthEngine.setParam(target.paramId, clamped)
                TrackSynthSessionStore.updateParam(0, target.paramId, clamped)
            }

            is MidiTarget.ChannelFader -> {
                // Map 0…1 → -60…+12 dB
                val db = (value * 72f - 60f).coerceIn(-60f, 12f)
                SynthEngine.setChannelFader(target.track, db)
                MixerSessionStore.updateTrack(target.track) { it.copy(faderDb = db) }
            }

            is MidiTarget.ChannelPan -> {
                // Map 0…1 → -1…+1
                val pan = (value * 2f - 1f).coerceIn(-1f, 1f)
                SynthEngine.setChannelPan(target.track, pan)
                MixerSessionStore.updateTrack(target.track) { it.copy(pan = pan) }
            }

            is MidiTarget.ChannelMute -> {
                val mute = value > 0.5f
                SynthEngine.setChannelMute(target.track, mute)
                MixerSessionStore.updateTrack(target.track) { it.copy(mute = mute) }
            }

            is MidiTarget.ChannelSolo -> {
                val solo = value > 0.5f
                SynthEngine.setChannelSolo(target.track, solo)
                MixerSessionStore.updateTrack(target.track) { it.copy(solo = solo) }
            }

            is MidiTarget.ChannelArm -> {
                val arm = value > 0.5f
                SynthEngine.setChannelArm(target.track, arm)
                MixerSessionStore.updateTrack(target.track) { it.copy(arm = arm) }
            }

            is MidiTarget.SendLevel -> {
                val level = value.coerceIn(0f, 1f)
                SynthEngine.setSendLevel(target.track, target.bus, level)
                MixerSessionStore.updateTrack(target.track) {
                    when (target.bus) {
                        0 -> it.copy(sendALevel = level)
                        1 -> it.copy(sendBLevel = level)
                        else -> it
                    }
                }
            }

            is MidiTarget.BusFader -> {
                // Map 0…1 → -60…+12 dB
                val db = (value * 72f - 60f).coerceIn(-60f, 12f)
                SynthEngine.setBusFader(target.bus, db)
                MixerSessionStore.updateBus(target.bus) { it.copy(faderDb = db) }
            }

            is MidiTarget.MasterFader -> {
                val db = (value * 72f - 60f).coerceIn(-60f, 12f)
                SynthEngine.setMasterFader(db)
                MixerSessionStore.update { it.copy(masterFaderDb = db) }
            }

            is MidiTarget.InsertParam -> {
                val clamped = value.coerceIn(0f, 1f)
                SynthEngine.setInsertParam(target.track, target.slot, target.paramId, clamped)
                MixerSessionStore.updateInsertParam(target.track, target.slot, target.paramId, clamped)
            }

            is MidiTarget.PerformFx -> {
                // Perform FX are toggled through MixerViewModel;
                // direct MIDI control is a future enhancement.
            }
        }
    }
}
