## 1. Lock-Free Note Queue

- [x] 1.1 In AudioEngine.h, add `NoteEvent` struct with `Type { NoteOn, NoteOff }`, `note`, `velocity`
- [x] 1.2 Add `std::array<NoteEvent, 64> noteQueue_`, `std::atomic<int> noteQueueHead_`, `std::atomic<int> noteQueueTail_`
- [x] 1.3 Add `processNoteQueue()` method — drain all pending events from the queue
- [x] 1.4 Rename current `noteOn()`/`noteOff()` to `handleNoteOn()`/`handleNoteOff()` (these will be called from the audio thread)
- [x] 1.5 Rewrite `noteOn()`/`noteOff()` to push events to the queue instead of directly modifying voices
- [x] 1.6 In `processAudio()`, call `processNoteQueue()` at the very start (before `swapParamsIfNeeded()`)
- [x] 1.7 Remove `applyModulationMatrix()` call from `noteOn()` (it's already called from `swapParamsIfNeeded()` on the audio thread)
- [x] 1.8 Build and verify

## 2. Wire LFO Modulation and Sub-Oscillator

- [x] 2.1 In AudioEngine.cpp processAudio(), use `modSources` to actually modulate parameters per-sample
- [x] 2.2 Add sub-oscillator output to the sample mixing section: `sample += generateSubOsc(freq) * subOscLevel_`
- [x] 2.3 Build and verify

## 3. Consolidate Parameter Loading

- [x] 3.1 Add `nativeSetAllParams(float[] params)` JNI method that takes all 34 params as a single float array
- [x] 3.2 In the JNI implementation, set all fields on `pendingParams_` then set `paramsPending_ = true` ONCE
- [x] 3.3 In MainSynthScreen.kt, replace 34 separate `SynthEngine.setParam()` calls with one `SynthEngine.setAllParams()` call
- [x] 3.4 Build and verify

## 4. Quality Gate

- [x] 4.1 Run `./gradlew lint`
- [x] 4.2 Run `./gradlew clean assembleDebug`