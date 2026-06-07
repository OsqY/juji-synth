## ADDED Requirements

### Requirement: Combined knob gesture handler
The knob SHALL detect both drag and long-press gestures within a single pointer input block, without using a separate overlay composable.

#### Scenario: Drag changes value
- **WHEN** user presses and drags up/down on a knob
- **THEN** the value changes immediately (no delay) in response to the drag

#### Scenario: Long-press shows tooltip
- **WHEN** user presses and holds still for 500ms on a knob
- **THEN** a tooltip popup appears with the parameter name, value, and description

#### Scenario: Movement cancels long-press
- **WHEN** user starts to press and hold, but moves their finger before 500ms
- **THEN** the gesture becomes a drag (value change) and no tooltip appears

### Requirement: No gesture competition
The knob SHALL NOT have any transparent overlay composables that intercept touch events.

#### Scenario: Single gesture layer
- **WHEN** user touches a knob
- **THEN** only one composable layer handles the touch (no sibling or parent gesture detectors in the same touch region)
