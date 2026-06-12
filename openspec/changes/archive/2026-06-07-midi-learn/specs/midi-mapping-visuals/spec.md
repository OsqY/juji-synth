## ADDED Requirements

### Requirement: Learn mode visual feedback
When learn mode is active, all mappable controls SHALL display a pulsing amber highlight to indicate they are selectable.

#### Scenario: Pulsing highlight on all knobs
- **WHEN** learn mode is active
- **THEN** all SynthKnob instances display an animated pulsing border

### Requirement: Selected control visual
When a control is selected for mapping, it SHALL display a bright white border indicating it's awaiting a MIDI CC.

#### Scenario: Selected knob indicator
- **WHEN** a knob is tapped in learn mode
- **THEN** it shows a white border glow and is visually distinct from other controls

### Requirement: Mapped control indicator
When NOT in learn mode, controls that have a MIDI mapping SHALL display a small green dot indicator.

#### Scenario: Mapped knob dot
- **WHEN** filter cutoff is mapped to CC74 and learn mode is not active
- **THEN** a small green dot appears in the corner of the filter cutoff knob

#### Scenario: Unmapped knob has no indicator
- **WHEN** a knob has no MIDI mapping
- **THEN** no indicator is shown
