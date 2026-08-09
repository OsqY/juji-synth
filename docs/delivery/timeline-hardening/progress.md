# Timeline Hardening — Progress

Updated: 2026-08-08. State: `CLOSE_PHASE` M22 complete; M23 next.

The required `.codex/tasks` location is read-only in this environment, so this
equivalent record lives under `docs/delivery/timeline-hardening/`.

Closed evidence: R1/R2/R3/R4 commits listed in `specification.md`; each passed
the four Gradle gates and independent review, with 14/16/18/19 device tests.

Gate status:

- DEFINE: complete — objective, boundaries, evidence, and acceptance recorded.
- ANALYZE: complete — callers, contracts, risks, persistence, and rollback recorded.
- PLAN: complete — M10–M15 increments and closure conditions recorded; M16
  remediation plan adds AC-R1–AC-R4 for the PR blockers.
- IMPLEMENT_PHASE: complete for the redesign — explicit auto-scroll Job,
  cancellation on Finish/Cancel/tool change/pinch/dispose, pointer-preserving
  preview, playback-follow guard, and deterministic 500% move/scroll/undo test.
- VALIDATE: unit tests, compile, lint, assemble, focused device test, and full
  device suite pass (20/20).
- REVIEW: PASS — `/root/m10_redesign_review`; no P0/P1 findings. P2 matrix
  coverage is assigned to M12.
- M11 IMPLEMENT_PHASE: complete — live zoom percentage, effective snap label,
  stable Delete button tag/state, and non-intercepting indicator coverage.
- M11 VALIDATE: four Gradle gates pass; focused indicator test and full device
  suite pass (21/21).
- M11 REVIEW: PASS — `/root/m11_review`; no P0-P3 findings.
- M12 IMPLEMENT_PHASE: complete — device matrix index, physical SM-G998W
  profile, measured size/density/orientation, screenshot, and explicit AVD
  gaps recorded.
- M12 VALIDATE: four Gradle gates and 21/21 device instrumentation pass.
- M12 REVIEW: PASS — `/root/m12_review`; no P0-P3 findings after evidence
  correction and follow-up review.
- M12 CLOSE_PHASE: complete — matrix artifacts committed; pending AVD profiles
  remain explicitly unvalidated.
- M13 IMPLEMENT_PHASE: complete — viewport clip filtering extracted and
  memoized, pattern lookup indexed by ID, and stable keys/virtualization kept.
- M13 VALIDATE: four Gradle gates, Android test APK build, focused performance
  unit test, and full 21/21 device instrumentation pass.
- M13 REVIEW: PASS — `/root/m13_review`; no P0-P3 findings. The attempted
  child-composable extraction that produced a DEX VerifyError was removed and
  the final diff was retested.
- M13 CLOSE_PHASE: complete — bounded composition evidence recorded; no FPS
  claim made without a frame profiler.
- M14 IMPLEMENT_PHASE: complete — technical documentation now describes the
  tick/viewport model, fixed 200-bar virtual extent, visible-range rendering,
  gesture ownership, atomic history, autosave, and constraints. Stale package
  paths in the historical backlog navigation map were corrected.
- M14 VALIDATE: `git diff --check`, all four Gradle gates, and connected device
  instrumentation pass; the physical SM-G998W suite reports 21/21.
- M14 REVIEW: initial P2 documentation findings were corrected; follow-up
  `/root/m14_review` PASS with no P0-P3 findings.
- M14 CLOSE_PHASE: complete — AC-C1 is met. See `phase-14-review.md`.
- M15 IMPLEMENT/FIX: security remediation complete — clip identity validation,
  canonical project/audio path containment, TimelineVM JNI boundary coverage,
  and active-project state consistency.
- M15 security commit: `229f144` (`fix(timeline): harden project paths and
  clip identity`).
- M15 VALIDATE: all four Gradle gates and the full `21/21` physical SM-G998W
  instrumentation suite pass. The required device-density matrix remains
  pending.
- M15 REVIEW: `/root/m15_review` final remediation review PASS with no P0-P3
  findings. See `phase-15-review.md`.
