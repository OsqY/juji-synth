## MODIFIED Requirements

### Requirement: Tooltip system
The system SHALL provide contextual tooltips on all knobs. Tooltips SHALL be positioned intelligently — above the knob when there is space, below the knob when the knob is near the top of the screen.

#### Scenario: Tooltip above knob
- **WHEN** user long-presses a knob in the lower portion of the screen
- **THEN** the tooltip appears above the knob

#### Scenario: Tooltip below knob
- **WHEN** user long-presses a knob near the top of the screen (top 40%)
- **THEN** the tooltip appears below the knob to avoid clipping
