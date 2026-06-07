## MODIFIED Requirements

### Requirement: Dual envelope generators
The synthesizer SHALL provide two ADSR (Attack, Decay, Sustain, Release) envelope generators: one for amplitude (AMP ENV) and one for filter (FILTER ENV). Each envelope SHALL be displayed with a realistic ADSR curve visualization showing the current shape, and controls rendered as 3D hardware knobs.

#### Scenario: Envelope triggers on note-on
- **WHEN** a note is pressed
- **THEN** the envelope generator begins the attack phase

#### Scenario: Envelope releases on note-off
- **WHEN** a note is released
- **THEN** the envelope generator begins the release phase from current level

#### Scenario: ADSR curve visualization
- **WHEN** the envelope panel is displayed
- **THEN** a real-time ADSR curve graphic shows the current envelope shape with attack, decay, sustain, and release segments visually represented

## MODIFIED Requirements

### Requirement: Attack time control
Each envelope SHALL have adjustable attack time from 0ms to 10000ms, rendered as a realistic 3D hardware knob with value display in milliseconds.

#### Scenario: Instant attack
- **WHEN** attack time is set to 0ms
- **THEN** envelope immediately jumps to peak level on note-on

#### Scenario: Slow attack
- **WHEN** attack time is set to high value
- **THEN** envelope gradually rises to peak level over the configured duration

### Requirement: Decay time control
Each envelope SHALL have adjustable decay time from 0ms to 10000ms, rendered as a realistic 3D hardware knob.

#### Scenario: Fast decay
- **WHEN** decay time is set to low value
- **THEN** envelope quickly drops from peak to sustain level

#### Scenario: Slow decay
- **WHEN** decay time is set to high value
- **THEN** envelope slowly descends from peak to sustain level

### Requirement: Sustain level control
Each envelope SHALL have adjustable sustain level from 0% to 100%, rendered as a realistic 3D hardware knob.

#### Scenario: Full sustain
- **WHEN** sustain level is set to 100%
- **THEN** envelope holds at peak level after decay

#### Scenario: Zero sustain
- **WHEN** sustain level is set to 0%
- **THEN** envelope decays completely to silence (no sustain phase)

### Requirement: Release time control
Each envelope SHALL have adjustable release time from 0ms to 10000ms, rendered as a realistic 3D hardware knob.

#### Scenario: Instant release
- **WHEN** release time is set to 0ms
- **THEN** envelope immediately silences on note-off

#### Scenario: Long release
- **WHEN** release time is set to high value
- **THEN** envelope gradually fades to silence over configured duration
