# Spec: FL-Style Step Sequencer

## Requirements

- **R1** Each sequencer row SHALL map 1:1 to a pad in bank A (row R =
  pad R, R in 0..15).
- **R2** Toggling a step on row R at step S SHALL schedule a `PAD_TRIGGER`
  event for `padIndex=R` at the tick corresponding to step S in the
  transport pattern — NOT a `note=60` on channel 0.
- **R3** When a step fires, the pad's loaded content (sample OR synth) SHALL
  play. The step grid SHALL NOT carry a hardcoded MIDI note; it carries
  `padIndex`.
- **R4** `SequencerViewModel.syncActivePatternToTransport` SHALL export a
  pattern whose `NoteEvent`s carry `padIndex` and use
  `schedulePadTrigger`, not `scheduleNoteOn`.
- **R5** The legacy C++ internal `Sequencer::process` note path SHALL be
  removed or gated off (the DAW transport is authoritative; legacy
  `SequencerView` is retired or routed through the same pad-trigger path).
- **R6** Row labels in the sequencer UI SHALL show the pad index/name and its
  loaded content type (sample name or synth preset name).

## Scenarios

### S1: Kick on row 1, step 1

- **Given** pad 1 (row 1) has a loaded kick sample
- **When** the user enables step 1 on row 1 and presses Play
- **Then** at step 1 the transport fires `PAD_TRIGGER(padIndex=1)`
- **And** the kick is heard.

### S2: Synth pad row

- **Given** pad 5 is in synth mode with a preset whose root note is C3
- **When** the user enables step 3 on row 5 and presses Play
- **Then** pad 5's `SynthInstrument` plays C3 at step 3.

### S3: Multiple rows fire independently

- **Given** pad 1 = kick, pad 2 = snare, both with steps on different cells
- **When** the sequence plays
- **Then** kick and snare each play on their own steps (multi-timbral).
