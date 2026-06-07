## MODIFIED Requirements

### Requirement: Sequencer transport controls
The sequencer SHALL provide play, stop, and reset controls. The stop button SHALL actually stop sequence playback.

#### Scenario: Stop button stops playback
- **WHEN** user taps the stop button while the sequencer is playing
- **THEN** the sequencer stops and the playing state is set to false

#### Scenario: Stop button click handler
- **WHEN** user taps the stop button
- **THEN** the sequencer playing state transitions from true to false
