## ADDED Requirements

### Requirement: Note labels on white keys
Each white key on the keyboard SHALL display its note name (e.g., "C3", "D3", "E3") near the bottom edge of the key.

#### Scenario: Note label visible
- **WHEN** the keyboard is displayed
- **THEN** each white key shows the corresponding note name (C, D, E, F, G, A, B) with the octave number

#### Scenario: Notes change with octave
- **WHEN** the octave is shifted up
- **THEN** the note labels update to show the new octave number

### Requirement: Note label style
Note labels SHALL be rendered in a small, unobtrusive font using the secondary text color.

#### Scenario: Label appearance
- **WHEN** looking at the keyboard
- **THEN** note labels are readable but do not distract from the keys themselves

#### Scenario: Label on pressed keys
- **WHEN** a key is pressed
- **THEN** the note label remains visible on the pressed key
