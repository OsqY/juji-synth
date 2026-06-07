## MODIFIED Requirements

### Requirement: 16-step sequencer
The synthesizer SHALL include a 16-step sequencer capable of programming note sequences with parameter automation. The sequencer UI SHALL be rendered with a realistic step grid featuring LED-style step indicators, transport controls with hardware-style buttons, and a tempo knob.

#### Scenario: Step plays note
- **WHEN** sequencer is running and playback reaches step 5
- **THEN** the note configured for step 5 is triggered and the step LED illuminates

#### Scenario: Sequencer panel appearance
- **WHEN** the sequencer panel is displayed
- **THEN** it shows a 2x8 grid of step buttons with LED indicators, play/stop/reset buttons, and a tempo knob

## MODIFIED Requirements

### Requirement: Per-step note control
Each sequencer step SHALL allow configuration of note pitch. Steps SHALL be displayed as toggle buttons that light up when active.

#### Scenario: Step set to specific note
- **WHEN** user taps a step to activate it
- **THEN** the step LED illuminates and the step is assigned a default note

#### Scenario: Step set to no note
- **WHEN** user taps an active step to deactivate it
- **THEN** the step LED turns off and no note is triggered for that step

### Requirement: Per-step velocity control
Each sequencer step SHALL allow configuration of velocity (0-127) controlling note loudness.

#### Scenario: Step with velocity
- **WHEN** step 4 is configured with velocity 100 and note E4
- **THEN** note E4 plays at velocity 100 when step 4 is reached

### Requirement: Per-step gate length control
Each sequencer step SHALL allow configuration of gate length (1% to 100%) controlling how long the note is held.

#### Scenario: Short gate
- **WHEN** step gate length is set to 25%
- **THEN** note sounds for 25% of step duration, silent for remaining 75%

#### Scenario: Long gate
- **WHEN** step gate length is set to 100%
- **THEN** note holds for entire step duration (legato style)

### Requirement: Sequencer playback controls
The sequencer SHALL provide play, stop, and reset controls rendered as hardware-style buttons with proper press feedback.

#### Scenario: Play from beginning
- **WHEN** user presses the play button
- **THEN** sequencer starts from step 1 and advances through steps sequentially, illuminating each active step

#### Scenario: Stop pauses playback
- **WHEN** user presses stop while sequencer is playing
- **THEN** sequencer pauses at current step position

#### Scenario: Reset returns to start
- **WHEN** user presses reset
- **THEN** sequencer position returns to step 1 and playback stops

### Requirement: Sequencer tempo
The sequencer SHALL support tempo adjustment from 30 BPM to 300 BPM via a 3D hardware knob with BPM display.

#### Scenario: Tempo affects step duration
- **WHEN** tempo is set to 120 BPM
- **THEN** each step duration is 125ms (half a second per beat at 4 steps per beat)

### Requirement: Parameter automation
The sequencer SHALL support recording and playback of parameter changes per step.

#### Scenario: Filter cutoff automation
- **WHEN** user records filter cutoff movements and plays back sequence
- **THEN** filter cutoff changes automatically at each step
