## ADDED Requirements

### Requirement: Modulation matrix SHALL display as a visual patch bay
The modulation routing interface SHALL show 8 rows with source jacks (left), destination jacks (right), and amount knobs (center). Active routes SHALL display a Bézier curve cable connecting the jacks.

#### Scenario: Viewing modulation routes
- **WHEN** the modulation section is visible
- **THEN** 8 rows of source/destination pairs are shown
- **AND** active routes have a visible cable connecting source to destination
- **AND** inactive routes show empty jacks

### Requirement: Cable visualization SHALL reflect modulation amount
The thickness and brightness of the cable SHALL increase with the modulation amount. At zero amount, no cable is drawn.

#### Scenario: Amount affects cable appearance
- **WHEN** a modulation route has amount = 0.0
- **THEN** no cable is visible between the jacks
- **WHEN** amount is increased to 1.0
- **THEN** a bright, thick cable connects the jacks
