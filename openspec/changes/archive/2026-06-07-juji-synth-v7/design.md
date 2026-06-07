## Context

V6 introduced a shared pointer map for multi-touch but removed the try/finally cleanup block from the previous awaitEachGesture approach. Preset loading has never worked — it's been an empty callback since v1. Black keys are the only keys without labels.

## Goals / Non-Goals

**Goals:**
- Fix note leak on gesture cancellation (try/finally)
- Wire preset loading to actually change the sound
- Add black key labels
- Remove duplicate pitch bend strip
- Add basic velocity sensitivity

**Non-Goals:**
- No synthesis engine changes
- No UI layout overhaul

## Decisions

### 1. Multi-Touch: Add try/finally Cleanup

**Decision:** Wrap the while(true) event loop in try/finally that releases all tracked notes.

```kotlin
.pointerInput(octaveOffset) {
    awaitPointerEventScope {
        val activePointers = mutableMapOf<Long, Int>()
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                for (change in event.changes) {
                    // ... existing pointer tracking logic ...
                }
            }
        } finally {
            // CRITICAL: release all tracked notes when coroutine is cancelled
            activePointers.values.forEach { note ->
                if (note >= 0) onNoteOff(note)
            }
            activePointers.clear()
        }
    }
}
```

### 2. Preset Loading: Wire onSelectPreset

**Decision:** In MainSynthScreen, update the `onSelectPreset` callback to:
1. Find the preset by name (search dbPresets or fallbackPresets)
2. Deserialize SynthState from `parametersJson`
3. Set `synthState = loadedState`
4. Apply each parameter to the audio engine via `SynthEngine.setParam()`

```kotlin
onSelectPreset = { name ->
    val preset = (dbPresets?.find { it.name == name }
        ?: fallbackPresets.find { it.name == name })
    if (preset != null) {
        try {
            val loadedState = presetJson.decodeFromString<SynthState>(preset.parametersJson)
            synthState = loadedState
            // Apply all params to engine
            applySynthStateToEngine(loadedState)
        } catch (e: Exception) {
            // If JSON parsing fails (fallback presets have "{}"), do nothing
        }
    }
    showPresets = false
}
```

For the `applySynthStateToEngine` function, map each SynthState field to its JNI param ID.

### 3. Black Key Labels

**Decision:** Add a second label loop in `drawKeyboard` that draws abbreviated note names on black keys.

```kotlin
val blackNoteNames = mapOf(1 to "C#", 2 to "D#", 4 to "F#", 5 to "G#", 6 to "A#")
val blackLabelStyle = TextStyle(color = Color(0xFF505060), fontSize = 7.sp)

for (octave in 0 until 2) {
    for ((whiteIdx, offsetRat) in blackKeyOffsets) {
        val baseX = octave * 7 * keyWidth
        val x = baseX + offsetRat * keyWidth - blackKeyWidth / 2f
        val semitone = baseOctaveMidi + octave * 12 + whiteIdxToBlackNote[whiteIdx]!!
        val displayOctave = (semitone / 12) - 1
        val label = "${blackNoteNames[whiteIdx]}$displayOctave"
        val textLayout = textMeasurer.measure(text = label, style = blackLabelStyle)
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(
                x = x + (blackKeyWidth - textLayout.size.width) / 2f,
                y = 2.dp.toPx() // near the top of the black key
            )
        )
    }
}
```

### 4. Remove Duplicate Pitch Bend

**Decision:** Remove the vertical pitch bend strip Box at lines 224-239 of MainSynthScreen.kt. Wire `onPitchBend` to KeyboardView to use its built-in horizontal strip.

```kotlin
KeyboardView(
    activeNotes = activeNotes,
    onNoteOn = { ... },
    onNoteOff = { ... },
    onPitchBend = { pitch -> SynthEngine.setParam(51, pitch) },
    octaveOffset = octaveOffset,
    modifier = Modifier.weight(1f)
)
```

### 5. Velocity Sensitivity

**Decision:** Track time between note-on and note-off. Map to velocity 40-127 (faster = louder).

In MainSynthScreen, track press times:
```kotlin
var notePressTimes by remember { mutableStateOf(mapOf<Int, Long>()) }

onNoteOn = { note ->
    activeNotes = activeNotes + note
    notePressTimes = notePressTimes + (note to System.currentTimeMillis())
    SynthEngine.noteOn(note, 100) // default, will be overridden on release
},
onNoteOff = { note ->
    activeNotes = activeNotes - note
    val pressTime = notePressTimes[note]
    if (pressTime != null) {
        val duration = System.currentTimeMillis() - pressTime
        // Very short press (<50ms) = low velocity, longer press = higher
        val velocity = ((duration.coerceAtMost(500) / 500f) * 87 + 40).toInt().coerceIn(40, 127)
        // Re-trigger with velocity (note-off + note-on with new velocity)
        SynthEngine.noteOff(note)
        SynthEngine.noteOn(note, velocity)
        notePressTimes = notePressTimes - note
    } else {
        SynthEngine.noteOff(note)
    }
}
```

## Risks / Trade-offs

[Risk] Velocity based on press duration may feel unnatural
→ Mitigation: Start with simple duration-based. Can be refined to use actual touch pressure (if device supports it) in future.

[Risk] Re-triggering notes for velocity on release creates a tiny audible blip
→ Mitigation: Use `notePressTimes` to set velocity on initial note-on instead (awaitFirstDown gives us the time, we set velocity immediately). But we don't know the release time until release...

Simpler approach: Set initial velocity to 100 on noteOn, but if noteOff comes very quickly (<100ms), retrigger with velocity 50-60. If the note was held for a while, keep velocity at 100. This avoids the "stuck at 40 velocity" issue.

## Migration Plan

1. Add try/finally to keyboard gesture handler
2. Wire preset loading callback
3. Add black key labels
4. Remove duplicate pitch bend, wire onPitchBend
5. Add velocity sensitivity
6. Build and verify