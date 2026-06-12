## MODIFIED Requirements

### Requirement: Fallback presets change the sound
When a fallback preset (database unavailable) is selected, the system SHALL load real parameter data and apply it to both the UI state and the audio engine.

#### Scenario: Select "Deep Sub Bass" fallback preset
- **WHEN** user selects "Deep Sub Bass" from the preset browser when the database is unavailable
- **THEN** oscillator levels, filter cutoff, envelope parameters, and all other synth parameters are updated to match a deep sub-bass sound

#### Scenario: Select "Acid Lead" fallback preset
- **WHEN** user selects "Acid Lead" from the preset browser when the database is unavailable
- **THEN** synth parameters are updated to match a squelching acid lead sound

### Requirement: User feedback on preset load
The system SHALL display a brief notification when a preset is successfully loaded.

#### Scenario: Toast shown on load
- **WHEN** a preset is loaded (either from DB or fallback)
- **THEN** a toast message "Loaded: [preset name]" is displayed for 2 seconds

#### Scenario: Toast on fallback preset
- **WHEN** a fallback preset is loaded
- **THEN** the toast still displays "Loaded: [preset name]" even though the parameters came from the fallback list
