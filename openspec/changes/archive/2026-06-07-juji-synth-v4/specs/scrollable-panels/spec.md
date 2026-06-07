## ADDED Requirements

### Requirement: Vertical scroll in panels
Content-heavy panels SHALL support vertical scrolling when their content exceeds the available height.

#### Scenario: Scroll effects panel
- **WHEN** the FX tab is selected and the effects panel content is taller than the available space
- **THEN** the user can scroll vertically to see all controls

#### Scenario: Scroll indicators
- **WHEN** a panel has scrollable content
- **THEN** a subtle scroll indicator is visible on the right edge

### Requirement: No hardcoded overflow heights
Panel dividers and spacing SHALL use relative heights rather than fixed pixel values to prevent overflow on different screen sizes.

#### Scenario: Divider height
- **WHEN** a section divider is displayed
- **THEN** its height is relative to the container, not a fixed value
