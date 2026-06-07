## ADDED Requirements

### Requirement: Multi-mode filter
The synthesizer SHALL include a filter capable of attenuating specific frequency ranges of the audio signal.

#### Scenario: Filter processes incoming audio
- **WHEN** audio passes through the filter
- **THEN** frequencies outside the passband are attenuated according to filter mode and cutoff setting

### Requirement: Filter modes
The filter SHALL support three modes: low-pass, high-pass, and band-pass.

#### Scenario: Low-pass mode
- **WHEN** low-pass mode is selected
- **THEN** frequencies above the cutoff are attenuated; frequencies below pass unchanged

#### Scenario: High-pass mode
- **WHEN** high-pass mode is selected
- **THEN** frequencies below the cutoff are attenuated; frequencies above pass unchanged

#### Scenario: Band-pass mode
- **WHEN** band-pass mode is selected
- **THEN** frequencies outside a narrow band around the cutoff are attenuated

### Requirement: Cutoff frequency control
The filter cutoff frequency SHALL be adjustable from 20Hz to 20kHz with exponential scaling for musically useful response.

#### Scenario: Cutoff at minimum
- **WHEN** cutoff frequency is set to 20Hz
- **THEN** almost all audio frequencies are attenuated by the filter

#### Scenario: Cutoff at maximum
- **WHEN** cutoff frequency is set to 20kHz
- **THEN** almost no frequencies are attenuated (filter essentially bypassed)

### Requirement: Resonance control
The filter SHALL include resonance control that boosts frequencies at the cutoff point from 0% (no resonance) to 100% (self-oscillation).

#### Scenario: Zero resonance
- **WHEN** resonance is set to 0%
- **THEN** filter has smooth frequency response with no peak at cutoff

#### Scenario: High resonance
- **WHEN** resonance is set to near maximum
- **THEN** frequencies at cutoff are emphasized, creating sharp, nasal timbre

#### Scenario: Self-oscillation
- **WHEN** resonance is set to maximum
- **THEN** filter produces a continuous tone at the cutoff frequency even without audio input

### Requirement: Filter envelope modulation
The filter cutoff SHALL be modulatable by the filter envelope, allowing automatic movement of the cutoff over time.

#### Scenario: Filter envelope amount positive
- **WHEN** filter envelope is at peak and envelope amount is positive
- **THEN** cutoff frequency increases by the envelope amount percentage of the envelope's range

#### Scenario: Filter envelope amount negative
- **WHEN** filter envelope is at peak and envelope amount is negative
- **THEN** cutoff frequency decreases by the envelope amount percentage of the envelope's range