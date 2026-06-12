## MODIFIED Requirements

### Requirement: Preset load does not interrupt held notes
When a preset is loaded, the system SHALL NOT apply envelope (ADSR) parameter changes to voices that are currently active (playing a note). Envelope changes SHALL only apply to new notes triggered after the preset load.

#### Scenario: Held note continues
- **WHEN** user holds a note and loads a preset with different envelope values
- **THEN** the held note continues with its original envelope until released

#### Scenario: New note uses new envelope
- **WHEN** user loads a preset and then plays a new note
- **THEN** the new note uses the preset's envelope parameters

### Requirement: Preset filter persistence
The preset browser's category filter SHALL persist across dialog open/close cycles.

#### Scenario: Filter stays selected
- **WHEN** user selects "Pads" category, closes the browser, and reopens it
- **THEN** the "Pads" category is still selected
