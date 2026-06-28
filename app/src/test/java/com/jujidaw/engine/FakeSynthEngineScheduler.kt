package com.jujidaw.engine

/**
 * A fake [SynthEngineScheduler] used in unit tests.
 *
 * Records every call into public lists/fields for assertion, and allows
 * the test to control the playhead sample and other state.
 *
 * Property names avoid clashing with interface method names; use
 * [controlledPlayheadSample] to set the value returned by [getPlayheadSample].
 */
class FakeSynthEngineScheduler : SynthEngineScheduler {

    // ---- recorded call data ----

    data class NoteOnEvent(val trackIndex: Int, val note: Int, val velocity: Float)
    data class NoteOffEvent(val trackIndex: Int, val note: Int)
    data class PadTriggerEvent(val trackIndex: Int, val padIndex: Int, val velocity: Float)
    data class AutomationEvent(val trackIndex: Int, val paramIndex: Int, val value: Float, val targetSample: Long)
    data class AudioClipStartEvent(
        val clipId: String,
        val trackIndex: Int,
        val startOffsetInBuffer: Int
    )

    val noteOnEvents = mutableListOf<NoteOnEvent>()
    val noteOffEvents = mutableListOf<NoteOffEvent>()
    val padTriggers = mutableListOf<PadTriggerEvent>()
    val scheduledAutomation = mutableListOf<AutomationEvent>()
    val audioClipStarts = mutableListOf<AudioClipStartEvent>()

    var clearScheduledEventsCount = 0
        private set

    // ---- test-controlled state ----

    /** Set this to control what [getPlayheadSample] returns. */
    var controlledPlayheadSample: Long = 0L

    // ---- captured argument state ----

    var lastTransportPlaying: Boolean = false
    var lastTransportRecording: Boolean = false
    var lastTransportTempoBpm: Float = 0f

    var lastSetPlayheadSample: Long = 0L

    var lastLoopEnabled: Boolean = false
    var lastLoopStartSample: Long = 0L
    var lastLoopEndSample: Long = 0L

    var lastPunchEnabled: Boolean = false
    var lastPunchInSample: Long = 0L
    var lastPunchOutSample: Long = 0L

    /** Set this to control what the scheduler observes via [isSequencerEnabled] checks. */
    var controlledSequencerEnabled: Boolean = true

    /** Last value passed to [setSequencerEnabled]. */
    var lastSequencerEnabled: Boolean = true

    // ---- interface implementation ----

    override fun scheduleNoteOn(trackIndex: Int, note: Int, velocity: Float): Boolean {
        noteOnEvents.add(NoteOnEvent(trackIndex, note, velocity))
        return true
    }

    override fun scheduleNoteOff(trackIndex: Int, note: Int): Boolean {
        noteOffEvents.add(NoteOffEvent(trackIndex, note))
        return true
    }

    override fun schedulePadTrigger(trackIndex: Int, padIndex: Int, velocity: Float): Boolean {
        padTriggers.add(PadTriggerEvent(trackIndex, padIndex, velocity))
        return true
    }

    override fun scheduleAutomation(trackIndex: Int, paramIndex: Int, value: Float, targetSample: Long): Boolean {
        scheduledAutomation.add(AutomationEvent(trackIndex, paramIndex, value, targetSample))
        return true
    }

    override fun clearScheduledEvents() {
        clearScheduledEventsCount++
    }

    override fun setTransport(playing: Boolean, recording: Boolean, tempoBpm: Float) {
        lastTransportPlaying = playing
        lastTransportRecording = recording
        lastTransportTempoBpm = tempoBpm
    }

    override fun getPlayheadSample(): Long = controlledPlayheadSample

    override fun setPlayheadSample(sample: Long) {
        lastSetPlayheadSample = sample
        controlledPlayheadSample = sample
    }

    override fun setLoop(enabled: Boolean, startSample: Long, endSample: Long) {
        lastLoopEnabled = enabled
        lastLoopStartSample = startSample
        lastLoopEndSample = endSample
    }

    override fun setPunchRange(enabled: Boolean, inSample: Long, outSample: Long) {
        lastPunchEnabled = enabled
        lastPunchInSample = inSample
        lastPunchOutSample = outSample
    }

    override fun setSequencerEnabled(enabled: Boolean) {
        lastSequencerEnabled = enabled
        controlledSequencerEnabled = enabled
    }

    override fun startAudioClip(clipId: String, trackIndex: Int, startOffsetInBuffer: Int): Boolean {
        audioClipStarts.add(AudioClipStartEvent(clipId, trackIndex, startOffsetInBuffer))
        return true
    }

    // ---- test helpers ----

    /** Reset all recorded data and state to defaults. */
    fun reset() {
        noteOnEvents.clear()
        noteOffEvents.clear()
        padTriggers.clear()
        audioClipStarts.clear()
        clearScheduledEventsCount = 0
        controlledPlayheadSample = 0L
        lastTransportPlaying = false
        lastTransportRecording = false
        lastTransportTempoBpm = 0f
        lastSetPlayheadSample = 0L
        lastLoopEnabled = false
        lastLoopStartSample = 0L
        lastLoopEndSample = 0L
        lastPunchEnabled = false
        lastPunchInSample = 0L
        lastPunchOutSample = 0L
        controlledSequencerEnabled = true
        lastSequencerEnabled = true
    }
}
