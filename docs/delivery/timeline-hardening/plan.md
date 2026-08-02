# Timeline Hardening — Delivery Plan

Every phase follows `DEFINE → ANALYZE → PLAN → IMPLEMENT_PHASE → VALIDATE →
REVIEW → CLOSE_PHASE`. Four Gradle gates and independent review are required.

## Closed prerequisites

R1 `e1c2f71`, R2 `8f25d30`, R3 `5d93af4`, and R4 `1c7e6e2` are closed.

## Remaining phases

| Phase | Objective | Commit | Closure |
| --- | --- | --- | --- |
| M10 | Edge auto-scroll while moving clips; 100%/500%, bounds, cancel, one undo | `feat(timeline): add edge auto-scroll while moving clips` | AC-F1–F4, gates, device, review PASS |
| M11 | Non-intercepting editing-state indicators | `feat(timeline): add editing state indicators` | AC-V1, gates, review PASS |
| M12 | Device/density validation matrix | `test(timeline): add device density validation matrix` | AC-D1, evidence review |
| M13 | Measured viewport performance/recomposition work | `perf(timeline): reduce viewport recomposition overhead` | AC-P1, before/after evidence |
| M14 | Document viewport/tick model and constraints | `docs(timeline): document viewport-based rendering model` | AC-C1, docs review |
| M15 | Final audit and authorized handoff | optional `chore(timeline): finalize validation and review artifacts` | AC-O1, final-audit complete/blocked |
| M16-A | Normalize audio paths and make save failures observable | `fix(project): preserve audio clips across save and rename` | AC-R1–R3, focused tests, gates, independent review |
| M16-B | Keep every captured clip composed during auto-scroll | `fix(timeline): pin all clips during multi-drag preview` | AC-R4, unit/Compose coverage, gates, independent review |
| M16-C | Re-audit the PR and update local handoff records | `docs(timeline): record PR remediation validation` | zero P0/P1 findings, final audit updated |

Current phase: M15 final audit (blocked). M10 is closed in `22b0461`, M11 in
`7f6de85`, M12 in `6f8f8d2`, M13 in `77a76f6`, and M14 is closed with AC-C1
evidence in `phase-14-review.md`. M15 security remediation has passed review;
the remaining work is PR remediation M16, the deferred density matrix, and
explicitly authorized external handoff. No direct merge is permitted.

## M16 — PR remediation plan

The PR review found two audio persistence blockers and one viewport-preview
blocker. This phase does not add Timeline features or change Oboe.

### M16-A — Audio path lifecycle and save results

Objective: keep `AudioClip.audioFilePath` relative to its project and never
report a failed save as successful.

Files/components:

- `ProjectRepository.kt`: resolve source files from the active/source project,
  copy them into the target `samples/` directory, and serialize normalized
  relative paths; normalize paths before/while rename.
- `ProjectAutosave.kt`: pass the active project as the source for autosave and
  update last-project settings only after `saveProject` succeeds.
- `ProjectViewModel.kt`: surface failure from the regular Save action.
- `TimelineViewModel.kt` and `ProjectViewModel.kt` import flow: store relative
  paths while resolving absolute files only for the engine boundary.
- `ProjectPathPolicyTest.kt` and focused repository/instrumentation tests:
  source-to-target autosave, rename, missing-file failure, and relative-path
  round trips.

Acceptance criteria:

- AC-R1: imported and timeline-created audio clips serialize as paths inside
  their project, preferably `samples/<file>`; no active-project absolute path
  is persisted.
- AC-R2: autosave can copy an audio clip from the active project into the
  autosave project and reload it with the same clip ID and relative path.
- AC-R3: renaming an audio project preserves the clip and reloads it from the
  renamed directory; missing/outside files fail without updating last-project
  state or showing a success toast.

Rollback: revert the M16-A commit; no schema migration is needed because the
loader already accepts relative paths and rejects unsafe paths.

### M16-B — Multi-clip viewport pinning

Objective: keep every clip captured by one move gesture visible while the
viewport auto-scrolls.

Files/components:

- `TimelineEditingMath.kt`: allow `timelineVisibleClips` to receive a set of
  pinned IDs.
- `TimelineScreen.kt`: pass all `draggedClipIds`, plus any active resize ID,
  to the visibility filter.
- `TimelineEditingMathTest.kt` and `TimelineComposeHarnessTest.kt`: cover
  multiple pinned clips and a captured multi-selection during edge movement.

Acceptance criterion:

- AC-R4: every clip in the captured move set remains composed during the active
  preview and auto-scroll; clips unpin after commit or cancel so unrelated
  offscreen clips remain virtualized. Undo/redo validates the model/history
  state, not persistent composition.

Rollback: revert the M16-B commit; the existing single-clip viewport behavior
remains available.

### M16-C — Revalidation and handoff

Objective: prove the fixes and refresh local handoff records. PR/Linear/Notion
updates remain a separate, explicitly authorized post-approval step.

Validation:

```bash
./gradlew testDebugUnitTest
./gradlew compileDebugKotlin
./gradlew lintDebug
./gradlew assembleDebug
```

Run `adb devices -l` first. If a device is attached, run
`./gradlew connectedDebugAndroidTest`; otherwise record the skipped result and
the prior SM-G998W evidence honestly. Request an independent adversarial review
of the M16 diff. Do not merge while any P0/P1 finding, failed gate, unresolved
review thread, or missing handoff remains. External PR/Linear/Notion updates
require explicit authorization after this local phase closes.
