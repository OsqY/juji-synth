# Timeline Hardening — Progress

Updated: 2026-08-02. State: `CLOSE_PHASE` M16-C local handoff next; M15 remains
blocked on device matrix and external authorization.

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
  `adb devices -l` found no attached device, so connected instrumentation was
  skipped honestly.
- M16-B REVIEW: PASS — `/root/m16b_review`; the lifecycle assertion P2 was
  resolved and no P0/P1 findings remain. See `phase-16b-review.md`.
- M16-C IMPLEMENT/VALIDATE/REVIEW: pending — update local final-audit records
  only; external PR/Linear/Notion actions still require explicit authorization.

Protected state: `app/src/main/cpp/oboe` and `.commandcode/` remain excluded.
