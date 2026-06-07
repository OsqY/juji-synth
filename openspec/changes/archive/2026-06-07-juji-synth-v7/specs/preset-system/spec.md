## MODIFIED Requirements

### Requirement: Preset loading changes sound
When a preset is selected in the browser, the system SHALL deserialize the stored SynthState JSON and apply ALL parameters to both the UI state and the audio engine.

#### Scenario: Select preset changes parameters
- **WHEN** user selects a preset from the browser
- **THEN** all synth parameters (oscillators, filter, envelopes, LFOs, effects, modulation, master) are updated in the UI and applied to the audio engine

#### Scenario: Factory preset "Deep Sub Bass" loads
- **WHEN** user selects the "Deep Sub Bass" preset
- **THEN** the oscillator levels, filter cutoff, envelopes, and all other parameters are set to the values stored in that preset

#### Scenario: Fallback preset loads without JSON
- **WHEN** a fallback preset (with parametersJson = "{}") is selected
- **THEN** the synth does not crash — default parameters remain
