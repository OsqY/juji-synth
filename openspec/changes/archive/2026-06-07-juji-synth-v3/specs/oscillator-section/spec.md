## MODIFIED Requirements

### Requirement: Dual oscillator system
The synthesizer SHALL provide two independent oscillators (OSC1 and OSC2) displayed as a full-width tab panel with large (80-100dp) 3D knobs for all controls.

#### Scenario: Oscillator waveform selection
- **WHEN** user selects waveform type for an oscillator
- **THEN** oscillator generates the selected waveform (saw, square, triangle, sine) at the current frequency

#### Scenario: Both oscillators active
- **WHEN** both OSC1 and OSC2 are enabled
- **THEN** both oscillators generate audio simultaneously and their signals are mixed

## ADDED Requirements

### Requirement: Full-width oscillator layout
When the OSC tab is selected, the oscillator panel SHALL fill the content area with controls arranged in a hardware-style layout: OSC1 on the left, OSC2 on the right, levels and global controls centered.

#### Scenario: Oscillator panel full-width
- **WHEN** the OSC tab is selected
- **THEN** the oscillator panel fills the available width with large knobs arranged in rows
