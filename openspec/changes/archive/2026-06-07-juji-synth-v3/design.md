## Context

Juji-synth v2 has 25 Kotlin source files, 13 C++ source files, compiles to a debug APK, but is fundamentally broken in user experience. Extensive codebase analysis revealed:

- **7 critical bugs** in knob gesture handling, keyboard touch, black key detection, voice state management, preset loading, and state management architecture
- **UI shows all 6 parameter panels simultaneously** at widths <180dp each — too small for usable controls
- **No hardware aesthetic** — flat boxes, no depth, no chassis frame, no reference to real synth design
- **DRC comparison**: DRC uses tabbed panels showing one section at a time with full-width controls. Our approach of showing everything at once makes it look like a toy.

## Goals / Non-Goals

**Goals:**
- Fix all 7 identified critical bugs (knob gesture, keyboard multi-touch + black keys + glissando, voice state leak, preset load, state architecture)
- Re-architect UI to tabbed navigation: one panel at a time, full-width, large controls
- Achieve DRC-like hardware synth visual quality: chassis frame, 3D knobs, panel textures, proper keyboard
- Single-source-of-truth state: one SynthState object replaces 40+ state variables
- Wire preset loading to actually apply parameters

**Non-Goals:**
- New synthesis features (wavetable, FM, ring mod)
- Audio engine optimization
- iOS support
- Cloud/preset sharing
- Audio recording

## Decisions

### 1. Gesture Fix: `rememberUpdatedState` for Knob Values

**Decision:** Use `rememberUpdatedState(value)` to wrap the `value` parameter in `SynthKnob`, ensuring the drag gesture always reads the latest value.

```kotlin
val currentValue by rememberUpdatedState(value)

// In pointerInput:
detectDragGestures { change, dragAmount ->
    change.consume()
    val delta = -dragAmount.y / 200f
    val newValue = (currentValue + delta).coerceIn(minValue, maxValue)
    onValueChange(newValue)
}
```

**Rationale:** `rememberUpdatedState` creates a `State` reference that the `pointerInput` lambda reads on each invocation, always getting the latest value. Without this, the lambda captures the initial `value` and never updates.

**Alternatives:** Using `var` with `remember` and manually updating — more error-prone. `rememberUpdatedState` is the idiomatic Compose pattern.

### 2. Keyboard: Multi-Touch with Pointer Tracking

**Decision:** Replace `detectTapGestures(onPress)` with `pointerInput` + `awaitPointerEventScope` tracking individual pointer IDs.

```
                   ┌──────────────┐
                   │  Pointer     │
                   │  Down        │
                   └──────┬───────┘
                          │
                   ┌──────▼───────┐
                   │ Identify key │
                   │ (white/black)│
                   └──────┬───────┘
                          │
                   ┌──────▼───────┐
                   │ onNoteOn(n)  │
                   │ Store ptrID  │
                   └──────┬───────┘
                          │
              ┌───────────┴────────────┐
              │                        │
     ┌────────▼────────┐    ┌─────────▼────────┐
     │ Pointer Move    │    │ Pointer Release  │
     │ (glissando)     │    │                  │
     └────────┬────────┘    └─────────┬────────┘
              │                       │
     ┌────────▼────────┐    ┌─────────▼────────┐
     │ New key?        │    │ onNoteOff(n)     │
     │ Yes → noteOff   │    │ Remove ptrID     │
     │ old, noteOn new │    └──────────────────┘
     └─────────────────┘
```

Each pointer gets tracked in a map: `pointerId → note`. Multi-touch allows 4 simultaneous notes for polyphony. Movement between keys fires note-off on old + note-on on new for glissando.

**Black key detection:** The key layout has known positions. A touch at position (x,y) is a black key if it falls within any black key's defined rectangle. Otherwise it's a white key.

### 3. Tabbed Navigation Architecture

**Decision:** Replace the side-by-side Row layout with a tab bar + content area.

```
┌──────────────────────────────────────────────────────────┐
│  ┌──────┬──────┬──────┬──────┬──────┬──────┐             │
│  │ OSC  │ FLTR │ ENV  │ LFO  │ FX   │ SEQ  │ ← Tab Bar │
│  └──────┴──────┴──────┴──────┴──────┴──────┘             │
│  ┌──────────────────────────────────────────────────────┐│
│  │                                                       ││
│  │  Only the selected tab's panel is shown here          ││
│  │  Full width, full height, large controls              ││
│  │                                                       ││
│  │  HardwareChassis wraps this area with:                ││
│  │  - Outer gradient border (chassis frame)              ││
│  │  - Inner shadow for depth                             ││
│  │  - Subtle panel texture (noise pattern)               ││
│  │  - Screw-hole indicators at corners                   ││
│  │                                                       ││
│  └──────────────────────────────────────────────────────┘│
│  ┌──────────────────────────────────────────────────────┐│
│  │  Keyboard (with pitch bend strip)  |  Preset/Help    ││
│  └──────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────┘
```

