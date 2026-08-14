package com.jujidaw.engine

import com.jujidaw.model.Arrangement
import com.jujidaw.model.AudioClip
import com.jujidaw.model.Clip
import com.jujidaw.model.NoteEvent
import com.jujidaw.model.ParamIds
import com.jujidaw.model.PadGateMode
import com.jujidaw.model.PPQ
import com.jujidaw.model.Pattern
import com.jujidaw.model.PatternClip
import com.jujidaw.model.PadClip
import com.jujidaw.model.TICKS_PER_BEAT
import com.jujidaw.model.TICKS_PER_STEP
import com.jujidaw.model.TimeSignature
import com.jujidaw.model.TransportPosition
import com.jujidaw.model.TransportState
import com.jujidaw.project.AutomationClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * DAW transport controller.
 *
 * Owns the musical clock (bars/beats/ticks), pattern data, and arrangement.
 * Runs a scheduler coroutine that converts upcoming musical events into
 * sample offsets and pushes them to the C++ engine via an abstract
 * [SynthEngineScheduler] so that the scheduling logic can be
 * unit-tested without native code.
 *
 * The internal C++ step sequencer is disabled while the transport is playing,
 * and re-enabled when stopped.
 */
class TransportController(
    sampleRate: Int = 48000,
    private val lookaheadMs: Long = 100,
    private val schedulingIntervalMs: Long = 50,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1)),
    private val scheduler: SynthEngineScheduler = NativeSynthEngineScheduler(),
) {
    @Volatile
    private var sampleRate: Int = sampleRate

    private var schedulerJob: Job? = null

    // Mutable state (must be accessed from the scheduler thread only, except
    // for the public control methods which post changes to pending state).
    var transportState: TransportState = TransportState()
        private set

    /** Automation clips loaded for the current arrangement. */
    var automationClips: MutableList<AutomationClip> = mutableListOf()

    var patterns: List<Pattern> = emptyList()
    var arrangement: Arrangement = Arrangement()

    /** True when the sequencer screen is active (disables arrangement playback). */
    var isSequencerMode: Boolean = false

    /** Current step index within the active pattern, updated by the scheduler. */
    var currentStep: Int = 0
        private set

    // Pattern launcher state (internal for test access)
    internal var activePatternId: Int = -1
        private set
    internal var queuedPatternId: Int = -1
        private set
    internal var pendingSwitchSample: Long = -1L
        private set

    // Held notes per track, used to send note-offs on pattern switch / stop.
    internal val heldNotes: MutableMap<Int, MutableSet<Int>> = mutableMapOf()

    // Clips that have already been started, so we don't re-trigger them.
    internal val startedClips: MutableSet<String> = mutableSetOf()

    // ---- Scheduled-event de-duplication ----
    //
    // The scheduler coroutine runs every ~50ms with a ~100ms lookahead, so a
    // single note whose start sample falls inside the window would otherwise be
    // re-pushed on every overlapping tick (and again if both the launcher and
    // the arrangement reference the same pattern). Each entry uniquely
    // identifies a (track, kind, note-or-pad, startSample) event already pushed
    // to the engine. Entries are cleared together with
    // scheduler.clearScheduledEvents() (stop / seek / loop wrap / pattern
    // switch) so legitimate re-triggers after those points still fire.
    internal sealed interface ScheduledEventKey {
        val startSample: Long

        data class NoteOn(
            override val startSample: Long,
            val track: Int,
            val note: Int,
        ) : ScheduledEventKey

        data class NoteOff(
            override val startSample: Long,
            val track: Int,
            val note: Int,
        ) : ScheduledEventKey

        data class Pad(
            override val startSample: Long,
            val track: Int,
            val padIndex: Int,
            val sourceId: String,
        ) : ScheduledEventKey

        data class PadRelease(
            override val startSample: Long,
            val track: Int,
            val padIndex: Int,
            val sourceId: String,
        ) : ScheduledEventKey
    }

    internal val scheduledEventKeys: MutableSet<ScheduledEventKey> = mutableSetOf()

    private fun clearScheduledEventKeys() {
        scheduledEventKeys.clear()
    }

    /**
     * Start transport playback. The scheduler coroutine begins pushing events.
     */
    fun play() {
        if (transportState.playing) return
        transportState = transportState.copy(playing = true)
        scheduler.setSequencerEnabled(false)
        scheduler.setTransport(true, transportState.recording, transportState.tempoBpm)
        startScheduler()
    }

    /**
     * Stop transport playback. All scheduled events are cleared and active
     * notes are released.
     */
    fun stop() {
        transportState = transportState.copy(playing = false)
        schedulerJob?.cancel()
        schedulerJob = null
        currentStep = 0
        clearScheduledEventKeys()
        stopAllHeldNotes()
        startedClips.clear()
        scheduler.resetTransport(scheduler.getPlayheadSample(), false, false)
        scheduler.setSequencerEnabled(true)
    }

    /** Stop all playback and return the transport to the first tick. */
    fun restart() {
        schedulerJob?.cancel()
        schedulerJob = null
        transportState = transportState.copy(
            playing = false,
            recording = false,
            position = TransportPosition(),
        )
        currentStep = 0
        clearScheduledEventKeys()
        stopAllHeldNotes()
        startedClips.clear()
        scheduler.resetTransport(0L, false, false)
        scheduler.setSequencerEnabled(true)
    }

    /**
     * Seek to an absolute musical position. Clears pending events and held
     * notes.
     */
    fun seek(position: TransportPosition) {
        val wasPlaying = transportState.playing
        val wasRecording = transportState.recording
        transportState = transportState.copy(position = position)
        val sample = tickToSample(position.toTicks(transportState.timeSignature))
        clearScheduledEventKeys()
        stopAllHeldNotes()
        startedClips.clear()
        scheduler.resetTransport(sample, wasPlaying, wasRecording)
    }

    /**
     * Queue a pattern switch at the next bar boundary.
     */
    fun queuePattern(patternId: Int) {
        if (patternId !in 0..15) return
        queuedPatternId = patternId
        pendingSwitchSample = -1L // recalculated on next scheduler tick
    }

    /**
     * Update transport tempo. Takes effect on the next scheduler tick.
     */
    fun setTempo(bpm: Float) {
        transportState = transportState.copy(tempoBpm = bpm)
        if (transportState.playing) {
            scheduler.setTransport(true, transportState.recording, bpm)
        }
    }

    /** Update the project time signature used for bar/beat transport positions. */
    fun setTimeSignature(timeSignature: TimeSignature) {
        transportState = transportState.copy(timeSignature = timeSignature)
    }

    /**
     * Set global 1/16-note swing. The value delays only events that start
     * exactly on an odd 1/16 division; free/off-grid events remain untouched.
     */
    fun setSwing(amount: Float) {
        require(amount in 0f..1f) { "Swing must be between 0 and 1" }
        transportState = transportState.copy(swing = amount)
    }

    /**
     * Toggle recording. Punch-in/out range is taken from [arrangement].
     */
    fun setRecording(recording: Boolean) {
        transportState = transportState.copy(recording = recording)
        if (recording) {
            val punchInSample =
                if (arrangement.punchEnabled) {
                    tickToSample(arrangement.punchInTick, transportState.tempoBpm)
                } else {
                    0L
                }
            val punchOutSample =
                if (arrangement.punchEnabled) {
                    tickToSample(arrangement.punchOutTick, transportState.tempoBpm)
                } else {
                    Long.MAX_VALUE
                }
            scheduler.setPunchRange(arrangement.punchEnabled, punchInSample, punchOutSample)
        } else {
            scheduler.setPunchRange(false, 0L, 0L)
        }
        if (transportState.playing) {
            scheduler.setTransport(true, recording, transportState.tempoBpm)
        }
    }

    /**
     * Replace the active pattern list.
     */
    fun loadPatterns(newPatterns: List<Pattern>) {
        patterns = newPatterns
    }

    /**
     * Replace the arrangement.
     */
    fun loadArrangement(newArrangement: Arrangement) {
        val migratedClips = newArrangement.clips.map { clip ->
            if (clip is PatternClip && clip.padIndex in 0..31 && clip.patternId in 1000..1031) {
                PadClip(
                    id = clip.id,
                    trackIndex = clip.trackIndex,
                    startTick = clip.startTick,
                    durationTicks = clip.durationTicks,
                    mute = clip.mute,
                    padIndex = clip.padIndex,
                )
            } else clip
        }
        arrangement = newArrangement.copy(clips = migratedClips)
        transportState =
            transportState.copy(
                loopEnabled = newArrangement.loopEnabled,
                loopStart = TransportPosition.fromTicks(newArrangement.loopStartTick, transportState.timeSignature),
                loopEnd = TransportPosition.fromTicks(newArrangement.loopEndTick, transportState.timeSignature),
                punchEnabled = newArrangement.punchEnabled,
                punchIn = TransportPosition.fromTicks(newArrangement.punchInTick, transportState.timeSignature),
                punchOut = TransportPosition.fromTicks(newArrangement.punchOutTick, transportState.timeSignature),
            )
    }

    /**
     * Release all resources.
     */
    fun release() {
        stop()
        coroutineScope.cancel()
    }

    /**
     * Update musical-time conversion to match the rate negotiated by Oboe.
     * This is safe while stopped or playing; subsequent scheduler ticks use
     * the new rate for both event targets and playhead position.
     */
    fun updateSampleRate(sampleRate: Int) {
        require(sampleRate > 0) { "Sample rate must be positive" }
        this.sampleRate = sampleRate
    }

    private fun startScheduler() {
        schedulerJob?.cancel()
        schedulerJob =
            coroutineScope.launch {
                while (isActive && transportState.playing) {
                    val currentSample = scheduler.getPlayheadSample()
                    val tick = sampleToTick(currentSample, transportState.tempoBpm)
                    currentStep = (tick / TICKS_PER_STEP).toInt()
                    transportState = transportState.copy(
                        position = TransportPosition.fromTicks(tick, transportState.timeSignature),
                    )
                    scheduleNextBlock()
                    delay(schedulingIntervalMs)
                }
            }
    }

    /**
     * Main scheduling tick — exposed as internal so that tests can drive
     * the scheduling logic without running the real coroutine loop.
     */
    internal fun scheduleNextBlock() {
        val currentSample = scheduler.getPlayheadSample()
        val lookaheadSamples = (sampleRate * lookaheadMs) / 1000
        val windowEnd = currentSample + lookaheadSamples

        val bpm = transportState.tempoBpm
        val timeSignature = transportState.timeSignature

        // 1. Pattern launcher / chaining (live sequencer pattern playback).
        schedulePatternLauncher(currentSample, windowEnd, bpm, timeSignature)

        // 2. Timeline arrangement — skipped while the sequencer screen owns
        //    playback (isSequencerMode) so the active launcher pattern and an
        //    arrangement clip referencing the same pattern can't both fire
        //    the same notes in the same tick.
        if (!isSequencerMode) {
            scheduleArrangement(currentSample, windowEnd, bpm, timeSignature)
        }

        // 3. Automation events
        scheduleAutomationEvents(currentSample, windowEnd, bpm)

        // 4. Loop wrap
        checkLoopWrap(currentSample, windowEnd, bpm, timeSignature)
    }

    private fun schedulePatternLauncher(
        currentSample: Long,
        windowEnd: Long,
        bpm: Float,
        timeSignature: TimeSignature,
    ) {
        if (activePatternId < 0) {
            // No active pattern yet; if one is queued, start immediately.
            if (queuedPatternId >= 0) {
                activePatternId = queuedPatternId
                queuedPatternId = -1
                pendingSwitchSample = -1L
            } else {
                return
            }
        }

        // Resolve bar-boundary pattern switch.
        if (queuedPatternId >= 0 && pendingSwitchSample < 0) {
            val samplesPerBar =
                tickToSampleDelta((PPQ * timeSignature.numerator).toLong(), bpm)
            val nextBarSample = ((currentSample / samplesPerBar) + 1) * samplesPerBar
            pendingSwitchSample = nextBarSample
        }

        if (queuedPatternId >= 0 && currentSample >= pendingSwitchSample) {
            stopAllNotesOnTrack(patterns.find { it.id == activePatternId }?.trackIndex ?: 0)
            clearScheduledEventKeys()
            activePatternId = queuedPatternId
            queuedPatternId = -1
            pendingSwitchSample = -1L
            // Reset transport position inside the pattern to tick 0
            transportState = transportState.copy(position = TransportPosition())
        }

        val pattern = patterns.find { it.id == activePatternId } ?: return
        val patternLength = pattern.lengthTicks.coerceAtLeast(TICKS_PER_STEP.toLong())
        val currentTick = sampleToTick(currentSample, bpm)
        val windowEndTick = sampleToTick(windowEnd, bpm)
        val firstCycle = (currentTick / patternLength) * patternLength
        // The scheduler ticks frequently enough to revisit the next cycle as
        // the playhead approaches it. Scheduling only the current cycle keeps
        // unusually large lookaheads from flooding the native event queue.
        val cycleStart = firstCycle
        if (cycleStart <= windowEndTick) {
            schedulePatternNotes(
                pattern = pattern,
                currentSample = currentSample,
                windowEnd = windowEnd,
                bpm = bpm,
                patternStartTickOffset = cycleStart,
                maxEndTick = cycleStart + patternLength,
            )
        }
    }

    private fun scheduleArrangement(
        currentSample: Long,
        windowEnd: Long,
        bpm: Float,
        timeSignature: TimeSignature,
    ) {
        val startTick = sampleToTick(currentSample, bpm)
        val endTick = sampleToTick(windowEnd, bpm)
        val clips = arrangement.clipsInRange(startTick, endTick)

        for (clip in clips) {
            when (clip) {
                is PatternClip -> schedulePatternClip(clip, currentSample, windowEnd, bpm)
                is PadClip -> schedulePadClip(clip, currentSample, windowEnd, bpm)
                is AudioClip -> scheduleAudioClip(clip, currentSample, windowEnd, bpm)
            }
        }
    }

    private fun schedulePadClip(
        clip: PadClip,
        currentSample: Long,
        windowEnd: Long,
        bpm: Float,
    ) {
        val swingOffsetTicks = swingOffsetTicks(clip.startTick)
        val startSample = tickToSample(clip.startTick + swingOffsetTicks, bpm)
        val endSample = startSample + tickToSampleDelta(clip.durationTicks, bpm)
        // Arrangement pad clips are trigger events, not audio regions. Do
        // not retrigger one when it is inserted during live recording after
        // its onset has already passed.
        if (startSample < currentSample || startSample > windowEnd) return
        val key = ScheduledEventKey.Pad(startSample, clip.trackIndex, clip.padIndex, clip.id)
        if (!scheduledEventKeys.add(key)) return
        val triggerId = timelineTriggerId(clip.id, startSample)
        schedulePadTrigger(clip.trackIndex, clip.padIndex, clip.velocity, startSample, triggerId)
        if (clip.gateMode == PadGateMode.TIMELINE_GATE) {
            val releaseKey = ScheduledEventKey.PadRelease(endSample, clip.trackIndex, clip.padIndex, clip.id)
            if (scheduledEventKeys.add(releaseKey)) {
                schedulePadRelease(clip.trackIndex, clip.padIndex, endSample, triggerId)
            }
        }
    }

    internal fun schedulePatternClip(
        clip: PatternClip,
        currentSample: Long,
        windowEnd: Long,
        bpm: Float,
    ) {
        val pattern = patterns.find { it.id == clip.patternId } ?: return
        val clipStartSample = tickToSample(clip.startTick, bpm)
        val clipEndSample = clipStartSample + tickToSampleDelta(clip.durationTicks, bpm)
        if (clipEndSample <= currentSample || clipStartSample > windowEnd) return

        // A resized pattern clip may begin partway through the source pattern.
        // Treat its content offset as a virtual pattern cycle that started
        // before the clip, then only emit notes inside the visible window.
        val patternLength = pattern.lengthTicks.coerceAtLeast(TICKS_PER_STEP.toLong())
        val contentOffset = clip.contentOffsetTicks % patternLength
        val clipEndTick = clip.startTick + clip.durationTicks
        var cycleStartTick = clip.startTick - contentOffset
        while (cycleStartTick < clipEndTick) {
            val cycleEndTick = cycleStartTick + patternLength
            if (tickToSample(cycleStartTick, bpm) > windowEnd) break

            schedulePatternNotes(
                pattern = pattern,
                currentSample = currentSample,
                windowEnd = windowEnd,
                bpm = bpm,
                patternStartTickOffset = cycleStartTick,
                trackIndex = clip.trackIndex,
                transpose = clip.transpose,
                padIndex = clip.padIndex,
                minStartTick = clip.startTick,
                maxEndTick = minOf(cycleEndTick, clipEndTick),
            )
            cycleStartTick = cycleEndTick
        }
    }

    internal fun schedulePatternNotes(
        pattern: Pattern,
        currentSample: Long,
        windowEnd: Long,
        bpm: Float,
        patternStartTickOffset: Long = 0L,
        trackIndex: Int = pattern.trackIndex,
        transpose: Int = 0,
        padIndex: Int = -1,
        minStartTick: Long = Long.MIN_VALUE,
        maxEndTick: Long = Long.MAX_VALUE,
    ) {
        val track = trackIndex.coerceIn(0, 15)

        for ((noteIndex, note) in pattern.notes.withIndex()) {
            val noteStartTick = patternStartTickOffset + note.startTick
            if (noteStartTick < minStartTick) continue
            val rawEndTick = noteStartTick + note.durationTicks
            val noteEndTick = minOf(rawEndTick, maxEndTick)
            if (noteEndTick <= noteStartTick) continue

            // Move an entire note by the same amount so swing changes onset
            // without shortening or lengthening its gate.
            val swingOffsetTicks = swingOffsetTicks(noteStartTick)
            val noteStartSample = tickToSample(noteStartTick + swingOffsetTicks, bpm)
            val noteEndSample = tickToSample(noteEndTick + swingOffsetTicks, bpm)

            if (noteEndSample < currentSample || noteStartSample > windowEnd) continue

            // Resolve effective padIndex: note-level > clip-level > legacy note % 16.
            // Pass the absolute noteStartSample/noteEndSample as targetSample so
            // the C++ EventQueue fires sample-accurately when the playhead
            // reaches the event, instead of firing immediately on the next
            // audio buffer (which would cluster every note in the lookahead
            // window onto a single buffer and fire them early).
            val effectivePadIndex =
                if (note.padIndex >= 0) note.padIndex
                else if (padIndex >= 0) padIndex
                else note.note % 16
            if (effectivePadIndex >= 0) {
                // De-duplicate: a pad trigger whose start sample was already
                // pushed in a previous scheduler tick must not fire again.
                val sourceId = "${pattern.id}:$patternStartTickOffset:$noteIndex"
                val padKey =
                    ScheduledEventKey.Pad(noteStartSample, track, effectivePadIndex, sourceId)
                if (padKey in scheduledEventKeys) continue
                scheduledEventKeys.add(padKey)
                val triggerId = timelineTriggerId(sourceId, noteStartSample)
                schedulePadTrigger(track, effectivePadIndex, note.velocity, noteStartSample, triggerId)
                val releaseKey = ScheduledEventKey.PadRelease(noteEndSample, track, effectivePadIndex, sourceId)
                if (scheduledEventKeys.add(releaseKey)) {
                    schedulePadRelease(track, effectivePadIndex, noteEndSample, triggerId)
                }
            } else {
                val finalNote = (note.note + transpose).coerceIn(0, 127)
                val onKey =
                    ScheduledEventKey.NoteOn(noteStartSample, track, finalNote)
                val offKey =
                    ScheduledEventKey.NoteOff(noteEndSample, track, finalNote)
                // De-duplicate the note-on/off pair across overlapping ticks.
                if (onKey in scheduledEventKeys || offKey in scheduledEventKeys) continue
                scheduledEventKeys.add(onKey)
                scheduledEventKeys.add(offKey)
                scheduleNoteOn(track, finalNote, note.velocity, noteStartSample)
                scheduleNoteOff(track, finalNote, noteEndSample)
            }
        }
    }

    private fun scheduleAudioClip(
        clip: AudioClip,
        currentSample: Long,
        windowEnd: Long,
        bpm: Float,
    ) {
        if (clip.id in startedClips) return
        val clipStartSample = tickToSample(clip.startTick, bpm)
        if (clipStartSample in currentSample..windowEnd) {
            val offsetInBuffer = (clipStartSample - currentSample).toInt()
            scheduler.startAudioClip(clip.id, clip.trackIndex, offsetInBuffer)
            startedClips.add(clip.id)
        }
    }

    internal fun checkLoopWrap(
        currentSample: Long,
        windowEnd: Long,
        bpm: Float,
        timeSignature: TimeSignature,
    ) {
        if (!arrangement.loopEnabled) return
        val loopStartSample = tickToSample(arrangement.loopStartTick, bpm)
        val loopEndSample = tickToSample(arrangement.loopEndTick, bpm)
        if (loopEndSample <= loopStartSample) return

        if (currentSample >= loopEndSample) {
            // Wrap back to loop start. Clear stale events and re-seek.
            clearScheduledEventKeys()
            stopAllHeldNotes()
            startedClips.clear()
            scheduler.resetTransport(loopStartSample, true, transportState.recording)
            transportState =
                transportState.copy(
                    position = TransportPosition.fromTicks(arrangement.loopStartTick, timeSignature),
                )
        } else if (windowEnd >= loopEndSample) {
            // Schedule a wrap on the next block.
            // We rely on the next scheduler tick to actually perform the seek.
        }
    }

    /** Load automation clips for scheduling. Replaces any previously loaded clips. */
    fun loadAutomation(clips: List<AutomationClip>) {
        automationClips = clips.toMutableList()
    }

    /**
     * Schedule all automation events whose tick position falls within the
     * current lookahead window. Each point is converted to a [tickToSample]
     * sample offset and pushed to the C++ EventQueue.
     */
    fun scheduleAutomationEvents(
        currentSample: Long,
        windowEnd: Long,
        bpm: Float,
    ) {
        if (arrangement.automation.isNotEmpty()) {
            for (point in arrangement.automation) {
                val (trackIndex, paramIndex) = automationTarget(point.paramId) ?: continue
                val targetSample = tickToSample(point.tick, bpm)
                if (targetSample >= currentSample && targetSample < windowEnd) {
                    scheduler.scheduleAutomation(trackIndex, paramIndex, point.value, targetSample)
                }
            }
        }
        for (clip in automationClips) {
            for (point in clip.points) {
                val targetSample = tickToSample(point.position, bpm)
                if (targetSample >= currentSample && targetSample < windowEnd) {
                    scheduler.scheduleAutomation(clip.trackIndex, clip.paramIndex, point.value, targetSample)
                }
            }
        }
    }

    /** Resolve the canonical timeline automation path to a native parameter. */
    private fun automationTarget(paramId: String): Pair<Int, Int>? {
        val match = Regex("^track\\.(\\d+)\\.synth\\.(.+)$").matchEntire(paramId) ?: return null
        val track = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..15 } ?: return null
        val nativeParam =
            when (match.groupValues[2]) {
                "filter.cutoff" -> ParamIds.FILTER_CUTOFF
                "amp.level", "master.volume" -> ParamIds.MASTER_VOLUME
                "lfo1.rate" -> ParamIds.LFO1_RATE
                else -> return null
            }
        return track to nativeParam
    }

    internal fun scheduleNoteOn(
        track: Int,
        note: Int,
        velocity: Float,
        targetSample: Long = -1L,
    ) {
        scheduler.scheduleNoteOn(track, note, velocity, targetSample)
        heldNotes.getOrPut(track) { mutableSetOf() }.add(note)
    }

    internal fun scheduleNoteOff(
        track: Int,
        note: Int,
        targetSample: Long = -1L,
    ) {
        scheduler.scheduleNoteOff(track, note, targetSample)
        heldNotes[track]?.remove(note)
    }

    internal fun schedulePadTrigger(
        track: Int,
        padIndex: Int,
        velocity: Float,
        targetSample: Long = -1L,
        triggerId: Long = 0L,
    ) {
        scheduler.schedulePadTrigger(track, padIndex, velocity, targetSample, triggerId)
    }

    internal fun schedulePadRelease(
        track: Int,
        padIndex: Int,
        targetSample: Long = -1L,
        triggerId: Long = 0L,
    ) {
        scheduler.schedulePadRelease(track, padIndex, targetSample, triggerId)
    }

    private fun timelineTriggerId(sourceId: String, startSample: Long): Long {
        var hash = -0x340d631b7bdddcdbL // FNV-1a offset basis
        sourceId.forEach { char ->
            hash = (hash xor char.code.toLong()) * 0x100000001b3L
        }
        return (hash xor startSample) and Long.MAX_VALUE
    }

    internal fun stopAllNotesOnTrack(track: Int) {
        heldNotes[track]?.forEach { scheduler.scheduleNoteOff(track, it) }
        heldNotes[track]?.clear()
    }

    internal fun stopAllHeldNotes() {
        heldNotes.forEach { (track, notes) ->
            notes.forEach { scheduler.scheduleNoteOff(track, it) }
        }
        heldNotes.clear()
    }

    // ---- Time conversion helpers ----

    fun tickToSample(
        tick: Long,
        bpm: Float = transportState.tempoBpm,
    ): Long = (tick * samplesPerBeat(bpm) / PPQ).roundToLong()

    internal fun tickToSampleDelta(
        ticks: Long,
        bpm: Float,
    ): Long = (ticks * samplesPerBeat(bpm) / PPQ).toLong()

    fun sampleToTick(
        sample: Long,
        bpm: Float,
    ): Long = (sample * PPQ / samplesPerBeat(bpm)).roundToLong()

    private fun samplesPerBeat(bpm: Float): Double {
        require(bpm > 0f) { "BPM must be positive" }
        return (60.0 / bpm) * sampleRate
    }

    private fun swingOffsetTicks(tick: Long): Long {
        if (transportState.swing == 0f || tick % TICKS_PER_STEP != 0L) return 0L
        val sixteenthIndex = tick / TICKS_PER_STEP
        if (sixteenthIndex % 2L == 0L) return 0L
        return (TICKS_PER_STEP * 0.5f * transportState.swing).roundToLong()
    }
}
