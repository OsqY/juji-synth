## ADDED Requirements

### Requirement: The main screen SHALL display all synth sections simultaneously
The synth UI SHALL present all parameter sections (OSC, FILTER, ENV, LFO, FX, MOD, SEQ) on a single screen without tab switching. Sections SHALL be organized in a grid layout that mimics hardware synthesizer front panels.

#### Scenario: User opens the app
- **WHEN** the app launches
- **THEN** all sections are visible simultaneously
- **AND** no tab bar is present at the top of the screen

#### Scenario: User adjusts oscillator and filter without switching views
- **WHEN** the user tweaks an oscillator parameter
- **THEN** the filter section remains visible
- **AND** the user can immediately adjust filter parameters without any navigation

### Requirement: Sections SHALL be visually grouped with hardware-style panel borders
Each section SHALL have a distinct panel with brushed metal appearance, screw head decorations at corners, and subtle inner shadow to create depth.

#### Scenario: Visual panel identification
- **WHEN** viewing the main screen
- **THEN** each section has a clear border and title label
- **AND** screw heads are visible at the four corners of each panel
- **AND** panels have a 3D beveled appearance
