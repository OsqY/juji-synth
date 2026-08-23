# Delivery Plan

1. Make the shell and Timeline content-first: compact destination dock, one
   transport, collapsed secondary Timeline controls, icon-led editing modes.
2. Recompose Mixer around full-height channel strips and remove empty/decorative
   space without changing mixer state or gestures.
3. Flatten Synth device chrome and preserve signal-flow grouping with fewer
   repeated titles and borders.
4. Refine Pads and Keys as performance surfaces: quieter labels, stronger state,
   and unchanged large touch targets.
5. Refine Sequencer, Project, Help, and Settings around their primary tasks and
   explicit destructive-action affordances.
6. Validate portrait/landscape adaptation, capture all destinations again,
   resolve independent review findings, and run the final acceptance audit.

Each phase is independently testable and reversible. No new dependency or
feature is introduced.

## Phase 2 — Mixer

- Objective: make full-height channel strips the visual subject and remove the
  unused area below intrinsic-height strips.
- Files: `MixerScreen.kt`, one connected Compose layout test, and phase records.
- Acceptance: `AC-D4`, `AC-D5`, `AC-Q1`, `AC-Q2`, `AC-A1`, and `AC-S1`.
- Constraints: preserve every ViewModel mutation, fader/knob gesture path,
  insert sheet, automation sheet, and MIDI-learn route.
- Validation: required Gradle gates, focused connected Mixer test, full
  connected suite, and independent adversarial review.
- Closure: strips fill the viewport in portrait, landscape remains scrollable,
  secondary Perform FX stays available but collapsed, and no blocking review
  finding remains.

## Phase 3 — Synth

- Objective: remove nested hardware-chassis chrome and make the ordered device
  sections the visual subject.
- Files: `SynthScreen.kt`, shared `SynthPanel.kt`, removal of the now-unused
  `HardwareChassis.kt`, one connected Compose layout test, and phase records.
- Acceptance: `AC-D4`, `AC-D5`, `AC-Q1`, `AC-Q2`, `AC-A1`, and `AC-S1`.
- Constraints: preserve every SynthViewModel, engine parameter, MIDI-learn,
  preset, track, and pad-source route.
- Validation: required Gradle gates, focused connected Synth tests, full
  connected suite, device capture, and independent adversarial review.
- Closure: each device section has one title and one flat boundary, the target
  chooser is revealable instead of permanently dominant, top actions are at
  least 44dp, and no blocking review finding remains.

## Phase 4 — Pads and Keys

- Objective: make finger-drumming and note performance visually dominant while
  keeping edit, scale, repeat, and arpeggiator controls quickly revealable.
- Files: `PadsScreen.kt`, `KeyboardScreen.kt`, one connected Compose layout
  test, and phase records.
- Acceptance: `AC-D4`, `AC-D5`, `AC-Q1`, `AC-Q2`, `AC-A1`, and `AC-S1`.
- Constraints: preserve velocity/aftertouch gestures, multi-touch note release,
  pad selection, target routing, bank switching, edit dialogs, and all engine
  callbacks.
- Validation: required Gradle gates, focused connected performance-surface
  tests, full connected suite, device captures, and independent review.
- Closure: edit/advanced controls are collapsed by default, essential context
  remains in one 44dp toolbar, pad/key identities are concise and accessible,
  and no blocking review finding remains.

## Phase 5 — Sequencer, Project, Help, and Settings

- Objective: make pattern editing and project browsing primary while keeping
  secondary workflow, export, recording, and diagnostic actions revealable.
- Files: `SequencerScreen.kt`, `ProjectScreen.kt`, `HelpScreen.kt`,
  `SettingsScreen.kt`, one connected Compose layout test, and phase records.
- Acceptance: `AC-D3`, `AC-D4`, `AC-D5`, `AC-Q1`, `AC-Q2`, `AC-A1`, and
  `AC-S1`.
- Constraints: preserve pattern editing, note gestures, project file actions,
  export launchers, recording routes, dialogs, and settings persistence; keep
  destructive actions explicit.
- Validation: required Gradle gates, focused connected task-surface tests, full
  connected suite, device captures, and independent review.
- Closure: no destination duplicates global transport, each primary surface
  occupies at least 70% of its content height, secondary controls are
  revealable, all touched actions are at least 44dp, and no blocking review
  finding remains.

## Phase 6 — Adaptive validation and final audit

- Objective: verify the completed redesign as one system in portrait and
  landscape, capture every destination, and close every acceptance criterion.
- Files: one connected shell regression and final delivery records only unless
  validation exposes a concrete UI defect.
- Acceptance: `AC-D1` through `AC-D5`, `AC-Q1`, `AC-Q2`, `AC-A1`, `AC-P1`, and
  `AC-S1`.
- Constraints: do not broaden into new features or repair unrelated dirty
  audio/project work; preserve Oboe and all user-owned files.
- Validation: all-destination landscape regression, required Gradle gates, full
  connected suite, seven final portrait captures, and independent final audit.
- Closure: all seven destinations fit in landscape with one global transport,
  final portrait captures match the content-first direction, residual failures
  are classified with evidence, and no blocking review finding remains.
