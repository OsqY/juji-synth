## MODIFIED Requirements

### Requirement: Preset storage system
The synthesizer SHALL include a preset system backed by Room database. The fallback presets used when the database is unavailable SHALL be of type `PresetEntity` to prevent runtime crashes.

#### Scenario: Fallback presets type-safe
- **WHEN** the database is unavailable and fallback presets are shown
- **THEN** they are instances of PresetEntity with proper fields (name, category, description, isFactory, parametersJson)

#### Scenario: Preset selection does not crash
- **WHEN** user selects a preset from the browser
- **THEN** the app does not crash, regardless of whether presets come from the database or fallback list
