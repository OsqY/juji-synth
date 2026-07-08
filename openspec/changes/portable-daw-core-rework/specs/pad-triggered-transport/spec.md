# Spec: Pad-Triggered Transport

## Requirements

- **R1** `PatternClip` and `NoteEvent` (`PatternModel.kt`) SHALL carry a
  `padIndex: Int` (0..15 for bank A) representing the pad to trigger, in
  addition to the existing `note`/`transpose` fields for backward compat.
- **R2** `TransportController` SHALL expose `schedulePadTrigger(padIndex,
  tick, velocity)` which calls JNI `nativeSchedulePadTrigger` →
  `ScheduledEvent::makePadTrigger(padIndex, sample)` (the event type already
  exists in `ScheduledEvent.h` / `Transport.cpp`).
- **R3** `Transport::firePendingEvents` PAD_TRIGGER branch SHALL call
  `sampler->triggerPad(padIndex, velocity)` AND, when the pad is in synth
  mode, delegate to that pad's owned `SynthInstrument` (see
  `multi-timbral-synth-per-pad`).
- **R4** When the timeline places a clip derived from a pad, the clip SHALL
  carry that pad's `padIndex`; on Play, the transport fires the pad at the
  clip's start tick.
- **R5** `schedulePatternNotes` SHALL NOT route pad clips through
  `scheduleNoteOn(trackIndex=0)`; pad clips SHALL use `schedulePadTrigger`.
  Legacy patterns without `padIndex` are migrated (default `padIndex = note %
  16`).
- **R6** The user SHALL be able to place a pad (with a loaded sample or synth)
  on the timeline and hear it on Play.

## Scenarios

### S1: Pad clip on timeline plays

- **Given** pad 1 has a loaded kick sample
- **When** the user places pad 1 on the timeline at tick T and presses Play
- **Then** at tick T the transport fires `PAD_TRIGGER` for `padIndex=1`
- **And** the kick sample is heard.

### S2: Legacy pattern migration

- **Given** a saved pattern with `NoteEvent(note=60, trackIndex=0)` and no
  `padIndex`
- **When** the project is loaded
- **Then** the note is migrated to `NoteEvent(padIndex = 60 % 16 = 12,
  note=60)` so it triggers pad 12 on play.

### S3: Synth pad clip plays

- **Given** pad 3 is in synth mode with a preset
- **When** a clip carrying `padIndex=3` is placed on the timeline and Play
  is pressed
- **Then** pad 3's owned `SynthInstrument` plays the synth root note.
