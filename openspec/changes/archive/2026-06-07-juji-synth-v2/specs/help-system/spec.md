## MODIFIED Requirements

### Requirement: Tooltip system
The synthesizer SHALL provide contextual tooltips on all interactive controls. Tooltips SHALL appear on long-press (500ms hold) and display the control name, current value, and a brief description of the parameter's function.

#### Scenario: Tooltip appears on long-press
- **WHEN** user long-presses (500ms) on any knob or control
- **THEN** a tooltip popup appears showing control name, current value, and parameter description

#### Scenario: Tooltip dismisses on release
- **WHEN** user releases the control after viewing tooltip
- **THEN** tooltip disappears

#### Scenario: Tooltip on knob
- **WHEN** user long-presses the filter cutoff knob
- **THEN** tooltip shows "Cutoff", current value (e.g., "2.5kHz"), and description "Sets the frequency point where the filter begins to attenuate"

### Requirement: Control identification
Every interactive control SHALL be clearly labeled with its function name displayed below or beside the control.

#### Scenario: Labeled control
- **WHEN** user looks at any knob
- **THEN** the knob has a visible label identifying its function (e.g., "Cutoff", "Attack", "LFO Rate")

### Requirement: In-app manual
The synthesizer SHALL include a comprehensive searchable user manual accessible via the HELP button.

#### Scenario: Help button opens manual
- **WHEN** user taps the HELP button
- **THEN** the full user manual screen opens with table of contents

### Requirement: Manual sections
The manual SHALL include at minimum the following sections: Getting Started, Understanding Subtractive Synthesis, Oscillator Guide, Filter Guide, Envelope Guide, LFO Guide, Effects Guide, Modulation Matrix Guide, Sequencer Guide, Preset Management, MIDI Setup, Troubleshooting.

#### Scenario: Navigate manual section
- **WHEN** user taps "Filter Guide" in manual table of contents
- **THEN** manual displays the Filter Guide section with detailed explanation

### Requirement: Searchable manual
The manual SHALL support text search allowing users to find specific topics or terms.

#### Scenario: Search for term
- **WHEN** user types "resonance" in manual search
- **THEN** manual displays all sections mentioning "resonance"

### Requirement: Tutorial presets
The manual SHALL include links to specific presets that demonstrate concepts being explained.

#### Scenario: Tutorial link in manual
- **WHEN** manual explains filter resonance and mentions "see example in Bright Pad preset"
- **THEN** tapping that link loads the Bright Pad preset for experimentation
