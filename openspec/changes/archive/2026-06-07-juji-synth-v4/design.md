## Context

Juji-synth v3 has 6 confirmed bugs from device testing. Codebase analysis identified the root cause of each:

1. **Stuck notes**: `awaitPointerEventScope` while-loop doesn't survive multi-touch because Compose cancels gesture coroutines when new pointers arrive. The first pointer's release event is lost.
2. **Preset crash**: `fallbackPresets` is `List<Triple<String,String,String>>` but `PresetBrowser` expects `List<PresetEntity>`. Type erasure hides the mismatch until runtime.
3. **FX overflow**: `SynthPanel` wraps panel content in a `Column` with no scroll. Hardcoded `180.dp` dividers overflow on smaller screens.
4. **No note labels**: Keyboard Canvas never calls `drawText()`. Keys are unlabeled rectangles.
5. **No octave shift**: No state, no buttons, all notes hardcoded to C3-B4 range.
6. **Tooltips dead code**: `TooltipData`, `ParameterTooltip`, `paramTooltips` exist in Components.kt but nothing triggers them.

## Goals / Non-Goals

**Goals:**
- Fix all 6 identified bugs
- Add octave shift buttons and note labels to keyboard
- Wire tooltips to knobs
- Crash-free preset browsing

**Non-Goals:**
- No new synthesis features
- No audio engine changes
- No UI layout overhaul (covered by v3)

## Decisions

### 1. Keyboard: `awaitEachGesture` per pointer

**Decision:** Replace the single `awaitPointerEventScope` while-loop with `awaitEachGesture` inside a pointer input that tracks multiple independent gestures.

```kotlin
.pointerInput(octaveOffset) {
    // Track all active pointers in a snapshot state
    val activePointers = mutableMapOf<Long, Int>()
    
    forEachGesture {
        awaitPointerEventScope {
            val down = awaitFirstDown()
            val note = noteAtPosition(down.position.x, down.position.y, ...)
            if (note >= 0) {
                note += octaveOffset * 12
                activePointers[down.id.value] = note
                onNoteOn(note)
            }
            
            // Track this pointer until release
            do {
                val event = awaitPointerEvent()
                val change = event.changes.find { it.id == down.id } ?: continue
                when {
                    !change.pressed -> {
                        val oldNote = activePointers.remove(change.id.value)
                        if (oldNote != null) onNoteOff(oldNote)
                    }
                    else -> {
                        // glissando check
                        val newNote = noteAtPosition(...)
                        val oldNote = activePointers[change.id.value]
                        if (newNote != oldNote && newNote >= 0) {
                            if (oldNote != null) onNoteOff(oldNote)
                            activePointers[change.id.value] = newNote
                            onNoteOn(newNote)
                        }
                    }
                }
                change.consume()
            } while (true)
        }
    }
}
```

**Rationale:** `forEachGesture` + `awaitEachGesture` creates one coroutine per pointer. When Compose cancels a gesture (because a new pointer arrived), the cancellation properly cleans up — calling the finally block where we can fire note-off. This prevents stuck notes.

**For the octave offset:** The `pointerInput(key)` key parameter is used to recreate the gesture detector when the octave changes. Add octaveOffset as the key.

### 2. Preset Crash: Fix fallback type

**Decision:** Replace the `fallbackPresets` list of `Triple<String,String,String>` with a list of `PresetEntity` objects that match the actual database schema.

```kotlin
private val fallbackPresets: List<PresetEntity> = listOf(
    PresetEntity(name = "Bright Lead", category = "Leads", 
        description = "Bright saw lead", isFactory = true, 
        parametersJson = "{}", id = -1),
    // ... more presets
)
```

### 3. Scrollable Panels

**Decision:** Add `Modifier.verticalScroll(rememberScrollState())` to the content area of each panel that might overflow. Remove hardcoded `height(180.dp)` on dividers, use `fillMaxHeight()` instead.

**Implementation:** The chassis content area already has `Modifier.weight(1f)`. Add `.verticalScroll()` to the `when(selectedTab)` block:

```kotlin
HardwareChassis(modifier = Modifier.weight(1f).fillMaxWidth()) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        when (selectedTab) { ... }
    }
}
```

### 4. Note Labels on Keyboard

**Decision:** Draw note names on each white key using `drawContext.canvas.nativeCanvas.drawText()` with a small `android.graphics.Paint`.

**Implementation:** Inside the `drawKeyboard` Canvas function, add after each white key is drawn:

