## ADDED Requirements

### Requirement: Tab bar for panel navigation
The system SHALL provide a tab bar at the top of the synth interface allowing the user to switch between parameter panels.

#### Scenario: Switch to Oscillator tab
- **WHEN** user taps the "OSC" tab
- **THEN** the Oscillator panel is displayed in the content area and all other panels are hidden

#### Scenario: Switch to Filter tab
- **WHEN** user taps the "FILTER" tab
- **THEN** the Filter panel is displayed in the content area and all other panels are hidden

#### Scenario: Active tab indicator
- **WHEN** a tab is selected
- **THEN** it is visually highlighted with the accent color and appears raised (3D effect)

### Requirement: Only one panel visible at a time
The system SHALL display exactly one parameter panel at a time in the content area.

#### Scenario: Exclusive panel display
- **WHEN** the "ENV" tab is active
- **THEN** only the Envelope panel is shown; Oscillator, Filter, LFO, Effects, and Sequencer panels are hidden

### Requirement: Tab labels
The system SHALL provide tabs for: OSC, FILTER, ENV, LFO, FX, SEQ, MOD MATRIX.

#### Scenario: All tabs present
- **WHEN** the tab bar is displayed
- **THEN** all seven tabs are visible and labeled

### Requirement: Tab persistence
The system SHALL remember the selected tab when switching between tabs.

#### Scenario: Tab stays selected
- **WHEN** user switches from OSC to FILTER and back to OSC
- **THEN** the OSC panel returns to its previous state without losing parameter changes
