## MODIFIED Requirements

### Requirement: USB MIDI device detection
The MIDI controller SHALL be instantiated and started when the main screen is displayed, and stopped when it is removed.

#### Scenario: MIDI auto-start
- **WHEN** the main screen is displayed
- **THEN** MidiController.startScanning() is called

#### Scenario: MIDI auto-stop
- **WHEN** the main screen is removed
- **THEN** MidiController.stopScanning() is called

### Requirement: MIDI sustain pedal
The MIDI controller SHALL handle CC64 messages to hold and release sustained notes.

#### Scenario: Sustain holds note
- **WHEN** sustain pedal is active and a note-off is received
- **THEN** the note is held until sustain pedal is released
