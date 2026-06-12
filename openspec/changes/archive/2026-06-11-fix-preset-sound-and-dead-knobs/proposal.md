## Why

Device testing revealed three classes of audio bugs: (1) noise-based presets like "Atmospheric" emit continuous hiss without any note pressed, (2) five UI controls (LFO 1/2, OSC Mix, OSC Sync, Mod Wheel, Sub Osc) have no audible effect, and (3) switching presets causes old audio to bleed through delay/reverb tails because effects are never reset. These issues make every pad/ambient preset sound "weird" and several prominent knobs feel broken.

## What Changes

- **Gate noise by voice activity**: White noise is currently mixed globally on every sample, producing constant hiss when `noiseLevel > 0`. Noise becomes a routable source that only plays when a voice is active, or gets its own simple envelope.
- **Wire LFO1/LFO2 into the modulation matrix**: LFO values are computed but never consumed. Route LFO1 and LFO2 into `ModulationMatrix::getModulation()` so modulation routes actually affect pitch, filter, and amp destinations.
- **Apply `oscMix` in per-voice output**: `SynthVoice::process()` currently mixes osc1+osc2 at fixed 50/50. Use the `oscMix` parameter instead.
- **Call `Oscillator::sync()` when `oscSync` is enabled**: Hook up the oscillator sync mechanism so it resets OSC2's phase from OSC1.
- **Wire `modWheel` into the audio path**: Route modulation wheel value as a modulation source (vibrato by default).
- **Wire `subOscLevel`**: `generateSubOsc()` is defined but never called. Add it to each voice's output.
- **Reset effect state on preset load**: Call `delay_.reset()` and `reverb_.reset()` when a new preset is applied so old buffer content doesn't bleed.
- **Atomic preset apply**: Replace 35+ sequential JNI `setParam` calls with a bulk `setParams()` snapshot so the audio thread swaps the entire state at once, eliminating glitches.

## Capabilities

### New Capabilities
- `noise-gate`: White noise gated by voice activity or routed through a simple envelope so it doesn't play without a note being held
- `lfo-modulation`: LFO1 and LFO2 wired into the modulation matrix so they modulate pitch, filter cutoff, resonance, amp, and osc mix destinations
- `effect-reset-on-preset`: Delay and reverb internal buffers cleared when a new preset is loaded to prevent audio bleed
- `atomic-preset-apply`: Preset parameters applied to the audio engine as a single atomic snapshot instead of 35+ sequential JNI calls
- `dead-knob-wiring`: OSC Mix, OSC Sync, Mod Wheel, and Sub Osc level controls wired so they actually affect output

### Modified Capabilities
*(None — no existing specs to modify)*

## Impact

- **`AudioEngine.cpp`**: `processAudio()` — route LFO values, gate noise, apply modWheel. `applyModulationMatrix()` — call `delay_.reset()`/`reverb_.reset()` on preset flag. `applySynthStateToEngine()` in `MainSynthScreen.kt` → replace with `nativeSetParamsBulk()`.
- **`SynthVoice.cpp`**: `process()` — use `oscMix_` for oscillator mixing, call `generateSubOsc()`, apply `oscSync` mechanism.
- **`modWheel_`**: Route as mod source (vibrato into osc frequency).
- **`JniBridge.cpp`**: Add `nativeSetParamsBulk()` that copies entire `SynthParams` struct, add `nativeResetEffects()`. Wire per-param setters for new destinations.
- **`ModulationMatrix.cpp`**: Already implemented but not called — wire it into `processAudio`.
- **`AudioEngine.h`**: May need `setWholeParams()` / `resetEffects()` methods.
