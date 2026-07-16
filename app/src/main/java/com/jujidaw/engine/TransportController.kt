package com.jujidaw.engine

import com.jujidaw.model.Arrangement
import com.jujidaw.model.AudioClip
import com.jujidaw.model.Clip
import com.jujidaw.model.NoteEvent
import com.jujidaw.model.PPQ
import com.jujidaw.model.Pattern
import com.jujidaw.model.PatternClip
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
    private val sampleRate: Int = 48000,
    private val lookaheadMs: Long = 100,
    private val schedulingIntervalMs: Long = 50,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1)),
    private val scheduler: SynthEngineScheduler = NativeSynthEngineScheduler(),
) {
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
        scheduler.clearScheduledEvents()
        clearScheduledEventKeys()
        stopAllHeldNotes()
        startedClips.clear()
        scheduler.setTransport(false, false, transportState.tempoBpm)
        scheduler.setSequencerEnabled(true)
    }

    /**
     * Seek to an absolute musical position. Clears pending events and held
     * notes.
     */
    fun seek(position: TransportPosition) {
        transportState = transportState.copy(position = position)
        val sample = tickToSample(position.toTicks(transportState.timeSignature))
        scheduler.clearScheduledEvents()
        clearScheduledEventKeys()
        stopAllHeldNotes()
        startedClips.clear()
        scheduler.setPlayheadSample(sample)
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
        arrangement = newArrangement
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

    private fun startScheduler() {
        schedulerJob?.cancel()
        schedulerJob =
            coroutineScope.launch {
                while (isActive && transportState.playing) {
                    val currentSample = scheduler.getPlayheadSample()
                    val tick = sampleToTick(currentSample, transportState.tempoBpm)
                    currentStep = (tick / TICKS_PER_STEP).toInt()
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
        // Clear per-tick de-duplication keys so that notes (especially pad
        // triggers) can re-fire on every scheduler tick. The original intent
        // was to prevent double-firing within a single lookahead overlap, but
        // persisting keys across ticks blocked legitimate re-triggers when the
        // arrangement loops or a PatternClip should re-enter the window.
        clearScheduledEventKeys()

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
        schedulePatternNotes(pattern, currentSample, windowEnd, bpm, 0L)
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
                is AudioClip -> scheduleAudioClip(clip, currentSample, windowEnd, bpm)
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

        val fullReps = (clip.durationTicks / pattern.lengthTicks).toInt()
        val remainderTicks = (clip.durationTicks % pattern.lengthTicks)

        // schedulePatternNotes treats patternStartTickOffset as an ABSOLUTE
        // song tick (it converts note ticks to samples via tickToSample),
        // so the clip's start tick must be folded in here. Without
        // clip.startTick, notes for any clip placed past tick 0 resolve to
        // samples near 0 and get dropped by the lookahead window check
        // (noteEndSample < currentSample), producing no sound.
        val clipStartTick = clip.startTick
        for (rep in 0 until fullReps) {
            val repOffsetTicks = rep * pattern.lengthTicks
            val repStartSample = clipStartSample + tickToSampleDelta(repOffsetTicks, bpm)
            if (repStartSample > windowEnd) break

            val repEndSample = repStartSample + tickToSampleDelta(pattern.lengthTicks, bpm)
            if (repEndSample <= currentSample) continue

            schedulePatternNotes(
                pattern = pattern,
                currentSample = currentSample,
                windowEnd = windowEnd,
                bpm = bpm,
                patternStartTickOffset = clipStartTick + repOffsetTicks,
                trackIndex = clip.trackIndex,
                transpose = clip.transpose,
                padIndex = clip.padIndex,
                maxEndTick = clipStartTick + repOffsetTicks + pattern.lengthTicks,
            )
        }

        if (remainderTicks > 0L) {
            val repOffsetTicks = fullReps * pattern.lengthTicks
            val repStartSample = clipStartSample + tickToSampleDelta(repOffsetTicks, bpm)
            if (repStartSample <= windowEnd && repStartSample < clipEndSample) {
                schedulePatternNotes(
                    pattern = pattern,
                    currentSample = currentSample,
                    windowEnd = windowEnd,
                    bpm = bpm,
                    patternStartTickOffset = clipStartTick + repOffsetTicks,
                    trackIndex = clip.trackIndex,
                    transpose = clip.transpose,
                    padIndex = clip.padIndex,
                    maxEndTick = clipStartTick + repOffsetTicks + remainderTicks,
                )
            }
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
        maxEndTick: Long = Long.MAX_VALUE,
    ) {
        val track = trackIndex.coerceIn(0, 15)

        for (note in pattern.notes) {
            val noteStartTick = patternStartTickOffset + note.startTick
            val rawEndTick = noteStartTick + note.durationTicks
            val noteEndTick = minOf(rawEndTick, maxEndTick)
            if (noteEndTick <= noteStartTick) continue

            val noteStartSample = tickToSample(noteStartTick, bpm)
            val noteEndSample = tickToSample(noteEndTick, bpm)

            if (noteEndSample < currentSample || noteStartSample > windowEnd) continue

            // Resolve effective padIndex: note-level > clip-level > legacy noteOn.
            // Pass the absolute noteStartSample/noteEndSample as targetSample so
            // the C++ EventQueue fires sample-accurately when the playhead
            // reaches the event, instead of firing immediately on the next
            // audio buffer (which would cluster every note in the lookahead
            // window onto a single buffer and fire them early).
            val effectivePadIndex = if (note.padIndex >= 0) note.padIndex else padIndex
            if (effectivePadIndex >= 0) {
                // De-duplicate: a pad trigger whose start sample was already
                // pushed in a previous scheduler tick must not fire again.
                val padKey =
                    ScheduledEventKey.Pad(noteStartSample, track, effectivePadIndex)
                if (padKey in scheduledEventKeys) continue
                scheduledEventKeys.add(padKey)
                schedulePadTrigger(track, effectivePadIndex, note.velocity, noteStartSample)
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
            scheduler.clearScheduledEvents()
            clearScheduledEventKeys()
            stopAllHeldNotes()
            startedClips.clear()
            scheduler.setPlayheadSample(loopStartSample)
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
        for (clip in automationClips) {
            for (point in clip.points) {
                val targetSample = tickToSample(point.position, bpm)
                if (targetSample >= currentSample && targetSample < windowEnd) {
                    scheduler.scheduleAutomation(clip.trackIndex, clip.paramIndex, point.value, targetSample)
                }
            }
        }
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
    ) {
        scheduler.schedulePadTrigger(track, padIndex, velocity, targetSample)
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

    internal fun tickToSample(
        tick: Long,
        bpm: Float = transportState.tempoBpm,
    ): Long = (tick * samplesPerBeat(bpm) / PPQ).roundToLong()

    internal fun tickToSampleDelta(
        ticks: Long,
        bpm: Float,
    ): Long = (ticks * samplesPerBeat(bpm) / PPQ).toLong()

    internal fun sampleToTick(
        sample: Long,
        bpm: Float,
    ): Long = (sample * PPQ / samplesPerBeat(bpm)).roundToLong()

    private fun samplesPerBeat(bpm: Float): Double {
        require(bpm > 0f) { "BPM must be positive" }
        return (60.0 / bpm) * sampleRate
    }
}
