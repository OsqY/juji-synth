## ADDED Requirements

### Requirement: Piano roll grid view
The system SHALL provide a piano roll grid view as an alternative to the piano keyboard. The grid SHALL display MIDI notes as rows and 16 steps as columns, with tappable cells to toggle notes on/off.

#### Scenario: Piano roll displayed
- **WHEN** user toggles to piano roll view
- **THEN** a grid is shown with note names on the left (C2-C7) and 16 step columns

#### Scenario: Tap cell toggles note
- **WHEN** user taps a cell in the piano roll
- **THEN** the cell toggles between active (filled) and inactive (empty)

#### Scenario: Tap plays preview
- **WHEN** user taps a cell
- **THEN** the corresponding MIDI note plays briefly as a preview

### Requirement: View toggle
The system SHALL provide a button to switch between piano keyboard view and piano roll view.

#### Scenario: Switch to roll
- **WHEN** user taps the roll toggle button
- **THEN** piano roll view replaces the piano keyboard

#### Scenario: Switch back to piano
- **WHEN** user taps the piano toggle button
- **THEN** piano keyboard replaces the piano roll view
