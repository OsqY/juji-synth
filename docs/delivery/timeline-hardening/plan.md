# Timeline Hardening — Delivery Plan

Every phase follows `DEFINE → ANALYZE → PLAN → IMPLEMENT_PHASE → VALIDATE →
REVIEW → CLOSE_PHASE`. Four Gradle gates and independent review are required.

## Closed prerequisites

R1 `e1c2f71`, R2 `8f25d30`, R3 `5d93af4`, and R4 `1c7e6e2` are closed.

## Follow-up phases

| Phase | Objective | Commit | Closure |
| --- | --- | --- | --- |
| M17 | Record merged baseline and replace the stale execution queue | `docs(timeline): record post-merge follow-up baseline` | Docs agree on merge, external state, API 35 evidence, and deferred M12 |
| M18 | Cover successful resize from both handles with one undo/redo transaction | `test(timeline): cover committed resize gestures` | AC-Q1, gates, API 35, review PASS |
| M19 | Cover a multi-clip Delete stroke as one history transaction | `test(timeline): cover atomic multi-delete gestures` | AC-Q2, gates, API 35, review PASS |
| M20 | Reject invalid audio metadata and project-external audio references | `fix(project): validate loaded audio clip data` | AC-S1, focused tests, gates, security review PASS |
| M21 | Prevent clip-end and export-duration arithmetic overflow | `fix(project): prevent timeline export arithmetic overflow` | AC-S2, focused tests, gates, security review PASS |
| M22 | Provide an explicit Follow Playhead control | `feat(timeline): add explicit playhead follow control` | AC-F5, Compose coverage, gates, API 35, review PASS |
| M23 | Run the final audit and prepare the authorized external handoff | `docs(timeline): close follow-up hardening audit` | AC-O2, zero blocking findings, evidence current |

Current phase: M17 on `feat/timeline-followup-hardening`, created from merge
commit `72fb813`. The original PR #1, OSQ-5, and Notion handoff are closed.
M12 remains explicitly deferred rather than passed. M18–M23 are new follow-up
work and do not reopen the completed M0–M16 implementation.

## M16 — PR remediation plan

The PR review found two audio persistence blockers and one viewport-preview
blocker. This phase does not add Timeline features or change Oboe.

### M16-A — Audio path lifecycle and save results

Objective: keep `AudioClip.audioFilePath` relative to its project and never
report a failed save as successful.

Files/components:

- `ProjectRepository.kt`: resolve source files from the active/source project,
  copy them into the target project at normalized relative paths (imports use
  `samples/`), and serialize those paths; normalize paths before/while rename.
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
- `TimelineViewportPerformanceTest.kt` and `TimelineComposeHarnessTest.kt`:
  cover multiple pinned clips and a captured multi-selection during edge
  movement.

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
./gradlew compileDebugAndroidTestKotlin
```

Run `adb devices -l` first. If a device is attached, run
`./gradlew connectedDebugAndroidTest`; otherwise record the skipped result and
the prior SM-G998W evidence honestly. Request an independent adversarial review
of the M16 diff. Do not merge while any P0/P1 finding, failed gate, unresolved
review thread, or missing handoff remains. External PR/Linear/Notion updates
require explicit authorization after this local phase closes.
