## Context

The Juji-Synth C++ audio engine has a clear architecture: `AudioEngine::processAudio()` runs on the audio thread (Oboe callback), processing 4 polyphonic `SynthVoice` instances followed by a global effects chain (distortion → chorus → delay → reverb). Parameter changes arrive from the Kotlin UI thread via JNI into `pendingParams_`, swapped atomically per audio block via `swapParamsIfNeeded()`.

The exploration found three categories of bug:

1. **Audio path holes**: LFO values computed and discarded. `ModulationMatrix` stored but never read. Noise mixed unconditionally. `subOscLevel`, `oscMix`, `oscSync`, `modWheel` all write-only fields.
2. **Preset-apply glitch**: 35+ sequential `setParam` JNI calls spread across multiple audio blocks, so voices see mismatched state mid-load. Old delay/reverb buffer content bleeds into new preset because effects are never reset.
3. **No sample smoothing on effect params**: Delay time changes cause integer-step read-index jumps. Reverb/delay mix, distortion drive, and other float params jump discontinuously per block.

All fixes touch only C++ with small additions to one Kotlin file.

## Goals / Non-Goals

**Goals:**
- Gate noise so presets like "Atmospheric" don't hiss when no note is held
- Wire LFO1/LFO2 through the modulation matrix so they actually modulate destinations
- Make oscMix, oscSync, subOscLevel, and modWheel knobs functional
- Reset delay/reverb buffers on preset load to prevent audio bleed
- Apply preset params atomically (single swap) to eliminate multi-block glitch
- Smooth all effect parameter changes to eliminate zipper noise when tweaking

