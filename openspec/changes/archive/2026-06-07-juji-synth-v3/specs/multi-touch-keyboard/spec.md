## ADDED Requirements

### Requirement: Multi-touch keyboard
The keyboard SHALL support up to 4 simultaneous touches for polyphonic note playback.

#### Scenario: Play chord with two fingers
- **WHEN** user presses two keys simultaneously
- **THEN** both notes play simultaneously (two voices)

#### Scenario: Play chord with four fingers
- **WHEN** user presses four keys simultaneously
- **THEN** all four notes play simultaneously (four voices)

#### Scenario: Fifth note steals oldest
- **WHEN** user presses a fifth key while four notes are already playing
- **THEN** the oldest note is released (voice stealing) and the new note plays

### Requirement: Note-on on press, note-off on release
Each touch on the keyboard SHALL trigger note-on when pressed and note-off when released.

#### Scenario: Single note press and release
- **WHEN** user presses a key and releases it
- **THEN** note-on fires on press and note-off fires on release

#### Scenario: Hold note while pressing others
- **WHEN** user presses key 1, then presses key 2 while still holding key 1, then releases key 2
- **THEN** key 1 continues sounding until released

### Requirement: Black key detection
The keyboard SHALL correctly identify black key presses by position.

#### Scenario: Black key press
- **WHEN** user presses the C# key (first black key)
- **THEN** the synth plays note C# (MIDI note 49)

#### Scenario: Adjacent white/black key distinction
- **WHEN** user presses the C key (white) vs C# key (black, slightly offset)
- **THEN** the correct note is played for each position

### Requirement: Glissando (drag across keys)
The keyboard SHALL support dragging a finger across keys, triggering note-off on the previous key and note-on on the new key.

#### Scenario: Drag across keys
- **WHEN** user presses C4 and drags finger to D4 without lifting
- **THEN** C4 stops and D4 starts sounding

### Requirement: Keyboard visual feedback
The keyboard SHALL visually highlight pressed keys and show released keys as inactive.

#### Scenario: Key press highlight
- **WHEN** a key is pressed
- **THEN** the key appears lit/depressed with a brighter color

#### Scenario: Key release
- **WHEN** a key is released
- **THEN** the key returns to its normal appearance
