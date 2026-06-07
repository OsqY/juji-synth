## MODIFIED Requirements

### Requirement: USB MIDI device detection
The synthesizer SHALL detect USB MIDI devices connected to the Android device and display connection status.

#### Scenario: MIDI device connected
- **WHEN** user connects a USB MIDI controller to the Android device
- **THEN** the device appears in the MIDI device list within the app and a connection indicator shows active

#### Scenario: MIDI device disconnected
- **WHEN** user disconnects a USB MIDI controller
- **THEN** the device is removed from the MIDI device list and MIDI input stops

### Requirement: MIDI note handling
The synthesizer SHALL receive MIDI note-on and note-off messages and trigger corresponding synth voices.

#### Scenario: MIDI note-on triggers synth
- **WHEN** MIDI note-on message for C4 with velocity 100 is received
- **THEN** synth voice plays note C4 at velocity corresponding to 100

#### Scenario: MIDI note-off releases voice
- **WHEN** MIDI note-off message for C4 is received
- **THEN** synth voice for C4 enters release phase

### Requirement: MIDI CC mapping
The synthesizer SHALL support configurable MIDI CC (Control Change) messages for external control of parameters.

#### Scenario: CC maps to filter cutoff
- **WHEN** user configures CC1 (mod wheel) to control filter cutoff
- **THEN** mod wheel movements on MIDI controller adjust filter cutoff

#### Scenario: Default CC mappings
- **WHEN** MIDI controller sends standard CC messages
- **THEN** default mappings apply (CC1 to mod wheel destinations, CC64 to sustain)

### Requirement: MIDI channel selection
The synthesizer SHALL support selection of MIDI channel (1-16) to respond to specific channels or omni mode.

#### Scenario: Channel-specific messages
- **WHEN** synth is set to channel 3 and receives MIDI message on channel 3
- **THEN** message is processed

#### Scenario: Omni mode
- **WHEN** synth is set to omni mode
- **THEN** messages on any MIDI channel are processed

### Requirement: MIDI device persistence
The synthesizer SHALL remember connected MIDI devices and automatically reconnect when the same device is plugged in again.

#### Scenario: Device reconnection
- **WHEN** previously used MIDI device is connected again
- **THEN** app automatically recognizes and enables MIDI input from that device without user intervention

#### Scenario: Device persistence across restarts
- **WHEN** app is restarted with a MIDI device connected
- **THEN** the device is automatically detected and connected on app startup
