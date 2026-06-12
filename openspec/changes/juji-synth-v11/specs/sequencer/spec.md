## MODIFIED Requirements

### Requirement: 16-step sequencer
The sequencer SHALL drive the C++ audio engine when playing. The playing state, tempo, and step data SHALL be sent to the engine via JNI.

#### Scenario: Play button starts engine sequencer
- **WHEN** user taps play on the sequencer
- **THEN** SynthEngine.setParam(61, 1f) is called and the C++ sequencer starts generating notes

#### Scenario: Tempo changes affect engine
- **WHEN** user adjusts tempo
- **THEN** SynthEngine.setParam(60, tempo) is called and the C++ sequencer tempo updates

#### Scenario: Step data sent to engine
- **WHEN** sequencer steps are modified
- **THEN** the step data is sent to the C++ engine via JNI
