# automation-system Specification

## Purpose
TBD - created by archiving change jujidaw-daw-completion. Update Purpose after archive.
## Requirements
### Requirement: Automation event handler
The C++ transport automation handler SHALL route scheduled `AUTOMATION` events to the correct destination: `paramIndex` values 0–38 map to the corresponding `SynthParams` field (identical order to `SynthInstrument::setAllParamsFromArray`); values 39–45 map to per-channel mixer commands (`MixerCommandType::SetFader`, `SetPan`, `SetMute`, `SetSolo`, `SetArm`, `SetSendA`, `SetSendB`). Values outside this range SHALL be silently ignored.

#### Scenario: Automation reaches the synth
- **WHEN** the transport scheduler fires an AUTOMATION event with `paramIndex` in 0–38
- **THEN** the corresponding `SynthParams` field is updated before the next audio buffer
- **AND** the update occurs on the audio thread via the existing `swapParamsIfNeeded` mechanism

#### Scenario: Automation reaches the mixer
- **WHEN** the transport scheduler fires an AUTOMATION event with `paramIndex` in 39–54
- **THEN** a `MixerCommand` is pushed to the mixer command queue for the target channel
- **AND** the channel responds in the next `processAudio` callback

#### Scenario: Unknown param
- **WHEN** an AUTOMATION event carries an unrecognised `paramIndex`
- **THEN** the event is silently dropped

### Requirement: Automation playback
Automation points SHALL be played back at their scheduled sample position. The transport scheduler (`TransportController`) SHALL convert automation events into scheduled `AUTOMATION` entries in the C++ `EventQueue` with the correct `paramIndex` and value.

#### Scenario: Automation plays back
- **WHEN** the transport reaches the sample position of a stored automation point
- **THEN** the parameter changes to the point's value at that exact sample

#### Scenario: Linear interpolation
- **WHEN** two automation points exist on the same parameter
- **THEN** the value between them is linearly interpolated per-sample
- **AND** this interpolation happens in the C++ automation handler

### Requirement: Manual draw mode
The user SHALL be able to add, remove, and drag automation points on a per-parameter lane in `SequencerScreen` (piano-roll automation overlay) and `MixerScreen` (per-channel automation sheet).

#### Scenario: Add automation point
- **WHEN** the user taps on an automation lane
- **THEN** a new automation point is created at that position and value
- **AND** existing points before/after are unchanged

#### Scenario: Remove automation point
- **WHEN** the user long-presses an existing automation point
- **THEN** a delete affordance appears
- **AND** after confirmation the point is removed

#### Scenario: Drag automation point
- **WHEN** the user drags an existing automation point
- **THEN** the point moves to the new position and value
- **AND** the curve between neighbouring points is recalculated

### Requirement: Live touch recording
The user SHALL be able to arm a parameter for automation recording. When armed and the transport is playing, each change to that parameter (via knob/touch in the UI) is recorded as an automation point at the current transport position.

#### Scenario: Arm and record
- **WHEN** the user taps the automation arm button on a parameter
- **THEN** a red indicator appears on the automation lane for that parameter
- **AND** when the transport is playing and the user moves the parameter, a new automation point is created at the current transport position

#### Scenario: Overdub
- **WHEN** a parameter is armed and recording, and an automation point already exists at the current position
- **THEN** the new value overwrites the existing point (last write wins)

#### Scenario: No redundant points
- **WHEN** the user arms a parameter and does not touch it during recording
- **THEN** no new automation points are created

### Requirement: Automation persistence
Automation clips SHALL be serialised in the project JSON. Each clip stores an ordered list of `AutomationPoint(position: Long, value: Float)` per parameter.

#### Scenario: Save and load
- **WHEN** the user saves a project
- **THEN** all automation data is written to the project JSON file
- **WHEN** the user loads a project
- **THEN** all automation data is restored and ready for playback

#### Scenario: Automation survives transport stop
- **WHEN** the transport is stopped and restarted
- **THEN** recorded automation points are still present

### Requirement: Edge case — automation write while track is muted
Automation recording SHALL still capture knob movements even when the target track is muted. The automation is written to the lane; whether the listener hears it depends on mute status during playback.

#### Scenario: Arm while muted
- **WHEN** a track is muted and its automation is armed
- **THEN** knob movements are still recorded into the automation lane
- **WHEN** the track is un-muted
- **THEN** the recorded automation plays back

### Requirement: Edge case — no automation for dead lane
Parameters that have no automation points SHALL play at their static (currently-set) value throughout. An empty automation lane is a silent pass-through.

#### Scenario: Empty lane
- **WHEN** a parameter has no automation points
- **THEN** the parameter stays at its last manually-set value
- **AND** no automation processing overhead occurs for that parameter

