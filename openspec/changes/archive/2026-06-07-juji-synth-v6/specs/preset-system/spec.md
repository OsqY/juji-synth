## MODIFIED Requirements

### Requirement: Preset storage system
The preset browser SHALL display presets without crashing by using unique keys for each preset item in the list.

#### Scenario: Fallback presets displayed
- **WHEN** the database is unavailable and fallback presets are shown
- **THEN** each preset has a unique key and the list renders without a DuplicateKeyException

#### Scenario: Preset browser opens
- **WHEN** user taps the PRESET button
- **THEN** the preset browser opens without crashing
