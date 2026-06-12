## MODIFIED Requirements

### Requirement: Playable sequencer grid
The sequencer grid SHALL allow users to audition notes by tapping a step when playback is stopped.

#### Scenario: Tap step previews note
- **WHEN** sequencer is stopped and user taps an active step
- **THEN** the step's note plays briefly and then stops

#### Scenario: Step shows note name
- **WHEN** a step has a note assigned
- **THEN** the step displays the note name (e.g., "C4") instead of only a dot

#### Scenario: Larger tap target
- **WHEN** user taps a sequencer step
- **THEN** the step has a minimum size of 28×22dp for comfortable tapping
