package com.jujidaw.model

import kotlin.math.roundToInt

/**
 * Extension that serialises a [SynthState] into the 39-element float array
 * expected by [com.jujidaw.audio.SynthEngine.applySynthState].
 *
 * The order MUST match `SynthInstrument::setAllParamsFromArray` in C++:
 *  0-8  oscillators       (osc1 level, osc2 level, osc1 wf, osc2 wf, detune,
 *                           subOsc, noise, oscMix, sync)
 *  9-12 filter             (cutoff, resonance, mode, envAmount)
 * 13-20 envelopes          (amp A/D/S/R + filter A/D/S/R)
 * 21-26 LFOs               (lfo1 rate/depth/wf, lfo2 rate/depth/wf)
 * 27-34 effects            (reverb mix/decay, delay mix/time/fb,
 *                           dist drive/mix, bypass)
 * 35-37 chorus             (rate/depth/mix)
 * 38   master volume
 */
fun SynthState.toParamsArray(): FloatArray {
    return floatArrayOf(
        // Oscillators (9)
        osc1Level, osc2Level, osc1Waveform.toFloat(), osc2Waveform.toFloat(),
        oscDetune, subOscLevel, noiseLevel, oscMix,
        if (oscSync) 1f else 0f,
        // Filter (4)
        filterCutoff, filterResonance, filterMode.toFloat(), filterEnvAmount,
        // Amp Envelope (4)
        ampAttack, ampDecay, ampSustain, ampRelease,
        // Filter Envelope (4)
        filterAttack, filterDecay, filterSustain, filterRelease,
        // LFO1 (3) + LFO2 (3)
        lfo1Rate, lfo1Depth, lfo1Waveform.toFloat(),
        lfo2Rate, lfo2Depth, lfo2Waveform.toFloat(),
        // Effects: Reverb (2), Delay (3), Distortion (2), Bypass (1), Chorus (3)
        reverbMix, reverbDecay,
        delayMix, delayTime, delayFeedback,
        distortionDrive, distortionMix,
        if (effectsBypass) 1f else 0f,
        chorusRate, chorusDepth, chorusMix,
        // Master (1)
        masterVolume
    )
}

/** Return a copy with one native parameter reflected in the persisted state. */
fun SynthState.withParamValue(
    paramId: Int,
    value: Float,
): SynthState? = when (paramId) {
    ParamIds.OSC1_LEVEL -> copy(osc1Level = value)
    ParamIds.OSC2_LEVEL -> copy(osc2Level = value)
    ParamIds.OSC1_WAVE -> copy(osc1Waveform = value.roundToInt())
    ParamIds.OSC2_WAVE -> copy(osc2Waveform = value.roundToInt())
    ParamIds.OSC_DETUNE -> copy(oscDetune = value)
    ParamIds.SUB_OSC_LEVEL -> copy(subOscLevel = value)
    ParamIds.NOISE_LEVEL -> copy(noiseLevel = value)
    ParamIds.OSC_MIX -> copy(oscMix = value)
    ParamIds.OSC_SYNC -> copy(oscSync = value > 0.5f)
    ParamIds.FILTER_CUTOFF -> copy(filterCutoff = value)
    ParamIds.FILTER_RESONANCE -> copy(filterResonance = value)
    ParamIds.FILTER_MODE -> copy(filterMode = value.roundToInt())
    ParamIds.FILTER_ENV_AMOUNT -> copy(filterEnvAmount = value)
    ParamIds.AMP_ATTACK -> copy(ampAttack = value)
    ParamIds.AMP_DECAY -> copy(ampDecay = value)
    ParamIds.AMP_SUSTAIN -> copy(ampSustain = value)
    ParamIds.AMP_RELEASE -> copy(ampRelease = value)
    ParamIds.FILTER_ATTACK -> copy(filterAttack = value)
    ParamIds.FILTER_DECAY -> copy(filterDecay = value)
    ParamIds.FILTER_SUSTAIN -> copy(filterSustain = value)
    ParamIds.FILTER_RELEASE -> copy(filterRelease = value)
    ParamIds.LFO1_RATE -> copy(lfo1Rate = value)
    ParamIds.LFO1_DEPTH -> copy(lfo1Depth = value)
    ParamIds.LFO1_WAVE -> copy(lfo1Waveform = value.roundToInt())
    ParamIds.LFO2_RATE -> copy(lfo2Rate = value)
    ParamIds.LFO2_DEPTH -> copy(lfo2Depth = value)
    ParamIds.LFO2_WAVE -> copy(lfo2Waveform = value.roundToInt())
    ParamIds.REVERB_MIX -> copy(reverbMix = value)
    ParamIds.REVERB_DECAY -> copy(reverbDecay = value)
    ParamIds.DELAY_MIX -> copy(delayMix = value)
    ParamIds.DELAY_TIME -> copy(delayTime = value)
    ParamIds.DELAY_FEEDBACK -> copy(delayFeedback = value)
    ParamIds.DIST_DRIVE -> copy(distortionDrive = value)
    ParamIds.DIST_MIX -> copy(distortionMix = value)
    ParamIds.EFFECTS_BYPASS -> copy(effectsBypass = value > 0.5f)
    ParamIds.CHORUS_RATE -> copy(chorusRate = value)
    ParamIds.CHORUS_DEPTH -> copy(chorusDepth = value)
    ParamIds.CHORUS_MIX -> copy(chorusMix = value)
    ParamIds.MASTER_VOLUME -> copy(masterVolume = value)
    ParamIds.PITCH_BEND -> copy(pitchBend = value)
    else -> null
}

/** Default SynthState used for new tracks (based on the 'Bright Lead' factory preset). */
fun defaultTrackSynthState(): SynthState = SynthState(
    osc1Waveform = 0, osc2Waveform = 1, oscMix = 0.7f,
    filterCutoff = 0.95f,
    ampAttack = 0.005f, ampDecay = 0.2f, ampSustain = 0.6f, ampRelease = 0.1f,
    reverbMix = 0.1f
)
