## ADDED Requirements

### Requirement: Room database initialization
The system SHALL initialize a Room database on first app launch and seed it with factory presets.

#### Scenario: First launch database creation
- **WHEN** the app is launched for the first time
- **THEN** a Room database is created at "juji-synth.db" and populated with 80 factory presets

#### Scenario: Subsequent launches
- **WHEN** the app is launched after the first time
- **THEN** the existing database is opened without re-seeding factory presets

### Requirement: Preset save functionality
The system SHALL allow users to save the current synth state as a named preset with a category.

#### Scenario: Save a new preset
- **WHEN** user taps the Save button and enters a name and category
- **THEN** a new preset is created in the database with all current synth parameters

#### Scenario: Save with empty name
- **WHEN** user attempts to save a preset with an empty name
- **THEN** the system shows a validation error and does not save

### Requirement: Preset load functionality
The system SHALL allow users to load any preset from the database, applying all stored parameters to the synth engine.

#### Scenario: Load a factory preset
- **WHEN** user taps a factory preset in the browser
- **THEN** all synth parameters are updated to match the preset values via JNI calls

#### Scenario: Load a user preset
- **WHEN** user taps a user preset in the browser
- **THEN** all synth parameters are updated to match the preset values via JNI calls

### Requirement: Preset delete functionality
The system SHALL allow users to delete their own custom presets but not factory presets.

#### Scenario: Delete a user preset
- **WHEN** user long-presses a user preset and confirms deletion
- **THEN** the preset is removed from the database

#### Scenario: Factory preset not deletable
- **WHEN** user long-presses a factory preset
- **THEN** no delete option is shown

### Requirement: Preset categories
The system SHALL organize presets into categories: Leads, Pads, Bass, FX, Ambient, and User.

#### Scenario: Filter by category
- **WHEN** user selects a category tab in the preset browser
- **THEN** only presets in that category are displayed

#### Scenario: All categories tab
- **WHEN** user selects the "All" tab
- **THEN** all presets across all categories are displayed

### Requirement: Factory preset library
The system SHALL include 80 factory presets distributed across categories: 20 Leads, 20 Pads, 15 Bass, 15 FX, 10 Ambient.

#### Scenario: Factory preset count
- **WHEN** the database is first seeded
- **THEN** exactly 80 factory presets are created with `isFactory = true`

#### Scenario: Factory preset categories
- **WHEN** factory presets are seeded
- **THEN** presets are distributed as: 20 Leads, 20 Pads, 15 Bass, 15 FX, 10 Ambient

### Requirement: Preset parameter serialization
The system SHALL serialize the complete SynthState as JSON for storage in the database.

#### Scenario: Save complete state
- **WHEN** a preset is saved
- **THEN** all synth parameters (oscillators, filter, envelopes, LFOs, effects, modulation, master) are serialized as JSON

#### Scenario: Load complete state
- **WHEN** a preset is loaded
- **THEN** all synth parameters are deserialized from JSON and applied to the synth engine
