package com.jujidaw.ui.sequencer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jujidaw.JujiDawApp
import com.jujidaw.audio.SynthEngine
import com.jujidaw.engine.TransportController
import com.jujidaw.model.NoteEvent
import com.jujidaw.model.Pattern
import com.jujidaw.model.PianoRollNote
import com.jujidaw.model.TICKS_PER_STEP
import com.jujidaw.model.TimeSignature
import com.jujidaw.model.TransportPosition
import com.jujidaw.project.AutomationPoint
import com.jujidaw.project.ProjectAutosave
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** View mode for the sequencer screen. */
enum class SequencerViewMode { STEP, PIANO_ROLL }

/** Automation parameters exposed to the sequencer automation lane. */
enum class AutomationParam(
    val id: Int,
    val label: String,
) {
    FILTER_CUTOFF(9, "Filter Cutoff"),
    FILTER_RES(10, "Filter Res"),
    AMP_RELEASE(16, "Amp Release"),
    REVERB_MIX(27, "Reverb Mix"),
    DELAY_MIX(29, "Delay Mix"),
    MASTER_VOLUME(38, "Master Vol"),
}

/** A single cell in the 16×16 step grid. */
data class StepCell(
    val active: Boolean = false,
    val velocity: Int = 100, // 0..127
    val gate: Float = 0.8f,
)

/** One track (row) in the step grid: 16 steps.
 *  Row R maps 1:1 to Pad R — [padIndex] determines which pad fires on step. */
data class StepTrack(
    val steps: List<StepCell> = List(16) { StepCell() },
    val defaultNote: Int = 60, // C4 (used for piano-roll only; step grid uses padIndex)
    val padIndex: Int = -1, // pad to trigger (-1 = set from track position)
)

/** Rich pattern model used by the sequencer UI.
 *  Holds both step-grid and piano-roll representations.
 *  The active view mode determines which representation is exported to [TransportController].
 */
data class SequencerPattern(
    val id: Int,
    val name: String = "Pattern ${id + 1}",
    val tracks: List<StepTrack> = List(16) { StepTrack(padIndex = it) },
    val pianoRollNotes: List<PianoRollNote> = emptyList(),
    val lengthSteps: Int = 16,
)

/** UI state exposed by [SequencerViewModel]. */
data class SequencerUiState(
    val patterns: List<SequencerPattern> = List(16) { SequencerPattern(id = it) },
    val selectedPatternId: Int = 0,
    val viewMode: SequencerViewMode = SequencerViewMode.STEP,
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val currentStep: Int = 0,
    val bpm: Float = 120f,
    val copyBufferPattern: SequencerPattern? = null,
    val showVelocityPopup: Boolean = false,
    val velocityEditTrack: Int = 0,
    val velocityEditStep: Int = 0,
    val showAutomation: Boolean = false,
    val selectedAutomationParam: AutomationParam = AutomationParam.FILTER_CUTOFF,
    val automationPoints: List<AutomationPoint> = emptyList(),
    val armedAutomationParams: Set<Int> = emptySet<Int>(),
)

/**
 * ViewModel for the pattern sequencer screen.
 *
 * Owns 16 patterns, each with a 16×16 step grid and an independent piano-roll note list.
 * Syncs the active pattern to [TransportController] for playback.
 *
 * ### Public API
 * - [uiState] – observable screen state
 * - [selectPattern], [toggleViewMode], [togglePlay], [toggleRecord], [setBpm]
 * - [toggleStep], [setStepVelocity] – step-grid editing
 * - [copyPattern], [pastePattern], [clearPattern] – pattern clipboard
 * - [updatePianoRollNotes] – piano-roll editing
 *
 * ### Assumptions
 * - The [TransportController] instance is either injected or created with defaults.
 *   For multi-screen usage the integrator should provide a singleton via a ViewModel
 *   factory (see TODO below).
 * - Step-grid notes use the per-track [StepTrack.defaultNote] (default C4=60).
 *   Track index maps to mixer channel via [NoteEvent.trackIndex].
 * - Only the **active view mode** representation is exported to the transport.
 *   Step mode exports the 16×16 grid; piano-roll mode exports [PianoRollNote]s.
 *   The other representation is preserved but silent until the mode is switched.
 */
