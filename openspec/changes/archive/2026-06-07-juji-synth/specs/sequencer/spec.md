## ADDED Requirements

### Requirement: 16-step sequencer
The synthesizer SHALL include a 16-step sequencer capable of programming note sequences with parameter automation.

#### Scenario: Step plays note
- **WHEN** sequencer is running and playback reaches step 5
- **THEN** the note configured for step 5 is triggered

### Requirement: Per-step note control
Each sequencer step SHALL allow configuration of note pitch (from any key in the playable range).

#### Scenario: Step set to specific note
- **WHEN** user configures step 3 to play note C4
- **THEN** when sequencer reaches step 3, note C4 is triggered

#### Scenario: Step set to no note
- **WHEN** user configures step 3 to have no note (rest)
- **THEN** when sequencer reaches step 3, no note is triggered

### Requirement: Per-step velocity control
Each sequencer step SHALL allow configuration of velocity (0-127) controlling note loudness.

#### Scenario: Step with velocity
- **WHEN** step 4 is configured with velocity 100 and note E4
- **THEN** note E4 plays at velocity 100 when step 4 is reached

### Requirement: Per-step gate length control
Each sequencer step SHALL allow configuration of gate length (1% to 100%) controlling how long the note is held vs. space before next step.

#### Scenario: Short gate
- **WHEN** step gate length is set to 25%
- **THEN** note sounds for 25% of step duration, silent for remaining 75%

#### Scenario: Long gate
- **WHEN** step gate length is set to 100%
- **THEN** note holds for entire step duration (legato style)

### Requirement: Sequencer playback controls
The sequencer SHALL provide play, stop, and reset controls.

#### Scenario: Play from beginning
- **WHEN** user presses play
- **THEN** sequencer starts from step 1 and advances through steps sequentially

#### Scenario: Stop pauses playback
- **WHEN** user presses stop while sequencer is playing
- **THEN** sequencer pauses at current step position

#### Scenario: Reset returns to start
- **WHEN** user presses reset
- **THEN** sequencer position returns to step 1 and playback stops

### Requirement: Sequencer tempo
The sequencer SHALL support tempo adjustment from 30 BPM to 300 BPM.

#### Scenario: Tempo affects step duration
- **WHEN** tempo is set to 120 BPM
- **THEN** each step duration is 125ms (half a second per beat at 4 steps per beat)

### Requirement: Parameter automation
The sequencer SHALL support recording and playback of parameter changes per step.

#### Scenario: Filter cutoff automation
- **WHEN** user records filter cutoff movements and plays back sequence
- **THEN** filter cutoff changes automatically at each step