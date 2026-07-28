# Juji DAW — Agent Guide

## Purpose

This repository contains an Android/Jetpack Compose music workstation. Current
work is focused on hardening the arrangement Timeline before new Timeline
features are introduced.

Read `CONVENTIONS.md` before any Git or GitHub operation.

## Non-negotiable guardrails

- Do not modify, stage, commit, or reset `app/src/main/cpp/oboe`.
- Treat unrelated dirty or untracked files as user-owned. Do not include them
  in a Timeline change.
- Timeline state uses musical ticks. Pixels are only for rendering and pointer
  input.
- Keep the Timeline viewport-virtualized. Never restore a full-width Timeline
  layout for rendering clips, grid, ruler, or playhead.
- One complete user gesture must create exactly one undo/redo transaction.

## Required workflow per phase

1. Read the acceptance criteria for the active phase in the authoritative queue
   `docs/plans/timeline-hardening-pending.md`. Consult
   `docs/timeline-hardening-plan.md` only for original module background.
2. Inspect only the files related to the phase. Do not broaden the change.
3. Add or update the smallest test that proves the intended behavior.
4. Implement one functional concern. Do not mix visual refinement into it.
5. Run the validation gates:

   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew compileDebugKotlin
   ./gradlew lintDebug
   ./gradlew assembleDebug
   ```

6. If a device or emulator is connected, also run:

   ```bash
   adb devices -l
   ./gradlew connectedDebugAndroidTest
   ```

7. Request an independent adversarial review of the phase diff from a separate
   agent. The implementer must not approve their own diff. Record the reviewer,
   verdict, and resolved blocking findings in the phase handoff or commit body;
   then rerun affected validation before starting another phase.
8. Commit only the explicitly reviewed files with a conventional commit. Use
   explicit paths when staging; never use `git add -A` in this repository.

## Timeline code map

- `app/src/main/java/com/jujidaw/ui/timeline/TimelineScreen.kt`: Compose UI,
  viewport rendering, and pointer interactions.
- `app/src/main/java/com/jujidaw/ui/timeline/TimelineViewModel.kt`: Timeline
  state, mutations, autosave, transport coordination, and history integration.
- `app/src/main/java/com/jujidaw/ui/timeline/TimelineEditingMath.kt`: pure
  tick/pixel transforms, snapping, hit testing, and resize calculations.
- `app/src/main/java/com/jujidaw/ui/timeline/TimelineGestureState.kt`: gesture
  arbitration state machine.
- `app/src/main/java/com/jujidaw/ui/timeline/TimelineEditCommands.kt` and
  `TimelineEditHistory.kt`: atomic undo/redo records.
- `app/src/androidTest/java/com/jujidaw/ui/timeline/TimelineComposeHarnessTest.kt`:
  UI and gesture integration tests.
- `app/src/test/java/com/jujidaw/ui/timeline/`: deterministic unit tests.

## Documentation

- `DESIGN.md` is the concise current architecture and interaction contract.
- `docs/timeline-hardening-plan.md` is the long-term module plan.
- `docs/plans/timeline-hardening-pending.md` is the authoritative current
  queue. `docs/backlog.md` is a historical snapshot and must not drive work.
