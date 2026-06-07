## ADDED Requirements

### Requirement: Shared pointer tracking
The keyboard SHALL track all active pointers in a single shared map within one awaitPointerEventScope, processing ALL pointer events without dropping events for any pointer.

#### Scenario: Four simultaneous touches
- **WHEN** user presses four keys simultaneously
- **WHEN** all four keys are released in any order
- **THEN** all four notes play and all four stop cleanly without any stuck notes

#### Scenario: Rapid sequential touches
- **WHEN** user presses and releases keys rapidly in sequence
- **THEN** no notes become stuck

#### Scenario: Pointer down event not lost
- **WHEN** one finger is held and a second finger goes down
- **THEN** the second note starts playing immediately (its down event is not consumed by the first finger's event handler)
