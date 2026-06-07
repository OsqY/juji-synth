## MODIFIED Requirements

### Requirement: Shared pointer tracking with cleanup
The keyboard SHALL track all active pointers in a single shared map. When the gesture coroutine is cancelled (due to octave change, screen rotation, or Activity recreation), all tracked notes SHALL be released.

#### Scenario: Octave change releases notes
- **WHEN** user changes the octave while holding keys
- **THEN** all held notes are released cleanly before the keyboard shifts

#### Scenario: Screen rotation releases notes
- **WHEN** the device is rotated while keys are held
- **THEN** all held notes are released cleanly

#### Scenario: Four simultaneous touches released
- **WHEN** user presses four keys and releases all four in any order
- **THEN** all four notes stop cleanly without any stuck notes
