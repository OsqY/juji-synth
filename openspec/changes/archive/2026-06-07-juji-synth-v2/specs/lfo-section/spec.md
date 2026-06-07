## MODIFIED Requirements

### Requirement: Dual LFO generators
The synthesizer SHALL provide two independent Low Frequency Oscillators (LFO1 and LFO2) for creating cyclic modulation effects. Each LFO section SHALL be rendered with realistic hardware-style waveform selectors and 3D knobs for rate and depth.

#### Scenario: LFO free-running
- **WHEN** LFO is enabled and key is not held
- **THEN** LFO cycles continuously at its configured rate

#### Scenario: LFO key sync
- **WHEN** key sync is enabled and a note is pressed
- **THEN** LFO restarts from its initial phase

#### Scenario: LFO panel appearance
- **WHEN** the LFO panel is displayed
- **THEN** it has a textured background with 3D knobs and waveform selectors matching the hardware aesthetic

## MODIFIED Requirements

### Requirement: LFO waveform types
Each LFO SHALL support the following waveform types: sine, square, sawtooth, triangle, random (sample-and-hold). Waveform selectors SHALL display a small waveform shape icon alongside the text label.

#### Scenario: Sine LFO
- **WHEN** sine waveform is selected
- **THEN** LFO produces smooth, continuous modulation following sine wave shape

#### Scenario: Square LFO
- **WHEN** square waveform is selected
- **THEN** LFO produces abrupt modulation switching between min and max

#### Scenario: Sawtooth LFO
- **WHEN** sawtooth waveform is selected
- **THEN** LFO produces linear ramp from min to max then instantly resets

#### Scenario: Triangle LFO
- **WHEN** triangle waveform is selected
- **THEN** LFO produces linear modulation up and down symmetrically

#### Scenario: Random LFO
- **WHEN** random waveform is selected
- **THEN** LFO produces stepped random values held for one LFO cycle

### Requirement: LFO rate control
Each LFO SHALL have rate control adjustable from 0.01Hz to 50Hz, rendered as a realistic 3D hardware knob with value display in Hz.

#### Scenario: Slow LFO
- **WHEN** LFO rate is set to low value (e.g., 0.1Hz)
- **THEN** modulation cycles slowly, suitable for ambient effects

#### Scenario: Fast LFO
- **WHEN** LFO rate is set to high value (e.g., 20Hz)
- **THEN** modulation cycles rapidly, creating trills or vibrato-like effects

### Requirement: LFO depth control
Each LFO SHALL have depth control adjustable from 0% to 100%, rendered as a realistic 3D hardware knob.

#### Scenario: Zero depth
- **WHEN** LFO depth is set to 0%
- **THEN** LFO has no effect on destination parameters

#### Scenario: Maximum depth
- **WHEN** LFO depth is set to 100%
- **THEN** LFO produces maximum modulation swing on destination parameters
