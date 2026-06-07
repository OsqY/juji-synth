## ADDED Requirements

### Requirement: Note velocity based on press speed
The keyboard SHALL vary note velocity based on how quickly the key is pressed and released. Faster presses produce louder notes.

#### Scenario: Quick press is quiet
- **WHEN** user taps a key very quickly (<100ms)
- **THEN** the note plays at velocity 50-60 (quiet)

#### Scenario: Slow press is loud
- **WHEN** user holds a key for longer (>300ms)
- **THEN** the note plays at velocity 100+ (loud)

#### Scenario: Normal press is medium
- **WHEN** user presses and releases a key at normal speed (100-300ms)
- **THEN** the note plays at velocity 60-100 (medium)
