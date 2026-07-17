package com.jujidaw.ui.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jujidaw.JujiDawApp
import com.jujidaw.audio.SynthEngine
import com.jujidaw.model.ParamIds
import com.jujidaw.project.PadSelectionStore
import com.jujidaw.project.PadSessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ── Enums ──────────────────────────────────────────────────────────────────

enum class KeyboardViewMode { GRID, PIANO }

enum class ScaleType(
    val displayName: String,
    val intervals: List<Int>,
) {
    MAJOR("Major", listOf(0, 2, 4, 5, 7, 9, 11)),
    MINOR("Minor", listOf(0, 2, 3, 5, 7, 8, 10)),
    PENTATONIC("Penta", listOf(0, 2, 4, 7, 9)),
    BLUES("Blues", listOf(0, 3, 5, 6, 7, 10)),
    DORIAN("Dorian", listOf(0, 2, 3, 5, 7, 9, 10)),
    MIXOLYDIAN("Mixo", listOf(0, 2, 4, 5, 7, 9, 10)),
}

enum class NoteRepeatRate(
    val displayName: String,
    val beatFraction: Float,
) {
    QUARTER("1/4", 1f),
    EIGHTH("1/8", 0.5f),
    SIXTEENTH("1/16", 0.25f),
    THIRTY_SECOND("1/32", 0.125f),
}

enum class ArpMode(
    val displayName: String,
) {
    UP("Up"),
    DOWN("Down"),
    UP_DOWN("Up-Dn"),
    RANDOM("Rnd"),
}

enum class ArpRate(
    val displayName: String,
    val beatFraction: Float,
) {
    QUARTER("1/4", 1f),
    EIGHTH("1/8", 0.5f),
    SIXTEENTH("1/16", 0.25f),
    THIRTY_SECOND("1/32", 0.125f),
}

sealed class KeyboardTarget(
    val displayName: String,
) {
    object Synth : KeyboardTarget("Synth")

    object SamplerA : KeyboardTarget("Smp A")

    object SamplerB : KeyboardTarget("Smp B")

    data class Track(
        val index: Int,
    ) : KeyboardTarget("Tr${index + 1}")

    data class SelectedPad(
        val padIndex: Int,
    ) : KeyboardTarget("Pad${padIndex + 1}")
}

// ── State ───────────────────────────────────────────────────────────────────

data class KeyboardUiState(
    val activeNotes: Set<Int> = emptySet(),
    val baseOctave: Int = 3, // C3 = MIDI 48
    val viewMode: KeyboardViewMode = KeyboardViewMode.GRID,
    val rootNote: Int = 0, // C
    val scaleType: ScaleType = ScaleType.MAJOR,
    val scaleLock: Boolean = false,
    val velocityFromTouch: Boolean = true,
    val aftertouchEnabled: Boolean = false,
    val noteRepeatEnabled: Boolean = false,
    val noteRepeatRate: NoteRepeatRate = NoteRepeatRate.EIGHTH,
    val arpEnabled: Boolean = false,
    val arpMode: ArpMode = ArpMode.UP,
    val arpRate: ArpRate = ArpRate.EIGHTH,
    val arpOctaveRange: Int = 1,
    val target: KeyboardTarget = KeyboardTarget.Synth,
    val tempoBpm: Float = 120f,
)

// ── ViewModel ───────────────────────────────────────────────────────────────

class KeyboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(KeyboardUiState())
    val uiState: StateFlow<KeyboardUiState> = _uiState.asStateFlow()

    // Notes physically held by the user (distinct from arp/repeat active notes)
    private val heldNotes = mutableSetOf<Int>()

    // Note-repeat jobs keyed by note
    private val noteRepeatJobs = mutableMapOf<Int, Job>()

    // Arpeggiator state
    private var arpJob: Job? = null
    private var arpSequence = listOf<Int>()
    private var arpIndex = 0
    private var arpDirectionUp = true
    private var currentArpNote: Int? = null

    init {
        viewModelScope.launch {
            merge(PadSelectionStore.selectedPad, PadSelectionStore.selectionEvents).collect { globalPad ->
                val isSynthPad = PadSessionStore.snapshot().getOrNull(globalPad)?.params?.synthMode == true
                if (isSynthPad) setTarget(KeyboardTarget.SelectedPad(globalPad))
            }
        }
    }

    companion object {
        const val MIN_VELOCITY = 30
        const val MAX_VELOCITY = 127
        const val RETRIGGER_GAP_MS = 5L
        const val MIN_RATE_MS = 20L
    }

    // ── Note input ─────────────────────────────────────────────────────────

    /**
     * Call when a pointer goes down on a key.
     * [touchY] and [keyHeight] are used for velocity-from-position when enabled.
     */
    fun noteOn(
        note: Int,
        velocity: Int = 100,
        touchY: Float = 0f,
        keyHeight: Float = 1f,
    ) {
        val effectiveVelocity =
            if (_uiState.value.velocityFromTouch && keyHeight > 0) {
                computeVelocityFromY(touchY, keyHeight)
            } else {
                velocity
            }

        val snappedNote =
            if (_uiState.value.scaleLock) {
                snapToScale(note, _uiState.value.rootNote, _uiState.value.scaleType)
            } else {
                note
            }

        heldNotes += snappedNote

        when {
            _uiState.value.arpEnabled -> {
                updateArpSequence()
            }

            _uiState.value.noteRepeatEnabled -> {
                startNoteRepeat(snappedNote, effectiveVelocity)
            }

            else -> {
                _uiState.update { it.copy(activeNotes = it.activeNotes + snappedNote) }
                sendNoteOn(snappedNote, effectiveVelocity)
            }
        }
    }

    /** Call when a pointer is lifted from a key. */
    fun noteOff(note: Int) {
        val snappedNote =
            if (_uiState.value.scaleLock) {
                snapToScale(note, _uiState.value.rootNote, _uiState.value.scaleType)
            } else {
                note
            }

        heldNotes -= snappedNote
        noteRepeatJobs.remove(snappedNote)?.cancel()

        if (!_uiState.value.arpEnabled) {
            _uiState.update { it.copy(activeNotes = it.activeNotes - snappedNote) }
            sendNoteOff(snappedNote)
        } else {
            updateArpSequence()
        }
    }

    /** Call while a finger drags inside a held key to emit aftertouch. */
    fun onNoteDrag(
        note: Int,
        touchY: Float,
        keyHeight: Float,
    ) {
        if (_uiState.value.aftertouchEnabled && keyHeight > 0) {
            val pressure = 1f - (touchY / keyHeight).coerceIn(0f, 1f)
            sendAftertouch(note, pressure)
        }
    }

    // ── Settings ───────────────────────────────────────────────────────────

    fun setBaseOctave(octave: Int) {
        _uiState.update { it.copy(baseOctave = octave.coerceIn(2, 4)) }
    }

    fun setRootNote(root: Int) {
        _uiState.update { it.copy(rootNote = root.mod12()) }
    }

    fun setScaleType(scale: ScaleType) {
        _uiState.update { it.copy(scaleType = scale) }
    }

    fun cycleScaleType() {
        val entries = ScaleType.entries
        val idx = entries.indexOf(_uiState.value.scaleType)
        setScaleType(entries[(idx + 1) % entries.size])
    }

    fun toggleScaleLock() {
        _uiState.update { it.copy(scaleLock = !it.scaleLock) }
    }

    fun toggleVelocityFromTouch() {
        _uiState.update { it.copy(velocityFromTouch = !it.velocityFromTouch) }
    }

    fun toggleAftertouch() {
        _uiState.update { it.copy(aftertouchEnabled = !it.aftertouchEnabled) }
    }

    fun toggleNoteRepeat() {
        val enabled = !_uiState.value.noteRepeatEnabled
        _uiState.update { it.copy(noteRepeatEnabled = enabled) }
        if (!enabled) {
            noteRepeatJobs.values.forEach { it.cancel() }
            noteRepeatJobs.clear()
            if (!_uiState.value.arpEnabled) {
                val repeated = _uiState.value.activeNotes - heldNotes
                _uiState.update { it.copy(activeNotes = it.activeNotes - repeated) }
                repeated.forEach { sendNoteOff(it) }
            }
        }
    }

    fun setNoteRepeatRate(rate: NoteRepeatRate) {
        _uiState.update { it.copy(noteRepeatRate = rate) }
    }

    fun cycleNoteRepeatRate() {
        val entries = NoteRepeatRate.entries
        val idx = entries.indexOf(_uiState.value.noteRepeatRate)
        setNoteRepeatRate(entries[(idx + 1) % entries.size])
    }

    fun toggleArp() {
        val enabled = !_uiState.value.arpEnabled
        _uiState.update { it.copy(arpEnabled = enabled) }
        if (enabled) {
            // Cancel any direct held notes
            _uiState.value.activeNotes.forEach { sendNoteOff(it) }
            startArpeggiator()
        } else {
            arpJob?.cancel()
            currentArpNote?.let {
                _uiState.update { s -> s.copy(activeNotes = s.activeNotes - it) }
                sendNoteOff(it)
            }
            currentArpNote = null
            // Restore direct held notes
            if (heldNotes.isNotEmpty()) {
                _uiState.update { it.copy(activeNotes = heldNotes.toSet()) }
                heldNotes.forEach { sendNoteOn(it) }
            }
        }
    }

    fun setArpMode(mode: ArpMode) {
        _uiState.update { it.copy(arpMode = mode) }
        arpIndex = 0
        arpDirectionUp = true
        updateArpSequence()
    }

    fun cycleArpMode() {
        val entries = ArpMode.entries
        val idx = entries.indexOf(_uiState.value.arpMode)
        setArpMode(entries[(idx + 1) % entries.size])
    }

    fun setArpRate(rate: ArpRate) {
        _uiState.update { it.copy(arpRate = rate) }
    }

    fun cycleArpRate() {
        val entries = ArpRate.entries
        val idx = entries.indexOf(_uiState.value.arpRate)
        setArpRate(entries[(idx + 1) % entries.size])
    }

    fun setArpOctaveRange(range: Int) {
        _uiState.update { it.copy(arpOctaveRange = range.coerceIn(1, 4)) }
        updateArpSequence()
    }

    fun setTarget(target: KeyboardTarget) {
        // Release everything before switching to avoid stuck notes across targets
        val active = _uiState.value.activeNotes.toSet()
        active.forEach { sendNoteOff(it) }
        heldNotes.clear()
        noteRepeatJobs.values.forEach { it.cancel() }
        noteRepeatJobs.clear()
        arpJob?.cancel()
        currentArpNote = null
        _uiState.update {
            it.copy(
                target = target,
                activeNotes = emptySet(),
                arpEnabled = false,
                noteRepeatEnabled = false,
            )
        }
        // Sync with the app-wide MIDI router so external MIDI note input
        // follows the same target.
        try {
            JujiDawApp.instance.midiRouter.keyboardTarget = target
        } catch (_: UninitializedPropertyAccessException) {
            // App not yet initialised — safe to ignore
        }
    }

    fun cycleTarget() {
        val targets =
            listOf(
                KeyboardTarget.Synth,
                KeyboardTarget.SamplerA,
                KeyboardTarget.SamplerB,
            ) + (0..15).map { KeyboardTarget.Track(it) } +
                (0..31).map { KeyboardTarget.SelectedPad(it) }
        val idx = targets.indexOf(_uiState.value.target)
        val next = targets.getOrElse((idx + 1) % targets.size) { targets.first() }
        setTarget(next)
    }

    fun setViewMode(mode: KeyboardViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun setTempo(bpm: Float) {
        _uiState.update { it.copy(tempoBpm = bpm.coerceIn(20f, 999f)) }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        arpJob?.cancel()
        noteRepeatJobs.values.forEach { it.cancel() }
        (_uiState.value.activeNotes + heldNotes).toSet().forEach { sendNoteOff(it) }
        heldNotes.clear()
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun computeVelocityFromY(
        y: Float,
        keyHeight: Float,
    ): Int {
        val ratio = 1f - (y / keyHeight).coerceIn(0f, 1f)
        return (MIN_VELOCITY + ratio * (MAX_VELOCITY - MIN_VELOCITY)).toInt()
    }

    private fun snapToScale(
        note: Int,
        root: Int,
        scale: ScaleType,
    ): Int {
        val semitone = (note - root).mod12()
        if (semitone in scale.intervals) return note
        val nearest =
            scale.intervals.minByOrNull { interval ->
                kotlin.math.min(
                    kotlin.math.abs(interval - semitone),
                    12 - kotlin.math.abs(interval - semitone),
                )
            } ?: 0
        return note + (nearest - semitone)
    }

    private fun Int.mod12(): Int = ((this % 12) + 12) % 12

    private fun sendNoteOn(
        note: Int,
        velocity: Int = 100,
    ) {
        when (val t = _uiState.value.target) {
            is KeyboardTarget.Synth -> {
                SynthEngine.noteOn(note, velocity)
            }

            is KeyboardTarget.SamplerA -> {
                SynthEngine.setSamplerBank(0)
                SynthEngine.triggerPad(note % 16, velocity)
            }

            is KeyboardTarget.SamplerB -> {
                SynthEngine.setSamplerBank(1)
                SynthEngine.triggerPad(note % 16, velocity)
            }

            is KeyboardTarget.Track -> {
                SynthEngine.scheduleNoteOn(t.index, note, velocity / 127f)
            }

            is KeyboardTarget.SelectedPad -> {
                SynthEngine.synthNoteOn(t.padIndex, note, velocity / 127f)
            }
        }
    }

    private fun sendNoteOff(note: Int) {
        when (val t = _uiState.value.target) {
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

    private fun sendAftertouch(
        note: Int,
        pressure: Float,
    ) {
        // TODO: Engine does not expose per-note aftertouch.
        // Mapping to MOD_WHEEL as a channel-pressure placeholder.
        if (_uiState.value.aftertouchEnabled) {
            SynthEngine.setParam(ParamIds.MOD_WHEEL, pressure)
        }
    }

    private fun startNoteRepeat(
        note: Int,
        velocity: Int,
    ) {
        if (noteRepeatJobs.containsKey(note)) return
        noteRepeatJobs[note] =
            viewModelScope.launch {
                while (isActive && note in heldNotes) {
                    _uiState.update { it.copy(activeNotes = it.activeNotes + note) }
                    sendNoteOn(note, velocity)
                    val delayMs = rateToMs(_uiState.value.noteRepeatRate, _uiState.value.tempoBpm)
                    delay(delayMs.coerceAtLeast(30))
                    sendNoteOff(note)
                    _uiState.update { it.copy(activeNotes = it.activeNotes - note) }
                    delay(RETRIGGER_GAP_MS)
                }
            }
    }

    private fun startArpeggiator() {
        arpJob?.cancel()
        arpJob =
            viewModelScope.launch {
                while (isActive) {
                    val state = _uiState.value
                    if (state.arpEnabled && heldNotes.isNotEmpty()) {
                        updateArpSequence()
                        if (arpSequence.isNotEmpty()) {
                            currentArpNote?.let { prev ->
                                _uiState.update { s -> s.copy(activeNotes = s.activeNotes - prev) }
                                sendNoteOff(prev)
                            }
                            val note = arpSequence[arpIndex.coerceIn(0, arpSequence.lastIndex)]
                            _uiState.update { it.copy(activeNotes = it.activeNotes + note) }
                            sendNoteOn(note)
                            currentArpNote = note
                            advanceArpIndex(arpSequence.size, state.arpMode)
                        }
                    } else {
                        currentArpNote?.let { prev ->
                            _uiState.update { s -> s.copy(activeNotes = s.activeNotes - prev) }
                            sendNoteOff(prev)
                        }
                        currentArpNote = null
                    }
                    val delayMs = rateToMs(state.arpRate, state.tempoBpm)
                    delay(delayMs.coerceAtLeast(MIN_RATE_MS))
                }
            }
    }

    private fun updateArpSequence() {
        val state = _uiState.value
        val sorted = heldNotes.sorted()
        if (sorted.isEmpty()) {
            arpSequence = emptyList()
            return
        }
        val expanded =
            buildList {
                for (octave in 0 until state.arpOctaveRange) {
                    addAll(sorted.map { it + octave * 12 })
                }
            }
        arpSequence = expanded
        arpIndex = arpIndex.coerceIn(0, (arpSequence.size - 1).coerceAtLeast(0))
    }

    private fun advanceArpIndex(
        size: Int,
        mode: ArpMode,
    ) {
        if (size <= 1) return
        when (mode) {
            ArpMode.UP -> {
                arpIndex = (arpIndex + 1) % size
            }

            ArpMode.DOWN -> {
                arpIndex = (arpIndex - 1 + size) % size
            }

            ArpMode.UP_DOWN -> {
                if (arpDirectionUp) {
                    if (arpIndex + 1 >= size) {
                        arpDirectionUp = false
                        arpIndex = (size - 2).coerceAtLeast(0)
                    } else {
                        arpIndex++
                    }
                } else {
                    if (arpIndex - 1 < 0) {
                        arpDirectionUp = true
                        arpIndex = 1.coerceAtMost(size - 1)
                    } else {
                        arpIndex--
                    }
                }
            }

            ArpMode.RANDOM -> {
                arpIndex = (0 until size).random()
            }
        }
    }

    private fun rateToMs(
        rate: ArpRate,
        bpm: Float,
    ): Long {
        val beatMs = 60000f / bpm.coerceAtLeast(1f)
        return (beatMs * rate.beatFraction).toLong()
    }

    private fun rateToMs(
        rate: NoteRepeatRate,
        bpm: Float,
    ): Long {
        val beatMs = 60000f / bpm.coerceAtLeast(1f)
        return (beatMs * rate.beatFraction).toLong()
    }
}
