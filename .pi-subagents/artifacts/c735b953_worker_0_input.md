# Task for worker

You are a delegated subagent running from a fork of the parent session. Treat the inherited conversation as reference-only context, not a live thread to continue. Do not continue or answer prior messages as if they are waiting for a reply. Your sole job is to execute the task below and return a focused result for that task using your tools.

Task:
Implement SDD slice D: multi-timbral-synth-per-pad for Juji-Synth (Android Kotlin/Compose + C++ DAW at /home/osqy/Desktop/juji-synth).

This is the largest slice. Implement in 3 logical parts: D1 (C++ engine pool), D2 (Kotlin VM + presets), D3 (Keyboard target).

## Context
- Slice A fixed WAV import (AudioConverter.kt)
- Slice B added padIndex to NoteEvent/PatternClip and wired PAD_TRIGGER routing
- Slice C made sequencer rows map to pads
- The engine currently has ONE SynthInstrument on channel 0 shared by all pads
- PadConfig.synthMode is a stub that routes to that shared synth

## D1: C++ engine pool (AudioEngine + SamplerInstrument)

### AudioEngine.h / AudioEngine.cpp
File: app/src/main/cpp/AudioEngine.h and AudioEngine.cpp

Read these files. Currently:
- `AudioEngine` owns one `SynthInstrument` (channel 0) and one `SamplerInstrument` (channel 1)
- `SamplerInstrument` has a `synthTarget_` pointer to the shared synth

Changes:
a) Add a fixed pool: `SynthInstrument* synthForPad_[16]` (lazy-initialized, nullptr by default)
b) Add `SynthInstrument* getPadSynth(int padIndex)` that lazily creates the synth
c) Add `void setPadSynthTarget(int padIndex, SynthInstrument* synth)` or just use the pool directly
d) In the mixer sum, include the per-pad synth channels

### SamplerInstrument.h / SamplerInstrument.cpp
File: app/src/main/cpp/SamplerInstrument.h and SamplerInstrument.cpp

Read these files. Currently `PadConfig` has:
```cpp
struct PadConfig {
    bool synthMode = false;
    int synthRootNote = 60;
    // ...
};
```
And `noteOn` checks `pad.synthMode` → `synthTarget_->noteOn(...)`.

Changes:
a) Change `PadConfig.synthMode: bool` → `enum PadMode { SAMPLE, SYNTH }` or keep bool but route differently
b) Remove `synthTarget_` (shared pointer). Instead, when `pad.synthMode == true`, call `getPadSynth(padIndex)->noteOn(...)` via the AudioEngine pool
c) SamplerInstrument needs access to AudioEngine to call `getPadSynth`. Pass it via constructor or a setter.

### JniBridge.cpp
File: app/src/main/cpp/JniBridge.cpp

Add JNI methods:
a) `nativeSynthNoteOn(padIndex, note, velocity)` → `synthForPad_[padIndex]->noteOn(note, velocity)`
b) `nativeLoadPresetToPad(padIndex, presetId)` → loads a preset into `synthForPad_[padIndex]` (may need to read preset params and apply them)
c) `nativeSetSynthParam(padIndex, paramIndex, value)` → sets a parameter on `synthForPad_[padIndex]`

## D2: Kotlin VM + preset DB

### SynthEngine.kt
File: app/src/main/java/com/jujidaw/audio/SynthEngine.kt

Add external JNI declarations:
```kotlin
external fun nativeSynthNoteOn(padIndex: Int, note: Int, velocity: Float): Boolean
external fun nativeLoadPresetToPad(padIndex: Int, presetId: Int): Boolean
external fun nativeSetSynthParam(padIndex: Int, paramIndex: Int, value: Float): Boolean
```

### SynthViewModel.kt
File: app/src/main/java/com/jujidaw/ui/synth/SynthViewModel.kt

Currently `trackStates: MutableMap<Int, SynthState>` is keyed by track (but only channel 0 matters).

Changes:
a) Re-key by `padIndex`: `padSynthStates: MutableMap<Int, SynthState>`
b) When editing pad N's synth, use `nativeSetSynthParam(padIndex=N, ...)` instead of the global synth params
c) Load preset for pad N via `nativeLoadPresetToPad(padIndex=N, presetId)`

### SynthScreen.kt
File: app/src/main/java/com/jujidaw/ui/synth/SynthScreen.kt

Changes:
a) The screen should show which pad's synth is being edited (header: "Pad N Synth")
b) Preset browser should load/save to the selected pad's preset

### PresetDatabase.kt
File: app/src/main/java/com/jujidaw/data/PresetDatabase.kt

Changes:
a) Add a `pad_presets` table or column to associate presets with pads: `padIndex INT, presetId INT`
b) Methods: `savePadPreset(padIndex, presetId)`, `loadPadPreset(padIndex): Preset?`

### PadsViewModel.kt / PadsScreen.kt
File: app/src/main/java/com/jujidaw/ui/pads/PadsViewModel.kt and PadsScreen.kt

Changes:
a) When a pad is switched to Synth mode, initialize its synth instance and load its last-used preset
b) When switching away from Synth mode, save the current preset state

## D3: Keyboard "Play Selected Pad" target

### KeyboardViewModel.kt
File: app/src/main/java/com/jujidaw/ui/keyboard/KeyboardViewModel.kt

Currently has `KeyboardTarget: Synth | SamplerA | SamplerB | Track(index)`.

Changes:
a) Add `SelectedPad(padIndex: Int)` target
b) When target is `SelectedPad(N)`:
   - If pad N is in SAMPLE mode → call `SynthEngine.triggerPad(note % 16, velocity)` (existing path)
   - If pad N is in SYNTH mode → call `SynthEngine.nativeSynthNoteOn(padIndex=N, note, velocity)`
c) Update KeyboardScreen to show "Play Selected Pad" option and display which pad is selected

## Constraints
- Do NOT modify files from slices A, B, C, E, F, G, H
- The C++ changes (D1) are the riskiest — keep changes minimal and focused
- If the C++ refactor is too complex for one pass, implement just the Kotlin-side routing (D2+D3) and leave C++ pool as a follow-up
- Run `./gradlew :app:testDebugUnitTest --no-daemon` after changes
- If C++ changes break the build (JNI signature mismatch etc.), fix the JNI signatures to match

## Acceptance criteria
- SamplerInstrument routes synth-mode pads to per-pad SynthInstrument (not shared)
- SynthViewModel is indexed by padIndex
- KeyboardViewModel has SelectedPad target
- PresetDatabase stores per-pad presets
- All tests pass

## Acceptance Contract
Acceptance level: checked
Completion is not accepted from prose alone. End with a structured acceptance report.

Criteria:
- criterion-1: Implement the requested change without widening scope

Required evidence: changed-files, tests-added, commands-run, residual-risks, no-staged-files

Finish with a fenced JSON block tagged `acceptance-report` in this shape:
Use empty arrays when no items apply; array fields contain strings unless object entries are shown.
```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "specific proof"
    }
  ],
  "changedFiles": [
    "src/file.ts"
  ],
  "testsAddedOrUpdated": [
    "test/file.test.ts"
  ],
  "commandsRun": [
    {
      "command": "command",
      "result": "passed",
      "summary": "short result"
    }
  ],
  "validationOutput": [
    "validation output or concise summary"
  ],
  "residualRisks": [
    "none"
  ],
  "noStagedFiles": true,
  "diffSummary": "short description of the diff",
  "reviewFindings": [
    "blocker: file.ts:12 - issue found, or no blockers"
  ],
  "manualNotes": "anything else the parent should know"
}
```