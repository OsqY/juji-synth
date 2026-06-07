## MODIFIED Requirements

### Requirement: USB MIDI device detection
The synthesizer SHALL detect USB MIDI devices. The multi-touch keyboard SHALL handle MIDI note-on/note-off messages for polyphonic playback, matching the behavior of on-screen touches.

#### Scenario: MIDI note-on triggers synth
- **WHEN** MIDI note-on message for C4 with velocity 100 is received
- **THEN** synth voice plays note C4 at velocity corresponding to 100

#### Scenario: MIDI note-off releases voice
- **WHEN** MIDI note-off message for C4 is received
- **THEN** synth voice for C4 enters release phase

#### Scenario: Polyphonic MIDI playback
- **WHEN** MIDI note-on messages for C4, E4, and G4 are received simultaneously
- **THEN** all three notes play simultaneously using separate voices
