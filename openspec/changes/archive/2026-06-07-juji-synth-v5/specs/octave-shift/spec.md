## MODIFIED Requirements

### Requirement: Octave shift display
The current octave SHALL be displayed prominently so users can visually confirm octave changes.

#### Scenario: Octave display shows current value
- **WHEN** the app is running
- **THEN** the current octave number is displayed (e.g., "C3", "C4") with a visible label

#### Scenario: Octave display updates immediately
- **WHEN** user taps the octave up or down button
- **THEN** the octave display updates to show the new value on the next frame
