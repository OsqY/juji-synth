## MODIFIED Requirements

### Requirement: Tooltip system
The system SHALL provide contextual tooltips on all knobs. Tooltips SHALL appear on long-press (500ms hold without movement) and display the control name, current value, and a brief description.

#### Scenario: Tooltip appears on long-press
- **WHEN** user long-presses (500ms) on any knob without moving their finger
- **THEN** a tooltip popup appears showing control name, current value, and parameter description

#### Scenario: Tooltip does not interfere with drag
- **WHEN** user starts a long-press but moves their finger before 500ms
- **THEN** the knob responds to drag normally and no tooltip appears
