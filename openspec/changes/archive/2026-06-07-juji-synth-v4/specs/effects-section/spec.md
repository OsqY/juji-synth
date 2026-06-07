## MODIFIED Requirements

### Requirement: Effects processing system
The synthesizer SHALL include effects displayed in a scrollable panel layout. The panel content SHALL support vertical scrolling when the controls exceed the available height.

#### Scenario: Effects panel scrollable
- **WHEN** the FX tab is selected
- **THEN** the effects panel content scrolls vertically if it exceeds the available height

#### Scenario: No hardcoded heights
- **WHEN** the effects panel is displayed
- **THEN** section dividers do not use hardcoded absolute heights that could cause overflow
