# M14 review — viewport-based technical documentation

Reviewer: `/root/m14_review` (independent context)
Date: 2026-07-30
Verdict: **PASS**

## Scope reviewed

- `docs/pads-timeline-workflow.md`
- `DESIGN.md`
- `docs/backlog.md` navigation paths

## Implementation

- Added the current tick-to-viewport rendering model and the required state →
  visible tick range → grid/ruler/clips/playhead diagram.
- Documented `TimelineTransform`, visible-range clip filtering, pinned active
  edits, anchored zoom, scroll/follow behavior, gesture ownership, atomic
  history, debounced autosave, and known constraints.
- Corrected the onboarding path map to the current `com.jujidaw` packages while
  keeping `docs/backlog.md` explicitly historical/non-authoritative.
- Updated `DESIGN.md` so its phase status and limitations match the closed
  M10–M13 evidence.

## Review corrections

The first review reported two P2 documentation inaccuracies:

1. `maxScroll()` was described as deriving its bound from arrangement duration;
   the documentation now records the fixed 200-bar virtual extent supplied by
   `TimelineScreen`.
2. Audio paths were described as project-relative only; the documentation now
   records that relative paths are resolved by autosave, absolute paths are
   currently accepted, and validation is deferred to M15 security review.

The follow-up review found no P0, P1, P2, or P3 findings.

## Validation

- `git diff --check`: PASS.
- `./gradlew testDebugUnitTest`: PASS.
- `./gradlew compileDebugKotlin`: PASS.
- `./gradlew lintDebug`: PASS.
- `./gradlew assembleDebug`: PASS.
- `adb devices -l`: SM-G998W connected.
- `./gradlew connectedDebugAndroidTest`: PASS, `21/21` tests on SM-G998W,
  Android 15.

The Android checks are regression evidence; M14 changes documentation only and
do not alter the APK.

## Closure

AC-C1 is met. Oboe and `.commandcode/` remain outside the staged scope. M14 is
closed; M15 is the final audit and explicitly authorized external handoff.
