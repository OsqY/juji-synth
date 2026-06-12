## ADDED Requirements

### Requirement: Chorus effect
The synthesizer SHALL include a stereo chorus effect that thickens the sound by mixing the dry signal with a delayed copy modulated by an internal LFO.

#### Scenario: Chorus enabled
- **WHEN** chorus mix is set above 0% and a note is played
- **THEN** the output is a blend of dry signal and modulated delayed signal, creating a thicker, wider sound

#### Scenario: Chorus parameters
- **WHEN** user adjusts rate, depth, or mix controls
- **THEN** the chorus character changes accordingly

### Requirement: Keyboard scrolling
The keyboard SHALL support horizontal scrolling to access more than 2 octaves.

#### Scenario: Scroll right
- **WHEN** user drags left on the keyboard
- **THEN** the keyboard scrolls to reveal higher notes

#### Scenario: Scroll left
- **WHEN** user drags right on the keyboard
- **THEN** the keyboard scrolls to reveal lower notes

### Requirement: Sustain pedal
The MIDI controller SHALL handle CC64 (sustain pedal) messages to hold notes after key release.

#### Scenario: Sustain on
- **WHEN** sustain pedal is pressed and user releases a key
- **THEN** the note continues sounding

#### Scenario: Sustain off
- **WHEN** sustain pedal is released
- **THEN** all sustained notes are released

### Requirement: Settings apply to audio engine
When sample rate or buffer size settings are changed, the system SHALL restart the audio engine with the new values.

#### Scenario: Change sample rate
- **WHEN** user changes sample rate from 44100 to 48000 Hz
- **THEN** the audio engine restarts at 48000 Hz
