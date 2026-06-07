## MODIFIED Requirements

### Requirement: Multi-mode filter
The synthesizer SHALL include a filter capable of attenuating specific frequency ranges of the audio signal. The filter section SHALL be rendered with realistic hardware-style knobs for cutoff and resonance, and mode selector buttons with visual indicators.

#### Scenario: Filter processes incoming audio
- **WHEN** audio passes through the filter
- **THEN** frequencies outside the passband are attenuated according to filter mode and cutoff setting

#### Scenario: Filter panel appearance
- **WHEN** the filter panel is displayed
- **THEN** it has a textured background matching the hardware aesthetic, with 3D knobs for cutoff and resonance

## MODIFIED Requirements

### Requirement: Cutoff frequency control
The filter cutoff frequency SHALL be adjustable from 20Hz to 20kHz with exponential scaling, rendered as a realistic 3D hardware knob with value display showing the frequency in Hz/kHz.

#### Scenario: Cutoff at minimum
- **WHEN** cutoff frequency is set to 20Hz
- **THEN** almost all audio frequencies are attenuated by the filter

#### Scenario: Cutoff at maximum
- **WHEN** cutoff frequency is set to 20kHz
- **THEN** almost no frequencies are attenuated (filter essentially bypassed)

#### Scenario: Cutoff knob visual
- **WHEN** user adjusts the cutoff knob
- **THEN** the knob rotates and the value display shows the frequency (e.g., "440Hz" or "2.5kHz")

### Requirement: Resonance control
The filter SHALL include resonance control rendered as a realistic 3D hardware knob, with values from 0% to 100%.

#### Scenario: High resonance
- **WHEN** resonance is set to near maximum
- **THEN** frequencies at cutoff are emphasized, creating sharp, nasal timbre

#### Scenario: Self-oscillation
- **WHEN** resonance is set to maximum
- **THEN** filter produces a continuous tone at the cutoff frequency even without audio input
