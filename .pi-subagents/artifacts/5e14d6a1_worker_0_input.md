# Task for worker

You are a delegated subagent running from a fork of the parent session. Treat the inherited conversation as reference-only context, not a live thread to continue. Do not continue or answer prior messages as if they are waiting for a reply. Your sole job is to execute the task below and return a focused result for that task using your tools.

Task:
Implement SDD slices E + G for Juji-Synth (Android Kotlin DAW at /home/osqy/Desktop/juji-synth).

Slice E: unified-transport-controls — remove duplicate Play/Record/Reset from per-screen transport strips.
Slice G: ui-control-clarity — replace L-/I-/O+/P cryptic labels with readable controls.

## Files to modify

### 1. TimelineScreen.kt — TransportStrip
File: app/src/main/java/com/jujidaw/ui/timeline/TimelineScreen.kt

Read the full file first. Find the `TransportStrip` composable (or similar transport section within TimelineScreen).

Changes:
a) REMOVE the Play and Record buttons from TimelineScreen's transport strip. Keep any timeline-specific controls (loop region, punch markers) but remove Play(▶/■), Record(●), and Reset(↺) buttons.
b) The remaining controls (L-/L+/I-/O+/P) should stay but be relabeled per slice G.

### 2. SequencerScreen.kt — SequencerTopBar
File: app/src/main/java/com/jujidaw/ui/sequencer/SequencerScreen.kt

Read the file. Find `SequencerTopBar` or the transport section.

Changes:
a) REMOVE Play and Record buttons from SequencerTopBar. Keep any sequencer-specific controls.

### 3. TimelineScreen.kt — relabel L-/I-/O+/P (slice G)
Still in TimelineScreen.kt's transport section (the loop/punch controls that remain after E):

Replace:
- `TinyButton("L-", ...)` → a labeled button like `Text("◀ Loop Start")` or an icon button with tooltip "Set loop start to playhead"
- `TinyButton("L+", ...)` → `Text("Loop End ▶")` or icon with tooltip "Set loop end to playhead"
- `TinyButton("I-", ...)` → `Text("Punch In")` or icon with tooltip "Set punch-in to playhead"
- `TinyButton("O+", ...)` → `Text("Punch Out")` or icon with tooltip "Set punch-out to playhead"
- `TransportButton("P", ...)` → `Text("Punch")` or toggle button with label "Punch" and tooltip "Toggle punch recording"

Use a consistent style — the buttons should be readable at a small size but not cryptic. Use Material icons where appropriate (e.g. `Icons.Filled.SkipToStart` for loop start). If the existing `TinyButton` composable is used, create a new `LabeledControl` or just use `TextButton`/`IconButton` with a tooltip.

IMPORTANT: The `TinyButton` composable itself may need updating (make it slightly bigger with a readable label) or you can replace inline. Keep the existing callback signatures (`onLoopStart`, `onLoopEnd`, `onPunchIn`, `onPunchOut`, `onTogglePunch`).

### 4. MainScreen.kt — verify global transport bar
File: app/src/main/java/com/jujidaw/ui/main/MainScreen.kt

Read `PersistentTransportBar`. Verify it has Play, Record, and Reset. No changes needed here — just confirm it's the single source of truth.

## Constraints
- Do NOT modify files from slices A, B, or C
- Do NOT touch MixerScreen.kt or RealKnob.kt (that's slice H)
- Do NOT touch Landscape nav code in MainScreen.kt (that's slice F)
- Run `./gradlew :app:testDebugUnitTest --no-daemon` after changes
- Keep existing test assertions passing — if any test checks for transport buttons in TimelineScreen/SequencerScreen, update or remove those assertions

## Acceptance criteria
- TimelineScreen no longer renders Play/Record/Reset buttons
- SequencerScreen no longer renders Play/Record buttons
- The remaining loop/punch controls in TimelineScreen use readable labels (not L-/I-/O+/P)
- MainScreen PersistentTransportBar is the sole transport with Play/Record/Reset
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