## ADDED Requirements

### Requirement: Preset browser SHALL be a slide-out drawer
The preset selection interface SHALL slide up from the bottom of the screen as a drawer, rather than appearing as a full-screen dialog. The synth controls SHALL remain partially visible behind the drawer.

#### Scenario: Opening preset browser
- **WHEN** the user taps the preset button
- **THEN** a drawer slides up from the bottom
- **AND** the synth UI remains visible behind it
- **AND** the drawer can be dismissed by tapping outside or swiping down

### Requirement: Drawer SHALL show category and preset lists
The drawer SHALL display three columns: category list (left), preset list (center), and preset details (right). Tapping a category filters the preset list.

#### Scenario: Browsing presets
- **WHEN** the drawer is open
- **THEN** categories are shown on the left
- **AND** presets in the selected category are shown in the center
- **AND** tapping a preset loads it and closes the drawer