class SequencerViewModel(
    private val transportController: TransportController = JujiDawApp.instance.transportController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SequencerUiState())
    val uiState: StateFlow<SequencerUiState> = _uiState.asStateFlow()

    init {
        transportController.isSequencerMode = true

        // Load initial patterns into the transport controller.
        syncActivePatternToTransport()

        // Poll transport position to derive current step and playing state.
        viewModelScope.launch {
            while (isActive) {
                val playing = transportController.transportState.playing
                val step = transportController.currentStep
                val bpm = transportController.transportState.tempoBpm
                _uiState.value =
                    _uiState.value.copy(
                        isPlaying = playing,
                        currentStep = step,
                        bpm = bpm,
                    )
                delay(33) // ~30 fps
            }
        }
    }

    /** Switch the active pattern (0..15). */
    fun selectPattern(id: Int) {
        if (id !in 0..15) return
        _uiState.value = _uiState.value.copy(selectedPatternId = id)
        syncActivePatternToTransport()
        // Queue the pattern so the transport can switch on the next bar boundary.
        transportController.queuePattern(id)
    }

    /** Toggle the automation lane overlay on/off in piano-roll mode. */
    fun toggleAutomation() {
        _uiState.value = _uiState.value.copy(showAutomation = !_uiState.value.showAutomation)
    }

    /** Select which automation parameter to show. */
    fun selectAutomationParam(param: AutomationParam) {
        _uiState.value = _uiState.value.copy(selectedAutomationParam = param)
    }

    /** Replace the automation points for the currently selected param. */
    fun updateAutomationPoints(points: List<AutomationPoint>) {
        _uiState.value = _uiState.value.copy(automationPoints = points)
    }

    /**
     * Record an automation point if the given param is armed and the transport
     * is currently playing. Call this from knob/slider change handlers to
     * implement live touch recording.
     *
     * @return true if a point was recorded, false if ignored (not armed or stopped).
     */
    fun recordAutomationIfArmed(
        paramId: Int,
        value: Float,
    ): Boolean {
        if (paramId !in _uiState.value.armedAutomationParams) return false
        if (!transportController.transportState.playing) return false
        val tick =
            transportController.transportState.position.toTicks(
                transportController.transportState.timeSignature,
            )
        val updatedPoints = _uiState.value.automationPoints + AutomationPoint(tick, value)
        _uiState.value = _uiState.value.copy(automationPoints = updatedPoints)
        return true
    }

    /**
     * Toggle arm state for a parameter. When armed and the transport is playing,
     * knob movements on that param are recorded as automation points.
     */
    fun toggleAutomationArm(paramId: Int) {
        val current = _uiState.value.armedAutomationParams
        _uiState.value =
            _uiState.value.copy(
                armedAutomationParams = if (paramId in current) current - paramId else current + paramId,
            )
    }

    /** Toggle between step and piano-roll view. */
    fun toggleViewMode() {
        val newMode =
            when (_uiState.value.viewMode) {
                SequencerViewMode.STEP -> SequencerViewMode.PIANO_ROLL
                SequencerViewMode.PIANO_ROLL -> SequencerViewMode.STEP
            }
        _uiState.value = _uiState.value.copy(viewMode = newMode)
        syncActivePatternToTransport()
    }

    /** Toggle a step cell on/off in the active pattern. */
    fun toggleStep(
        track: Int,
        step: Int,
    ) {
        if (track !in 0..15 || step !in 0..15) return
        val patterns = _uiState.value.patterns.toMutableList()
        val pattern = patterns[_uiState.value.selectedPatternId]
        val tracks = pattern.tracks.toMutableList()
        val trackState = tracks[track]
        val steps = trackState.steps.toMutableList()
        val cell = steps[step]
        steps[step] = cell.copy(active = !cell.active)
        tracks[track] = trackState.copy(steps = steps)
        patterns[_uiState.value.selectedPatternId] = pattern.copy(tracks = tracks)
        _uiState.value = _uiState.value.copy(patterns = patterns)
        syncActivePatternToTransport()
    }

    /** Set velocity for a specific step cell (0..127). */
    fun setStepVelocity(
        track: Int,
        step: Int,
        velocity: Int,
    ) {
        if (track !in 0..15 || step !in 0..15) return
        val patterns = _uiState.value.patterns.toMutableList()
        val pattern = patterns[_uiState.value.selectedPatternId]
        val tracks = pattern.tracks.toMutableList()
        val trackState = tracks[track]
        val steps = trackState.steps.toMutableList()
        val cell = steps[step]
        steps[step] =
            cell.copy(
                active = true,
                velocity = velocity.coerceIn(0, 127),
            )
        tracks[track] = trackState.copy(steps = steps)
        patterns[_uiState.value.selectedPatternId] = pattern.copy(tracks = tracks)
        _uiState.value = _uiState.value.copy(patterns = patterns)
        syncActivePatternToTransport()
    }

    /** Show the velocity popup for a step cell. */
    fun showVelocityEditor(
        track: Int,
        step: Int,
    ) {
        _uiState.value =
            _uiState.value.copy(
                showVelocityPopup = true,
                velocityEditTrack = track,
                velocityEditStep = step,
            )
    }

    /** Dismiss the velocity popup. */
    fun dismissVelocityEditor() {
        _uiState.value = _uiState.value.copy(showVelocityPopup = false)
    }

    /** Copy the active pattern to the internal clipboard. */
    fun copyPattern() {
        val pattern = _uiState.value.patterns[_uiState.value.selectedPatternId]
        _uiState.value = _uiState.value.copy(copyBufferPattern = pattern.copy(id = pattern.id))
    }

    /** Paste the clipboard pattern into the active pattern slot. */
    fun pastePattern() {
        val buffer = _uiState.value.copyBufferPattern ?: return
        val patterns = _uiState.value.patterns.toMutableList()
        val activeId = _uiState.value.selectedPatternId
        patterns[activeId] = buffer.copy(id = activeId, name = buffer.name)
        _uiState.value = _uiState.value.copy(patterns = patterns)
        syncActivePatternToTransport()
    }

    /** Clear all notes in the active pattern (both step grid and piano roll). */
    fun clearPattern() {
        val patterns = _uiState.value.patterns.toMutableList()
        val activeId = _uiState.value.selectedPatternId
        patterns[activeId] = SequencerPattern(id = activeId)
        _uiState.value = _uiState.value.copy(patterns = patterns)
        syncActivePatternToTransport()
    }

    /** Toggle transport play / stop. */
    fun togglePlay() {
        if (_uiState.value.isPlaying) {
            transportController.stop()
        } else {
            // Ensure the active pattern is queued before play.
            transportController.queuePattern(_uiState.value.selectedPatternId)
            transportController.play()
        }
    }

    /** Toggle recording arm. */
    fun toggleRecord() {
        val newRecording = !_uiState.value.isRecording
        _uiState.value = _uiState.value.copy(isRecording = newRecording)
        transportController.setRecording(newRecording)
    }

    /** Update BPM (30..300). */
    fun setBpm(bpm: Float) {
        val clamped = bpm.coerceIn(30f, 300f)
        _uiState.value = _uiState.value.copy(bpm = clamped)
        transportController.setTempo(clamped)
        // Also sync to the legacy engine tempo parameter so other screens stay in sync.
        SynthEngine.setParam(60, clamped)
    }

    /** Replace piano-roll notes for the active pattern. */
    fun updatePianoRollNotes(notes: List<PianoRollNote>) {
        val patterns = _uiState.value.patterns.toMutableList()
        val activeId = _uiState.value.selectedPatternId
        val pattern = patterns[activeId]
        patterns[activeId] = pattern.copy(pianoRollNotes = notes)
        _uiState.value = _uiState.value.copy(patterns = patterns)
        syncActivePatternToTransport()
    }

    /** Convert the active [SequencerPattern] to a canonical [Pattern] and push to transport. */
    private fun syncActivePatternToTransport() {
        val seqPattern = _uiState.value.patterns[_uiState.value.selectedPatternId]
        val pattern =
            when (_uiState.value.viewMode) {
                SequencerViewMode.STEP -> seqPattern.toStepPattern()
                SequencerViewMode.PIANO_ROLL -> seqPattern.toPianoRollPattern()
            }
        val allPatterns =
            _uiState.value.patterns.mapIndexed { index, sp ->
                if (index == _uiState.value.selectedPatternId) pattern else sp.toStepPattern()
            }
        transportController.loadPatterns(allPatterns)
        scheduleAutosave()
    }

    /**
     * Debounced auto-save so pattern edits survive a crash or forced kill
     * (the [com.jujidaw.MainActivity] onStop save only fires on background).
     */
    private fun scheduleAutosave() {
        ProjectAutosave.scheduleAutoSave(
            JujiDawApp.instance,
            transportController,
        )
    }

    override fun onCleared() {
        transportController.isSequencerMode = false
        transportController.release()
        super.onCleared()
    }
}

