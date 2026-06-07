## ADDED Requirements

### Requirement: Octave shift buttons
The keyboard SHALL have octave up and octave down buttons that shift the played range by 12 semitones per click.

#### Scenario: Octave up
- **WHEN** user taps the octave up (+) button
- **THEN** all keyboard keys shift up one octave (12 semitones)

#### Scenario: Octave down
- **WHEN** user taps the octave down (-) button
- **THEN** all keyboard keys shift down one octave (12 semitones)

#### Scenario: Octave range limits
- **WHEN** the octave is already at the minimum (C1) and user taps octave down
- **THEN** the octave does not change

#### Scenario: Octave range limits upper
- **WHEN** the octave is already at the maximum (C7) and user taps octave up
- **THEN** the octave does not change

### Requirement: Octave display
The current octave number SHALL be displayed on screen.

#### Scenario: Octave display shows current value
- **WHEN** the app is running
- **THEN** the current octave number is displayed (e.g., "C3", "C4")

#### Scenario: Octave display updates
- **WHEN** user changes the octave
- **THEN** the display updates to show the new octave number
