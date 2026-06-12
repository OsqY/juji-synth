## ADDED Requirements

### Requirement: DAW-style piano roll
The piano roll view SHALL be a full pattern editor with variable-length notes, velocity editing, and playback synchronized with the sequencer.

- The piano roll SHALL display a grid with MIDI notes (C2-C7) as rows and time steps as columns
- Users SHALL be able to tap and drag on the grid to create notes of variable length and position
- Existing notes SHALL be draggable to new positions (horizontal = time, vertical = pitch)
- Existing notes SHALL be resizable by dragging their left or right edge
- Notes SHALL have velocity (0-127), visualized by color intensity or a separate velocity lane
- A playhead SHALL move across the grid in sync with the sequencer tempo
- The pattern SHALL play back through the synth engine when transport is playing
- Notes SHALL NOT play immediately when placed — they only play when the transport is running
- The piano roll SHALL support toggling between the existing 16-step sequencer grid and the new DAW-style view

#### Scenario: Create a note by tapping
- **WHEN** user taps on an empty cell in the piano roll grid
- **THEN** a note is created at that pitch and position with default length (1 step) and velocity (100)
- **AND** the note does NOT play immediately (waits for transport)

#### Scenario: Drag to create variable-length note
- **WHEN** user taps and drags horizontally on an empty cell
- **THEN** a note is created spanning from the start position to the release position
- **AND** the note length visually reflects the drag distance

#### Scenario: Move an existing note
- **WHEN** user drags an existing note vertically
- **THEN** the note's pitch changes to match the row position
- **WHEN** user drags an existing note horizontally
- **THEN** the note's start position shifts by the drag amount

#### Scenario: Resize a note
- **WHEN** user drags the right edge of an existing note
- **THEN** the note duration increases or decreases

#### Scenario: Pattern playback
- **WHEN** user presses Play on the transport
- **THEN** the playhead moves across the piano roll grid at sequencer tempo
- **AND** notes at the playhead position trigger noteOn/noteOff on the synth engine
- **AND** the pattern loops when it reaches the end

#### Scenario: Velocity editing
- **WHEN** user long-presses an existing note
- **THEN** a velocity slider or velocity lane becomes visible for that note
- **AND** user can adjust the note's velocity (0-127)