**Non-Goals:**
- Adding new effect types (no flanger, phaser, etc.)
- Rewriting the modulation matrix system (it already works, just isn't called)
- Adding sample-accurate automation or DAW integration
- Stereo widening or multi-channel output

## Decisions

### 1. Noise: voice-gated with one-pole smoothing

**Option**: Per-voice noise oscillator
**Chosen**: Global smoothed noise gate
**Why**: Noise is a global mix element in the current architecture. Making it per-voice would require changing the voice allocation API. Instead, track the count of active voices in a `std::atomic<int> activeVoiceCount_` (incremented/decremented in `handleNoteOn`/`handleNoteOff` and the voice-activity sync loop). Multiply noise by a smoothed version of `(activeVoiceCount > 0) ? noiseLevel_ : 0.0`. The one-pole smoother (~50ms time constant) prevents the noise from clicking on/off.

```cpp
// In processAudio:
float noiseTarget = (activeVoiceCount > 0) ? noiseLevel_ : 0.0f;
noiseSmooth_ += (noiseTarget - noiseSmooth_) * 0.01f; // ~10ms at 44.1kHz
if (noiseSmooth_ > 0.001f) {
    sample += generateNoise() * noiseSmooth_;
}
```

**Alternative rejected**: Making noise a 5th voice. This would require envelope routing and compositing changes — too invasive for the value.

### 2. LFO modulation: hook into existing ModulationMatrix

`ModulationMatrix::getModulation()` and the route storage are fully implemented. The only missing piece is calling it. In `processAudio()`, construct the `modSources` array (which already includes LFO1 and LFO2 values) and for each destination, add the modulation value into the per-sample feedback.

Implementation: In the per-sample loop, query the modulation matrix for each destination and temporarily store the results. Then apply to voices before they process:
- `modPitch` → adjust voice frequency in the per-sample loop (add to `pitchBend_` or as a separate frequency offset)
- `modFilterCutoff` → route into `Filter::applyEnvelope()` as an additional modulation source
- `modAmp` → adjust envelope output level
- `modOscMix` → adjust per-sample mix

This requires extending `SynthVoice::process()` to accept modulation parameters, or applying modulation inside the `AudioEngine` loop between the voice sum and the effects chain.

Simplest approach: compute modulation values once per block (before the per-sample loop) and apply them statically via `setFilterCutoff()`, `setAmplitude()` etc. This avoids per-sample overhead but means modulation updates at the block rate (~5.8ms), which is fine for LFO rates.

**Rejected**: Per-sample modulation. The block-rate approach is simpler, introduces no CPU regressions, and ~5.8ms modulation resolution is faster than any LFO these presets use.

### 3. Effect reset on preset

Add a `bool pendingEffectsReset_` flag to `AudioEngine`. The Kotlin `applySynthStateToEngine` calls `nativeResetEffects()` before the param sweep. The audio thread, on seeing the flag, calls `reverb_.reset()`, `delay_.reset()`, and `distortion_.reset()` (already implemented). Clears the flag after.

### 4. Atomic preset apply

Replace the 35-line `applySynthStateToEngine` with a single `nativeApplySynthState(serialized: String)` or `nativeSetAllParams(values: FloatArray)`. Since JNI handles arrays efficiently:

- Kotlin side: build a `FloatArray` of ~35 values in a known order → call `nativeSetAllParams(array)` once.
- C++ side: `JniBridge.cpp` unpacks the array into `pendingParams_` fields in one go, then sets `paramsPending_ = true`.

This guarantees the audio thread sees a completely consistent state on the next block.

### 5. Dead knob wiring

| Knob | Field | Where to wire |
|---|---|---|
| OSC Mix | `oscMix_` | `SynthVoice::process()`: `osc1Out * (1 - oscMix) + osc2Out * oscMix` |
| OSC Sync | `oscSync_` | `SynthVoice::process()`: after `osc1_.process()`, if OSC1 phase wrapped, call `osc2_.sync()` |
| Mod Wheel | `modWheel_` | Apply as additional pitch bend in `SynthVoice::process()`: `pitchBend_ * (1.0 + modWheel * 0.5)` |
| Sub Osc Level | `subOscLevel_` | `SynthVoice::process()`: generate square wave one octave below base freq, mix at `subOscLevel_` level |

**All four** require that `AudioEngine::applyModulationMatrix()` passes these values to each active voice. Currently it only passes `pitchBend_`. Extend the per-voice setters in `applyModulationMatrix()`:

```cpp
for (int v = 0; v < MAX_VOICES; v++) {
    if (voiceActive_[v]) {
        voices_[v].setOscMix(oscMix_);
        voices_[v].setOscSyncEnabled(oscSync_);
        voices_[v].setPitchBend(pitchBend_); // already done
        voices_[v].setModWheel(modWheel_);
        voices_[v].setSubOscLevel(subOscLevel_);
    }
}
```

### 6. Smoothing on effect parameter changes (bonus)

Apply a one-pole low-pass smoother on each effect parameter that can be swept continuously (mix, drive, delayTime, decay, damping). The smoother lives inside each effect class. Audio-thread-safe because both the set and the read are on the audio thread.

- `Delay::process()`: replace `delaySamples_` integer with a smoothed fractional read index using linear interpolation between two buffer positions (already done in Chorus). Add a one-pole on `mix_`.
- `Reverb::process()`: one-pole on `mix_`, `decay_`, `damping_`.
- `Distortion::process()`: one-pole on `drive_`.
- `Chorus::process()`: one-pole on `mix_`, `depth_`, `rate_`.

## Risks / Trade-offs

- **Active voice count needs to be atomic**: The count is read from the audio thread and written from `handleNoteOn`/`handleNoteOff` (both on the audio thread via the note queue) and from the voice-sync loop. It's already single-threaded-safe on the audio thread. Adding `std::atomic<int>` is defensive.
- **Modulation at block rate vs per-sample**: Block-rate modulation of filter cutoff from LFO with a 5.8ms update rate introduces audible stepping at high LFO rates (>10Hz), but our LFOs cap at 50Hz and the audible stepping threshold is ~5Hz. At the typical preset rate (0.05–0.3 Hz), block-rate is indistinguishable from per-sample. If stepping becomes audible later, the design can be upgraded to per-sample without API changes.
- **Sub-oscillator phase**: `generateSubOsc()` currently uses a `static double subPhase` — this is shared across all voices. Fix by making it a per-voice variable. The current implementation is `static` and would produce wrong results with polyphony. The fix adds a `subPhase_` member to `SynthVoice`.
- **OSC Sync with polyphony**: When OSC's phase wraps, it resets OSC2 in the same voice. Shared across 4 voices independently — no issue.

## Open Questions

- Should the noise smoother time constant be hardcoded or exposed as a UI parameter? If hardcoded, 50ms is a good default.
- The modulation matrix supports 6 sources: LFO1, LFO2, ENV1, ENV2, Velocity, Aftertouch. ENV1/ENV2 are currently not accessible in the modulation loop because they're per-voice. Do we want to expose them as global modulation sources (using the first active voice's envelope value) or leave them for a future change? Decision: leave them — this change focuses on making LFO work.
