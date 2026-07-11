# Task for worker

You are a delegated subagent running from a fork of the parent session. Treat the inherited conversation as reference-only context, not a live thread to continue. Do not continue or answer prior messages as if they are waiting for a reply. Your sole job is to execute the task below and return a focused result for that task using your tools.

Task:
Implement SDD slice H: mixer-fader-gesture for Juji-Synth (Android Kotlin/Compose DAW at /home/osqy/Desktop/juji-synth).

## Goal
Fix the clunky mixer fader by:
1. Creating a shared `DraggableValueController` that handles tap vs drag with hysteresis
2. Refactoring `VerticalFader` in MixerScreen to use it
3. Refactoring `RealKnob` to use the same controller
4. Debouncing level polling to ≥300ms

## Files to modify/create

### 1. NEW: DraggableValue.kt
File: app/src/main/java/com/jujidaw/ui/DraggableValue.kt

Create a shared gesture controller for draggable UI values (faders, knobs):

```kotlin
package com.jujidaw.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp

/**
 * Shared controller for draggable value UI (faders, knobs).
 * Handles tap vs drag discrimination via touch-slop hysteresis.
 */
object DraggableValueController {
    /** Minimum drag distance before entering drag mode (prevents tap-jump on small drags). */
    val touchSlop = 8.dp
    
    /** Quantization step for dB values (0.1 dB steps). */
    const val dB_STEP = 0.1f
    
    /**
     * Quantize a dB value to the nearest step.
     */
    fun quantizeDb(value: Float, step: Float = dB_STEP): Float {
        return (value / step).roundToInt() * step
    }
}
```

### 2. MixerScreen.kt — refactor VerticalFader
File: app/src/main/java/com/jujidaw/ui/mixer/MixerScreen.kt

Read the file first. Find the `VerticalFader` composable.

Current issues:
- Two separate `pointerInput` blocks: `detectTapGestures` + `detectDragGestures` in parallel → tap/drag contention
- Per-pixel state mutation causes recomposition churn
- No hysteresis between tap and drag

Fix:
a) Replace the two `pointerInput` blocks with a SINGLE `pointerInput` that uses `detectTapGestures` with custom handling:
   - On DOWN: record start position and current value
   - If drag distance exceeds `touchSlop` (8dp): enter DRAG mode, update value by `-dragAmount.y * sensitivity`, quantize to 0.1 dB steps
   - If drag never exceeds slop on UP: treat as tap (jump to Y position for fader)
   
   OR keep `detectDragGestures` but add a `requireUncaught` or use `awaitPointerEventScope` to properly discriminate.

   Simpler approach: use a single `pointerInput` with `awaitPointerEventScope`:
   ```
   modifier.pointerInput(Unit) {
       awaitPointerEventScope {
           while (true) {
               val down = awaitFirstDown()
               // Record start
               var dragging = false
               do {
                   val event = awaitPointerEvent()
                   if (!dragging) {
                       val distance = (event.changes.first().position - down.position).getDistance()
                       if (distance > touchSlopPx) dragging = true
                   }
                   if (dragging) {
                       // update value
                   }
               } while (event.changes.any { it.pressed })
               if (!dragging) {
                   // tap → jump to position
               }
           }
       }
   }
   ```

b) Hoist the fader's drawn state: the thumb position should be a `mutableStateOf` that updates on drag, not recomposing the entire 16-strip Row. Use `Canvas` with `drawLine`/`drawRoundRect` that reads the state without triggering recomposition of sibling strips.

c) Quantize value to 0.1 dB steps during drag.

### 3. RealKnob.kt — use DraggableValueController
File: app/src/main/java/com/jujidaw/ui/RealKnob.kt

Read the file. The knob currently uses `detectDragGestures` with `-dragAmount.y / 200f` sensitivity and 8dp touch-slop.

Changes:
a) Import and use `DraggableValueController.touchSlop` for the touch-slop threshold
b) Use `DraggableValueController.quantizeDb` if the knob controls a dB value
c) Keep the existing drag gesture structure but align thresholds with the fader

### 4. MixerViewModel.kt — debounce level polling
File: app/src/main/java/com/jujidaw/ui/mixer/MixerViewModel.kt

Read the file. Find `startLevelPolling` (polls channel levels via JNI).

Changes:
a) Change the polling interval from 200ms to 300ms (or higher, like 500ms)
b) If the polling uses a `while(true)` + `delay()` loop, that's fine — just increase the delay
c) Consider collecting levels into a `Channel` or `mutableStateListOf` that the meter Canvas observes without recomposing the strip Row

## Constraints
- Do NOT modify files from slices A, B, C, E, or G
- Do NOT touch MainScreen.kt, TimelineScreen.kt, SequencerScreen.kt, PatternModel.kt, ClipModel.kt, TransportController.kt
- The new DraggableValue.kt file is the only new file
- Run `./gradlew :app:testDebugUnitTest --no-daemon` after changes
- The fader and knob should feel smooth — no tap-jump on small drags, quantized steps

## Acceptance criteria
- DraggableValueController.kt exists with touchSlop and quantizeDb
- VerticalFader uses a single pointer input with hysteresis (no parallel detectTapGestures + detectDragGestures)
- RealKnob uses consistent touch-slop from DraggableValueController
- Level polling interval ≥ 300ms
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