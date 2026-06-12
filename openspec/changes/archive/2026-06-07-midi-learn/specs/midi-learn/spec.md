## ADDED Requirements

### Requirement: MIDI Learn button
The system SHALL provide a MIDI Learn button in the toolbar that toggles MIDI learn mode on and off.

#### Scenario: Enter learn mode
- **WHEN** user taps the MIDI Learn button
- **THEN** learn mode activates and all mappable controls display a pulsing highlight

#### Scenario: Exit learn mode
- **WHEN** user taps the MIDI Learn button while in learn mode
- **THEN** learn mode deactivates and all highlights are removed

### Requirement: Tap to select control
In learn mode, tapping a knob SHALL select it for mapping without changing its value.

#### Scenario: Select knob for mapping
- **WHEN** user taps a knob while in learn mode
- **THEN** the knob displays a "selected" indicator (bright white border) and is ready to receive a MIDI mapping

#### Scenario: Tap does not change value in learn mode
- **WHEN** user taps a knob in learn mode
- **THEN** the knob's parameter value does not change

### Requirement: CC capture for mapping
When a control is selected and a MIDI CC is received, the system SHALL create a mapping from that CC to the selected parameter.

#### Scenario: Map CC to knob
- **WHEN** user selects filter cutoff knob and then presses CC74 on their MIDI controller
- **THEN** a mapping is created: CC74 → filter cutoff parameter

#### Scenario: Mapping replaces existing
- **WHEN** CC74 was previously mapped to another parameter and the user remaps it
- **THEN** the old mapping is replaced with the new one

### Requirement: Mapped CC applies parameter
When a mapped CC message arrives, the system SHALL apply the CC value to the mapped parameter.

#### Scenario: CC drives mapped parameter
- **WHEN** user moves CC74 on their MIDI controller and CC74 is mapped to filter cutoff
- **THEN** the filter cutoff changes according to the CC value (0-127 → 0.0-1.0)

#### Scenario: Unmapped CC falls through
- **WHEN** a MIDI CC arrives that has no mapping
- **THEN** the system applies default CC handling (CC1 = mod wheel, CC64 = sustain)

### Requirement: Mapping persistence
All MIDI mappings SHALL be persisted via DataStore and survive app restarts.

#### Scenario: Mappings persist across restart
- **WHEN** user maps CC74 to filter cutoff and restarts the app
- **THEN** CC74 still controls filter cutoff

### Requirement: Clear mappings
The system SHALL support clearing all mappings or a single mapping.

#### Scenario: Clear all mappings
- **WHEN** user long-presses the MIDI Learn button
- **THEN** all MIDI mappings are cleared

#### Scenario: Clear single mapping
- **WHEN** user is in learn mode and long-presses a mapped control
- **THEN** that control's mapping is removed
