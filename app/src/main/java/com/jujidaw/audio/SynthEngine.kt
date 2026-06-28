package com.jujidaw.audio

/**
 * JNI bridge to the native C++ audio engine.
 * All native methods are thread-safe and can be called from any thread.
 */
object SynthEngine {
    var isLoaded = false
        private set

    init {
        isLoaded = try {
            System.loadLibrary("jujisynth")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    // Lifecycle
    external fun nativeStart(): Boolean
    external fun nativeStop(): Boolean
    external fun nativeIsRunning(): Boolean
    external fun nativePanic()
    external fun nativeResetEffects()

    // Engine info / error reporting (populated by nativeStart)
    external fun nativeGetSampleRate(): Int
    external fun nativeGetFramesPerBurst(): Int
    external fun nativeGetLastError(): String?

    // Oscilloscope
    external fun nativeGetWaveform(buffer: FloatArray)

    // Bulk preset apply
    external fun nativeApplySynthState(values: FloatArray)

    // Note events
    external fun nativeNoteOn(note: Int, velocity: Int)
    external fun nativeNoteOff(note: Int)

    // Parameter IDs (must match JniBridge.cpp)
    // Oscillators: 0-8
    // Filter: 10-13
    // Amp Envelope: 20-23
    // Filter Envelope: 25-28
    // LFO1: 30-32, LFO2: 35-37
    // Effects: 40-47
    // Master: 50-52
    // Sequencer: 60-62

    external fun nativeSetParam(paramId: Int, value: Float)
    external fun nativeGetParam(paramId: Int): Float

    // Modulation matrix
    external fun nativeSetModulationRoute(index: Int, source: Int, destination: Int, amount: Float, active: Boolean)

    // Sequencer
    external fun nativeSetSequencerSteps(notes: IntArray, velocities: IntArray, gates: FloatArray, automation: FloatArray)
    external fun nativeGetSequencerStep(): Int
    external fun nativeGetSequencerLooping(): Boolean

    // Sampler
    external fun nativeLoadSampleToPad(path: String, padIndex: Int): Boolean
    external fun nativeSetPadParam(padIndex: Int, paramId: Int, value: Float)
    external fun nativeSetSamplerBank(bank: Int)
    external fun nativeTriggerPad(padIndex: Int, velocity: Int)
    external fun nativeChopSample(sourcePad: Int, startPad: Int, numSlices: Int)
    external fun nativeTimeStretchPad(padIndex: Int, tempoChangePercent: Double,
                                      pitchSemiTones: Double, rateChangePercent: Double): Boolean
    external fun nativeTimeStretchPadAsync(padIndex: Int, tempoChangePercent: Double,
                                            pitchSemiTones: Double, rateChangePercent: Double): Boolean
    external fun nativeIsTimeStretching(): Boolean
    external fun nativeSetTimeStretchListener(listener: TimeStretchListener?)
    external fun nativeWritePadToWav(padIndex: Int, path: String): Boolean
    external fun nativeReleasePad(padIndex: Int)

    // Transport and scheduled events
    external fun nativeScheduleNoteOn(trackIndex: Int, note: Int, velocity: Float, targetSample: Long): Boolean
    external fun nativeScheduleNoteOff(trackIndex: Int, note: Int, targetSample: Long): Boolean
    external fun nativeSchedulePadTrigger(trackIndex: Int, padIndex: Int, velocity: Float, targetSample: Long): Boolean
    external fun nativeClearScheduledEvents()
    external fun nativeSetTransport(playing: Boolean, recording: Boolean, tempoBpm: Float)
    external fun nativeGetPlayheadSample(): Long
    external fun nativeSetPlayheadSample(sample: Long)
    external fun nativeSetLoop(enabled: Boolean, startSample: Long, endSample: Long)
    external fun nativeSetSequencerEnabled(enabled: Boolean)

    // Audio clip timeline playback
    external fun nativeLoadAudioClip(clipId: String, path: String): Boolean
    external fun nativeUnloadAudioClip(clipId: String)
    external fun nativeStartAudioClip(clipId: String, trackIndex: Int, startOffsetInBuffer: Int): Boolean
    external fun nativeStopAudioClip(trackIndex: Int)
    external fun nativeSetPunchRange(enabled: Boolean, inSample: Long, outSample: Long)

    // Recording
    external fun nativeStartRecording(sampleRate: Int): Boolean
    external fun nativeStopRecording()
    external fun nativeIsRecording(): Boolean
    external fun nativeAssignRecordingToPad(padIndex: Int): Boolean

    // Convenience wrappers
    fun start() = nativeStart()
    fun stop() = nativeStop()
    fun isRunning() = nativeIsRunning()
    fun panic() = nativePanic()
    fun resetEffects() = nativeResetEffects()

    fun getNativeSampleRate(): Int = nativeGetSampleRate()
    fun getNativeFramesPerBurst(): Int = nativeGetFramesPerBurst()
    fun getNativeLastError(): String? = nativeGetLastError()
    fun getWaveform(buffer: FloatArray) = nativeGetWaveform(buffer)
    fun applySynthState(values: FloatArray) = nativeApplySynthState(values)

    fun noteOn(note: Int, velocity: Int = 100) = nativeNoteOn(note, velocity)
    fun noteOff(note: Int) = nativeNoteOff(note)

    fun setParam(id: Int, value: Float) = nativeSetParam(id, value)
    fun getParam(id: Int) = nativeGetParam(id)

    fun setModulationRoute(index: Int, source: Int, destination: Int, amount: Float, active: Boolean) =
        nativeSetModulationRoute(index, source, destination, amount, active)

    fun setSequencerSteps(notes: IntArray, velocities: IntArray, gates: FloatArray, automation: FloatArray) =
        nativeSetSequencerSteps(notes, velocities, gates, automation)
    fun getSequencerStep() = nativeGetSequencerStep()
    fun getSequencerLooping() = nativeGetSequencerLooping()

    fun loadSampleToPad(path: String, padIndex: Int) = nativeLoadSampleToPad(path, padIndex)
    fun setPadParam(padIndex: Int, paramId: Int, value: Float) = nativeSetPadParam(padIndex, paramId, value)
    fun setSamplerBank(bank: Int) = nativeSetSamplerBank(bank)
    fun triggerPad(padIndex: Int, velocity: Int = 100) = nativeTriggerPad(padIndex, velocity)
    fun chopSample(sourcePad: Int, startPad: Int, numSlices: Int) = nativeChopSample(sourcePad, startPad, numSlices)
    fun timeStretchPad(padIndex: Int, tempoChangePercent: Double,
                       pitchSemiTones: Double, rateChangePercent: Double) =
        nativeTimeStretchPad(padIndex, tempoChangePercent, pitchSemiTones, rateChangePercent)
    fun timeStretchPadAsync(padIndex: Int, tempoChangePercent: Double,
                            pitchSemiTones: Double, rateChangePercent: Double) =
        nativeTimeStretchPadAsync(padIndex, tempoChangePercent, pitchSemiTones, rateChangePercent)
    fun isTimeStretching() = nativeIsTimeStretching()
    fun setTimeStretchListener(listener: TimeStretchListener?) =
        nativeSetTimeStretchListener(listener)
    fun writePadToWav(padIndex: Int, path: String) = nativeWritePadToWav(padIndex, path)
    fun releasePad(padIndex: Int) = nativeReleasePad(padIndex)

    // Transport and scheduled events
    fun scheduleNoteOn(trackIndex: Int, note: Int, velocity: Float, targetSample: Long = -1L) =
        nativeScheduleNoteOn(trackIndex, note, velocity, targetSample)
    fun scheduleNoteOff(trackIndex: Int, note: Int, targetSample: Long = -1L) =
        nativeScheduleNoteOff(trackIndex, note, targetSample)
    fun schedulePadTrigger(trackIndex: Int, padIndex: Int, velocity: Float, targetSample: Long = -1L) =
        nativeSchedulePadTrigger(trackIndex, padIndex, velocity, targetSample)
    fun clearScheduledEvents() = nativeClearScheduledEvents()
    fun setTransport(playing: Boolean, recording: Boolean, tempoBpm: Float) =
        nativeSetTransport(playing, recording, tempoBpm)
    fun getPlayheadSample(): Long = nativeGetPlayheadSample()
    fun setPlayheadSample(sample: Long) = nativeSetPlayheadSample(sample)
    fun setLoop(enabled: Boolean, startSample: Long, endSample: Long) =
        nativeSetLoop(enabled, startSample, endSample)
    fun setSequencerEnabled(enabled: Boolean) = nativeSetSequencerEnabled(enabled)

    // Audio clip timeline playback
    fun loadAudioClip(clipId: String, path: String) = nativeLoadAudioClip(clipId, path)
    fun unloadAudioClip(clipId: String) = nativeUnloadAudioClip(clipId)
    fun startAudioClip(clipId: String, trackIndex: Int, startOffsetInBuffer: Int) =
        nativeStartAudioClip(clipId, trackIndex, startOffsetInBuffer)
    fun stopAudioClip(trackIndex: Int) = nativeStopAudioClip(trackIndex)
    fun setPunchRange(enabled: Boolean, inSample: Long, outSample: Long) =
        nativeSetPunchRange(enabled, inSample, outSample)

    fun startRecording(sampleRate: Int = 48000) = nativeStartRecording(sampleRate)
    fun stopRecording() = nativeStopRecording()
    fun isRecording() = nativeIsRecording()
    fun assignRecordingToPad(padIndex: Int) = nativeAssignRecordingToPad(padIndex)

    // ========== Mixer control ==========

    /** Effect types matching C++ EffectType enum values. */
    enum class EffectType(val value: Int) {
        None(0),
        Reverb(1),
        Delay(2),
        Distortion(3),
        Chorus(4),
        Filter(5),
        Bitcrusher(6),
        Compressor(7),
        Eq(8);
    }

    external fun nativeSetChannelFader(trackIndex: Int, db: Float)
    external fun nativeSetChannelPan(trackIndex: Int, pan: Float)
    external fun nativeSetChannelMute(trackIndex: Int, mute: Boolean)
    external fun nativeSetChannelSolo(trackIndex: Int, solo: Boolean)
    external fun nativeSetChannelArm(trackIndex: Int, arm: Boolean)
    external fun nativeSetSendLevel(trackIndex: Int, bus: Int, level: Float)
    external fun nativeSetBusFader(bus: Int, db: Float)
    external fun nativeSetMasterFader(db: Float)
    external fun nativeAddInsertEffect(trackIndex: Int, slot: Int, type: Int)
    external fun nativeRemoveInsertEffect(trackIndex: Int, slot: Int)
    external fun nativeSetInsertBypass(trackIndex: Int, slot: Int, bypass: Boolean)
    external fun nativeSetInsertParam(trackIndex: Int, slot: Int, paramId: Int, value: Float)
    external fun nativeGetChannelLevel(trackIndex: Int): Float
    external fun nativeGetChannelFaderDb(trackIndex: Int): Float
    external fun nativeGetChannelPan(trackIndex: Int): Float
    external fun nativeIsChannelMute(trackIndex: Int): Boolean
    external fun nativeIsChannelSolo(trackIndex: Int): Boolean
    external fun nativeIsChannelArm(trackIndex: Int): Boolean
    external fun nativeGetSendLevel(trackIndex: Int, bus: Int): Float
    external fun nativeGetBusFaderDb(bus: Int): Float
    external fun nativeGetMasterFaderDb(): Float
    external fun nativeGetMasterLevel(): Float
    external fun nativeReorderChannelInserts(trackIndex: Int, fromSlot: Int, toSlot: Int)
    external fun nativeTriggerPerformFx(type: Int)

    // Offline render
    external fun nativeStartOfflineRender(path: String, durationSamples: Long): Boolean
    external fun nativeStartOfflineRenderForTrack(path: String, trackIndex: Int, durationSamples: Long): Boolean
    external fun nativeStopOfflineRender()

    // Recording to WAV
    external fun nativeWriteRecordingToWav(path: String): Boolean

    fun setChannelFader(trackIndex: Int, db: Float) = nativeSetChannelFader(trackIndex, db)
    fun setChannelPan(trackIndex: Int, pan: Float) = nativeSetChannelPan(trackIndex, pan)
    fun setChannelMute(trackIndex: Int, mute: Boolean) = nativeSetChannelMute(trackIndex, mute)
    fun setChannelSolo(trackIndex: Int, solo: Boolean) = nativeSetChannelSolo(trackIndex, solo)
    fun setChannelArm(trackIndex: Int, arm: Boolean) = nativeSetChannelArm(trackIndex, arm)
    fun setSendLevel(trackIndex: Int, bus: Int, level: Float) = nativeSetSendLevel(trackIndex, bus, level)
    fun setBusFader(bus: Int, db: Float) = nativeSetBusFader(bus, db)
    fun setMasterFader(db: Float) = nativeSetMasterFader(db)
    fun addInsertEffect(trackIndex: Int, slot: Int, type: EffectType) =
        nativeAddInsertEffect(trackIndex, slot, type.value)
    fun removeInsertEffect(trackIndex: Int, slot: Int) =
        nativeRemoveInsertEffect(trackIndex, slot)
    fun setInsertBypass(trackIndex: Int, slot: Int, bypass: Boolean) =
        nativeSetInsertBypass(trackIndex, slot, bypass)
    fun setInsertParam(trackIndex: Int, slot: Int, paramId: Int, value: Float) =
        nativeSetInsertParam(trackIndex, slot, paramId, value)
    fun getChannelLevel(trackIndex: Int): Float = nativeGetChannelLevel(trackIndex)
    fun getChannelFaderDb(trackIndex: Int): Float = nativeGetChannelFaderDb(trackIndex)
    fun getChannelPan(trackIndex: Int): Float = nativeGetChannelPan(trackIndex)
    fun isChannelMute(trackIndex: Int): Boolean = nativeIsChannelMute(trackIndex)
    fun isChannelSolo(trackIndex: Int): Boolean = nativeIsChannelSolo(trackIndex)
    fun isChannelArm(trackIndex: Int): Boolean = nativeIsChannelArm(trackIndex)
    fun getSendLevel(trackIndex: Int, bus: Int): Float = nativeGetSendLevel(trackIndex, bus)
    fun getBusFaderDb(bus: Int): Float = nativeGetBusFaderDb(bus)
    fun getMasterFaderDb(): Float = nativeGetMasterFaderDb()
    fun getMasterLevel(): Float = nativeGetMasterLevel()
    fun reorderChannelInserts(trackIndex: Int, fromSlot: Int, toSlot: Int) =
        nativeReorderChannelInserts(trackIndex, fromSlot, toSlot)

    /** Perform FX type constants matching C++ AudioEngine. */
    object PerformFxType {
        const val TAPE_STOP = 1
        const val BITCRUSH = 2
        const val FILTER_SWEEP = 3
    }

    fun triggerPerformFx(type: Int) = nativeTriggerPerformFx(type)

    fun startOfflineRender(path: String, durationSamples: Long): Boolean =
        nativeStartOfflineRender(path, durationSamples)
    fun startOfflineRenderForTrack(path: String, trackIndex: Int, durationSamples: Long): Boolean =
        nativeStartOfflineRenderForTrack(path, trackIndex, durationSamples)
    fun stopOfflineRender() = nativeStopOfflineRender()

    /** Write the current recording buffer to a WAV file. */
    fun writeRecordingToWav(path: String): Boolean = nativeWriteRecordingToWav(path)
}
