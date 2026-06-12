## 1. C++ Infrastructure: Enable Per-Voice Wiring

- [x] 1.1 Add `oscMix_`, `oscSyncEnabled_`, `modWheel_`, `subOscLevel_`, and `subPhase_` member fields to `SynthVoice.h`
- [x] 1.2 Add setter methods in `SynthVoice.h`: `setOscMix(float)`, `setOscSyncEnabled(bool)`, `setModWheel(float)`, `setSubOscLevel(float)`, `setSubPhase(double)`
- [x] 1.3 In `AudioEngine::applyModulationMatrix()`, call the new setters on each active voice: `voices_[v].setOscMix(oscMix_)`, etc.
- [x] 1.4 Add `std::atomic<int> activeVoiceCount_{0}` to `AudioEngine.h` and increment/decrement it in `handleNoteOn()`/`handleNoteOff()`/voice-sync-loop

## 2. Audio Path: Wire Dead Knobs

- [x] 2.1 In `SynthVoice::process()`, replace the fixed `(osc1Out + osc2Out) * 0.5f` mix with `osc1Out * (1.0f - oscMix_) + osc2Out * oscMix_`
- [x] 2.2 In `SynthVoice::process()`, add `syncEnabled`: after `osc1_.process()`, detect phase wrap (store previous `tableIndex_` and compare) and call `osc2_.sync()` if wrapped
- [x] 2.3 In `SynthVoice::process()`, add per-voice sub oscillator: generate a square wave one octave below using `subPhase_` and mix at `subOscLevel_` level
- [x] 2.4 In `SynthVoice::process()`, apply `modWheel_` as an additional pitch bend factor: `pitchBendSemitones += modWheel_ * 0.5`
- [ ] 2.5 **BUILD & VERIFY**: OSC Mix, OSC Sync, Sub Osc Level, and Mod Wheel knobs produce audible changes

## 3. Noise Gate

- [x] 3.1 Add `float noiseSmooth_ = 0.0f` member to `AudioEngine.h`
- [x] 3.2 In `AudioEngine::processAudio()`, compute the noise target: `float noiseTarget = (activeVoiceCount > 0) ? noiseLevel_ : 0.0f`
- [x] 3.3 Add one-pole smoother: `noiseSmooth_ += (noiseTarget - noiseSmooth_) * 0.01f`
- [x] 3.4 Gate the noise: change `sample += generateNoise() * noiseLevel_` to `sample += generateNoise() * noiseSmooth_` (only when `noiseSmooth_ > 0.001f`)
- [ ] 3.5 **BUILD & VERIFY**: Atmospheric preset produces no noise when no key is pressed; noise fades in/out smoothly when keys are played

## 4. LFO Modulation Wiring

- [x] 4.1 In `AudioEngine::processAudio()`, call `modMatrix_.getModulation()` for each destination using the `modSources` array as input
- [x] 4.2 Apply modulation results to each active voice: filter cutoff offset, pitch offset, amp gain offset, osc mix offset
- [x] 4.3 Tune modulation scaling: routes with amount=1.0 and depth=1.0 produce full-range sweep
- [ ] 4.4 **BUILD & VERIFY**: Moving Pad preset produces audible filter sweep. Filter Sweep preset produces extreme filter modulation.

## 5. Effect Reset on Preset Load

- [x] 5.1 Add `bool pendingEffectsReset_` flag to `AudioEngine.h`
- [x] 5.2 Add `setPendingEffectsReset()` method that safely sets the flag
- [x] 5.3 In `swapParamsIfNeeded()`, check the flag after applying params and call `reverb_.reset()`/`delay_.reset()`. Clear the flag after reset.
- [x] 5.4 Add JNI method `nativeResetEffects()` in `JniBridge.cpp` that calls `engine.setPendingEffectsReset()`
- [x] 5.5 Add `resetEffects()` method to `SynthEngine.kt` calling `nativeResetEffects()`
- [x] 5.6 Add `SynthEngine.resetEffects()` call at the top of `applySynthStateToEngine()` in `MainSynthScreen.kt`
- [ ] 5.7 **BUILD & VERIFY**: switching presets produces no residual audio from the previous preset's delay/reverb

## 6. Atomic Preset Apply

- [x] 6.1 Define fixed float-array protocol: 39 values, documented in `setAllParamsFromArray` comment and `applySynthStateToEngine`
- [x] 6.2 Add `nativeApplySynthState(values: FloatArray)` and `applySynthState(values)` in `SynthEngine.kt`
- [x] 6.3 Implement `nativeApplySynthState` in `JniBridge.cpp` with `setAllParamsFromArray` → atomically sets `pendingParams_`, one `paramsPending_` release store at end
- [x] 6.4 Refactor `MainSynthScreen.kt:applySynthStateToEngine()` to build a FloatArray and call `SynthEngine.applySynthState(array)`
- [ ] 6.5 **BUILD & VERIFY**: loading any preset produces no audible mid-load glitch

## 7. Effect Parameter Smoothing (Bonus)

- [x] 7.1 Create `Smoother.h` with one-pole `process(float target)` method
- [x] 7.2 `Delay.cpp`: linear interpolation on read index, Smoother on mix_ parameter
- [x] 7.3 `Reverb.cpp`: Smoother on mix_ parameter before dry/wet blend
- [x] 7.4 `Distortion.cpp`: Smoother on driveFactor_ in process()
- [ ] 7.5 **BUILD & VERIFY**: sweeping Delay Time, Reverb Mix, and Distortion Drive produce smooth transitions

## 8. Cleanup & Verify Build

- [x] 8.1 Remove dead code: delete `generateSubOsc()`, `paramsDirty_`, `noiseState_` write-only fields
- [x] 8.2 **BUILD**: Run `./gradlew lint` — passed
- [x] 8.3 **BUILD**: Run `./gradlew clean assembleDebug` — **BUILD SUCCESSFUL**

## 9. Post-Test Bug Fixes (Discovered on Device)

- [x] 9.1 Make `pendingEffectsReset_` atomic: change `bool` → `std::atomic<bool>` with `memory_order_release` on set, `memory_order_acquire` on read in `swapParamsIfNeeded()`
- [x] 9.2 Apply per-voice params in `handleNoteOn()`: after allocating a voice and calling `init()`, call `setOscMix`, `setOscSyncEnabled`, `setSubOscLevel`, `setModWheel`, `setPitchBend` on the newly allocated voice before `noteOn()`
- [x] 9.3 Reset `subPhase_` in `SynthVoice::init()`: add `subPhase_ = 0.0` to prevent phase discontinuity click on new notes
- [ ] 9.4 **BUILD & VERIFY**: Load Dreamscape, play first note — sub oscillator audible immediately, no stutter/click, old preset audio doesn't bleed
