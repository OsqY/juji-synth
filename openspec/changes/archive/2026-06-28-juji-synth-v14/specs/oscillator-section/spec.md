## ADDED Requirements

### Requirement: LFO modulation affects sound
LFO output SHALL be routed to modulate synthesizer parameters in real-time based on the modulation matrix configuration.

#### Scenario: LFO modulates filter
- **WHEN** LFO depth > 0 and LFO destination is filter cutoff
- **THEN** the filter cutoff modulates at the LFO rate and depth

### Requirement: Sub-oscillator generates sound
The sub-oscillator SHALL generate sound when its level is above 0.

#### Scenario: Sub-oscillator audible
- **WHEN** sub-oscillator level is set above 0 and a note is played
- **THEN** a square wave one octave below the played note is audible
