## MODIFIED Requirements

### Requirement: Multi-mode filter
The synthesizer SHALL include a filter displayed as a full-width tab panel with large (80-100dp) 3D knobs for cutoff and resonance, and a visible envelope curve overlay.

#### Scenario: Filter processes incoming audio
- **WHEN** audio passes through the filter
- **THEN** frequencies outside the passband are attenuated according to filter mode and cutoff setting

#### Scenario: Filter panel full-width
- **WHEN** the FILTER tab is selected
- **THEN** the filter panel fills the available width with large cutoff and resonance knobs, mode selector buttons, and a visual envelope curve display
