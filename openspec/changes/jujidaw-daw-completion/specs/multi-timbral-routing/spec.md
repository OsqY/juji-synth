# multi-timbral-routing Specification

## Purpose

Each DAW track carries its own `SynthState` preset (oscillator, filter, envelope, LFO, effects parameters). When a track is selected as the active instrument, the engine loads that track's preset via `SynthEngine.applySynthState()`. Notes from each track are routed through a MIDI channel to the corresponding `MixerChannel` (0–15), reusing the existing 16-channel mixer with zero new C++.

## ADDED Requirements

### Requirement: Per-track SynthState
Each track SHALL have an independent `SynthState` that defines its sound. The `SynthState` is stored in the project model and serialised in the project JSON.

#### Scenario: Track has its own sound
- **WHEN** the user switches between two tracks
- **THEN** the synth engine applies the selected track's `SynthState` via `SynthEngine.applySynthState()`
- **AND** the sound changes to match the selected track's preset

#### Scenario: New track gets default preset
- **WHEN** the user creates a new empty track
- **THEN** the track receives a default `SynthState` (initially the same as "Bright Lead" factory preset)
- **AND** the default is fully editable independently of other tracks

### Requirement: MIDI channel routing
Each track SHALL be assigned a MIDI channel (0–15) that maps to the corresponding `MixerChannel`. The assignment is stored per-track in the project.

#### Scenario: Note plays through mixer channel
- **WHEN** the transport scheduler plays a note with `trackIndex = 3`
- **THEN** the synth voice associated with that track is routed to `MixerChannel[3]`
- **AND** the channel's fader, pan, inserts and sends affect the note's output

#### Scenario: Channel assignment
- **WHEN** a track is created
- **THEN** it receives the lowest unassigned MIDI channel (0–15)
- **WHEN** a track is deleted
- **THEN** its channel is freed for reassignment

### Requirement: Note event carries trackIndex
`NoteEvent`, `ScheduledEvent`, and `PadTrigger` SHALL carry a `trackIndex` field so the engine knows which `MixerChannel` to route the voice through.

#### Scenario: Sequencer step targets a track
- **WHEN** the user edits step `(row=2, col=5)` in the sequencer
- **THEN** the resulting `NoteEvent` has `trackIndex = 2`
- **AND** the audio engine plays that note through `MixerChannel[2]`

#### Scenario: Piano-roll note target
- **WHEN** the user draws a note in piano-roll mode on track 3
- **THEN** the note's `trackIndex` is 3

### Requirement: Single engine, shared voices
All tracks share one `SynthInstrument` and one `SamplerInstrument`. Per-track sound switching is done by swapping `SynthState` on track focus, not by instantiating new synths. Voice stealing is global.

#### Scenario: Many tracks, limited voices
- **WHEN** the user plays a chord on track 0 and another chord on track 1
- **THEN** all voices come from the same voice pool (MAX_VOICES = 8)
- **AND** if the pool is exhausted, the oldest voice is stolen regardless of which track owns it

### Requirement: Edge case — two tracks on same MIDI channel
When two tracks share the same MIDI channel (user reassignment), their notes are mixed on the same `MixerChannel`. The UI SHALL warn when a collision occurs.

#### Scenario: Channel collision warning
- **WHEN** the user assigns a MIDI channel that is already used by another track
- **THEN** a toast or badge warns "MIDI channel collision: tracks X and Y share channel N"
- **AND** audio still plays (both tracks feed the same mixer channel)

### Requirement: Edge case — empty track is silent
A track with no pattern, clip, or notes loaded SHALL produce silence. Its mixer channel processes no audio.

#### Scenario: Empty track
- **WHEN** a track has no clips or patterns
- **THEN** its `MixerChannel` produces silence (no instrument, no voice allocated)
- **AND** the channel strip still shows its fader, pan, and insert state (for future use)
