# M13 review — viewport performance and recomposition

Reviewer: `/root/m13_review` (independent context)
Date: 2026-07-30
Verdict: **PASS**

## Scope reviewed

- `app/src/main/java/com/jujidaw/ui/timeline/TimelineEditingMath.kt`
- `app/src/main/java/com/jujidaw/ui/timeline/TimelineScreen.kt`
- `app/src/test/java/com/jujidaw/ui/timeline/TimelineViewportPerformanceTest.kt`

## Implementation

- Extracted the viewport clip predicate into `timelineVisibleClips`.
- Memoized the visible clip result by arrangement, visible tick bounds, and
  active drag/resize target.
- Memoized the transform and visible tick range for unchanged viewport inputs.
- Replaced per-clip pattern list scans with a remembered ID map.
- Preserved stable `key(clip.id)` usage and the existing viewport-sized render
  layer; no full-width layout was introduced.

## Before/after evidence

The deterministic test creates 4,000 clips across a 200-bar timeline at 500%
zoom and a 720 px viewport:

| Metric | Before M13 | After M13 | Method |
| --- | --- | --- | --- |
| Clips emitted for this viewport | 8 expected by the existing predicate | 8 | `TimelineViewportPerformanceTest` exact assertion |
| Clip candidates visited on transport-only recomposition | Filter executed in `TimelineScreen` | Memoized result reused | Compose key inspection |
| Pattern lookup per emitted clip | Linear `patterns.find` | One `Map` lookup | Source-level operation count |

This phase makes no FPS or frame-time claim; the evidence verifies bounded
composition and eliminated repeated work. A frame profiler remains a later
optimization if device traces show a remaining bottleneck.

## Validation

- `./gradlew testDebugUnitTest`: PASS.
- `./gradlew compileDebugKotlin`: PASS.
- `./gradlew lintDebug`: PASS.
- `./gradlew assembleDebug`: PASS.
- `./gradlew assembleDebugAndroidTest`: PASS.
- Physical SM-G998W, Android 15: APKs installed with `--no-streaming`; full
  Compose suite `OK (21 tests)`.
- A prior extraction of playhead/transport into additional composables caused
  a DEX `VerifyError`; it was removed before this reviewed diff and the focused
  plus full device suites were rerun successfully.

## Findings and closure

The independent review found no P0, P1, P2, or P3 findings. Oboe remains an
unrelated dirty submodule and `.commandcode/` remains excluded. M13 is closed;
M14 is the next phase for technical documentation.
