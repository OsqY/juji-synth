# M21 — Safe export duration arithmetic

## Scope and acceptance

AC-S2 requires clip-end and export-duration calculations to reject overflow or
invalid values before native export starts.

## Implementation

- `calculateProjectExportTiming()` is the single checked arithmetic path for
  `exportMix` and `exportStems`.
- Clip ends, denominator, sample duration, millisecond duration, and the final
  wait use checked `Long` arithmetic.
- Non-finite, non-positive, or too-small tempos and invalid sample rates fail
  with `Result.failure` before `SynthEngine.startOfflineRender*` is called.
- Unit tests cover clip-end overflow, duration multiplication overflow, normal
  timing, and invalid tempos.

## Validation

- `./gradlew testDebugUnitTest` — PASS.
- `./gradlew compileDebugKotlin` — PASS.
- `./gradlew lintDebug` — PASS.
- `./gradlew assembleDebug` — PASS.
- `./gradlew compileDebugAndroidTestKotlin` — PASS.
- `git diff --check` — PASS.
- `adb devices -l` — no attached device; connected instrumentation not run.

## Independent review

`/root/m20_followup` reviewed the implementation and returned PASS with no
P0–P3 findings. The review confirmed both export paths validate timing before
native calls, checked arithmetic covers overflow, and Oboe/`.commandcode` stay
outside the diff.

## Closure

M21 is complete. M22 is next: add an explicit Follow Playhead control.
