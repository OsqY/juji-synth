## MODIFIED Requirements

### Requirement: Effects processing system
The synthesizer SHALL include effects displayed in a scrollable panel layout. Section dividers SHALL use fixed pixel heights that render correctly inside scrollable columns.

#### Scenario: Divider renders correctly
- **WHEN** the FX tab is selected
- **THEN** section dividers between effect columns have a fixed height and are visible

#### Scenario: No fillMaxHeight in scroll context
- **WHEN** the effects panel is displayed
- **THEN** no divider or spacing element uses `fillMaxHeight` or `fillMaxWidth` inside a scrollable container
