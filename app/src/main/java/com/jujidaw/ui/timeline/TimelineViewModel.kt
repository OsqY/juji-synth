package com.jujidaw.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jujidaw.JujiDawApp
import com.jujidaw.audio.SynthEngine
import com.jujidaw.engine.TransportController
import com.jujidaw.model.*
import com.jujidaw.project.ProjectAutosave
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the Arrangement Timeline screen.
 *
 * Bridges UI interactions to the [TransportController] and keeps UI state
 * (zoom, snap, track mixer, selected pattern, automation editing).
 *
 * TODO(integrator): Provide a singleton [TransportController] via a custom
 * ViewModelProvider.Factory if other screens also need to share transport state.
 */
class TimelineViewModel(
    val transportController: TransportController = JujiDawApp.instance.transportController,
) : ViewModel() {
    private val _transportState = MutableStateFlow(transportController.transportState)
    val transportState: StateFlow<TransportState> = _transportState.asStateFlow()

    private val _arrangement = MutableStateFlow(transportController.arrangement)
    val arrangement: StateFlow<Arrangement> = _arrangement.asStateFlow()

    private val _patterns = MutableStateFlow(transportController.patterns)
    val patterns: StateFlow<List<Pattern>> = _patterns.asStateFlow()

    private val _deletedClips = MutableStateFlow<List<Clip>>(emptyList())
    val deletedClips: StateFlow<List<Clip>> = _deletedClips.asStateFlow()

    private val _selectedPatternId = MutableStateFlow(0)
    val selectedPatternId: StateFlow<Int> = _selectedPatternId.asStateFlow()

    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _snap = MutableStateFlow(Snap.QUARTER)
    val snap: StateFlow<Snap> = _snap.asStateFlow()

    private val _selectedTrack = MutableStateFlow(0)
    val selectedTrack: StateFlow<Int> = _selectedTrack.asStateFlow()

    private val _selectedAutomationParam = MutableStateFlow<String?>(null)
    val selectedAutomationParam: StateFlow<String?> = _selectedAutomationParam.asStateFlow()

    /** Track mixer UI state. Engine wiring pending JNI mixer API. */
    data class TrackUiState(
        val mute: Boolean = false,
        val solo: Boolean = false,
        val arm: Boolean = false,
        val level: Float = 0f,
    )

    private val _trackStates = MutableStateFlow(List(16) { TrackUiState() })
    val trackStates: StateFlow<List<TrackUiState>> = _trackStates.asStateFlow()

    /** Stable wrapper for automation point editing. */
    data class UiAutomationPoint(
        val id: Long,
        val point: AutomationPoint,
    )

    private val _automationPoints = MutableStateFlow<List<UiAutomationPoint>>(emptyList())
    val automationPoints: StateFlow<List<UiAutomationPoint>> = _automationPoints.asStateFlow()

    private var pollJob: Job? = null

    init {
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob =
            viewModelScope.launch {
                while (isActive) {
                    val sample = SynthEngine.getPlayheadSample()
                    // The controller is shared with the global transport and
                    // project loader. Read it on every tick so a Timeline VM
                    // created before auto-load never keeps an empty snapshot.
                    val controllerState = transportController.transportState
                    val tick = transportController.sampleToTick(sample, controllerState.tempoBpm)
                    _transportState.value =
                        controllerState.copy(
                            position = TransportPosition.fromTicks(tick, controllerState.timeSignature),
                        )
                    _arrangement.value = transportController.arrangement
                    _patterns.value = transportController.patterns
                    // TODO(integrator): poll per-track level from C++ mixer when API available
                    delay(100)
                }
            }
    }

    override fun onCleared() {
        pollJob?.cancel()
        // The controller belongs to JujiDawApp and is shared by the global
        // transport bar. Releasing it here would permanently cancel its
        // scheduler whenever the user navigates away from Timeline.
        super.onCleared()
    }

    // ---- Transport ----

    fun play() {
        transportController.play()
        _transportState.value = _transportState.value.copy(playing = true)
    }

    fun stop() {
        transportController.stop()
        _transportState.value = _transportState.value.copy(playing = false)
    }

    fun toggleRecording() {
        val recording = !_transportState.value.recording
        transportController.setRecording(recording)
        _transportState.value = _transportState.value.copy(recording = recording)
    }

    fun setTempo(bpm: Float) {
        val clamped = bpm.coerceIn(30f, 300f)
        transportController.setTempo(clamped)
        _transportState.value = _transportState.value.copy(tempoBpm = clamped)
    }

    fun seekToTick(tick: Long) {
        val ts = _transportState.value.timeSignature
        val pos = TransportPosition.fromTicks(tick.coerceAtLeast(0), ts)
        transportController.seek(pos)
        _transportState.value = _transportState.value.copy(position = pos)
    }

    fun nudgePlayhead(ticks: Long) {
        val current = _transportState.value.position.toTicks()
        seekToTick(current + ticks)
    }

    fun resetLoop() {
        val newArr =
            _arrangement.value.copy(
                loopEnabled = false,
                loopStartTick = 0L,
                loopEndTick = PPQ * 4L,
            )
        updateArrangement(newArr)
        SynthEngine.setLoop(false, 0L, 0L)
    }

    // ---- Loop ----

    fun toggleLoop() {
        val enabled = !_transportState.value.loopEnabled
        _transportState.value = _transportState.value.copy(loopEnabled = enabled)
        val newArr = _arrangement.value.copy(loopEnabled = enabled)
        updateArrangement(newArr)
        val bpm = _transportState.value.tempoBpm
        val startSample = transportController.tickToSample(newArr.loopStartTick, bpm)
        val endSample = transportController.tickToSample(newArr.loopEndTick, bpm)
        SynthEngine.setLoop(enabled, startSample, endSample)
    }

    fun setLoopStartToPlayhead() {
        val tick = _transportState.value.position.toTicks()
        setLoopStart(tick)
    }

    fun setLoopEndToPlayhead() {
        val tick = _transportState.value.position.toTicks()
        setLoopEnd(tick)
    }

    private fun setLoopStart(tick: Long) {
        val newArr = _arrangement.value.copy(loopStartTick = tick)
        updateArrangement(newArr)
        if (_transportState.value.loopEnabled) {
            val bpm = _transportState.value.tempoBpm
            SynthEngine.setLoop(
                true,
                transportController.tickToSample(newArr.loopStartTick, bpm),
                transportController.tickToSample(newArr.loopEndTick, bpm),
            )
        }
    }

    private fun setLoopEnd(tick: Long) {
        val newArr = _arrangement.value.copy(loopEndTick = tick)
        updateArrangement(newArr)
        if (_transportState.value.loopEnabled) {
            val bpm = _transportState.value.tempoBpm
            SynthEngine.setLoop(
                true,
                transportController.tickToSample(newArr.loopStartTick, bpm),
                transportController.tickToSample(newArr.loopEndTick, bpm),
            )
        }
    }

    // ---- Punch ----

    fun togglePunch() {
        val state = _transportState.value
        val enabling = !state.punchEnabled
        val current = _arrangement.value
        val oneBarTicks = PPQ * state.timeSignature.numerator.toLong()
        val newArr =
            if (enabling) {
                enablePunchArrangement(current, state.position.toTicks(), oneBarTicks)
            } else {
                current.copy(punchEnabled = false)
            }
        updateArrangement(newArr)
        _transportState.value = transportController.transportState
        transportController.setRecording(_transportState.value.recording)
    }

    fun setPunchInToPlayhead() {
        val tick = _transportState.value.position.toTicks()
        val oneBarTicks = PPQ * _transportState.value.timeSignature.numerator.toLong()
        val newArr =
            _arrangement.value.copy(
                punchInTick = tick,
                punchOutTick = maxOf(_arrangement.value.punchOutTick, tick + oneBarTicks),
            )
        updateArrangement(newArr)
        if (_transportState.value.recording) transportController.setRecording(true)
    }

    fun setPunchOutToPlayhead() {
        val tick = _transportState.value.position.toTicks()
        val oneBarTicks = PPQ * _transportState.value.timeSignature.numerator.toLong()
        val newArr =
            _arrangement.value.copy(
                punchInTick = minOf(_arrangement.value.punchInTick, (tick - oneBarTicks).coerceAtLeast(0L)),
                punchOutTick = tick.coerceAtLeast(1L),
            )
        updateArrangement(newArr)
        if (_transportState.value.recording) transportController.setRecording(true)
    }

    // ---- Zoom / Snap ----

    fun setZoom(z: Float) {
        _zoom.value = z.coerceIn(0.2f, 5f)
    }

    fun setSnap(s: Snap) {
        _snap.value = s
    }

    // ---- Tracks ----

    fun selectTrack(index: Int) {
        _selectedTrack.value = index.coerceIn(0, 15)
    }

    fun selectPattern(id: Int) {
        _selectedPatternId.value = id.coerceIn(0, 15)
    }

    fun toggleMuteTrack(index: Int) {
        _trackStates.value =
            _trackStates.value.mapIndexed { i, s ->
                if (i == index) s.copy(mute = !s.mute) else s
            }
    }

    fun toggleSoloTrack(index: Int) {
        _trackStates.value =
            _trackStates.value.mapIndexed { i, s ->
                if (i == index) s.copy(solo = !s.solo) else s
            }
    }

    fun toggleArmTrack(index: Int) {
        _trackStates.value =
            _trackStates.value.mapIndexed { i, s ->
                if (i == index) s.copy(arm = !s.arm) else s
            }
    }

    // ---- Clips ----

    /** Base ID for cached pad-trigger patterns (pad 0..31 → id 1000..1031). */
    companion object {
        const val PAD_PATTERN_ID_BASE = 1000
    }

    fun addPatternClip(
        trackIndex: Int,
        startTick: Long,
        patternId: Int = _selectedPatternId.value,
        durationTicks: Long = PPQ * 4L,
    ) {
        val snapped = snapTick(startTick.coerceAtLeast(0))
        val newClip =
            PatternClip(
                id = "clip_${System.nanoTime()}",
                trackIndex = trackIndex,
                startTick = snapped,
                durationTicks = durationTicks.coerceAtLeast(TICKS_PER_STEP.toLong()),
                patternId = patternId,
            )
        updateClips(_arrangement.value.clips + newClip)
    }

    fun addAudioClip(
        trackIndex: Int,
        startTick: Long,
        path: String,
        durationTicks: Long,
    ) {
        val snapped = snapTick(startTick.coerceAtLeast(0))
        val newClip =
            AudioClip(
                id = "clip_${System.nanoTime()}",
                trackIndex = trackIndex,
                startTick = snapped,
                durationTicks = durationTicks.coerceAtLeast(TICKS_PER_STEP.toLong()),
                audioFilePath = path,
            )
        updateClips(_arrangement.value.clips + newClip)
        SynthEngine.loadAudioClip(newClip.id, path)
    }

    /** Place a pad on the timeline: creates a pad-trigger clip at [startTick]. */
    fun addPadClip(
        trackIndex: Int,
        startTick: Long,
        padIndex: Int,
    ) {
        val snapped = snapTick(startTick.coerceAtLeast(0))
        val newClip =
            PadClip(
                id = "padClip_${System.nanoTime()}",
                trackIndex = trackIndex,
                startTick = snapped,
                padIndex = padIndex,
            )
        updateClips(_arrangement.value.clips + newClip)
    }

    /**
     * Get or create a cached [Pattern] whose sole note triggers [padIndex].
     */
    private fun getOrCreatePadTriggerPattern(padIndex: Int): Pattern {
        val id = PAD_PATTERN_ID_BASE + padIndex.coerceIn(0, 31)
        return _patterns.value.find { it.id == id } ?: createPadPattern(id, padIndex)
    }

    private fun createPadPattern(
        id: Int,
        padIndex: Int,
    ): Pattern {
        val p =
            Pattern(
                id = id,
                lengthSteps = 4,
                lengthTicks = TICKS_PER_STEP * 4L,
                trackIndex = 1,
                notes =
                    listOf(
                        NoteEvent(
                            note = 60,
                            velocity = 1.0f,
                            startTick = 0L,
                            durationTicks = TICKS_PER_STEP.toLong(),
                            trackIndex = 1,
                            padIndex = padIndex,
                        ),
                    ),
            )
        _patterns.value = _patterns.value + p
        transportController.loadPatterns(_patterns.value)
        scheduleAutosave()
        return p
    }

    fun moveClip(
        clipId: String,
        newStartTick: Long,
        newTrackIndex: Int,
    ) {
        val snapped = snapTick(newStartTick.coerceAtLeast(0))
        val track = newTrackIndex.coerceIn(0, 15)
        updateClips(
            _arrangement.value.clips.map {
                when (it) {
                    is PatternClip -> if (it.id == clipId) it.copy(startTick = snapped, trackIndex = track) else it
                    is PadClip -> if (it.id == clipId) it.copy(startTick = snapped, trackIndex = track) else it
                    is AudioClip -> if (it.id == clipId) it.copy(startTick = snapped, trackIndex = track) else it
                }
            },
        )
    }

    fun trimClip(
        clipId: String,
        newDurationTicks: Long,
    ) {
        val d = newDurationTicks.coerceAtLeast(TICKS_PER_STEP.toLong())
        updateClips(
            _arrangement.value.clips.map {
                when (it) {
                    is PatternClip -> if (it.id == clipId) it.copy(durationTicks = d) else it
                    is PadClip -> if (it.id == clipId) it.copy(durationTicks = d) else it
                    is AudioClip -> if (it.id == clipId) it.copy(durationTicks = d) else it
                }
            },
        )
    }

    fun toggleMuteClip(clipId: String) {
        updateClips(
            _arrangement.value.clips.map {
                val mute = if (it.id == clipId) !it.mute else it.mute
                when (it) {
                    is PatternClip -> it.copy(mute = mute)
                    is PadClip -> it.copy(mute = mute)
                    is AudioClip -> it.copy(mute = mute)
                }
            },
        )
    }

    fun deleteClip(clipId: String) {
        val clip = _arrangement.value.clips.find { it.id == clipId }
        if (clip != null) _deletedClips.value = _deletedClips.value + clip
        if (clip is AudioClip) SynthEngine.unloadAudioClip(clip.id)
        updateClips(_arrangement.value.clips.filter { it.id != clipId })
    }

    fun restoreLastDeletedClip() {
        val clip = _deletedClips.value.lastOrNull() ?: return
        _deletedClips.value = _deletedClips.value.dropLast(1)
        updateClips(_arrangement.value.clips + clip)
        if (clip is AudioClip) SynthEngine.loadAudioClip(clip.id, clip.audioFilePath)
    }

    private fun updateClips(newClips: List<Clip>) {
        val newArr = _arrangement.value.copy(clips = newClips)
        updateArrangement(newArr)
    }

    private fun updateArrangement(newArr: Arrangement) {
        _arrangement.value = newArr
        transportController.loadArrangement(newArr)
        scheduleAutosave()
    }

    /**
     * Debounced auto-save so timeline edits survive a crash or forced kill
     * (the [com.jujidaw.MainActivity] onStop save only fires on background).
     */
    private fun scheduleAutosave() {
        ProjectAutosave.scheduleAutoSave(
            JujiDawApp.instance,
            transportController,
        )
    }

    // ---- Automation ----

    fun selectAutomationParam(paramId: String?) {
        _selectedAutomationParam.value = paramId
        refreshAutomationPoints()
    }

    private fun refreshAutomationPoints() {
        val param = _selectedAutomationParam.value
        val pts =
            if (param == null) {
                emptyList()
            } else {
                _arrangement.value.automation.filter { it.paramId == param }
            }
        _automationPoints.value =
            pts.mapIndexed { idx, p ->
                UiAutomationPoint(id = idx.toLong(), point = p)
            }
    }

    fun addAutomationPoint(
        paramId: String,
        tick: Long,
        value: Float,
        curve: AutomationCurve = AutomationCurve.LINEAR,
    ) {
        val newPoint = AutomationPoint(paramId, tick.coerceAtLeast(0), value.coerceIn(0f, 1f), curve)
        val newArr =
            _arrangement.value.copy(
                automation = _arrangement.value.automation + newPoint,
            )
        updateArrangement(newArr)
        refreshAutomationPoints()
    }

    fun moveAutomationPoint(
        id: Long,
        newTick: Long,
        newValue: Float,
    ) {
        val ui = _automationPoints.value.find { it.id == id } ?: return
        val oldList = _arrangement.value.automation.toMutableList()
        val idx =
            oldList.indexOfFirst {
                it === ui.point || (it.paramId == ui.point.paramId && it.tick == ui.point.tick && it.value == ui.point.value)
            }
        if (idx >= 0) {
            oldList[idx] =
                ui.point.copy(
                    tick = newTick.coerceAtLeast(0),
                    value = newValue.coerceIn(0f, 1f),
                )
            updateArrangement(_arrangement.value.copy(automation = oldList))
            refreshAutomationPoints()
        }
    }

    fun deleteAutomationPoint(id: Long) {
        val ui = _automationPoints.value.find { it.id == id } ?: return
        val newList =
            _arrangement.value.automation.filterNot {
                it.paramId == ui.point.paramId && it.tick == ui.point.tick && it.value == ui.point.value
            }
        updateArrangement(_arrangement.value.copy(automation = newList))
        refreshAutomationPoints()
    }

    // ---- Patterns ----

    fun loadPatterns(patterns: List<Pattern>) {
        _patterns.value = patterns
        transportController.loadPatterns(patterns)
    }

    // ---- Helpers ----

    fun snapTick(tick: Long): Long {
        val res = _snap.value.ticks
        return ((tick + res / 2) / res) * res
    }

    enum class Snap(
        val ticks: Long,
        val label: String,
    ) {
        BAR(PPQ * 4L, "Bar"),
        QUARTER(PPQ.toLong(), "1/4"),
        EIGHTH((PPQ / 2).toLong(), "1/8"),
        SIXTEENTH((PPQ / 4).toLong(), "1/16"),
    }
}

/** Build a valid punch range without making UI state briefly invalid. */
internal fun enablePunchArrangement(
    arrangement: Arrangement,
    currentTick: Long,
    oneBarTicks: Long,
): Arrangement =
    if (arrangement.punchInTick < arrangement.punchOutTick) {
        arrangement.copy(punchEnabled = true)
    } else {
        arrangement.copy(
            punchEnabled = true,
            punchInTick = currentTick.coerceAtLeast(0L),
            punchOutTick = currentTick.coerceAtLeast(0L) + oneBarTicks.coerceAtLeast(1L),
        )
    }
