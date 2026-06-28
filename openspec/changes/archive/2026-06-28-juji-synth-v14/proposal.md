## Why

Deep codebase tracing revealed a critical data race between the UI thread and the audio thread. When `noteOn()` or `noteOff()` is called from JNI (UI thread), it directly modifies voice state (`active_`, envelope stage, pitch, amplitude). Simultaneously, `processAudio()` on the audio thread reads the same voice objects. This undefined behavior causes torn reads, corrupted voice state, and eventual audio cutoff/stutter. The stutter recirculates through delay/reverb feedback loops, creating the repeating ~1Hz pattern users hear.

## What Changes

- **Add lock-free SPSC command queue**: Replace direct `noteOn`/`noteOff` JNI calls with a command queue. UI thread pushes commands. Audio thread drains them at the start of each `processAudio()` callback. This eliminates ALL data races on voice data.
- **Move `applyModulationMatrix()` to audio thread only**: Remove the call from `noteOn()`. The audio thread already calls it via `swapParamsIfNeeded()`. New voices get their params directly in `noteOn()`.
- **Fix dead code**: Wire LFO modulation to actually affect parameters. Wire sub-oscillator to audio output.
- **Consolidate `applySynthStateToEngine`**: Replace 34 separate `setParam` JNI calls with a single `setParams(SynthParams)` call to atomically swap all params at once.

## Capabilities

### New Capabilities
- `lock-free-note-queue`: Thread-safe command queue for note events, eliminating UI/audio thread data race

### Modified Capabilities
- `multi-touch-fix-v2`: noteOn/noteOff now push to a queue instead of directly modifying voice state
- `oscillator-section`: Sub-oscillator actually generates sound (was dead code)
- `lfo-section`: LFO modulation actually routes to parameters (was dead code)

## Impact
- **AudioEngine.h/cpp**: Add SPSC queue (`moodycamel::ReaderWriterQueue` or custom `std::array` + atomics). `noteOn()`/`noteOff()` push to queue. `processAudio()` drains queue at start. Remove `applyModulationMatrix()` call from `noteOn()`.
- **SynthEngine.kt**: No change — JNI signatures stay the same
- **MainSynthScreen.kt**: Replace 34 `setParam` calls in `applySynthStateToEngine` with one `nativeSetParams` call passing serialized params