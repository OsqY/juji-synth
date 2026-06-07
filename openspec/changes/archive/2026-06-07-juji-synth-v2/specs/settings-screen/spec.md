## ADDED Requirements

### Requirement: Settings screen accessible from main UI
The system SHALL provide a settings screen accessible via a gear icon in the top bar of the main synth interface.

#### Scenario: Open settings screen
- **WHEN** user taps the gear icon in the top bar
- **THEN** a settings bottom sheet opens displaying audio configuration options

#### Scenario: Close settings screen
- **WHEN** user taps outside the settings sheet or presses back
- **THEN** the settings sheet closes and the user returns to the main synth interface

### Requirement: Sample rate configuration
The system SHALL allow the user to select the audio sample rate from 44100 Hz or 48000 Hz.

#### Scenario: Change sample rate
- **WHEN** user selects a different sample rate in settings
- **THEN** the audio engine restarts with the new sample rate on next app launch

#### Scenario: Default sample rate
- **WHEN** the app is first installed
- **THEN** the sample rate defaults to 44100 Hz

### Requirement: Buffer size configuration
The system SHALL allow the user to select the audio buffer size from 128, 256, or 512 samples.

#### Scenario: Change buffer size
- **WHEN** user selects a different buffer size in settings
- **THEN** the audio engine uses the new buffer size on next app launch

#### Scenario: Default buffer size
- **WHEN** the app is first installed
- **THEN** the buffer size defaults to 256 samples

### Requirement: Output mode configuration
The system SHALL allow the user to select mono or stereo audio output.

#### Scenario: Change output mode
- **WHEN** user selects mono or stereo in settings
- **THEN** the audio engine uses the selected output mode on next app launch

#### Scenario: Default output mode
- **WHEN** the app is first installed
- **THEN** the output mode defaults to stereo

### Requirement: Settings persistence
The system SHALL persist all settings across app restarts using DataStore preferences.

#### Scenario: Settings survive restart
- **WHEN** user changes settings and restarts the app
- **THEN** the previously selected settings are restored
