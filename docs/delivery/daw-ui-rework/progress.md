# Progress

## Baseline

- State: Phase 1 review PASS; ready for focused commit
- Device: physical SM-G998W, 1080x2400 override, connected
- Current build: `assembleDebug` PASS
- Captures: seven primary destinations saved under `/tmp/juji-ui-*.png`
- Protected Oboe submodule: dirty before this work; excluded
- Other dirty files: user-owned; excluded

## Research decisions

- Keep one persistent Control Bar because Ableton groups transport and tempo in
  a single global surface.
- Make secondary controls revealable because Ableton lets users show/hide mixer
  and track controls rather than permanently consuming canvas space.
- Keep the arrangement and performance grids visually dominant.
- Use adaptive portrait/landscape structure rather than fixed phone-only sizing.

## Current phase

Phase 1 acceptance: `AC-D1`, `AC-D2`, `AC-D3`, `AC-A1`, `AC-P1`, and `AC-S1`.

### Implementation

- `MainScreen.kt`: compact custom destination dock/rail, one active label,
  slimmer global transport, and explicit transport accessibility descriptions.
- `TimelineScreen.kt`: advanced controls collapsed by default, icon-led editing
  and visibility controls, selected semantics, and 44dp targets in both
  orientations.
- `TimelineComposeHarnessTest.kt`: initial content-priority regression and
  explicit expansion for tests that use advanced controls, plus icon target
  bounds coverage.
- `MainScreenLayoutTest.kt`: landscape shell regression for collapsed inline
  controls and a single global transport.

### Validation

- `./gradlew testDebugUnitTest compileDebugKotlin lintDebug assembleDebug`: PASS
- Focused correction regressions: PASS, 2/2 on SM-G998W / Android 15
- Full connected suite: 38/39 passed. Every UI test passed; the only failure is
  the pre-existing dirty
  `ProjectRepositoryAudioTest.padAssetsStayProjectRelativeAcrossSaveLoadAndRename`
  expectation (`pad_0_imported...` vs `pad_0_pad_0_imported...`), outside this
  phase and left untouched.
- Device accessibility dump exposes names for all new icon-only controls,
  destinations, position/reset, tempo editor, and record state.
- Device capture: `/tmp/juji-ui-phase1-timeline.png`; arrangement begins around
  y=454 on the 1080x2400 profile, versus y=626 in the baseline capture.
- `git diff --check`: PASS; protected Oboe remains dirty and untouched.

### Review gate

- FUSE was enabled on the host with `pkexec modprobe fuse`; the running Orca
  AppImage's bundled CLI restored orchestration access.
- Initial independent review: `task_ec19de785622` / `ctx_2ff31efe1922`, Codex
  `gpt-5.6-terra` medium, verdict FAIL. It found exposed landscape advanced
  controls and 36/40dp icon targets.
- Corrections: reused the existing collapsed inline Timeline controls in
  landscape, removed the separate landscape controls surface, applied
  `TouchTargetMin` to Undo/Redo/Zoom, and added device regressions.
- Independent re-review: `task_e526dd8bdcd7` / `ctx_e3dcff81719b`, verdict PASS
  with zero blocking findings. See `phase-01-review.md`.
