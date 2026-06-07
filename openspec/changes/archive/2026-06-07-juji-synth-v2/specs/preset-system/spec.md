## MODIFIED Requirements

### Requirement: Preset storage system
The synthesizer SHALL include a preset storage system backed by a Room database, allowing saving, loading, and managing synthesizer parameter configurations. The preset browser SHALL display presets in a categorized list with visual indicators for factory vs user presets.

#### Scenario: Save preset
- **WHEN** user saves current synth state as a preset with name "My Lead"
- **THEN** preset "My Lead" is stored in the Room database with all current parameter values

#### Scenario: Load preset
- **WHEN** user selects preset "My Lead" from the browser
- **THEN** all synthesizer parameters are restored to the values saved in that preset

### Requirement: Preset categories
Presets SHALL be organized into the following categories: Leads, Pads, Bass, FX, Ambient, User. The browser SHALL display category tabs for filtering.

#### Scenario: Category filtering
- **WHEN** user selects "Pads" category tab
- **THEN** only presets categorized as pads are displayed

### Requirement: Factory presets
The synthesizer SHALL include 80 factory presets covering all categories, demonstrating the full range of the synth's capabilities. Factory presets SHALL be visually distinguished from user presets.

#### Scenario: Factory preset accessible
- **WHEN** user browses factory presets
- **THEN** presets are organized by category and include descriptive names

#### Scenario: Factory preset count
- **WHEN** the database is first seeded
- **THEN** exactly 80 factory presets are present: 20 Leads, 20 Pads, 15 Bass, 15 FX, 10 Ambient

### Requirement: User preset creation
Users SHALL be able to create, save, and manage custom presets via a save dialog with name input and category selection.

#### Scenario: Save custom preset
- **WHEN** user configures synth parameters, taps Save, enters name "My Custom Sound" and selects category
- **THEN** "My Custom Sound" appears in user presets list

#### Scenario: Delete custom preset
- **WHEN** user long-presses a custom preset and confirms deletion
- **THEN** preset is removed from storage and no longer appears in list

### Requirement: Preset overwrite
Users SHALL be able to overwrite existing custom presets with current parameter state.

#### Scenario: Overwrite existing preset
- **WHEN** user modifies parameters and saves to existing custom preset "My Custom Sound"
- **THEN** "My Custom Sound" now contains the new parameter values

### Requirement: Preset parameters
Each preset SHALL store complete synthesizer state including: oscillator configurations, filter settings, envelope settings, LFO configurations, effect settings, modulation routing configurations, sequencer pattern (if any). State SHALL be serialized as JSON.

#### Scenario: Full state restoration
- **WHEN** preset "Bright Pad" is loaded
- **THEN** all parameters (oscillators, filter, envelopes, LFOs, effects, modulation) are set to stored values
