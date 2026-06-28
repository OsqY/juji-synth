package com.jujidaw.data

import com.jujidaw.model.SynthState
import kotlinx.serialization.json.Json

private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

private fun se(
    name: String,
    category: String,
    description: String,
    state: SynthState
): PresetEntity {
    return PresetEntity(
        name = name,
        category = category,
        description = description,
        isFactory = true,
        parametersJson = json.encodeToString(SynthState.serializer(), state)
    )
}

suspend fun seedFactoryPresets(database: PresetDatabase) {
    val dao = database.presetDao()

    // ── Leads (10) ──────────────────────────────────────────────────────────
    val leads = listOf(
        se("Bright Lead", "Leads", "Piercing bright lead with open filter",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 1, oscMix = 0.7f,
                filterCutoff = 0.95f, filterResonance = 0.15f,
                ampAttack = 0.005f, ampDecay = 0.2f, ampSustain = 0.6f, ampRelease = 0.1f,
                reverbMix = 0.1f
            )),
        se("Saw Lead", "Leads", "Aggressive detuned saw wave lead",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 0, oscDetune = 0.3f, oscMix = 0.6f,
                filterCutoff = 0.85f, filterResonance = 0.1f,
                ampAttack = 0.01f, ampDecay = 0.25f, ampSustain = 0.7f, ampRelease = 0.15f
            )),
        se("Square Lead", "Leads", "Classic square wave lead with resonance",
            SynthState(
                osc1Waveform = 1, osc2Waveform = 1, oscDetune = 0.15f,
                filterCutoff = 0.7f, filterResonance = 0.3f,
                ampAttack = 0.01f, ampDecay = 0.3f, ampSustain = 0.6f, ampRelease = 0.2f
            )),
        se("Analog Brass", "Leads", "Classic analog-style brass patch",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 0, oscDetune = 0.1f, oscMix = 0.5f,
                filterCutoff = 0.6f, filterResonance = 0.25f, filterEnvAmount = 0.4f,
                ampAttack = 0.05f, ampDecay = 0.3f, ampSustain = 0.8f, ampRelease = 0.15f
            )),
        se("Resonant Pluck", "Leads", "Filtered pluck with high resonance peak",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 2, oscMix = 0.8f,
                filterCutoff = 0.5f, filterResonance = 0.7f, filterEnvAmount = 0.6f,
                ampAttack = 0.001f, ampDecay = 0.15f, ampSustain = 0.3f, ampRelease = 0.05f
            )),
        se("Acid Lead", "Leads", "Classic TB-303 style squelching lead",
            SynthState(
                osc1Waveform = 1, osc2Waveform = 0, oscMix = 0.9f,
                filterCutoff = 0.4f, filterResonance = 0.85f, filterEnvAmount = 0.7f,
                ampAttack = 0.001f, ampDecay = 0.4f, ampSustain = 0.5f, ampRelease = 0.1f,
                distortionDrive = 0.2f, distortionMix = 0.3f
            )),
        se("Filtered Lead", "Leads", "Mellow lead with low-pass filter",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 0, oscDetune = 0.2f,
                filterCutoff = 0.3f, filterResonance = 0.2f,
                ampAttack = 0.02f, ampDecay = 0.3f, ampSustain = 0.7f, ampRelease = 0.3f
            )),
        se("Sync Lead", "Leads", "Oscillator sync with detuned harmonics",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 1, oscSync = true, oscDetune = 0.4f,
                filterCutoff = 0.8f, filterResonance = 0.2f,
                ampAttack = 0.01f, ampDecay = 0.2f, ampSustain = 0.6f, ampRelease = 0.1f
            )),
        se("Soft Lead", "Leads", "Smooth triangle-based lead",
            SynthState(
                osc1Waveform = 2, osc2Waveform = 3, oscMix = 0.5f,
                filterCutoff = 0.7f,
                ampAttack = 0.03f, ampDecay = 0.3f, ampSustain = 0.7f, ampRelease = 0.3f,
                reverbMix = 0.2f
            )),
        se("Wide Lead", "Leads", "Wide detuned saws with stereo effect",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 0, oscDetune = 0.5f, oscMix = 0.6f,
                filterCutoff = 0.85f, filterResonance = 0.15f,
                ampAttack = 0.01f, ampDecay = 0.2f, ampSustain = 0.65f, ampRelease = 0.15f,
                reverbMix = 0.15f, delayMix = 0.1f
            ))
    )

    // ── Pads (10) ───────────────────────────────────────────────────────────
    val pads = listOf(
        se("Soft Pad", "Pads", "Warm evolving pad with slow attack",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 2, oscMix = 0.5f,
                filterCutoff = 0.6f, filterResonance = 0.1f,
                ampAttack = 0.5f, ampDecay = 0.5f, ampSustain = 0.8f, ampRelease = 0.5f,
                reverbMix = 0.3f, reverbDecay = 0.7f
            )),
        se("Warm Pad", "Pads", "Warm triangle-based pad",
            SynthState(
                osc1Waveform = 2, osc2Waveform = 2, oscDetune = 0.1f,
                filterCutoff = 0.55f, filterResonance = 0.1f,
                ampAttack = 0.4f, ampDecay = 0.4f, ampSustain = 0.9f, ampRelease = 0.6f,
                reverbMix = 0.25f
            )),
        se("Motion Pad", "Pads", "Pad with LFO-modulated filter sweep",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 2, oscDetune = 0.2f,
                filterCutoff = 0.5f, filterResonance = 0.3f,
                lfo1Rate = 0.2f, lfo1Depth = 0.4f, lfo1Waveform = 0,
                ampAttack = 0.3f, ampDecay = 0.4f, ampSustain = 0.8f, ampRelease = 0.5f,
                reverbMix = 0.3f
            )),
        se("Dreamscape", "Pads", "Ethereal layered ambient pad",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 2, oscDetune = 0.4f, oscMix = 0.5f,
                subOscLevel = 0.2f,
                filterCutoff = 0.7f, filterResonance = 0.15f,
                ampAttack = 0.6f, ampDecay = 0.5f, ampSustain = 0.7f, ampRelease = 0.8f,
                reverbMix = 0.5f, reverbDecay = 0.9f, delayMix = 0.2f, delayTime = 0.4f
            )),
        se("Atmospheric", "Pads", "Evolving noise-based texture",
            SynthState(
                osc1Waveform = 3, osc2Waveform = 3, oscMix = 0.3f,
                noiseLevel = 0.4f, subOscLevel = 0.1f,
                filterCutoff = 0.5f, filterResonance = 0.1f, filterEnvAmount = 0.2f,
                lfo1Rate = 0.1f, lfo1Depth = 0.3f, lfo1Waveform = 0,
                lfo2Rate = 0.05f, lfo2Depth = 0.2f, lfo2Waveform = 3,
                ampAttack = 1.0f, ampDecay = 0.5f, ampSustain = 0.8f, ampRelease = 1.0f,
                reverbMix = 0.6f, reverbDecay = 0.9f, delayMix = 0.3f
            )),
        se("Sweep Pad", "Pads", "Pad with envelope-controlled filter sweep",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 2, oscDetune = 0.15f,
                filterCutoff = 0.3f, filterResonance = 0.4f, filterEnvAmount = 0.5f,
                ampAttack = 0.5f, ampDecay = 0.4f, ampSustain = 0.8f, ampRelease = 0.5f,
                reverbMix = 0.3f
            )),
        se("Dark Pad", "Pads", "Deep dark pad with low filter cutoff",
            SynthState(
                osc1Waveform = 1, osc2Waveform = 1, oscDetune = 0.1f,
                filterCutoff = 0.25f, filterResonance = 0.2f,
                ampAttack = 0.3f, ampDecay = 0.4f, ampSustain = 0.9f, ampRelease = 0.6f,
                reverbMix = 0.2f, reverbDecay = 0.6f, masterVolume = 0.7f
            )),
        se("Chorus Pad", "Pads", "Heavily detuned chorus-like pad",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 0, oscDetune = 0.6f, oscMix = 0.5f,
                filterCutoff = 0.65f, filterResonance = 0.1f,
                lfo1Rate = 0.3f, lfo1Depth = 0.15f, lfo1Waveform = 0,
                ampAttack = 0.3f, ampDecay = 0.4f, ampSustain = 0.8f, ampRelease = 0.4f,
                reverbMix = 0.2f, delayMix = 0.15f, delayTime = 0.25f, delayFeedback = 0.2f
            )),
        se("Shimmer Pad", "Pads", "Bright shimmering pad with reverb",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 3, oscDetune = 0.2f,
                filterCutoff = 0.85f, filterResonance = 0.1f,
                ampAttack = 0.4f, ampDecay = 0.4f, ampSustain = 0.7f, ampRelease = 0.6f,
                reverbMix = 0.5f, reverbDecay = 0.8f
            )),
        se("Slow Pad", "Pads", "Very slow evolving pad with long release",
            SynthState(
                osc1Waveform = 2, osc2Waveform = 3, oscMix = 0.5f,
                filterCutoff = 0.6f, filterResonance = 0.15f,
                ampAttack = 2.0f, ampDecay = 0.5f, ampSustain = 0.9f, ampRelease = 1.5f,
                reverbMix = 0.4f, reverbDecay = 0.8f
            ))
    )

    // ── Bass (5) ────────────────────────────────────────────────────────────
    val bass = listOf(
        se("Deep Sub", "Bass", "Heavy sub-bass with sub oscillator",
            SynthState(
                osc1Waveform = 3, osc2Waveform = 3, oscMix = 0.8f,
                subOscLevel = 0.6f,
                filterCutoff = 0.3f, filterResonance = 0.05f,
                ampAttack = 0.005f, ampDecay = 0.2f, ampSustain = 0.9f, ampRelease = 0.05f
            )),
        se("Pulse Bass", "Bass", "Pulse wave bass with slight detune",
            SynthState(
                osc1Waveform = 1, osc2Waveform = 1, oscDetune = 0.1f, oscMix = 0.7f,
                filterCutoff = 0.35f, filterResonance = 0.15f,
                ampAttack = 0.005f, ampDecay = 0.15f, ampSustain = 0.85f, ampRelease = 0.05f
            )),
        se("Resonant Bass", "Bass", "Resonant filtered bass tone",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 3, oscMix = 0.8f,
                subOscLevel = 0.3f,
                filterCutoff = 0.3f, filterResonance = 0.6f, filterEnvAmount = 0.3f,
                ampAttack = 0.005f, ampDecay = 0.2f, ampSustain = 0.7f, ampRelease = 0.05f
            )),
        se("Acid Bass", "Bass", "TB-303 style squelching bass",
            SynthState(
                osc1Waveform = 1, osc2Level = 0.0f, oscMix = 1.0f,
                filterCutoff = 0.25f, filterResonance = 0.8f, filterEnvAmount = 0.5f,
                ampAttack = 0.001f, ampDecay = 0.3f, ampSustain = 0.5f, ampRelease = 0.02f,
                distortionDrive = 0.15f, distortionMix = 0.2f
            )),
        se("Saw Bass", "Bass", "Aggressive saw wave bass",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 0, oscDetune = 0.05f,
                osc1Level = 0.8f, osc2Level = 0.3f, oscMix = 0.8f,
                filterCutoff = 0.4f, filterResonance = 0.2f,
                ampAttack = 0.005f, ampDecay = 0.15f, ampSustain = 0.8f, ampRelease = 0.05f
            ))
    )

    // ── FX (5) ──────────────────────────────────────────────────────────────
    val fx = listOf(
        se("Space Echo", "FX", "Effects patch with heavy delay and reverb",
            SynthState(
                osc1Waveform = 0, oscMix = 0.5f,
                filterCutoff = 0.7f, filterResonance = 0.1f,
                ampAttack = 0.01f, ampDecay = 0.3f, ampSustain = 0.5f, ampRelease = 0.5f,
                reverbMix = 0.5f, reverbDecay = 0.9f,
                delayMix = 0.5f, delayTime = 0.5f, delayFeedback = 0.6f,
                masterVolume = 0.7f
            )),
        se("Filter Sweep", "FX", "Extreme filter modulation effect",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 1, oscDetune = 0.3f,
                filterCutoff = 0.5f, filterResonance = 0.7f, filterEnvAmount = 0.8f,
                lfo1Rate = 0.15f, lfo1Depth = 0.5f, lfo1Waveform = 3,
                ampAttack = 0.01f, ampDecay = 0.3f, ampSustain = 0.6f, ampRelease = 0.3f,
                reverbMix = 0.2f
            )),
        se("Arp Bubble", "FX", "Plucky arpeggiated synth with delay",
            SynthState(
                osc1Waveform = 1, osc2Waveform = 2, oscMix = 0.7f,
                filterCutoff = 0.6f, filterResonance = 0.3f,
                ampAttack = 0.001f, ampDecay = 0.1f, ampSustain = 0.1f, ampRelease = 0.05f,
                delayMix = 0.3f, delayTime = 0.16f, delayFeedback = 0.4f,
                reverbMix = 0.15f
            )),
        se("Noise Wash", "FX", "Washy noise-based texture",
            SynthState(
                osc1Waveform = 3, osc2Waveform = 3, oscMix = 0.2f,
                noiseLevel = 0.7f,
                filterCutoff = 0.4f, filterResonance = 0.1f,
                lfo1Rate = 0.2f, lfo1Depth = 0.3f, lfo1Waveform = 0,
                ampAttack = 0.5f, ampDecay = 0.5f, ampSustain = 0.7f, ampRelease = 1.0f,
                reverbMix = 0.6f, reverbDecay = 0.9f, delayMix = 0.2f,
                distortionDrive = 0.1f, distortionMix = 0.2f
            )),
        se("Stab", "FX", "Short stabby chord with fast decay",
            SynthState(
                osc1Waveform = 0, osc2Waveform = 1, oscDetune = 0.2f, oscMix = 0.7f,
                filterCutoff = 0.5f, filterResonance = 0.4f,
                ampAttack = 0.001f, ampDecay = 0.05f, ampSustain = 0.0f, ampRelease = 0.02f,
                reverbMix = 0.1f, masterVolume = 0.9f
            ))
    )

    val allPresets = leads + pads + bass + fx
    allPresets.forEach { preset -> dao.insertPreset(preset) }
}
