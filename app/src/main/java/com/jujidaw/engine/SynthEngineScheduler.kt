package com.jujidaw.engine

import com.jujidaw.audio.SynthEngine

/**
 * Abstraction over the native C++ engine scheduling calls that
 * [TransportController] uses.  Production code uses [NativeSynthEngineScheduler]
 * which delegates directly to [SynthEngine] JNI methods.  Tests supply a fake
 * implementation that records calls and controls time.
 */
interface SynthEngineScheduler {
    fun scheduleNoteOn(trackIndex: Int, note: Int, velocity: Float): Boolean
    fun scheduleNoteOff(trackIndex: Int, note: Int): Boolean
    fun schedulePadTrigger(trackIndex: Int, padIndex: Int, velocity: Float): Boolean
    fun clearScheduledEvents()
    fun setTransport(playing: Boolean, recording: Boolean, tempoBpm: Float)
    fun getPlayheadSample(): Long
    fun setPlayheadSample(sample: Long)
    fun setLoop(enabled: Boolean, startSample: Long, endSample: Long)
    fun setPunchRange(enabled: Boolean, inSample: Long, outSample: Long)
    fun setSequencerEnabled(enabled: Boolean)
    fun startAudioClip(clipId: String, trackIndex: Int, startOffsetInBuffer: Int): Boolean
}

/**
 * Production implementation that delegates every call to [SynthEngine].
 * Safe to use only when the native library is loaded (Android device/emulator).
 */
class NativeSynthEngineScheduler : SynthEngineScheduler {
    override fun scheduleNoteOn(trackIndex: Int, note: Int, velocity: Float): Boolean =
        SynthEngine.scheduleNoteOn(trackIndex, note, velocity)

    override fun scheduleNoteOff(trackIndex: Int, note: Int): Boolean =
        SynthEngine.scheduleNoteOff(trackIndex, note)

    override fun schedulePadTrigger(trackIndex: Int, padIndex: Int, velocity: Float): Boolean =
        SynthEngine.schedulePadTrigger(trackIndex, padIndex, velocity)

    override fun clearScheduledEvents() = SynthEngine.clearScheduledEvents()
    override fun setTransport(playing: Boolean, recording: Boolean, tempoBpm: Float) =
        SynthEngine.setTransport(playing, recording, tempoBpm)
    override fun getPlayheadSample(): Long = SynthEngine.getPlayheadSample()
    override fun setPlayheadSample(sample: Long) = SynthEngine.setPlayheadSample(sample)
    override fun setLoop(enabled: Boolean, startSample: Long, endSample: Long) =
        SynthEngine.setLoop(enabled, startSample, endSample)
    override fun setPunchRange(enabled: Boolean, inSample: Long, outSample: Long) =
        SynthEngine.setPunchRange(enabled, inSample, outSample)
    override fun setSequencerEnabled(enabled: Boolean) =
        SynthEngine.setSequencerEnabled(enabled)

    override fun startAudioClip(clipId: String, trackIndex: Int, startOffsetInBuffer: Int): Boolean =
        SynthEngine.startAudioClip(clipId, trackIndex, startOffsetInBuffer)
}
