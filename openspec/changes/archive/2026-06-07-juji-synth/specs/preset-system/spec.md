## ADDED Requirements

### Requirement: Preset storage system
The synthesizer SHALL include a preset storage system allowing saving, loading, and managing synthesizer parameter configurations.

#### Scenario: Save preset
- **WHEN** user saves current synth state as a preset with name "My Lead"
- **THEN** preset "My Lead" is stored with all current parameter values

#### Scenario: Load preset
- **WHEN** user selects preset "My Lead"
- **THEN** all synthesizer parameters are restored to the values saved in that preset

### Requirement: Preset categories
Presets SHALL be organized into the following categories: Leads, Pads, Bass, FX, Ambient.

#### Scenario: Category filtering
- **WHEN** user selects "Pads" category
- **THEN** only presets categorized as pads are displayed

### Requirement: Factory presets
The synthesizer SHALL include 50-100 factory presets covering all categories, demonstrating the full range of the synth's capabilities.

#### Scenario: Factory preset accessible
- **WHEN** user browses factory presets
- **THEN** presets are organized by category and include descriptive names

### Requirement: User preset creation
Users SHALL be able to create, save, and manage custom presets.

#### Scenario: Save custom preset
- **WHEN** user configures synth parameters and saves as "My Custom Sound"
- **THEN** "My Custom Sound" appears in user presets list

#### Scenario: Delete custom preset
- **WHEN** user deletes custom preset "My Custom Sound"
- **THEN** preset is removed from storage and no longer appears in list

### Requirement: Preset overwrite
Users SHALL be able to overwrite existing custom presets with current parameter state.

#### Scenario: Overwrite existing preset
- **WHEN** user modifies parameters and saves to existing custom preset "My Custom Sound"
- **THEN** "My Custom Sound" now contains the new parameter values

### Requirement: Preset parameters
Each preset SHALL store complete synthesizer state including: oscillator configurations, filter settings, envelope settings, LFO configurations, effect settings, modulation routing configurations, sequencer pattern (if any).

#### Scenario: Full state restoration
- **WHEN** preset "Bright Pad" is loaded
- **THEN** all parameters (oscillators, filter, envelopes, LFOs, effects, modulation) are set to stored values