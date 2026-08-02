# M16-B Review — Multi-clip viewport pinning

Date: 2026-08-02

Reviewer: `/root/m16b_review` (independent adversarial review)

Verdict: PASS. No P0/P1 findings.

Resolved non-blocking finding:

- The Compose harness now asserts both sides of the lifecycle: an offscreen
  captured clip is present while the move is held and virtualized again after
  `up()` completes the gesture.

Implementation evidence:

- `timelineVisibleClips` accepts a set of pinned IDs, preserving unrelated
  offscreen clips outside the active gesture filter.
- `TimelineScreen` derives the set from all captured move IDs plus the active
  resize ID and clears it with the existing finish/cancel state cleanup.
- Unit and Compose tests cover multi-ID pinning and unpinning after completion.

Validation:

- `./gradlew testDebugUnitTest` — PASS
- `./gradlew compileDebugKotlin` — PASS
- `./gradlew lintDebug` — PASS
- `./gradlew assembleDebug` — PASS
- `./gradlew compileDebugAndroidTestKotlin` — PASS
- `adb devices -l` — no devices attached; connected instrumentation skipped

Protected `app/src/main/cpp/oboe` and untracked `.commandcode/` were excluded.
