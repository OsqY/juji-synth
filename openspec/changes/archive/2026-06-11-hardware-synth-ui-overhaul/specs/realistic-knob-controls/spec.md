## ADDED Requirements

### Requirement: Knobs SHALL look like photorealistic rotary encoders
All parameter controls SHALL be rendered as rotary knobs with: a metal rim, tick marks around the perimeter, a pointer needle, and a colored LED ring that illuminates when the value is above zero.

#### Scenario: Knob appearance
- **WHEN** viewing any parameter knob
- **THEN** the knob has a 3D metallic appearance with depth shadow
- **AND** tick marks are visible around the perimeter
- **AND** an arc highlights the active value range
- **AND** a pointer needle indicates the current position

### Requirement: Knobs SHALL support vertical drag gesture
Dragging vertically on a knob SHALL change its value. The change SHALL be proportional to drag distance with fine control (small movements = small changes).

#### Scenario: Adjusting a parameter
- **WHEN** the user drags vertically upward on a knob
- **THEN** the value increases smoothly
- **AND** the knob rotates clockwise to match
- **AND** haptic feedback triggers at major tick marks

### Requirement: Knobs SHALL have color-coded LED rings
Each section's knobs SHALL have a distinct LED ring color: amber for oscillators, cyan for filter, green for envelopes, pink for LFO, red for effects.

#### Scenario: Visual section identification
- **WHEN** viewing the effects section
- **THEN** all knobs in that section have red LED rings
- **AND** the LED intensity increases with the parameter value
