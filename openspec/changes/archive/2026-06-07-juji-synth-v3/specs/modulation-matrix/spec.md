## MODIFIED Requirements

### Requirement: Modulation routing system
The synthesizer SHALL include a modulation matrix displayed as a dedicated tab panel with a visual grid of source→destination routings.

#### Scenario: Modulation source connected to destination
- **WHEN** user configures a modulation routing
- **THEN** the modulation source affects the destination parameter in real-time

#### Scenario: Modulation matrix full-width
- **WHEN** the MOD MATRIX tab is selected
- **THEN** the modulation matrix fills the available width showing all 8 routing slots with visual source→destination→amount indicators
