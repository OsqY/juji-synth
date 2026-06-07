## ADDED Requirements

### Requirement: Effects processing system
The synthesizer SHALL include an effects system capable of processing audio in real-time to add spatial, temporal, and harmonic character.

#### Scenario: Effects enabled
- **WHEN** effects are enabled
- **THEN** dry signal is routed through effect processors before output

### Requirement: Reverb effect
The synthesizer SHALL include a reverb effect providing spatial ambience simulation.

#### Scenario: Reverb adds space
- **WHEN** reverb is enabled with moderate settings and a note is played
- **THEN** the sound appears to have spatial depth and trailing echoes

#### Scenario: Reverb parameters adjustable
- **WHEN** user adjusts reverb parameters (size, decay, mix)
- **THEN** the reverb character changes accordingly

### Requirement: Delay effect
The synthesizer SHALL include a delay effect providing time-based echo repetition.

#### Scenario: Delay creates echoes
- **WHEN** delay is enabled with moderate settings and a note is played
- **THEN** repeated copies of the sound appear at the delay interval

#### Scenario: Delay sync to tempo
- **WHEN** delay sync is enabled
- **THEN** delay times align to musical note values (e.g., 1/4, 1/8)

### Requirement: Distortion effect
The synthesizer SHALL include a distortion effect providing harmonic saturation and clipping.

#### Scenario: Distortion adds harmonics
- **WHEN** distortion is enabled with moderate drive settings
- **THEN** the signal exhibits additional harmonic content creating warmth or grit

#### Scenario: Soft clipping
- **WHEN** soft clipping mode is selected
- **THEN** distortion creates smooth, tube-like saturation

### Requirement: Effects mix control
Each effect SHALL have a mix control allowing parallel blending of wet (processed) and dry (original) signals from 0% (dry only) to 100% (wet only).

#### Scenario: Dry signal only
- **WHEN** effect mix is set to 0%
- **THEN** output contains only the original dry signal

#### Scenario: Wet signal only
- **WHEN** effect mix is set to 100%
- **THEN** output contains only the fully processed wet signal

### Requirement: Effects bypass
The effects system SHALL support global bypass allowing instant switching between effected and dry sound.

#### Scenario: Bypass enabled
- **WHEN** effects bypass is toggled on
- **THEN** all effects are removed from signal chain immediately