// ── Conversion helpers ─────────────────────────────────────────────

/** Build a [Pattern] from the step-grid representation.
 *  Each row fires a PAD_TRIGGER for its mapped pad.
 */
private fun SequencerPattern.toStepPattern(): Pattern {
    val noteEvents = mutableListOf<NoteEvent>()
    tracks.forEachIndexed { trackIndex, track ->
        // Row R = Pad R; padIndex defaults to track position.
        val padIndex = track.padIndex.coerceAtLeast(0).let { if (it < 0) trackIndex else it }
        track.steps.forEachIndexed { stepIndex, cell ->
            if (cell.active) {
                noteEvents.add(
                    NoteEvent(
                        note = track.defaultNote.coerceIn(0, 127),
                        velocity = cell.velocity / 127f,
                        startTick = stepIndex * TICKS_PER_STEP.toLong(),
                        durationTicks = (TICKS_PER_STEP * cell.gate).toLong().coerceAtLeast(1L),
                        trackIndex = trackIndex,
                        padIndex = padIndex,
                    ),
                )
            }
        }
    }
    return Pattern(
        id = id,
        name = name,
        trackIndex = 0,
        lengthSteps = lengthSteps,
        notes = noteEvents,
    )
}

/** Build a [Pattern] from the piano-roll representation. */
private fun SequencerPattern.toPianoRollPattern(): Pattern {
    val noteEvents =
        pianoRollNotes.map { prn ->
            NoteEvent(
                note = prn.note.coerceIn(0, 127),
                velocity = prn.velocity / 127f,
                startTick = (prn.startStep * TICKS_PER_STEP).toLong(),
                durationTicks = (prn.duration * TICKS_PER_STEP).toLong().coerceAtLeast(1L),
                trackIndex = 0,
            )
        }
    return Pattern(
        id = id,
        name = name,
        trackIndex = 0,
        lengthSteps = 64,
        notes = noteEvents,
    )
}
