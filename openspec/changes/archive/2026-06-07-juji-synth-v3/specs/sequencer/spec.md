## MODIFIED Requirements

### Requirement: 16-step sequencer
The synthesizer SHALL include a 16-step sequencer accessible via its own tab with a full-width step grid, transport controls, and tempo control. A mini transport bar (play/stop/tempo) SHALL remain visible at the bottom when other tabs are active.

#### Scenario: Step plays note
- **WHEN** sequencer is running and playback reaches step 5
- **THEN** the note configured for step 5 is triggered and the step LED illuminates

#### Scenario: Sequencer tab full-width
- **WHEN** the SEQ tab is selected
- **THEN** the sequencer fills the available width with a 16-step grid, per-step controls, and transport

#### Scenario: Mini transport always visible
- **WHEN** any non-SEQ tab is selected
- **THEN** a compact transport bar with play/stop and tempo is visible at the bottom of the screen
