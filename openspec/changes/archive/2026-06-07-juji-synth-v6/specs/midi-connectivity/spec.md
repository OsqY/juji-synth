## MODIFIED Requirements

### Requirement: USB MIDI device detection
The synthesizer SHALL detect USB MIDI devices connected to the Android device. The MIDI controller SHALL be instantiated and started when the main screen is displayed.

#### Scenario: MIDI device connected
- **WHEN** user connects a USB MIDI controller
- **THEN** the MidiController starts scanning and detects the device

#### Scenario: MIDI lifecycle
- **WHEN** the main screen is displayed
- **THEN** the MidiController is instantiated and startScanning is called

#### Scenario: MIDI cleanup
- **WHEN** the main screen is removed
- **THEN** stopScanning is called on the MidiController