- M15 FINAL_AUDIT: blocked — AVD/density matrix and PR/Linear/Notion/merge
  actions lack explicit authorization. See
  `final-audit.md`.
- M16 DEFINE: complete — independent PR review found one P0 autosave failure,
  one P1 rename failure, and one P1 multi-drag viewport-preview failure; no new
  feature scope was added.
- M16 ANALYZE: complete — source/target project roots, save-result handling,
  rename behavior, captured drag IDs, tests, and rollback were traced.
- M16 PLAN: complete — M16-A audio lifecycle, M16-B multi-clip pinning, and
  M16-C revalidation are documented in `plan.md`.
- M16-A IMPLEMENT: complete — audio paths are normalized at import, timeline
  insertion, save, autosave, and rename boundaries; save failures are surfaced
  and cannot advance `last_project`.
- M16-A VALIDATE: four Gradle gates and `compileDebugAndroidTestKotlin` pass;
  `adb devices -l` found no attached device, so connected instrumentation was
  skipped honestly.
- M16-A REVIEW: PASS — `/root/m16a_review`; no remaining P0/P1 findings. See
  `phase-16a-review.md`.
- M16-B IMPLEMENT: complete — all captured move IDs are pinned during active
  preview, while unrelated clips remain viewport-virtualized.
- M16-B VALIDATE: four Gradle gates and `compileDebugAndroidTestKotlin` pass;
  `SM-G998W` manual instrumentation passes `24/24` after installing both APKs
  with `--no-streaming`. The Gradle connected task still hangs in its streaming
  installer on this device.
- M16-B REVIEW: PASS — `/root/m16b_review`; the lifecycle assertion P2 was
  resolved, the duplicate flaky Compose test was removed, and no P0/P1
  findings remain. See `phase-16b-review.md`.
- M16-C IMPLEMENT: complete — `final-audit.md`, `plan.md`, the specification,
  and the authoritative pending queue reflect M16-A/B and the real blockers.
- M16-C VALIDATE: `git diff --check`, all M16 gates, and manual `24/24`
  instrumentation on `SM-G998W` pass; the density matrix remains deferred.
- M16-C REVIEW: PASS — `/root/m16b_review`; local handoff records are
  consistent, no blocking findings, and external writes remain unauthorized.
- M16-C CLOSE_PHASE: complete locally. Overall final audit remains blocked only
  by the deferred device evidence and explicit PR/Linear/Notion/merge authority.

Protected state: `app/src/main/cpp/oboe` and `.commandcode/` remain excluded.

## Post-merge follow-up

- PR #1 is merged as `72fb813`; its reviewed head is `ad9c9a1`.
- Linear OSQ-5 is Done and the Notion task is Realizada; neither is reopened.
- `CoC-API35` (Android 15/API 35) passes `connectedDebugAndroidTest`, 24/24.
- `Pixel_8` API 37 fails in Espresso infrastructure because
  `InputManager.getInstance()` is unavailable; no Timeline assertion runs.
- M17 IMPLEMENT: post-merge queue, specification, plan, final audit, progress,
  and design contract now agree on the merged baseline and M17–M23 order.
- M17 VALIDATE: `testDebugUnitTest`, `compileDebugKotlin`, `lintDebug`,
  `assembleDebug`, and `compileDebugAndroidTestKotlin` PASS; API 35 connected
  instrumentation PASS, 24/24.
- M17 REVIEW: `/root/m17_review` PASS after correcting the stale no-streaming
  documentation reference; no P0–P3 findings remain.
- M17 CLOSE_PHASE: complete; M18 is next.

## M18 — Resize Compose coverage

- M18 IMPLEMENT: complete — added deterministic Compose coverage for successful
  left- and right-handle resize gestures on short PadClips. Each test verifies
  the musical edge invariant and one undo/redo restoration transaction.
- M18 VALIDATE: `testDebugUnitTest`, `compileDebugKotlin`, `lintDebug`,
  `assembleDebug`, and `compileDebugAndroidTestKotlin` PASS. `adb devices -l`
  found `emulator-5554` (CoC-API35, Android 15/API 35); the full
  `connectedDebugAndroidTest` suite passes 26/26, including both new resize
  tests. No production Timeline code changed.
