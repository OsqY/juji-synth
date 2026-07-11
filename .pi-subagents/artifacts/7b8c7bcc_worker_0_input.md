# Task for worker

You are a delegated subagent running from a fork of the parent session. Treat the inherited conversation as reference-only context, not a live thread to continue. Do not continue or answer prior messages as if they are waiting for a reply. Your sole job is to execute the task below and return a focused result for that task using your tools.

Task:
Implement SDD slice B: pad-triggered-transport data model for Juji-Synth (Android Kotlin + C++ DAW app at /home/osqy/Desktop/juji-synth).

## Goal
Timeline clips and sequencer steps trigger pads via PAD_TRIGGER instead of sending MIDI notes to channel 0 (synth). This is the core fix that makes pads→timeline and pads→sequencer actually produce sound.

## Files to modify (in order)

### 1. PatternModel.kt — add padIndex to NoteEvent
File: app/src/main/java/com/jujidaw/model/PatternModel.kt
- Add `padIndex: Int = -1` to `NoteEvent` data class (-1 = legacy/unknown)
- Do NOT change any other fields

### 2. ClipModel.kt — add padIndex to PatternClip
File: app/src/main/java/com/jujidaw/model/ClipModel.kt
- Add `padIndex: Int = -1` to `PatternClip` data class (-1 = legacy)
- Do NOT change any other fields

### 3. SynthEngine.kt — add nativeSchedulePadTrigger JNI method
File: app/src/main/java/com/jujidaw/audio/SynthEngine.kt
- Find the existing `external fun nativeScheduleNoteOn(...)` declaration
- Add a new: `external fun nativeSchedulePadTrigger(padIndex: Int, tick: Long, velocity: Float)`
- Follow the same pattern as nativeScheduleNoteOn

### 4. TransportController.kt — add schedulePadTrigger + route pad clips
File: app/src/main/java/com/jujidaw/engine/TransportController.kt
This is the main change. Read the file carefully first (use module_report or read).

a) Add a new method `schedulePadTrigger(padIndex: Int, startTick: Long, velocity: Float)`:
   - Convert startTick to sample position using tickToSample
   - Call `scheduler.schedulePadTrigger(padIndex, startTick, velocity)` — BUT check: does `SynthEngineScheduler` (or its interface) have a `schedulePadTrigger` method? If not, add it to the scheduler interface and implementation.
   - If `SynthEngineScheduler` calls `SynthEngine.nativeSchedulePadTrigger(padIndex, tick, velocity)`, that's the path.

b) Modify `schedulePatternNotes(...)`:
   - Currently it calls `scheduleNoteOn(trackIndex=pattern.trackIndex, note=noteEvent.note, velocity=...)`
   - Change: if `noteEvent.padIndex >= 0`, call `schedulePadTrigger(padIndex=noteEvent.padIndex, startTick=..., velocity=...)` INSTEAD of `scheduleNoteOn`
   - If `noteEvent.padIndex < 0` (legacy), fall back to the existing `scheduleNoteOn` path (backward compat)

c) Modify `schedulePatternClip(...)`:
   - When building note events from a pattern, carry through the clip's `padIndex` if the note's `padIndex` is -1:
     `val effectivePadIndex = if (note.padIndex >= 0) note.padIndex else clip.padIndex`
   - Use `effectivePadIndex` when deciding pad-trigger vs noteOn

### 5. SynthEngineScheduler.kt — add schedulePadTrigger to scheduler interface
File: app/src/main/java/com/jujidaw/engine/SynthEngineScheduler.kt
- Add `fun schedulePadTrigger(padIndex: Int, tick: Long, velocity: Float)` to the interface
- In the implementation (NativeSynthEngineScheduler or wherever the real impl is), call `SynthEngine.nativeSchedulePadTrigger(padIndex, tick, velocity)`

### 6. JniBridge.cpp — add native method implementation
File: app/src/main/cpp/JniBridge.cpp
- Find existing `nativeScheduleNoteOn` implementation
- Add `Java_com_jujidaw_audio_SynthEngine_nativeSchedulePadTrigger` that calls `SynthEngine::getInstance().schedulePadTrigger(padIndex, tick, velocity)`
- NOTE: Check if `SynthEngine` already has a `schedulePadTrigger` method. The scout report says ScheduledEvent::makePadTrigger exists and Transport::firePendingEvents handles PAD_TRIGGER → sampler->triggerPad. So the C++ path should already work if we push a ScheduledEvent::makePadTrigger. Find how nativeScheduleNoteOn pushes its event and mirror that pattern for pad trigger.

### 7. Project migration — handle legacy patterns
File: app/src/main/java/com/jujidaw/project/ProjectRepository.kt
- When loading a Pattern, if any NoteEvent has `padIndex == -1` (or field missing from JSON), set `padIndex = note % 16`
- This ensures old projects still route notes somewhere sensible

## Existing test structure
File: app/src/test/java/com/jujidaw/engine/TransportControllerTest.kt
- Uses FakeSynthEngineScheduler that records noteOnEvents/noteOffEvents
- Add a `padTriggerEvents` list to FakeSynthEngineScheduler to capture pad triggers
- Add tests:
  a) `schedulePatternClip_withPadIndex_callsSchedulePadTrigger` — create a NoteEvent(padIndex=3), verify padTriggerEvents contains padIndex=3
  b) `schedulePatternClip_legacyPadIndex_fallsBackToNoteOn` — create a NoteEvent(padIndex=-1, note=60), verify noteOnEvents is called (backward compat)
  c) `schedulePatternClip_clipPadIndex_overridesNotePadIndex` — clip.padIndex=5, note.padIndex=-1 → padTriggerEvents contains padIndex=5

## Constraints
- Do NOT modify SampleBuffer.cpp, AudioConverter.kt, or any file touched by slice A (already done)
- Do NOT modify UI files (MainScreen, TimelineScreen, SequencerScreen) — those are slices E/C
- Preserve backward compatibility: old patterns with no padIndex must still work
- Run `./gradlew :app:testDebugUnitTest --no-daemon` after changes and verify ALL tests pass (including the existing TransportControllerTest)
- If a C++ change is needed (JniBridge.cpp), just add the function — do not modify Transport.cpp or ScheduledEvent.h unless absolutely necessary (the existing PAD_TRIGGER path should already work)

## Acceptance criteria
- `padIndex` field exists on NoteEvent and PatternClip with default -1
- `schedulePadTrigger` method exists in TransportController
- `schedulePatternNotes` routes to padTrigger when padIndex >= 0
- FakeSynthEngineScheduler has padTriggerEvents list
- New tests pass alongside existing tests
- No regressions: `./gradlew :app:testDebugUnitTest` all green

## Acceptance Contract
Acceptance level: reviewed
Completion is not accepted from prose alone. End with a structured acceptance report.

Criteria:
- criterion-1: Implement the requested change without widening scope
- criterion-2: Return evidence sufficient for an independent acceptance review

Required evidence: changed-files, tests-added, commands-run, validation-output, residual-risks, no-staged-files

Review gate: required by reviewer.

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