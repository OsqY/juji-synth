## ADDED Requirements

### Requirement: Each knob SHALL have a small LCD-style value display
Every parameter control SHALL have a small readout showing the current formatted value (e.g., "440Hz", "0.7s", "Saw", "60%").

#### Scenario: Value readout visibility
- **WHEN** the user adjusts any knob
- **THEN** the LCD display updates in real-time
- **AND** the value is formatted with appropriate units

### Requirement: LCD displays SHALL have a hardware aesthetic
Value displays SHALL use a monospace font, dark background with light text, and a subtle screen glow effect to mimic a backlit LCD panel.

#### Scenario: LCD appearance
- **WHEN** viewing any parameter section
- **THEN** value displays appear as small rectangular screens
- **AND** text is legible with appropriate contrast
- **AND** the display has a slight inner glow
