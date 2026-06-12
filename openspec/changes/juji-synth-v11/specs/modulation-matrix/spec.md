## MODIFIED Requirements

### Requirement: Modulation routing system
Active modulation routes SHALL be sent to the C++ audio engine so they actually affect the sound.

#### Scenario: Route affects filter cutoff
- **WHEN** user sets a modulation route from LFO1 to filter cutoff with amount 50%
- **THEN** the LFO1 modulates the filter cutoff in real-time at the configured depth
