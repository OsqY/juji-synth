## ADDED Requirements

### Requirement: Sequencer tempo correct transmission
The sequencer tempo value from the UI SHALL be transmitted to the engine as raw BPM (30.0-300.0), not as a normalized 0-1 ratio.

- SynthEngine.setParam(ParamIds.SEQ_TEMPO, value) SHALL receive the actual BPM value
- The C++ Sequencer::setTempo(double bpm) SHALL receive values in the 30-300 range
- The tempo knob in SequencerView SHALL send the unscaled BPM value to the engine

#### Scenario: Tempo knob sends raw BPM
- **WHEN** user sets tempo to 120 BPM
- **THEN** engine receives setSequencerTempo(120.0)
- **AND** the sequencer runs at 120 BPM

### Requirement: Current step exposed from engine to UI
The sequencer's current step index SHALL be readable from the Kotlin UI layer via JNI.

- A new JNI function nativeGetSequencerStep() SHALL return the current step index (0-15)
- MainSynthScreen SHALL poll the step index at UI frame rate (~30fps) when the sequencer is playing
- The step index SHALL be passed to SequencerView as currentStep prop
- SequencerView SHALL highlight the active step with SeqStepCurrent color

#### Scenario: Playback step highlighting
- **WHEN** the sequencer is playing
- **THEN** the current active step in the SequencerView grid is highlighted with SeqStepCurrent color
- **AND** the highlight advances to the next step when the sequencer advances

### Requirement: Sequencer step editing JNI
The nativeSetSequencerSteps() JNI function SHALL be implemented to accept notes and velocities for all 16 steps.

- nativeSetSequencerSteps() SHALL populate the engine's sequencer steps array
- The function SHALL validate array lengths before copying
- AudioEngine SHALL expose a setSequencerStepsFromArrays method

#### Scenario: Step data flows through JNI
- **WHEN** user edits a step in the sequencer UI
- **THEN** the change is propagated via JNI to nativeSetSequencerSteps()
- **AND** the engine's sequencer steps array is updated