Implementation:
- Row of `Tab` composables at the top (Material3 Tab or custom hardware-style tab)
- `when(selectedTab)` switches between panel composables
- Each panel composable is a full-width `Column` with large knobs/controls

### 4. Single Source of Truth for State

**Decision:** Replace all individual `mutableStateOf` variables in `MainSynthScreen` with a single `SynthState` object managed via `MutableState<SynthState>`.

```kotlin
data class SynthState(
    val osc1Level: Float = 1.0f,
    val osc2Level: Float = 1.0f,
    val filterCutoff: Float = 0.8f,
    // ... all parameters
)

// In composable:
var synthState by remember { mutableStateOf(SynthState()) }
```

Panel callbacks update the single object:
```kotlin
OscillatorPanel(
    state = synthState,
    onStateChange = { newState -> synthState = newState }
)
```

For preset loading, just set the entire state at once:
```kotlin
synthState = presetJson.decodeFromString<SynthState>(jsonStr)
// Then apply all params to engine
```

### 5. Voice State Fix (C++)

**Decision:** After processing each voice in `AudioEngine::processAudio()`, check if the voice's internal `active_` flag is false and synchronize `voiceActive_`.

```cpp
for (int v = 0; v < MAX_VOICES; v++) {
    if (voiceActive_[v] && !voices_[v].isActive()) {
        voiceActive_[v] = false;  // Voice completed its envelope cycle
    }
}
```

### 6. Hardware Visual Design

**Chassis frame:**
```kotlin
@Composable
fun HardwareChassis(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, Brush.linearGradient(listOf(darkEdge, lightEdge, darkEdge)), RoundedCornerShape(12.dp))
            .background(Brush.radialGradient(...)) // subtle panel texture
            .padding(16.dp) // inner shadow area
    ) {
        // Screw holes at corners
        Canvas(modifier = Modifier.matchParentSize()) {
            // Draw 4 small circles at corners
        }
        content()
    }
}
```

**3D Keyboard:**
- Each key drawn with Canvas: base rect → side shadow → top surface → highlight edge
- Black keys: taller, narrower, with depth on left/top edges
- Pressed state: key shifts down, shadow intensifies

**Large Knobs:**
- 80-100dp diameter (was 32-44dp)
- Same 3D rendering (radial gradient, metallic rim, shadow, indicator)
- Only possible because tabs free up horizontal space

## Risks / Trade-offs

[Risk] Tabbed navigation hides parameters from view — users can't see all controls at once
→ Mitigation: Match DRC's approach. Use visual tab indicators (colored dots, icons) so users quickly learn where each section is. Add a "Quick Overview" mode that shows all sections in compact form.

[Risk] Multi-touch keyboard adds complexity and potential for touch conflicts
→ Mitigation: Test with 2-3 finger chords. If issues arise, limit to 2 simultaneous touches initially and expand to 4.

[Risk] Single SynthState object may cause unnecessary recompositions when only one sub-field changes
→ Mitigation: Use `derivedStateOf` for panel-level reads, or nest `SnapshotMutationPolicy` to only update when relevant fields change. In practice, Compose's snapshot system handles field-level reads efficiently.

[Risk] Large 3D knobs (100dp) consume more screen real estate
→ Mitigation: Each tab gets the full width, so 100dp knobs fit comfortably. A tab with 6 knobs needs ~700dp width which fits landscape phones.

## Migration Plan

1. Create new composables (no breaking changes to existing yet)
2. Add tab navigation alongside existing layout for testing
3. Fix knob gesture → verify
4. Fix keyboard → verify
5. Switch to single-source state → verify
6. Replace old layout with tab layout
7. Remove old code paths
8. Fix C++ voice state
9. Wire preset loading
10. Device testing

## Open Questions

1. **Tab style**: Material3 `Tab` vs custom hardware-style tab? Decision: Custom hardware-style tabs matching the synth aesthetic (raised/recessed look).

2. **Sequencer as tab vs always-visible**: The sequencer needs to be accessible while playing. Decision: Sequencer gets a tab, but a mini "Transport bar" (play/stop/tempo) stays visible at the bottom at all times.

3. **Modulation Matrix placement**: Should it be its own tab or a sub-tab of each section? Decision: Its own tab for power users, with a simplified "quick routing" option on each section's tab.

4. **Pitch bend strip**: Touch strip above the keyboard? Decision: Add a 24dp touch strip above the keyboard that detects horizontal position for pitch bend. Optional in settings.