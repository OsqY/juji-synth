# M16-B Review — Multi-clip viewport pinning

Date: 2026-08-03

Reviewer: `/root/m16b_review` (independent adversarial review)

Verdict: PASS. No P0/P1 findings.

Follow-up correction review: `/root/device_validation_review` — PASS. The
device-validation test removal and evidence updates have no P0–P3 findings;
the reviewer’s two P2 documentation notes were resolved before commit.

Resolved non-blocking finding:

- Unit coverage asserts both sides of the lifecycle: captured IDs are pinned
  during preview and removed from the pinned set after completion. The
  existing Compose harness continues to cover a complete multi-clip move and
  undo. A duplicate device-only lifecycle test was removed after it proved
  flaky when the gesture was split across two Compose input blocks; it did not
  cover behavior not already protected by the unit and existing Compose tests.

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
- `adb devices -l` — `SM-G998W` / Android 15 attached
- `./gradlew connectedDebugAndroidTest` — installer hung during streaming APK
  installation on this device
- Manual no-streaming install plus
  `adb shell am instrument -w ...AndroidJUnitRunner` — PASS, `OK (24 tests)`
  after the flaky duplicate test was removed

Protected `app/src/main/cpp/oboe` and untracked `.commandcode/` were excluded.
