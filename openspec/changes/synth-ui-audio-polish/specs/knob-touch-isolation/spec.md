## ADDED Requirements

### Requirement: Adjacent knob touch isolation
Knobs SHALL NOT bleed touch input to adjacent knobs when drag-gestures are near the widget boundary.

- RealKnob SHALL implement a minimum drag distance threshold (touch slop) before registering a value change
- Adjacent knobs in all panels SHALL have minimum horizontal spacing of 4dp between hit areas
- Knobs SHALL NOT respond to drag gestures that start outside their visual bounds
- The touch slop SHALL be at minimum 8dp from touch-down position before drag is activated
- This SHALL apply to ALL panels using RealKnob: Oscillator, Filter, Envelope, Effects, LFO

#### Scenario: Touch slop prevents accidental adjustment
- **WHEN** user places finger on a knob but moves less than 8dp
- **THEN** the knob does NOT change value
- **WHEN** user moves finger more than 8dp
- **THEN** the knob begins to adjust value as expected

#### Scenario: Adjacent knobs don't interfere
- **WHEN** user drags the Sustain knob vertically
- **THEN** only the Sustain knob changes value
- **AND** the Decay knob value remains unchanged
- **AND** the Attack knob value remains unchanged
- **AND** the Release knob value remains unchanged
