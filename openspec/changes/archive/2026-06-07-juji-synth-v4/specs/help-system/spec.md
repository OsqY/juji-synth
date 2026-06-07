## MODIFIED Requirements

### Requirement: Tooltip system
The synthesizer SHALL provide contextual tooltips on all interactive controls. Tooltips SHALL appear on long-press (500ms hold) and display the control name, current value, and a brief description of the parameter's function. The tooltip SHALL auto-dismiss after 2 seconds.

#### Scenario: Tooltip appears on long-press
- **WHEN** user long-presses (500ms) on any knob or control
- **THEN** a tooltip popup appears showing control name, current value, and parameter description

#### Scenario: Tooltip dismisses after timeout
- **WHEN** the tooltip has been visible for 2 seconds
- **THEN** it automatically disappears

#### Scenario: Tooltip shows param value
- **WHEN** user long-presses the filter cutoff knob
- **THEN** tooltip shows "Cutoff", current value (e.g., "80%"), and description "Sets the frequency point where the filter begins to attenuate"

### Requirement: Control identification
Every interactive control SHALL be clearly labeled with its function name.

#### Scenario: Labeled control
- **WHEN** user looks at any knob
- **THEN** the knob has a visible label identifying its function (e.g., "Cutoff", "Attack", "LFO Rate")
