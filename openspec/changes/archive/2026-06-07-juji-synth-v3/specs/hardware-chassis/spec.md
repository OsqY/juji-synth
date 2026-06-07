## ADDED Requirements

### Requirement: Hardware chassis frame
The main synth interface SHALL be wrapped in a hardware-style chassis frame with visual depth.

#### Scenario: Chassis boundary
- **WHEN** the app is displayed
- **THEN** the main content area has a visible outer border with gradient shading giving it a 3D chassis appearance

#### Scenario: Panel texture
- **WHEN** looking at the chassis interior
- **THEN** a subtle texture pattern is visible (noise dots or subtle gradient)

#### Scenario: Screw-hole indicators
- **WHEN** the chassis is displayed
- **THEN** small circular indicators are shown at each corner, resembling hardware screw holes

### Requirement: Section dividers
The system SHALL visually separate different control sections within a panel using hardware-style dividers.

#### Scenario: Divider between sections
- **WHEN** two control groups are adjacent
- **THEN** a visual divider separates them (grooved line or raised ridge)

### Requirement: Knob layout within chassis
Knobs SHALL be arranged in a grid pattern matching hardware synthesizer front panels.

#### Scenario: Knob grid
- **WHEN** a panel shows multiple knobs
- **THEN** they are arranged in rows with consistent spacing, labeled below each knob

#### Scenario: Visual grouping
- **WHEN** knobs belong to a subgroup (e.g., OSC1 vs OSC2)
- **THEN** they are visually grouped with subtle background shading or borders
