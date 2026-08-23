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

## Phase 2 — Mixer

- State: PASS; ready for focused commit
- Acceptance: `AC-D4`, `AC-D5`, `AC-Q1`, `AC-Q2`, `AC-A1`, `AC-S1`
- Root cause: the strip row uses intrinsic-height children inside a weighted
  viewport, leaving the remaining screen visually inactive.
- Decision: retain the existing controls and state wiring, make strips fill the
  viewport, give spare height to faders, and move the collapsed Perform FX
  affordance into the compact Mixer toolbar.
- Implementation: full-height flat channel/master strips, flexible fader travel,
  selected-channel/master readouts, one full-width insert affordance per strip,
  passive MIDI-learn status, and 44dp actions.
- Focused connected Mixer regressions: PASS, 4/4 on SM-G998W / Android 15,
  covering portrait fill, landscape scroll reachability, fader/pan gestures,
  Perform FX, inserts, and automation access.
- `./gradlew testDebugUnitTest compileDebugKotlin lintDebug assembleDebug`: PASS.
- Landscape gesture reproduction: PASS, 1/1 with the physical device forced to
  landscape; the test gesture stays inside the knob and clear of system edges.
- Full connected suite: 42/43. Every UI test passed; the sole failure remains
  the pre-existing user-owned
  `ProjectRepositoryAudioTest.padAssetsStayProjectRelativeAcrossSaveLoadAndRename`
  filename expectation.
- Device capture: `/tmp/juji-ui-phase2-mixer.png`; channel strips fill the
  workspace and about two-and-a-half strips remain visible at phone width.
- Initial independent review: `task_d002a0e269ba` / `ctx_48902bb5c1e0`, verdict
  FAIL. Its actionable finding was missing landscape, gesture, insert, and
  automation regression coverage. The claimed missing `assertDoesNotExist`
  import was disproved by successful Android-test compilation and execution.
- Correction: expanded the focused suite to four tests, used the scroll
  semantics action to prove bottom-control reachability, and kept knob input
  away from Android's edge-navigation zone.
- Independent correction review: `task_dbcdd34bec46` / `ctx_b43119b7045f`,
  verdict FAIL. The reviewer confirmed the earlier coverage gap was closed and
  found one remaining accessibility issue: the short `FX` toolbar action could
  measure narrower than 44dp.
- Correction: the shared toolbar action now enforces a 44dp minimum width, and
  the focused device regression verifies both `FX` and `AUTO` are at least
  44x44dp. Focused Mixer suite 4/4 and all required Gradle gates pass after the
  correction.
- Independent closure review: `task_339c2d574819` / `ctx_9d94bacdee39`, verdict
  PASS with zero blocking findings. See `phase-02-review.md`.

## Phase 3 — Synth

- State: PASS; ready for focused commit
- Acceptance: `AC-D4`, `AC-D5`, `AC-Q1`, `AC-Q2`, `AC-A1`, `AC-S1`
- Root cause: `SynthScreen` wraps panel composables that already render their
  own titled panel, producing duplicate titles, nested borders, decorative
  screws, and excess vertical travel before controls.
- Decision: reuse each existing device panel directly, flatten the shared panel
  surface, collapse track/pad target selection behind the current target, and
  move MIDI/preset/save/panic actions to a compact top toolbar.
- Implementation: removed the redundant chassis and outer panel wrappers,
  including the now-unreferenced `HardwareChassis` implementation;
  reduced the shared panel to one neutral boundary with a signal marker; added
  a collapsed `T01 · GLOBAL` target chooser; moved MIDI, library, save, and panic
  into the top 44dp toolbar; preserved all existing callbacks and parameter
  panels.
- Test-first baseline: focused Synth tests initially failed on duplicate
  `FILTER` titles and the absent target toggle, then passed after implementation.
- Focused connected Synth suite: PASS, 2/2 on SM-G998W / Android 15.
- `./gradlew testDebugUnitTest compileDebugKotlin lintDebug assembleDebug`: PASS.
- Full connected suite: 43/45. All Synth and shared Sequencer UI tests passed.
  The known user-owned project-audio filename test still fails; the previously
  committed Mixer insert-sheet animation assertion also failed only in suite
  order and passed immediately in isolated rerun, 1/1. No Phase 3 code is on
  either path.
- Device capture: `/tmp/juji-ui-phase3-synth.png`; the first viewport now shows
  Oscillators, Filter, and the start of Envelopes instead of nested chrome around
  barely one device section.
- Independent review: `task_1b4b7418bf52` / `ctx_3cf6f782d815`, verdict PASS
  with zero blocking findings. See `phase-03-review.md`.

## Phase 4 — Pads and Keys

- State: PASS; ready for focused commit
- Acceptance: `AC-D4`, `AC-D5`, `AC-Q1`, `AC-Q2`, `AC-A1`, `AC-S1`
- Root cause: Pads permanently spends toolbar width on five editing actions,
  while Keys permanently spends a second row on ten abbreviated settings; the
  playing surfaces are strong but their edit chrome reads as generic controls.
- Decision: keep bank/target/octave/view context in one compact toolbar and
  reveal editing or advanced performance controls only on demand. Preserve
  direct pad/key gestures and expose concise identities through semantics.
- Implementation: Pads now keeps only A/B, the concise current target (`A01`),
  and a tools toggle above the grid; import/chop/stretch/synth/edit live in the
  revealable row. Default pad names render as `1–16` while custom names remain.
  Keys now keeps target, octave, grid/piano mode, and a controls toggle in one
  row; scale, velocity, aftertouch, repeat, and arp controls are collapsed.
- Accessibility: banks and view modes expose selection semantics; every toolbar
  and revealed control is at least 44dp; pad and key cells expose full spoken
  identities plus TalkBack play actions without altering direct multi-touch
  gesture handling.
- Test-first baseline: both focused tests failed on absent content-first roots
  and toggles, then passed after implementation.
- Focused connected performance suite: PASS, 2/2 on SM-G998W / Android 15.
- `./gradlew testDebugUnitTest compileDebugKotlin lintDebug assembleDebug`: PASS.
- Full connected suite: 46/47. Every UI test passed; the only failure remains
  the pre-existing user-owned
  `ProjectRepositoryAudioTest.padAssetsStayProjectRelativeAcrossSaveLoadAndRename`
  filename expectation.
- Device captures: `/tmp/juji-ui-phase4-pads.png` and
  `/tmp/juji-ui-phase4-keys.png`; both playing surfaces now begin immediately
  below one compact toolbar.
- Independent review: `task_c82055da9bb3` / `ctx_61fe4f452614`, verdict PASS
  with zero blocking findings. See `phase-04-review.md`.
