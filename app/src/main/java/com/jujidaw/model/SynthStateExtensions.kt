package com.jujidaw.model

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

/** Default SynthState used for new tracks (based on the 'Bright Lead' factory preset). */
fun defaultTrackSynthState(): SynthState = SynthState(
    osc1Waveform = 0, osc2Waveform = 1, oscMix = 0.7f,
    filterCutoff = 0.95f,
    ampAttack = 0.005f, ampDecay = 0.2f, ampSustain = 0.6f, ampRelease = 0.1f,
    reverbMix = 0.1f
)
