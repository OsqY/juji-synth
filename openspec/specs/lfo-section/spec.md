# lfo-section Specification

## Purpose
TBD - created by archiving change juji-synth-v14. Update Purpose after archive.
## Requirements
### Requirement: LFO routes to destinations
The LFO SHALL support routing its output to modulate parameters including pitch, filter cutoff, and amplitude.

#### Scenario: LFO modulates amplitude
- **WHEN** LFO is routed to amplitude with depth > 0
- **THEN** the output volume modulates at the LFO rate (tremolo effect)

