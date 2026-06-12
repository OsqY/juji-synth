package com.jujisynth.audio

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

    // Convenience wrappers
    fun start() = nativeStart()
    fun stop() = nativeStop()
    fun isRunning() = nativeIsRunning()
    fun panic() = nativePanic()
    fun resetEffects() = nativeResetEffects()
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
}
