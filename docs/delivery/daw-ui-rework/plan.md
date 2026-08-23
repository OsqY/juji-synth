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
