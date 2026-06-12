## ADDED Requirements

### Requirement: Animated LFO waveform preview
The LFO panel SHALL display an animated waveform preview that shows the LFO shape oscillating in real-time at the configured rate.

- The preview SHALL animate continuously, showing one full cycle of the selected LFO waveform
- The animation speed SHALL correspond to the LFO rate setting (faster rate = faster oscillation)
- The preview SHALL support all 5 LFO shapes: Sin, Sqr, Saw, Tri, Rnd
- Random (Rnd) SHALL be visualized as a sample-and-hold stepped waveform
- The animation SHALL use `withFrameMillis` for smooth motion
- LFO1 SHALL use KnobPink, LFO2 SHALL use KnobOrange
- The preview size SHALL be at minimum column-width × 48dp

#### Scenario: LFO rate affects animation speed
- **WHEN** user increases the LFO Rate knob
- **THEN** the waveform animation oscillates faster
- **WHEN** user decreases the LFO Rate knob
- **THEN** the waveform animation oscillates slower

#### Scenario: Waveform shape change updates animation
- **WHEN** user selects a different LFO waveform (e.g., from Sine to Square)
- **THEN** the animation immediately shows the new shape oscillating

#### Scenario: LFO depth visualization
- **WHEN** LFO depth is 0
- **THEN** the animation shows a flat line (no modulation)
- **WHEN** LFO depth increases
- **THEN** the waveform amplitude in the animation increases proportionally
