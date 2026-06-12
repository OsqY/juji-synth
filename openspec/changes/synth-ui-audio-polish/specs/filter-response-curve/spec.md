## ADDED Requirements

### Requirement: Filter frequency response curve
The FILTER panel SHALL display a frequency response curve that visualizes the filter's behavior based on current cutoff, resonance, and mode settings.

- The curve SHALL update in real-time when cutoff, resonance, or mode changes
- The curve SHALL show the frequency response shape for the current filter mode (LPF, HPF, or BPF)
- Higher resonance SHALL be visualized as a peak at the cutoff frequency
- The curve SHALL use the filter's accent color (KnobCyan)
- The visualization size SHALL be at minimum column-width × 60dp

#### Scenario: Cutoff change updates curve
- **WHEN** user changes the Cutoff knob
- **THEN** the frequency response curve shifts left (lower cutoff) or right (higher cutoff)

#### Scenario: Resonance peak visualization
- **WHEN** user increases the Resonance knob
- **THEN** the curve shows a visible peak/gain boost at the cutoff frequency
- **AND** the peak becomes more pronounced as resonance increases

#### Scenario: Mode change updates curve type
- **WHEN** user taps LPF mode
- **THEN** the curve shows low-pass shape (flat below cutoff, rolling off above)
- **WHEN** user taps HPF mode
- **THEN** the curve shows high-pass shape (rolling off below cutoff, flat above)
- **WHEN** user taps BPF mode
- **THEN** the curve shows band-pass shape (peak at cutoff, roll-off on both sides)