```kotlin
val noteNames = arrayOf("C", "D", "E", "F", "G", "A", "B")
val textPaint = android.graphics.Paint().apply {
    color = android.graphics.Color.parseColor("#707080")
    textSize = 10.dp.toPx()
    textAlign = android.graphics.Paint.Align.CENTER
    isAntiAlias = true
}
val whiteNote = octaveNotes[whiteIdx]
val noteName = noteNames[whiteNote]
val octaveNum = 3 + octave - 2 // adjust for display
val label = "$noteName$octaveNum"
drawContext.canvas.nativeCanvas.drawText(
    label, x + keyWidth / 2f, viewHeight - 6.dp.toPx(), textPaint
)
```

### 5. Octave Shift

**Decision:** Add octave up/down buttons to the keyboard row in MainSynthScreen. Pass `octaveOffset` to KeyboardView. The keyboard offsets all notes by `octaveOffset * 12`.

**Implementation:**
```kotlin
var octaveOffset by remember { mutableStateOf(3) } // Middle C = C3

// In bottom bar, before keyboard:
Row {
    Box(clickable = { octaveOffset = (octaveOffset - 1).coerceAtLeast(1) }) { Text("-") }
    Text("C${octaveOffset}")
    Box(clickable = { octaveOffset = (octaveOffset + 1).coerceAtMost(7) }) { Text("+") }
}

// Pass to KeyboardView:
KeyboardView(octaveOffset = octaveOffset, ...)
```

In `KeyboardView`, the `noteAtPosition` function adds `octaveOffset * 12` to the calculated note. The `pointerInput` key should include `octaveOffset` so the gesture detector recreates when the octave changes.

### 6. Wire Tooltips

**Decision:** Add a long-press handler to `SynthKnob` that sets a tooltip state, and render a `ParameterTooltip` popup above the knob when the state is active.

**Implementation:**
- In `MainSynthScreen`, wrap the content area in a `Box` that can overlay tooltips
- Pass tooltip state down to `SynthKnob`
- In `SynthKnob`, add `detectTapGestures(onLongPress = { ... })` to set tooltip visible
- Show `ParameterTooltip` as an overlay when tooltip is active

Simpler approach: Add tooltip state to `SynthKnob` itself using `Popup`:

```kotlin
var showTooltip by remember { mutableStateOf(false) }

Box {
    // ... knob rendering ...
    // pointer input that detects both drag AND long press
    .pointerInput(Unit) {
        detectTapGestures(
            onLongPress = { showTooltip = true },
            onPress = { ... } // for drag
        )
    }
    
    // Tooltip popup
    if (showTooltip) {
        Popup(
            alignment = Alignment.TopCenter,
            onDismissRequest = { showTooltip = false }
        ) {
            ParameterTooltip(
                text = buildString {
                    append(label)
                    if (valueDisplay.isNotEmpty()) append(": $valueDisplay")
                    append("\n")
                    append(paramTooltips[label] ?: "")
                }
            )
        }
    }
}
```

But `Popup` positioning is tricky in Compose. Alternative: use `DropdownMenu` or a custom `Box` overlay.

**Better approach:** Use `LongPressGesture` detection that shows a small overlay above the knob. The simplest approach that works:

```kotlin
.pointerInput(Unit) {
    detectTapGestures(
        onLongPress = { showTooltip = true }
    )
}
```

And render a tooltip `Box` above the knob when `showTooltip` is true, with `LaunchedEffect` to auto-dismiss after 2 seconds.

## Risks / Trade-offs

[Risk] `forEachGesture` + per-pointer coroutines adds complexity
→ Mitigation: Test with 2-3 finger chords on device. If issues persist, limit to 2 simultaneous touches.

[Risk] `octaveOffset` as pointerInput key causes gesture detector recreation, which might briefly lose tracked notes
→ Mitigation: Only recreate when octaveOffset changes by more than 0. In practice, the user isn't holding keys when changing octaves.

[Risk] Tooltip popup positioning may overlap with other controls
→ Mitigation: Position tooltip above the knob with `Alignment.TopCenter`. If the knob is near the top of the screen, position below instead.

## Migration Plan

1. Fix preset crash (quickest, prevents crash)
2. Add scroll to panels (prevents overflow)
3. Fix multi-touch keyboard (most impactful)
4. Add note labels (visual polish)
5. Add octave shift (new feature)
6. Wire tooltips (completes dead code)

## Open Questions

1. **Tooltip display duration**: 2 seconds auto-dismiss? Or visible until user taps elsewhere? Decision: Auto-dismiss after 2 seconds for now.

2. **Octave range**: What range of octaves? Decision: C1 to C7 (MIDI 24-96), controllable via +/- buttons. Display shows current octave number.

3. **Keyboard texture change**: The user mentioned changing the texture of the keyboard. What texture? A darker, more matte finish like felt or rubber (synth-style)? Or glossy like a real piano? Decision: Default to matte dark finish matching the hardware aesthetic.