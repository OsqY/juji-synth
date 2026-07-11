# Task for worker

You are a delegated subagent running from a fork of the parent session. Treat the inherited conversation as reference-only context, not a live thread to continue. Do not continue or answer prior messages as if they are waiting for a reply. Your sole job is to execute the task below and return a focused result for that task using your tools.

Task:
Implement SDD slice C: FL-style step sequencer for Juji-Synth (Android Kotlin DAW at /home/osqy/Desktop/juji-synth).

## Context
Slice B just completed: PatternModel.NoteEvent and ClipModel.PatternClip now have `padIndex: Int = -1`. TransportController.schedulePatternNotes routes to `schedulePadTrigger` when `effectivePadIndex >= 0`, falling back to `scheduleNoteOn` otherwise.

## Goal
Sequencer rows map 1:1 to pads (row R = pad R). Toggling a step on row R schedules a PAD_TRIGGER for that pad, not a note=60 on channel 0. Each pad's loaded content plays when its step fires.

## Files to modify

### 1. SequencerViewModel.kt
File: app/src/main/java/com/jujidaw/ui/sequencer/SequencerViewModel.kt

Read this file first to understand:
- `StepTrack` data class — currently has `note: Int = 60` as the default
- `toggleStep(track, step)` — toggles a step cell
- `syncActivePatternToTransport()` — exports Pattern to TransportController

Changes needed:
a) In `StepTrack`, the row maps to a pad: `val padIndex: Int = trackIndex` (or compute from the track index). Each row R corresponds to pad R.
b) `toggleStep(track, step)`: when creating a NoteEvent for the step, set `padIndex = track.padIndex` (NOT `note = 60`). Keep `note = track.defaultNote` for backward compat, but the padIndex is what matters.
c) `syncActivePatternToTransport()`: ensure the exported Pattern's NoteEvents carry the correct `padIndex` per step. Each step's NoteEvent should have `padIndex = track.padIndex`.
d) The step-to-tick calculation should remain unchanged (step * TICKS_PER_STEP).

### 2. SequencerScreen.kt
File: app/src/main/java/com/jujidaw/ui/sequencer/SequencerScreen.kt

Read this file. Changes:
a) Row labels should show the pad number/index: e.g. "Pad 1", "Pad 2", etc. — or show the pad's loaded content name if available from the ViewModel.
b) No structural changes needed beyond labels; the step toggle already delegates to `onStepToggle(track, step)` which calls `toggleStep`.

### 3. Test updates
File: app/src/test/java/com/jujidaw/engine/TransportControllerTest.kt

The existing tests should still pass (they test TransportController, not SequencerViewModel directly). But if any test constructs StepTrack or calls syncActivePatternToTransport, update it to use the new padIndex field.

Also add a targeted test if possible — but since SequencerViewModel likely depends on Compose/runtime, a pure unit test may not be feasible. Focus on making sure existing TransportController tests pass.

## Constraints
- Do NOT modify files touched by slices A or B (AudioConverter.kt, SampleBuffer.cpp, PatternModel.kt, ClipModel.kt, TransportController.kt, SynthEngineScheduler.kt, SynthEngine.kt, JniBridge.cpp, TransportControllerTest.kt, FakeSynthEngineScheduler.kt)
- Do NOT touch UI files outside SequencerScreen.kt (MainScreen, TimelineScreen, etc.)
- Run `./gradlew :app:testDebugUnitTest --no-daemon` after changes and verify ALL tests pass
- Keep the sequencer's existing pattern generation logic intact; only change what padIndex/note values are set on the NoteEvents

## Acceptance criteria
- StepTrack has a `padIndex` that equals its track index (row R = pad R)
- NoteEvents exported by syncActivePatternToTransport carry `padIndex = row's padIndex`
- Existing TransportController tests still pass (no regressions)
- Sequencer UI row labels show pad numbers

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