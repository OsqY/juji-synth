## Context

Five iterative improvements (v1-v5) have built a functional synth app with tabbed navigation, hardware UI, Room database, single-source state, and combined knob gestures. Device testing revealed multi-touch still has issues, and the preset browser crashes on open. Plus four additional issues were discovered during codebase exploration.

## Goals / Non-Goals

**Goals:**
- Fix multi-touch so 4 simultaneous notes play cleanly without stuck notes
- Fix preset browser crash on open
- Fix tooltip clipping at screen top
- Fix sequencer stop button (currently does nothing)
- Wire MIDI controller (currently dead code)
- Fix settings screen coroutine leak
- Add octave range label
- Add JNI error handling

**Non-Goals:**
- No new synthesis features
- No UI layout changes

## Decisions

### 1. Multi-Touch: Shared Pointer Map in awaitPointerEventScope

**Decision:** Replace `awaitEachGesture` with a single `awaitPointerEventScope` + shared `HashMap<Long, Int>` tracking all active pointers.

```kotlin
.pointerInput(octaveOffset) {
    awaitPointerEventScope {
        val activePointers = mutableMapOf<Long, Int>()
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            for (change in event.changes) {
                val ptrId = change.id.value
                when {
                    !change.pressed -> {
                        activePointers.remove(ptrId)?.let { onNoteOff(it) }
                    }
                    ptrId !in activePointers -> {
                        val note = noteAtPosition(...)
                        if (note >= 0) {
                            activePointers[ptrId] = note
                            onNoteOn(note)
                        }
                    }
                    else -> {
                        // Existing pointer moved — glissando check
                        val oldNote = activePointers[ptrId]
                        val newNote = noteAtPosition(...)
                        if (newNote != oldNote) {
                            if (oldNote != null) onNoteOff(oldNote)
                            if (newNote >= 0) {
                                activePointers[ptrId] = newNote
                                onNoteOn(newNote)
                            } else {
                                activePointers.remove(ptrId)
                            }
                        }
                    }
                }
                change.consume()
            }
        }
    }
}
```

**Rationale:** A single event loop processes ALL pointer changes. No events are dropped because there's no per-pointer filtering. The shared map tracks up to 4 simultaneous touches. Each pointer's lifecycle (down → move → release) is handled in one pass.

### 2. Preset Crash Fix

**Decision:** Change LazyColumn key from `{ it.id }` to `{ it.name }`. Fallback presets all have id=0 causing DuplicateKeyException. Preset names are unique.

```kotlin
items(displayPresets, key = { it.name }) { preset ->
```

### 3. Smart Tooltip Positioning

**Decision:** Before showing the tooltip, check the knob's Y position relative to the screen height. If Y < 40% of screen height, position tooltip BELOW the knob. Otherwise position ABOVE.

```kotlin
val tooltipAbove = // calculate based on knob position
Box(
    modifier = Modifier
        .align(if (tooltipAbove) Alignment.TopCenter else Alignment.BottomCenter)
        .offset(y = if (tooltipAbove) (-8).dp else 8.dp)
)
```

Since the knob is inside a scrollable Column, the absolute position is relative to the scroll. Use `onGloballyPositioned` to get the knob's coordinates.

### 4. Sequencer Stop Button

Change empty click handler to:
```kotlin
.clickable { synthState = synthState.copy(sequencerPlaying = false) }
```

### 5. MIDI Controller Wiring

Add to MainSynthScreen:
```kotlin
val midiController = remember { MidiController(context) }
DisposableEffect(Unit) {
    midiController.startScanning()
    onDispose { midiController.stopScanning() }
}
```

### 6. Settings Screen Coroutine Fix

Replace `settingsFlow.collect` with one-time load:
```kotlin
LaunchedEffect(settingsDataStore) {
    val settings = settingsDataStore?.getSettings()
    if (settings != null) {
        selectedSampleRate = settings.sampleRate
        selectedBufferSize = settings.bufferSize
        selectedOutputMode = settings.outputMode
    }
}
```

### 7. JNI Error Handling

```kotlin
object SynthEngine {
    var isLoaded = false
        private set

    init {
        isLoaded = try {
            System.loadLibrary("jujisynth")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }
}
```

## Risks / Trade-offs

[Risk] Shared pointer map replaces awaitEachGesture — regression risk
→ Mitigation: Same pattern worked in v3 but with wrong event pass. Now using PointerEventPass.Main with proper consumption.

[Risk] MIDI controller might crash on devices without USB host support
→ Mitigation: Check `packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)` before starting.

## Migration Plan

1. Fix preset crash (1-line change, most impactful)
2. Fix sequencer stop button (1-line change)
3. Fix settings coroutine leak (1-line change)
4. Fix tooltip positioning
5. Fix multi-touch gesture
6. Wire MIDI controller
7. Add JNI error handling
8. Build and test