package com.jujidaw.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jujidaw.JujiDawApp
import com.jujidaw.audio.SynthEngine
import com.jujidaw.engine.TransportController
import com.jujidaw.model.*
import com.jujidaw.project.ProjectAutosave
import com.jujidaw.project.PadSelectionStore
import com.jujidaw.project.PatternSelectionStore
import com.jujidaw.project.PadPerformanceEvent
import com.jujidaw.project.PadPerformanceEventBus
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

    private val _tool = MutableStateFlow(TimelineTool.SELECT)
    val tool: StateFlow<TimelineTool> = _tool.asStateFlow()

    private val _selectedClipIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedClipIds: StateFlow<Set<String>> = _selectedClipIds.asStateFlow()

    /** Reused by the draw tools so repeated entry does not require a resize every time. */
    private var lastPadDurationTicks: Long = PPQ.toLong()
    private var lastPatternDurationTicks: Long? = null
    private var clipboard: List<Clip> = emptyList()
    private val recordedPadStarts = mutableMapOf<Int, RecordedPadStart>()

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
        viewModelScope.launch {
            PatternSelectionStore.selectedPattern.collect { selected ->
                _selectedPatternId.value = selected
            }
        }
        viewModelScope.launch {
            PadPerformanceEventBus.events.collect(::recordPadPerformanceEvent)
        }
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
                    if (SynthEngine.isLoaded) {
                        _trackStates.value =
                            List(16) { index ->
                                TrackUiState(
                                    mute = SynthEngine.isChannelMute(index),
                                    solo = SynthEngine.isChannelSolo(index),
                                    arm = SynthEngine.isChannelArm(index),
                                    level = SynthEngine.getChannelLevel(index).coerceIn(0f, 1f),
                                )
                            }
                    }
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

    fun setSwing(amount: Float) {
        val clamped = amount.coerceIn(0f, 1f)
        transportController.setSwing(clamped)
        _transportState.value = _transportState.value.copy(swing = clamped)
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

    fun setTool(tool: TimelineTool) {
        _tool.value = tool
        if (tool != TimelineTool.SELECT) _selectedClipIds.value = emptySet()
    }

    // ---- Tracks ----

    fun selectTrack(index: Int) {
        _selectedTrack.value = index.coerceIn(0, 15)
    }

    fun selectPattern(id: Int) {
        PatternSelectionStore.select(id)
    }

    fun toggleMuteTrack(index: Int) {
        val target = index.coerceIn(0, 15)
        val mute = !_trackStates.value[target].mute
        if (SynthEngine.isLoaded) SynthEngine.setChannelMute(target, mute)
        _trackStates.value =
            _trackStates.value.mapIndexed { i, s ->
                if (i == target) s.copy(mute = mute) else s
            }
    }

    fun toggleSoloTrack(index: Int) {
        val target = index.coerceIn(0, 15)
        val solo = !_trackStates.value[target].solo
        if (SynthEngine.isLoaded) SynthEngine.setChannelSolo(target, solo)
        _trackStates.value =
            _trackStates.value.mapIndexed { i, s ->
                if (i == target) s.copy(solo = solo) else s
            }
    }

    fun toggleArmTrack(index: Int) {
        val target = index.coerceIn(0, 15)
        val arm = !_trackStates.value[target].arm
        if (SynthEngine.isLoaded) {
            for (track in 0 until 16) SynthEngine.setChannelArm(track, arm && track == target)
        }
        _trackStates.value =
            _trackStates.value.mapIndexed { i, s ->
                s.copy(arm = arm && i == target)
            }
    }

    /** Recording follows the single armed mixer row, or the last row touched by the user. */
    fun recordingTrack(): Int = _trackStates.value.indexOfFirst { it.arm }.takeIf { it >= 0 } ?: _selectedTrack.value

    // ---- Clips ----

    /** Base ID for cached pad-trigger patterns (pad 0..31 → id 1000..1031). */
    companion object {
        const val PAD_PATTERN_ID_BASE = 1000
    }

    fun addPatternClip(
        trackIndex: Int,
        startTick: Long,
        patternId: Int = _selectedPatternId.value,
        durationTicks: Long = lastPatternDurationTicks
            ?: _patterns.value.firstOrNull { it.id == patternId }?.lengthTicks
            ?: PPQ * 4L,
    ) {
        val snapped = snapTick(startTick.coerceAtLeast(0))
        val duration = normalizeDuration(durationTicks)
        val newClip =
            PatternClip(
                id = "clip_${System.nanoTime()}",
                trackIndex = trackIndex,
                startTick = snapped,
                durationTicks = duration,
                patternId = patternId,
            )
        updateClips(_arrangement.value.clips + newClip)
        _selectedClipIds.value = setOf(newClip.id)
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
        durationTicks: Long = lastPadDurationTicks,
    ) {
        val snapped = snapTick(startTick.coerceAtLeast(0))
        val newClip =
            PadClip(
                id = "padClip_${System.nanoTime()}",
                trackIndex = trackIndex,
                startTick = snapped,
                durationTicks = normalizeDuration(durationTicks),
                padIndex = padIndex,
                gateMode = PadGateMode.TIMELINE_GATE,
            )
        updateClips(_arrangement.value.clips + newClip)
        _selectedClipIds.value = setOf(newClip.id)
    }

    private fun recordPadPerformanceEvent(event: PadPerformanceEvent) {
        val state = transportController.transportState
        val tick = transportController.sampleToTick(event.sampleTime, state.tempoBpm).coerceAtLeast(0L)
        when (event) {
            is PadPerformanceEvent.On -> {
                if (!state.playing || !state.recording || !isInsidePunch(tick)) return
                recordedPadStarts[event.padIndex] =
                    RecordedPadStart(
                        startTick = snapTick(tick),
                        trackIndex = recordingTrack(),
                        velocity = (event.velocity / 127f).coerceIn(0f, 1f),
                    )
            }

            is PadPerformanceEvent.Off -> {
                val started = recordedPadStarts.remove(event.padIndex) ?: return
                if (!state.playing || !state.recording) return
                val endTick = if (_arrangement.value.punchEnabled) {
                    tick.coerceAtMost(_arrangement.value.punchOutTick)
                } else {
                    tick
                }
                val duration = normalizeDuration(endTick - started.startTick)
                val newClip =
                    PadClip(
                        id = "padClip_${System.nanoTime()}",
                        trackIndex = started.trackIndex,
                        startTick = started.startTick,
                        durationTicks = duration,
                        padIndex = event.padIndex,
                        velocity = started.velocity,
                        gateMode = PadGateMode.TIMELINE_GATE,
                    )
                updateClips(_arrangement.value.clips + newClip)
                _selectedClipIds.value = setOf(newClip.id)
            }
        }
    }

    private fun isInsidePunch(tick: Long): Boolean =
        !_arrangement.value.punchEnabled || tick in _arrangement.value.punchInTick until _arrangement.value.punchOutTick

    /** Insert the current source onto the row that was tapped by the user. */
    fun placeSelectedSource(trackIndex: Int, startTick: Long) {
        when (_tool.value) {
            TimelineTool.DRAW_PAD -> addPadClip(trackIndex, startTick, PadSelectionStore.selectedPad.value)
            TimelineTool.DRAW_PATTERN -> addPatternClip(trackIndex, startTick, PatternSelectionStore.selectedPattern.value)
            TimelineTool.SELECT -> selectTrack(trackIndex)
        }
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

    fun moveSelectedClips(
        anchorClipId: String,
        newStartTick: Long,
        newTrackIndex: Int,
    ) {
        val selected = _selectedClipIds.value.ifEmpty { setOf(anchorClipId) }
        val anchor = _arrangement.value.clips.firstOrNull { it.id == anchorClipId } ?: return
        val minStart = _arrangement.value.clips.filter { it.id in selected }.minOfOrNull { it.startTick } ?: anchor.startTick
        val minTrack = _arrangement.value.clips.filter { it.id in selected }.minOfOrNull { it.trackIndex } ?: anchor.trackIndex
        val maxTrack = _arrangement.value.clips.filter { it.id in selected }.maxOfOrNull { it.trackIndex } ?: anchor.trackIndex
        val deltaTick = (snapTick(newStartTick) - anchor.startTick).coerceAtLeast(-minStart)
        val deltaTrack = (newTrackIndex - anchor.trackIndex).coerceIn(-minTrack, 15 - maxTrack)
        updateClips(
            _arrangement.value.clips.map { clip ->
                if (clip.id !in selected) return@map clip
                moveClipValue(clip, clip.startTick + deltaTick, clip.trackIndex + deltaTrack)
            },
        )
    }

    fun trimClip(
        clipId: String,
        newDurationTicks: Long,
    ) {
        val d = normalizeDuration(newDurationTicks)
        val changed = _arrangement.value.clips.find { it.id == clipId }
        if (changed is PadClip) lastPadDurationTicks = d
        if (changed is PatternClip) lastPatternDurationTicks = d
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

    /** Keep the right edge fixed while changing a clip's left edge. */
    fun trimClipFromLeft(clipId: String, requestedStartTick: Long) {
        val clip = _arrangement.value.clips.find { it.id == clipId } ?: return
        val end = clip.startTick + clip.durationTicks
        val minDuration = minimumDuration()
        val start = snapTick(requestedStartTick.coerceAtLeast(0)).coerceAtMost(end - minDuration)
        val duration = (end - start).coerceAtLeast(minDuration)
        if (clip is PadClip) lastPadDurationTicks = duration
        if (clip is PatternClip) lastPatternDurationTicks = duration
        updateClips(
            _arrangement.value.clips.map {
                if (it.id != clipId) it else resizeClipValue(it, start, duration)
            },
        )
    }

    fun selectClip(clipId: String, addToSelection: Boolean = false) {
        _selectedClipIds.value =
            if (addToSelection) {
                _selectedClipIds.value.toggle(clipId)
            } else {
                setOf(clipId)
            }
    }

    fun selectClipsInRange(
        startTick: Long,
        endTick: Long,
        startTrack: Int,
        endTrack: Int,
    ) {
        val left = minOf(startTick, endTick)
        val right = maxOf(startTick, endTick)
        val top = minOf(startTrack, endTrack)
        val bottom = maxOf(startTrack, endTrack)
        _selectedClipIds.value =
            _arrangement.value.clips
                .filter { it.trackIndex in top..bottom && it.startTick < right && it.startTick + it.durationTicks > left }
                .mapTo(linkedSetOf()) { it.id }
    }

    fun clearClipSelection() {
        _selectedClipIds.value = emptySet()
    }

    fun copySelectedClips() {
        clipboard = _arrangement.value.clips.filter { it.id in _selectedClipIds.value }
    }

    fun pasteClipboard(anchorTick: Long, anchorTrack: Int) {
        if (clipboard.isEmpty()) return
        val minTick = clipboard.minOf { it.startTick }
        val minTrack = clipboard.minOf { it.trackIndex }
        val maxTrack = clipboard.maxOf { it.trackIndex }
        val trackOffset = anchorTrack.coerceIn(0, 15 - (maxTrack - minTrack)) - minTrack
        val tickOffset = snapTick(anchorTick.coerceAtLeast(0)) - minTick
        val pasted = clipboard.map { clip -> copyClipWithNewId(clip, clip.startTick + tickOffset, clip.trackIndex + trackOffset) }
        updateClips(_arrangement.value.clips + pasted)
        _selectedClipIds.value = pasted.mapTo(linkedSetOf()) { it.id }
    }

    fun duplicateSelectedClips() {
        val selected = _arrangement.value.clips.filter { it.id in _selectedClipIds.value }
        if (selected.isEmpty()) return
        val minTick = selected.minOf { it.startTick }
        val maxEnd = selected.maxOf { it.startTick + it.durationTicks }
        val offset = normalizeDuration(maxEnd - minTick)
        val duplicated = selected.map { copyClipWithNewId(it, it.startTick + offset, it.trackIndex) }
        updateClips(_arrangement.value.clips + duplicated)
        _selectedClipIds.value = duplicated.mapTo(linkedSetOf()) { it.id }
    }

    fun deleteSelectedClips() {
        val selected = _selectedClipIds.value
        if (selected.isEmpty()) return
        _arrangement.value.clips.filter { it.id in selected }.forEach { if (it is AudioClip) SynthEngine.unloadAudioClip(it.id) }
        _deletedClips.value = _deletedClips.value + _arrangement.value.clips.filter { it.id in selected }
        updateClips(_arrangement.value.clips.filter { it.id !in selected })
        _selectedClipIds.value = emptySet()
    }

    fun toggleMuteSelectedClips() {
        val selected = _selectedClipIds.value
        if (selected.isEmpty()) return
        val mute = _arrangement.value.clips.any { it.id in selected && !it.mute }
        updateClips(
            _arrangement.value.clips.map { clip ->
                if (clip.id !in selected) clip else copyClipWithMute(clip, mute)
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
        _selectedClipIds.value = _selectedClipIds.value - clipId
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
        if (_snap.value == Snap.FREE) return tick.coerceAtLeast(0)
        val res = _snap.value.ticks
        return ((tick + res / 2) / res) * res
    }

    private fun minimumDuration(): Long = if (_snap.value == Snap.FREE) 1L else _snap.value.ticks

    private fun normalizeDuration(duration: Long): Long {
        val minimum = minimumDuration()
        if (_snap.value == Snap.FREE) return duration.coerceAtLeast(minimum)
        return ((duration.coerceAtLeast(minimum) + minimum / 2) / minimum) * minimum
    }

    private fun moveClipValue(clip: Clip, start: Long, track: Int): Clip =
        when (clip) {
            is PatternClip -> clip.copy(startTick = start, trackIndex = track)
            is PadClip -> clip.copy(startTick = start, trackIndex = track)
            is AudioClip -> clip.copy(startTick = start, trackIndex = track)
        }

    private fun resizeClipValue(clip: Clip, start: Long, duration: Long): Clip =
        when (clip) {
            is PatternClip -> {
                val patternLength = _patterns.value.firstOrNull { it.id == clip.patternId }?.lengthTicks ?: 1L
                val offset = Math.floorMod(clip.contentOffsetTicks + (start - clip.startTick), patternLength)
                clip.copy(startTick = start, durationTicks = duration, contentOffsetTicks = offset)
            }
            is PadClip -> clip.copy(startTick = start, durationTicks = duration)
            is AudioClip -> clip.copy(startTick = start, durationTicks = duration)
        }

    private fun copyClipWithMute(clip: Clip, mute: Boolean): Clip =
        when (clip) {
            is PatternClip -> clip.copy(mute = mute)
            is PadClip -> clip.copy(mute = mute)
            is AudioClip -> clip.copy(mute = mute)
        }

    private fun copyClipWithNewId(clip: Clip, start: Long, track: Int): Clip {
        val id = "clip_${System.nanoTime()}_${clip.id.hashCode()}"
        return when (clip) {
            is PatternClip -> clip.copy(id = id, startTick = start, trackIndex = track)
            is PadClip -> clip.copy(id = id, startTick = start, trackIndex = track)
            is AudioClip -> clip.copy(id = id, startTick = start, trackIndex = track)
        }
    }

    private data class RecordedPadStart(
        val startTick: Long,
        val trackIndex: Int,
        val velocity: Float,
    )

    enum class Snap(
        val ticks: Long,
        val label: String,
    ) {
        FREE(1L, "Free"),
        BAR(PPQ * 4L, "Bar"),
        QUARTER(PPQ.toLong(), "1/4"),
        EIGHTH((PPQ / 2).toLong(), "1/8"),
        SIXTEENTH((PPQ / 4).toLong(), "1/16"),
    }
}

enum class TimelineTool {
    SELECT,
    DRAW_PAD,
    DRAW_PATTERN,
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

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
