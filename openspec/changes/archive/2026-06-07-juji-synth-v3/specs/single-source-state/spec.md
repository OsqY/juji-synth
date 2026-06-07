## ADDED Requirements

### Requirement: Single SynthState object
The system SHALL maintain all synthesizer parameters in a single `SynthState` data class instance as the source of truth.

#### Scenario: UI state initialization
- **WHEN** the app starts
- **THEN** a single `SynthState` instance is created with default parameter values

#### Scenario: Parameter change updates single state
- **WHEN** user adjusts any knob or control
- **THEN** the corresponding field in the single `SynthState` object is updated

### Requirement: Atomic preset loading
Loading a preset SHALL atomically replace the entire SynthState and apply all parameters to the audio engine.

#### Scenario: Load preset updates all params
- **WHEN** a preset is loaded
- **THEN** all synth parameters are replaced with the preset's stored values AND applied to the audio engine via JNI

#### Scenario: Partial update not possible
- **WHEN** a preset is loaded
- **THEN** no individual parameter is left with its previous value

### Requirement: Panel callbacks use state object
Each panel composable SHALL receive the current SynthState and a callback to update it.

#### Scenario: Oscillator panel uses state
- **WHEN** the oscillator panel updates a parameter
- **THEN** it modifies the shared SynthState via the update callback

#### Scenario: State changes propagate
- **WHEN** any panel modifies the SynthState
- **THEN** all panels reading the state reflect the change on next recomposition