- M18 REVIEW: PASS — `/root/m18_review`; the initial P2 test-integrity finding
  was resolved by using clip-relative, density-independent edge coordinates.
  Focused API 35 resize coverage is 2/2 and `git diff --check` passes. See
  `phase-18-review.md`.
- M18 CLOSE_PHASE: complete — AC-Q1 is covered; M19 is next.

## M19 — Atomic multi-delete Compose coverage

- M19 IMPLEMENT: complete — added a deterministic Delete-tool stroke over
  three clips, crossing one target twice while keeping a fourth clip on another
  row outside the stroke.
- M19 VALIDATE: `testDebugUnitTest`, `compileDebugKotlin`, `lintDebug`,
  `assembleDebug`, and `compileDebugAndroidTestKotlin` PASS. Focused API 35
  coverage is 1/1; the full `connectedDebugAndroidTest` suite is 27/27. The
  assertions require exactly three trash entries, preserve the unrelated clip,
  and restore/reapply all targets with one undo/redo. No production Timeline
  code changed.
- M19 REVIEW: PASS — `/root/m18_review`; the initial P1 coverage gap was
  resolved by adding the non-target clip, repeated crossing, exact trash-size
  assertions, and delete/undo/redo preservation checks. `git diff --check`
  passes; no P0–P3 findings remain. See `phase-19-review.md`.
- M19 CLOSE_PHASE: complete — AC-Q2 is covered; M20 is next.

## M20 — Audio metadata and path validation

- M20 IMPLEMENT: complete — `AudioClip` rejects negative offsets/fades,
  negative or non-finite gains; project loading rejects missing or
  project-external audio references before native loading.
- M20 VALIDATE: all four Gradle gates, `compileDebugAndroidTestKotlin`,
  focused external-path instrumentation (1/1), and API 35 connected
  instrumentation (28/28) pass. The final unit-test-only correction retry had
  no attached ADB device, so connected instrumentation was not rerun. `git
  diff --check` passes.
- M20 REVIEW: PASS — `/root/m20_followup` and `/root/m18_review`; the initial
  coverage findings for fade-out, positive infinity, and symlink escape were
  corrected. No P0–P3 findings remain. See `phase-20-review.md`.
- M20 SECURITY: PASS — canonical path containment, symlink escape rejection,
  metadata validation, and pre-native-load ordering reviewed; no secrets or
  protected files changed.
- M20 CLOSE_PHASE: complete — AC-S1 is covered; M21 is next.

## M21 — Safe export duration arithmetic

- M21 IMPLEMENT: complete — shared checked timing math protects `exportMix`
  and `exportStems` from clip-end, duration, denominator, and wait overflow;
  invalid tempos fail before native export.
- M21 VALIDATE: `testDebugUnitTest`, `compileDebugKotlin`, `lintDebug`,
  `assembleDebug`, `compileDebugAndroidTestKotlin`, and `git diff --check`
  pass. `adb devices -l` found no device, so connected instrumentation was
  skipped.
- M21 REVIEW: PASS — `/root/m20_followup`; no P0–P3 findings. See
  `phase-21-review.md`.
- M21 CLOSE_PHASE: complete — AC-S2 is covered; M22 is next.

## M22 — Explicit Follow Playhead control

- M22 IMPLEMENT: complete — Follow state is shared in `TimelineViewModel`,
  exposed in portrait/landscape controls, and all manual Timeline gestures
  disable it.
- M22 VALIDATE: `testDebugUnitTest`, `compileDebugKotlin`, `lintDebug`,
  `assembleDebug`, `compileDebugAndroidTestKotlin`, and `git diff --check`
  pass. `adb devices -l` found no device, so connected instrumentation was
  skipped.
- M22 REVIEW: PASS — `/root/m20_followup`; the initial P2 gesture coverage gap
  was corrected and no P0–P3 findings remain. See `phase-22-review.md`.
- M22 CLOSE_PHASE: complete — AC-F5 is covered; M23 is next.
