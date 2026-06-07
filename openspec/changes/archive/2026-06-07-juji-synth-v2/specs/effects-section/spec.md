## MODIFIED Requirements

### Requirement: Effects processing system
The synthesizer SHALL include an effects system capable of processing audio in real-time. The effects panel SHALL be rendered with realistic hardware-style controls: 3D knobs for mix/decay/time/feedback/drive, toggle buttons with LED-style indicators, and a panel background matching the hardware aesthetic.

#### Scenario: Effects enabled
- **WHEN** effects are enabled
- **THEN** dry signal is routed through effect processors before output

#### Scenario: Effects panel appearance
- **WHEN** the effects panel is displayed
- **THEN** it has a textured background with 3D knobs and LED-style bypass indicator

## MODIFIED Requirements

### Requirement: Reverb effect
The synthesizer SHALL include a reverb effect with mix and decay controls rendered as 3D hardware knobs.

#### Scenario: Reverb adds space
- **WHEN** reverb is enabled with moderate settings and a note is played
- **THEN** the sound appears to have spatial depth and trailing echoes

#### Scenario: Reverb parameters adjustable
- **WHEN** user adjusts reverb knobs
- **THEN** the reverb character changes accordingly with smooth visual feedback

### Requirement: Delay effect
The synthesizer SHALL include a delay effect with mix, time, and feedback controls rendered as 3D hardware knobs.

#### Scenario: Delay creates echoes
- **WHEN** delay is enabled with moderate settings and a note is played
- **THEN** repeated copies of the sound appear at the delay interval

#### Scenario: Delay sync to tempo
- **WHEN** delay sync is enabled
- **THEN** delay times align to musical note values (e.g., 1/4, 1/8)

### Requirement: Distortion effect
The synthesizer SHALL include a distortion effect with drive and mix controls rendered as 3D hardware knobs.

#### Scenario: Distortion adds harmonics
- **WHEN** distortion is enabled with moderate drive settings
- **THEN** the signal exhibits additional harmonic content creating warmth or grit

#### Scenario: Soft clipping
- **WHEN** soft clipping mode is selected
- **THEN** distortion creates smooth, tube-like saturation

### Requirement: Effects mix control
Each effect SHALL have a mix control rendered as a 3D hardware knob allowing parallel blending of wet and dry signals from 0% to 100%.

#### Scenario: Dry signal only
- **WHEN** effect mix is set to 0%
- **THEN** output contains only the original dry signal

#### Scenario: Wet signal only
- **WHEN** effect mix is set to 100%
- **THEN** output contains only the fully processed wet signal

### Requirement: Effects bypass
The effects system SHALL support global bypass via a toggle button with LED-style indicator showing active/inactive state.

#### Scenario: Bypass enabled
- **WHEN** effects bypass is toggled on
- **THEN** all effects are removed from signal chain immediately and the LED indicator changes state
