## Why

The keyboard has two usability problems: black keys are hard to distinguish from white keys due to insufficient visual separation, and there's only one view mode (piano keyboard). Adding a piano roll view — a grid-based note entry interface — gives users an alternative way to compose and visualize notes that's common in modern music production.

## What Changes

- **Improve black key visual separation**: Add a subtle gap or lighter border between white and black keys. Slightly reduce black key width to create visible space. Ensure black key side edges are clearly distinct from adjacent white keys.
- **Add piano roll view**: New toggleable view mode that replaces the piano keyboard with a horizontal grid. Y-axis = note pitch (scrollable), X-axis = time. Tap grid cells to toggle notes. Shows active notes as filled cells.
- **View toggle button**: Add a button in the keyboard area to switch between Piano and Piano Roll views.

## Capabilities

### New Capabilities
- `piano-roll-view`: Grid-based note entry with scrollable pitch axis and time-axis steps

### Modified Capabilities
- `keyboard-note-labels`: Improved black key visual separation with gaps and clearer borders
- `keyboard-scrolling`: Removed (replaced by piano roll for composition use cases)