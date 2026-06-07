## MODIFIED Requirements

### Requirement: Multi-touch keyboard
The keyboard SHALL support up to 4 simultaneous touches without losing note-off events. Each pointer SHALL be tracked in an independent gesture coroutine so that gesture cancellation does not prevent note-off from firing.

#### Scenario: Play chord with two fingers
- **WHEN** user presses two keys simultaneously and releases both
- **THEN** both notes play and both stop cleanly without stuck notes

#### Scenario: Play chord with four fingers
- **WHEN** user presses four keys simultaneously and releases in any order
- **THEN** all four notes play and each stops when its finger is released

#### Scenario: Rapid sequential presses
- **WHEN** user presses and releases keys rapidly in sequence
- **THEN** no notes become stuck

### Requirement: Octave shift support
The keyboard SHALL accept an octaveOffset parameter that shifts all note values by octaveOffset × 12 semitones.

#### Scenario: Octave shifted keyboard
- **WHEN** octaveOffset is 4 (C4)
- **THEN** pressing the first white key plays MIDI note 60 (C4)

### Requirement: Note-on on press, note-off on release
Each touch on the keyboard SHALL trigger note-on when pressed and note-off when released, even during multi-touch.

#### Scenario: Single note press and release
- **WHEN** user presses a key and releases it
- **THEN** note-on fires on press and note-off fires on release

#### Scenario: Hold note while pressing others
- **WHEN** user presses key 1, then presses key 2 while still holding key 1, then releases key 2
- **THEN** key 1 continues sounding until released

### Requirement: Glissando (drag across keys)
The keyboard SHALL support dragging a finger across keys, triggering note-off on the previous key and note-on on the new key.

#### Scenario: Drag across keys
- **WHEN** user presses C4 and drags finger to D4 without lifting
- **THEN** C4 stops and D4 starts sounding
