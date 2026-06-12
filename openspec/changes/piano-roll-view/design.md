## Context

The piano keyboard has been iterated through 12 versions. Users report the black keys are hard to distinguish from white keys. The current rendering uses rectangles with shadows and highlights, but the visual separation between black and white keys is insufficient. Adding a piano roll view provides an alternative composition method.

## Goals / Non-Goals

**Goals:**
- Black keys visibly separate from white keys (gap, border, or lighter boundary)
- Piano roll grid with tap-to-toggle notes
- View toggle button (Piano ↔ Piano Roll)
- Panic button to stop all sound

**Non-Goals:**
- No changes to audio engine
- Piano roll doesn't need to play back — just visual composition

## Decisions

### 1. Black Key Separation

**Decision:** Add a 1dp gap between black and white keys by reducing black key width from 60% to 55% of white key width. Add a subtle lighter border on the right edge of each black key.

```kotlin
val blackKeyWidth = keyWidth * 0.55f  // was 0.6f
```

Also add a visible gap beneath each black key (at the bottom of the key area) so the edge is clearly visible.

### 2. Piano Roll View

**Decision:** New composable `PianoRollView` that renders a scrollable grid. The grid has:
- Y-axis: MIDI notes (C2-C7, scrollable vertically)
- X-axis: 16 steps (matching sequencer grid)
- Each cell is 24×16dp, tappable to toggle
- Active cells filled with purple, inactive with dark background
- Note labels on the left edge (C, C#, D, etc.)
- Tapping plays the note (preview)

### 3. View Toggle

**Decision:** Add a button between the octave controls and the keyboard that switches between Piano and Piano Roll views.

### 4. Panic Button (already started)

**Decision:** Add a PANIC button in the toolbar that calls `SynthEngine.panic()` to stop all voices and reset the engine. Already implemented in C++ and Kotlin — needs JNI bridge and UI button.

## Risks / Trade-offs

[Risk] Piano roll takes more screen space than keyboard
→ Mitigation: Use same height as keyboard, make pitch axis scrollable

[Risk] Users might prefer one view over the other
→ Mitigation: Toggle remembers last selection

## Migration Plan

1. Add JNI bridge for panic + UI button
2. Fix black key separation
3. Create PianoRollView composable
4. Add view toggle to MainSynthScreen
5. Build and test