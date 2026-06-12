## Context

Extensive codebase audit found 5 high-impact gaps and 5 medium-impact missing features. Most critically, the MIDI controller, sequencer, modulation matrix, and settings are all UI-only scaffolding — they store/display data but never communicate with the C++ audio engine.

## Goals / Non-Goals

**Goals:**
- Wire all existing scaffolding to the audio engine
- Add chorus effect (most impactful missing feature)
- Add keyboard scrolling beyond 2 octaves
- Add sustain pedal support
- Fix manual search
- Make settings actually apply

**Non-Goals:**
- No visual redesign
- No new synthesis types

## Decisions

### 1. Wire MIDI Controller

**Decision:** Add `MidiController` instantiation to MainSynthScreen with `DisposableEffect`.

```kotlin
val midiController = remember { MidiController(context) }
DisposableEffect(Unit) {
    midiController.startScanning()
    onDispose { midiController.stopScanning() }
}
```

Also expose a `connectedDevices` StateFlow to show a MIDI indicator in the UI.

### 2. Wire Sequencer to C++ Engine

**Decision:** The sequencer currently updates `synthState` but never calls JNI. Add calls to `SynthEngine.setParam(60, tempo)` and `SynthEngine.setParam(61, playing)` when those values change. Send step data via a new JNI method `nativeSetSequencerSteps(steps: ByteArray)`.

Also need to add the sequencer start call in `AudioEngine.cpp` — currently `setSequencerPlaying` just sets `pendingParams_.sequencer.playing` but `processAudio` never checks this. The sequencer needs to call `sequencer_.setPlaying(playing)` in `applyModulationMatrix()`.

### 3. Wire Modulation Matrix to C++ Engine

**Decision:** Add a new JNI method `nativeSetModulationRoute(index: Int, source: Int, destination: Int, amount: Float, active: Boolean)` that calls `engine.setModulationRoute(index, route)`. Call it from `ModulationMatrixPanel.onRouteChange`.

### 4. Make Settings Apply

**Decision:** The SettingsScreen already saves to DataStore. Add a callback `onSettingsChanged` that restarts the audio engine by calling `SynthEngine.stop()` + `SynthEngine.start()` with the new settings. The engine's native start/stop are already implemented.

### 5. Fix Manual Search

**Decision:** The ManualScreen has a search field but the results are never filtered. The `searchQuery` state exists but `manualSections` is never filtered by it. Replace the hardcoded `val section = manualSections[selectedSection]` with a filtered list.

```kotlin
val filteredSections = if (searchQuery.isBlank()) manualSections
    else manualSections.filter { 
        it.title.contains(searchQuery, ignoreCase = true) || 
        it.content.contains(searchQuery, ignoreCase = true) 
    }
```

### 6. Add Chorus Effect

**Decision:** Add a stereo modulated delay line chorus to the C++ engine. Chorus works by mixing the dry signal with a delayed copy whose delay time is modulated by a slow LFO.

```cpp
class Chorus {
public:
    void init(double sampleRate);
    void setRate(double rate);    // 0.0-1.0 → 0.1-5.0 Hz
    void setDepth(double depth);  // 0.0-1.0 → 0-20ms modulation
    void setMix(double mix);      // 0.0-1.0 dry/wet
    float process(float input);
    void reset();
private:
    static constexpr int MAX_DELAY = 48000; // 1 second at 48kHz
    std::array<float, MAX_DELAY> buffer_{};
    int writeIndex_ = 0;
    double sampleRate_ = 44100.0;
    double rate_ = 0.5;           // Hz
    double depth_ = 0.0;          // modulation depth in samples
    double mix_ = 0.0;
    double phase_ = 0.0;
};
```

Add to AudioEngine, EffectsPanel, and JNI param IDs (48-49 for chorus rate/depth, or new IDs 55-57).

### 7. Keyboard Scrolling

**Decision:** Add `var keyboardScroll by remember { mutableStateOf(0f) }` state and detect horizontal drag gestures for scrolling. Offset all note calculations by `keyboardScroll / keyWidth`.

**Implementation:** Use `detectHorizontalDragGestures` in a separate `pointerInput` block on the keyboard Box. Convert scroll offset to note offset (each scroll of 1 key width = 1 semitone shift).

Alternative: simpler approach — add swipeable modifier.

### 8. Sustain Pedal (MIDI CC64)

**Decision:** In `MidiController.handleMidiMessage`, add handling for CC64 (sustain pedal). When sustain is on, don't send noteOff; instead, collect released notes in a set. When sustain turns off, release all held notes.

```kotlin
private var sustainOn = false
private val sustainedNotes = mutableSetOf<Int>()

// In handleMidiMessage:
0xB0 -> {
    val controller = msg[offset + 1].toInt() and 0x7F
    val value = (msg[offset + 2].toInt() and 0x7F) > 63
    if (controller == 64) { // Sustain pedal
        sustainOn = value
        if (!sustainOn) {
            sustainedNotes.forEach { SynthEngine.noteOff(it) }
            sustainedNotes.clear()
        }
    }
}
// In noteOff handling:
if (sustainOn) {
    sustainedNotes.add(note)
} else {
    SynthEngine.noteOff(note)
}
```

## Risks / Trade-offs

[Risk] Restarting the audio stream on settings change causes audio dropout
→ Mitigation: Add a brief fade-out before stop and fade-in after start to mask the transition.

[Risk] Chorus increases CPU usage
→ Mitigation: Use a simple delay-line design with linear interpolation. The effect is disabled when mix is 0%.

[Risk] Keyboard scrolling conflicts with note press gestures
→ Mitigation: Only scroll when dragging in the pitch bend strip area (which is already touch-sensitive) or use a two-finger gesture for scroll.

## Migration Plan

1. Wire MIDI controller (3 lines, high impact)
2. Fix manual search (1 condition check)
3. Wire settings to actually restart engine
4. Wire sequencer to C++ engine (JNI + AudioEngine changes)
5. Wire modulation matrix to C++ engine (JNI + AudioEngine changes)
6. Add chorus effect (C++ + UI)
7. Add keyboard scrolling
8. Add sustain pedal support
9. Build and test