## MODIFIED Requirements

### Requirement: Preset storage system
The synthesizer SHALL include a preset system backed by Room database. Loading a preset SHALL atomically restore all parameters by deserializing the stored SynthState JSON and applying each parameter to the audio engine via JNI.

#### Scenario: Save preset
- **WHEN** user saves current synth state as a preset
- **THEN** the current SynthState is serialized to JSON and stored in the Room database

#### Scenario: Load preset applies all params
- **WHEN** user selects a preset from the browser
- **THEN** the SynthState JSON is deserialized, the UI state is atomically replaced, and all parameters are applied to the audio engine via SynthEngine.setParam() calls
