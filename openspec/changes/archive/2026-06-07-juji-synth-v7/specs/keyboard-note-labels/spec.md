## MODIFIED Requirements

### Requirement: Black key labels
Each black key SHALL display its note name (e.g., "C#3", "D#3", "F#3", "G#3", "A#3") near the top of the key using a smaller font than white key labels.

#### Scenario: Black key label visible
- **WHEN** the keyboard is displayed
- **THEN** each black key shows the corresponding note name (C#, D#, F#, G#, A#) with the octave number

#### Scenario: Labels update with octave
- **WHEN** the octave is shifted up
- **THEN** the black key labels update to show the new octave number
