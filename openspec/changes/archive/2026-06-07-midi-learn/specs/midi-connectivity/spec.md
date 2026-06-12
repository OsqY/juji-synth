## MODIFIED Requirements

### Requirement: MIDI CC handling
When a MIDI CC message arrives, the system SHALL first check the mapping table. If a mapping exists, apply the CC value to the mapped parameter. If no mapping exists, apply default CC handling.

#### Scenario: Mapped CC applies
- **WHEN** CC74 arrives and is mapped to filter cutoff
- **THEN** the filter cutoff is set to the scaled CC value

#### Scenario: Unmapped CC falls through
- **WHEN** CC1 arrives and is not mapped
- **THEN** default mod wheel handling applies (mod wheel parameter)
