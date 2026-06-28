# effects-section Specification

## Purpose
TBD - created by archiving change juji-synth-v15. Update Purpose after archive.
## Requirements
### Requirement: Effects off by default
Reverb and delay effects SHALL be disabled by default (mix = 0) and only activate when the user turns the mix knob above 0.

#### Scenario: First note has no reverb
- **WHEN** user plays the first note after launching the app
- **THEN** the output is completely dry — no reverb or delay is applied

#### Scenario: Reverb activates on knob turn
- **WHEN** user turns the reverb mix knob above 0
- **THEN** reverb becomes audible

