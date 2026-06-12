## ADDED Requirements

### Requirement: Sequencer recording mode
The sequencer SHALL support recording keyboard input into pattern steps in real-time.

- A record arm toggle button SHALL be added to the sequencer transport controls
- When record is armed and transport is playing, keyboard notes SHALL be captured into sequencer steps
- Recorded notes SHALL be quantized to the nearest 1/16th note grid position
- Recording SHALL not overwrite existing steps unless the step slot is empty or the user has cleared it
- A recording indicator (red dot/animation) SHALL be visible during active recording
- The record arm state SHALL be part of SynthState (sequencerRecording: Boolean)

#### Scenario: Arm and record
- **WHEN** user taps the record arm button
- **THEN** the button shows a red armed state
- **WHEN** user presses Play
- **THEN** the sequencer starts running and recording
- **WHEN** user plays notes on the keyboard during recording
- **THEN** notes are captured into sequencer steps at the nearest grid position

#### Scenario: Stop recording
- **WHEN** user presses Stop during recording
- **THEN** the sequence is preserved with all captured notes
- **AND** the record arm disengages

### Requirement: Sequencer loop playback
The sequencer SHALL support loop mode where the pattern continuously repeats.

- A loop toggle button SHALL be added to the sequencer transport controls
- When loop is enabled (default), the sequencer resets to step 0 after reaching the last step
- When loop is disabled, the sequencer stops after playing through once
- The loop state SHALL be part of SynthState (sequencerLooping: Boolean, default true)

#### Scenario: Loop enabled
- **WHEN** loop is enabled and the sequencer reaches the last step
- **THEN** the sequencer wraps back to step 0 and continues playing
- **AND** this repeats indefinitely until Stop is pressed

#### Scenario: Loop disabled
- **WHEN** loop is disabled and the sequencer reaches the last step
- **THEN** the sequencer stops automatically
- **AND** the playing state returns to stopped
