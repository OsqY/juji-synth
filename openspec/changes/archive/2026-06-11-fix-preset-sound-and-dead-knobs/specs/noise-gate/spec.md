## ADDED Requirements

### Requirement: Noise SHALL be silent when no voice is active
White noise SHALL NOT produce audible output when zero voices are active. When at least one voice is active, noise SHALL fade in smoothly (not click on).

#### Scenario: Atmospheric preset loads, no note pressed
- **WHEN** the Atmospheric preset is loaded (noiseLevel=0.4f)
- **THEN** the output SHALL be silent (no noise heard) until a key is pressed

#### Scenario: Noise fades in when first note starts
- **WHEN** a key is pressed while noiseLevel > 0.0
- **THEN** the noise SHALL fade in smoothly with a time constant of no more than 100ms to avoid a click

#### Scenario: Noise fades out on last note release
- **WHEN** all voices are released (envelopes reach idle)
- **THEN** the noise SHALL fade out smoothly with a time constant of no more than 100ms

### Requirement: Noise gate SHALL use one-pole smoothing
The noise envelope SHALL use a one-pole low-pass filter on the noise amplitude target to prevent zipper noise when the gate transitions.

#### Scenario: Smooth transition verified
- **WHEN** a note is pressed and held with noiseLevel=0.4f
- **THEN** the noise amplitude SHALL reach within 90% of target within 100ms without audible clicking
