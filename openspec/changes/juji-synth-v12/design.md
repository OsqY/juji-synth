## Context

Six confirmed issues from device testing: keyboard contrast (black keys invisible, labels unreadable), preset stutter (envelope params change held notes), preset filter resets (remembered state lost on dialog close), help too thin (no sequencer tutorial, missing MIDI learn guide, search broken), sequencer grid (tiny steps, can't preview notes), and note label contrast (gray on white = invisible).

## Goals / Non-Goals

**Goals:**
- Black keys visible against background, labels readable on both key colors
- Held notes not affected by preset envelope changes
- Preset filter persists across dialog sessions
- Help sections expanded with tutorials, search actually filters
- Sequencer steps are larger, playable by tap, show note names
- Note labels have sufficient contrast

**Non-Goals:**
- No new synthesis features
- No UI layout changes beyond colors and sizes

## Decisions

### 1. Keyboard Contrast Fix

**Decision:** Change KeyBlack from `0xFF1A1A2E` to `0xFF3A3A5E`. Change note label text:

```kotlin
// White key labels (dark for contrast against white surface)
val labelStyle = TextStyle(color = Color(0xFF333333), fontSize = 9.sp)

// Black key labels (light for contrast against dark surface)
val blackLabelStyle = TextStyle(color = Color(0xFF9A9AB0), fontSize = 7.sp)
```

Black key color `0xFF1A1A2E` was indistinguishable from background `0xFF221840` (delta = 0x080818, barely perceptible). New color `0xFF3A3A5E` is visibly lighter (delta = 0x181C1E).

### 2. Preset Stutter Fix

**Decision:** In `applyModulationMatrix()`, skip `setAmpEnvelope()` and `setFilterEnvelope()` calls on active voices. Only new voices get the new envelope parameters.

```cpp
void AudioEngine::applyModulationMatrix() {
    for (int i = 0; i < MAX_VOICES; i++) {
        if (voiceActive_[i]) {
            const auto& p = currentParams_;
            // Skip ADSR envelope changes on active voices!
            // Only update oscillators, filter cutoff, filter resonance, etc.
            voices_[i].setOsc1Level(p.oscillators.osc1.level);
            voices_[i].setOsc2Level(p.oscillators.osc2.level);
            voices_[i].setOsc1Waveform(p.oscillators.osc1.waveform);
            voices_[i].setOsc2Waveform(p.oscillators.osc2.waveform);
            voices_[i].setOscDetune(p.oscillators.osc1.detune * 100.0f);
            voices_[i].setFilterCutoff(p.filter.cutoff);
            voices_[i].setFilterResonance(p.filter.resonance);
            voices_[i].setFilterMode(p.filter.mode);
            // DON'T apply envelopes to active voices:
            // voices_[i].setAmpEnvelope(...) — SKIP
            // voices_[i].setFilterEnvelope(...) — SKIP
        }
    }
    // Still sync global components
    ...
}
```

New voices (`noteOn` → `init()` → `applyModulationMatrix()` → `noteOn()`) still get the full treatment because `init()` resets the voice first.

### 3. Preset Filter Persistence

**Decision:** Lift `selectedCategory` from PresetBrowser into MainSynthScreen.

Add to MainSynthScreen state:
```kotlin
var presetCategory by remember { mutableStateOf("All") }
```

Pass to PresetBrowser:
```kotlin
PresetBrowser(
    onDismiss = { showPresets = false },
    onSelectPreset = { ... },
    presetDao = presetDao,
    selectedCategory = presetCategory,
    onCategoryChange = { presetCategory = it }
)
```

Update PresetBrowser signature.

### 4. Help Expansion

**Decision:** Add 3 new ManualSection entries:

1. **Sequencer Walkthrough**: Step-by-step guide with examples (creating a bassline, using automation, syncing tempo)
2. **MIDI Learn**: Documentation of the learn workflow (tap MIDI button, select control, press MIDI button)
3. **Chorus Effect**: What chorus does, parameter descriptions

Fix search: The search field currently resets `selectedSection` but doesn't actually filter. Use the `filteredSections` approach but ensure it properly navigates to matching content.

### 5. Sequencer Grid

**Decision:** Increase step Box size from 20×16dp to 28×22dp. Add tap-to-preview: when sequencer is not playing, tapping a step plays its note briefly (100ms note-on then note-off). Show note name text instead of a dot inside active steps.

```kotlin
Box(modifier = Modifier.size(width = 28.dp, height = 22.dp)...) {
    if (hasNote) {
        Text(
            noteName(step.note),
            color = Color.White, fontSize = 7.sp
        )
    }
}
```

### 6. Label Contrast

Apply the color changes described in decision 1 to both `drawKeyboard` label loops.

## Risks / Trade-offs

[Risk] Not updating envelopes on active voices means preset envelope changes only apply to new notes
→ Mitigation: This is the correct behavior — a held note should not jump to a new envelope mid-way through. Users expect envelope changes to take effect on the next note.

[Risk] Larger sequencer steps might not fit in the tab area
→ Mitigation: 28×22dp for 16 steps = 448×44dp plus spacing. Fits in available width easily.

## Migration Plan

1. Fix keyboard contrast (Color.kt + KeyboardView.kt labels)
2. Fix preset stutter (AudioEngine.cpp)
3. Fix preset filter persistence (MainSynthScreen + PresetBrowser)
4. Expand help + fix search (ManualScreen.kt)
5. Improve sequencer grid (SequencerView.kt)
6. Build and test