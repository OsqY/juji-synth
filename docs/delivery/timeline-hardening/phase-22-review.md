# M22 — Explicit Follow Playhead control

## Scope and acceptance

AC-F5 requires manual Timeline interaction to disable playhead following and an
explicit control to re-enable it.

## Implementation

- Follow state lives in `TimelineViewModel` and is shared by portrait and
  landscape controls.
- Scroll, pinch/zoom, marquee selection, clip tap/move/resize, scrub, draw,
  and Delete gestures disable following.
- `timeline-follow-playhead` toggles the state without consuming Timeline
  viewport gestures.
- Compose coverage verifies initial visibility, manual scroll disable/control
  re-enable, and draw/Delete disable behavior.

## Validation

- `./gradlew testDebugUnitTest` — PASS.
- `./gradlew compileDebugKotlin` — PASS.
- `./gradlew lintDebug` — PASS.
- `./gradlew assembleDebug` — PASS.
- `./gradlew compileDebugAndroidTestKotlin` — PASS.
- `git diff --check` — PASS.
- `adb devices -l` — no attached device; connected instrumentation not run.

## Independent review

`/root/m20_followup` initially identified incomplete gesture coverage (P2).
After adding disable calls for marquee, resize, draw, Delete, and clip taps,
the follow-up review returned PASS with no P0–P3 findings. Oboe and
`.commandcode` remain outside the diff.

## Closure

M22 is complete. M23 is next: final audit and authorized external handoff.
