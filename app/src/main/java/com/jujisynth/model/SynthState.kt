package com.jujisynth.model

import kotlinx.serialization.Serializable

/**
 * UI-level representation of the complete synthesizer state.
 * Mapped to/from C++ SynthParams via JNI parameter IDs.
 */
@Serializable
data class SynthState(
    // Oscillators
    val osc1Level: Float = 1.0f,
    val osc2Level: Float = 1.0f,
    val osc1Waveform: Int = 0,       // 0=saw, 1=square, 2=triangle, 3=sine
    val osc2Waveform: Int = 0,
    val oscDetune: Float = 0.0f,     // -1.0 to 1.0
    val subOscLevel: Float = 0.0f,
    val noiseLevel: Float = 0.0f,
    val oscMix: Float = 0.5f,
    val oscSync: Boolean = false,

    // Filter
    val filterCutoff: Float = 0.8f,
    val filterResonance: Float = 0.0f,
    val filterMode: Int = 0,         // 0=LPF, 1=HPF, 2=BPF
    val filterEnvAmount: Float = 0.0f,

    // Amp Envelope
    val ampAttack: Float = 0.01f,
    val ampDecay: Float = 0.3f,
    val ampSustain: Float = 0.7f,
    val ampRelease: Float = 0.2f,

    // Filter Envelope
    val filterAttack: Float = 0.01f,
    val filterDecay: Float = 0.3f,
    val filterSustain: Float = 0.7f,
    val filterRelease: Float = 0.2f,

    // LFO1
    val lfo1Rate: Float = 0.3f,
    val lfo1Depth: Float = 0.0f,
    val lfo1Waveform: Int = 0,

    // LFO2
    val lfo2Rate: Float = 0.3f,
    val lfo2Depth: Float = 0.0f,
    val lfo2Waveform: Int = 0,

    // Effects
    val reverbMix: Float = 0.0f,
    val reverbDecay: Float = 0.5f,
    val delayMix: Float = 0.0f,
    val delayTime: Float = 0.3f,
    val delayFeedback: Float = 0.3f,
    val distortionDrive: Float = 0.0f,
    val distortionMix: Float = 0.0f,
    // Chorus
    val chorusRate: Float = 0.3f,
    val chorusDepth: Float = 0.0f,
    val chorusMix: Float = 0.0f,
    val effectsBypass: Boolean = false,

    // Master
    val masterVolume: Float = 0.8f,
    val pitchBend: Float = 0.0f,

    // Sequencer
    val sequencerSteps: List<SequencerStep> = List(16) { SequencerStep() },
    val sequencerTempo: Float = 120.0f,
    val sequencerPlaying: Boolean = false,
    val sequencerCurrentStep: Int = 0,
    val sequencerRecording: Boolean = false,
    val sequencerLooping: Boolean = true,

    // Piano roll pattern
    val pianoRollNotes: List<PianoRollNote> = emptyList(),
    val pianoRollLength: Int = 16,

    // Modulation routes (simplified)
    val modulationRoutes: List<ModulationRoute> = List(8) { ModulationRoute() }
)

/**
 * Note in the piano roll pattern editor.
 * Uses fractional step positions for sub-step precision.
 */
@Serializable
data class PianoRollNote(
    val note: Int = 60,          // MIDI note 0-127
    val startStep: Float = 0f,   // fractional step position
    val duration: Float = 1f,    // in steps (1.0 = one beat at 4/4)
    val velocity: Int = 100,     // 0-127
    val muted: Boolean = false
)

@Serializable
data class SequencerStep(
    val note: Int = -1,
    val velocity: Int = 100,
    val gate: Float = 0.8f,
    val automation: Float = 0.0f
)

@Serializable
data class ModulationRoute(
    val source: Int = 0,          // 0=LFO1, 1=LFO2, 2=ENV1, 3=ENV2, 4=Vel
    val destination: Int = 0,     // 0=pitch, 1=filter, 2=amp...
    val amount: Float = 0.0f,
    val active: Boolean = false
)

@Serializable
enum class Waveform(val id: Int, val displayName: String) {
    SAW(0, "Saw"),
    SQUARE(1, "Square"),
    TRIANGLE(2, "Triangle"),
    SINE(3, "Sine");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: SAW
    }
}

@Serializable
enum class FilterMode(val id: Int, val displayName: String) {
    LOW_PASS(0, "LPF"),
    HIGH_PASS(1, "HPF"),
    BAND_PASS(2, "BPF");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: LOW_PASS
    }
}

@Serializable
enum class LfoWaveform(val id: Int, val displayName: String) {
    SINE(0, "Sine"),
    SQUARE(1, "Square"),
    SAW(2, "Saw"),
    TRIANGLE(3, "Triangle"),
    RANDOM(4, "Random");

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id } ?: SINE
    }
}

@Serializable
enum class EffectType { REVERB, DELAY, DISTORTION }

/** Mapping of param IDs for JNI bridge */
object ParamIds {
    const val OSC1_LEVEL = 0
    const val OSC2_LEVEL = 1
    const val OSC1_WAVE = 2
    const val OSC2_WAVE = 3
    const val OSC_DETUNE = 4
    const val SUB_OSC_LEVEL = 5
    const val NOISE_LEVEL = 6
    const val OSC_MIX = 7
    const val OSC_SYNC = 8

    const val FILTER_CUTOFF = 10
    const val FILTER_RESONANCE = 11
    const val FILTER_MODE = 12
    const val FILTER_ENV_AMOUNT = 13

    const val AMP_ATTACK = 20
    const val AMP_DECAY = 21
    const val AMP_SUSTAIN = 22
    const val AMP_RELEASE = 23
    const val FILTER_ATTACK = 25
    const val FILTER_DECAY = 26
    const val FILTER_SUSTAIN = 27
    const val FILTER_RELEASE = 28

    const val LFO1_RATE = 30
    const val LFO1_DEPTH = 31
    const val LFO1_WAVE = 32
    const val LFO2_RATE = 35
    const val LFO2_DEPTH = 36
    const val LFO2_WAVE = 37

    const val REVERB_MIX = 40
    const val REVERB_DECAY = 41
    const val DELAY_MIX = 42
    const val DELAY_TIME = 43
    const val DELAY_FEEDBACK = 44
    const val DIST_DRIVE = 45
    const val DIST_MIX = 46
    const val EFFECTS_BYPASS = 47
    const val CHORUS_RATE = 55
    const val CHORUS_DEPTH = 56
    const val CHORUS_MIX = 57

    const val MASTER_VOLUME = 50
    const val PITCH_BEND = 51
    const val MOD_WHEEL = 52

    const val SEQ_TEMPO = 60
    const val SEQ_PLAYING = 61
    const val SEQ_LOOPING = 62
}
