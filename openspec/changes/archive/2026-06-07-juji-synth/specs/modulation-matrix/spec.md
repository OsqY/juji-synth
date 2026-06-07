## ADDED Requirements

### Requirement: Modulation routing system
The synthesizer SHALL include a modulation matrix allowing flexible routing of modulation sources to destination parameters.

#### Scenario: Modulation source connected to destination
- **WHEN** user configures a modulation routing (e.g., LFO1 to filter cutoff)
- **THEN** the modulation source affects the destination parameter in real-time

### Requirement: Modulation sources
The modulation matrix SHALL support the following modulation sources: LFO1, LFO2, Envelope1, Envelope2, Velocity, Aftertouch.

#### Scenario: LFO as modulation source
- **WHEN** LFO1 is routed to a destination
- **THEN** destination parameter modulates following LFO1's waveform

#### Scenario: Envelope as modulation source
- **WHEN** Envelope1 is routed to a destination
- **THEN** destination parameter follows the envelope shape during note lifecycle

#### Scenario: Velocity as modulation source
- **WHEN** Velocity is routed to a destination
- **THEN** destination parameter responds to how hard the note is played

### Requirement: Modulation destinations
The modulation matrix SHALL support the following destinations: Oscillator pitch, Oscillator mix, Filter cutoff, Filter resonance, Amplitude, Effect mix, LFO rate.

#### Scenario: Modulation to filter
- **WHEN** envelope is routed to filter cutoff
- **THEN** filter cutoff moves according to envelope shape on each note

#### Scenario: Modulation to amplitude
- **WHEN** velocity is routed to amplitude
- **THEN** louder notes produce higher amplitude (velocity sensitivity)

### Requirement: Modulation amount control
Each modulation routing SHALL have an amount control (-100% to +100%) controlling modulation intensity and direction.

#### Scenario: Positive modulation
- **WHEN** modulation amount is positive (e.g., +50%)
- **THEN** destination increases when modulation source increases

#### Scenario: Negative modulation
- **WHEN** modulation amount is negative (e.g., -50%)
- **THEN** destination decreases when modulation source increases (inverse modulation)

### Requirement: Multiple simultaneous routings
The modulation matrix SHALL support multiple simultaneous routings allowing complex, layered modulation.

#### Scenario: Multiple modulations active
- **WHEN** LFO1 routes to filter and LFO2 routes to amplitude simultaneously
- **THEN** both modulations affect their respective destinations independently

### Requirement: Visual modulation display
The UI SHALL display active modulation routings visually showing source, destination, and amount.

#### Scenario: Active routing displayed
- **WHEN** a modulation routing is active
- **THEN** it appears in the modulation matrix UI with source, destination, and amount