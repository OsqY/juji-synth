## ADDED Requirements

### Requirement: Thread-safe note events
Note-on and note-off events SHALL be passed from the UI thread to the audio thread through a lock-free command queue, never by directly modifying shared voice state.

#### Scenario: Note played while audio processing
- **WHEN** user presses a key (UI thread)
- **THEN** the note event is pushed to a lock-free queue and processed on the next audio callback

#### Scenario: No data races
- **WHEN** noteOn and processAudio run concurrently
- **THEN** no shared mutable state is accessed without synchronization

### Requirement: Queue never blocks
The note queue SHALL use lock-free atomics so that the audio thread is never blocked by the UI thread.

#### Scenario: UI floods queue
- **WHEN** UI thread pushes events faster than audio thread drains them
- **THEN** excess events are silently dropped (queue does not block)
