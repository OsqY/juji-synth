## Context

Juji-synth v1 compiles and produces a working APK, but device testing revealed critical bugs and a UI that doesn't meet the hardware-synth aesthetic target. The codebase has:
- 21 Kotlin files (UI, audio bridge, models, data, MIDI)
- 13 C++ source files (audio engine, DSP, JNI bridge)
- Full Oboe source tree (including ~350 unnecessary files from samples/tests)
- Room database defined but never initialized
- 20 hardcoded demo presets (target: 50-100)

The C++ audio engine is complete and untested on device. All bugs are in the Kotlin UI layer.

## Goals / Non-Goals

**Goals:**
- Fix all critical interaction bugs (note-off, button clicks)
- Achieve DRC/Minilogue-like visual quality with realistic 3D knobs, panel textures, proper key shapes
- Complete preset persistence (Room DB wired, save/load/delete flow)
- Add tap-and-hold tooltips on all controls
- Add settings screen for audio configuration
- Expand factory presets to 50-100
- Clean up Oboe dependency (remove samples/tests/apps)
- Pass lint and typecheck cleanly

**Non-Goals:**
- iOS support
- Cloud features, preset sharing
- Audio recording/export
- New synthesis features (wavetable, FM, etc.)
- Performance optimization beyond current state

## Decisions

### 1. Keyboard Touch: `awaitPointerEventScope` for Press/Release

**Decision:** Replace `detectTapGestures` with `pointerInput` using `awaitPointerEventScope` to detect press (note-on) and release (note-off) events independently.

**Rationale:** `detectTapGestures` only fires on complete tap (press + release), making it impossible to distinguish press from release. `awaitPointerEventScope` gives us raw pointer events with `PointerEventPass.Main` for press detection and `PointerEventPass.Final` for release. This is the standard Compose pattern for instrument-style touch interaction.

**Implementation:**
```kotlin
.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            if (change.pressed && !change.previousPressed) {
                // Press → note-on
                val note = noteAtPosition(change.position.x, size.width.toFloat())
                if (note >= 0) onNoteOn(note)
            } else if (!change.pressed && change.previousPressed) {
                // Release → note-off
                val note = noteAtPosition(change.position.x, size.width.toFloat())
                if (note >= 0) onNoteOff(note)
            }
        }
    }
}
```

**Alternatives considered:**
- `detectDragGestures`: Doesn't distinguish press from drag start
- `Modifier.pointerInput` with `detectTapGestures(onPress)`: The `onPress` callback fires on press but the release detection is unreliable

### 2. Button Fix: Z-Ordering and Clickable Area

**Decision:** The PRESET/HELP buttons use `.clickable` which should work. The issue is likely that the 36.dp Box is too small for reliable touch, or the buttons are obscured by overlapping composables. Fix by:
1. Increasing button size to 44.dp minimum (Material touch target)
2. Adding explicit `.zIndex(1f)` to ensure buttons are above other content
3. Adding `.pointerInput(Unit) { detectTapGestures { ... } }` as a more reliable alternative to `.clickable`

**Rationale:** Material Design specifies 48.dp as the minimum touch target. The current 36.dp is below this threshold. The `.clickable` modifier can also be unreliable when composables overlap.

### 3. UI Overhaul: Canvas-Based Realistic Components

**Decision:** Redesign all UI components using `Canvas` for realistic rendering:
- **Knobs**: 3D appearance with radial gradients, shadow, highlight, rotation indicator line, metallic rim
- **Panels**: Layered backgrounds with subtle noise texture, inner shadow, beveled edges
- **Keyboard**: Proper key shapes with shadows, 3D black keys, pressed state with depth effect
- **Buttons**: Embossed/engraved appearance with proper press feedback

**Rationale:** The current components use flat colored boxes which look like a prototype. Hardware synths have depth, texture, and visual weight. Canvas-based rendering gives us full control over appearance without external assets.

**Implementation approach:**
- Create a `HardwareKnob` composable that draws: outer shadow → metallic rim → body gradient → indicator line → highlight
- Create a `PanelBackground` composable that draws: base color → noise texture (via `drawContext.canvas.nativeCanvas` with `Paint`) → inner shadow → border
- Create a `RealisticKey` composable for keyboard keys with proper 3D shaping

**Alternatives considered:**
- External PNG/SVG assets: More work to create, harder to maintain, larger APK
- Material3 components: Too flat, doesn't match hardware aesthetic
- Third-party UI library: No good Compose library for hardware-style controls

### 4. Room Database Integration

**Decision:** Initialize the Room database in a custom `Application` class (`JujiSynthApp`) and provide it via a singleton. Use `Flow<List<PresetEntity>>` for reactive queries in the PresetBrowser.

**Rationale:** The database needs to be initialized once and shared across the app. An Application class is the standard Android pattern for app-wide singletons. Flow provides automatic UI updates when presets change.

**Implementation:**
```kotlin
class JujiSynthApp : Application() {
    val database: PresetDatabase by lazy {
        Room.databaseBuilder(this, PresetDatabase::class.java, "juji-synth.db")
            .addCallback(presetDatabaseCallback) // Seed factory presets on first run
            .build()
    }
}
```

The `presetDatabaseCallback` seeds the database with 50-100 factory presets on first creation.

### 5. Preset Save/Load Flow

**Decision:** Add a "Save" button in the main screen that opens a dialog with:
- Name input field
- Category dropdown (Leads, Pads, Bass, FX, Ambient, User)
- Save/Cancel buttons

Loading a preset: Tap a preset in the browser → all synth parameters update via `SynthEngine.setParam()` calls.

**Rationale:** This matches the workflow of hardware synths where you can save your current sound. The category system helps organize presets.

### 6. Tap-and-Hold Tooltips

**Decision:** Add a `Modifier.longPressTooltip(text: String)` extension that shows a tooltip popup after 500ms of long press. Apply to all `SynthKnob`, `SynthToggle`, and `WaveformButton` instances.

**Implementation:** Use `pointerInput` with `detectTapGestures(onLongPress = { ... })` to trigger a `Popup` or `TooltipBox` composable showing the parameter name and description.

### 7. Settings Screen

**Decision:** Add a settings icon in the top bar that opens a bottom sheet with:
- Sample rate: 44100 / 48000 Hz (radio buttons)
- Buffer size: 128 / 256 / 512 samples (radio buttons)
- Output: Mono / Stereo (radio buttons)

Persist settings using `DataStore<Preferences>` (modern replacement for SharedPreferences).

**Rationale:** These settings affect audio latency and quality. Users need to tune them for their device. DataStore is the recommended persistence mechanism for simple key-value settings.

### 8. Oboe Cleanup

**Decision:** Delete the following directories from `app/src/main/cpp/oboe/`:
- `apps/` (OboeTester, fxlab, etc.)
- `samples/` (LiveEffect, MegaDrone, RhythmGame, etc.)
- `tests/` (unit tests)

Keep only: `include/`, `src/`, `CMakeLists.txt`, `LICENSE`

**Rationale:** These directories contain ~350 files that are not needed for the library itself. They bloat the project and slow down builds.

### 9. Factory Preset Expansion

**Decision:** Create 80 factory presets distributed across categories:
- Leads: 20 presets
- Pads: 20 presets
- Bass: 15 presets
- FX: 15 presets
- Ambient: 10 presets

Each preset is a `PresetEntity` with all synth parameters serialized as JSON. Presets are seeded into the Room database on first app launch.

**Rationale:** 80 presets provide enough variety for users to explore different sounds without overwhelming them. The distribution matches typical hardware synth preset libraries.

## Risks / Trade-offs

[Risk] Canvas-based UI rendering may be slower than using standard Compose components
→ Mitigation: Profile on target devices. If performance is an issue, use `rememberDrawingCache` to cache expensive drawings. Knobs and panels are static (don't redraw every frame) so performance should be acceptable.

[Risk] Room database migration if schema changes in future
→ Mitigation: Start with version 1 and add migration strategies as needed. For now, use `fallbackToDestructiveMigration()` which recreates the database on schema change (acceptable for v2 since there are no users yet).

[Risk] Long-press tooltips may conflict with knob drag gestures
→ Mitigation: Use `detectTapGestures(onLongPress)` which fires before drag detection. The tooltip appears after 500ms of holding still, so it won't interfere with normal knob adjustment.

[Risk] Oboe cleanup may break the build if CMake references deleted files
→ Mitigation: The Oboe CMakeLists.txt only references files in `src/` and `include/`. The `apps/`, `samples/`, and `tests/` directories are standalone projects not referenced by the main build.

[Risk] 80 factory presets is a lot of manual work
→ Mitigation: Create a preset generation script that programmatically creates presets by varying parameters within musically useful ranges. Manually curate and name the best ones.

## Migration Plan

This is an incremental update to an existing codebase. No data migration needed (no users yet). Deployment:

1. Fix critical bugs (keyboard, buttons) — immediate priority
2. UI overhaul — largest effort
3. Room DB integration + preset save/load
4. Settings screen + tooltips
5. Oboe cleanup + preset expansion
6. Quality gate (lint, typecheck)
7. Device testing

**Rollback:** Not applicable — fully local app. Previous APK can be reinstalled.

## Open Questions

1. **Preset parameter serialization:** Should we serialize the entire `SynthState` data class as JSON, or store individual parameters as columns? Decision: JSON serialization of `SynthState` — simpler schema, easier to add new parameters later.

2. **Settings persistence:** DataStore vs SharedPreferences? Decision: DataStore — it's the modern recommended approach and provides type-safe preferences.

3. **Tooltip implementation:** Custom `Popup` vs Material3 `TooltipBox`? Decision: Custom `Popup` — more control over appearance and positioning, matches the hardware aesthetic better.

4. **Panel texture approach:** Canvas noise vs PNG texture? Decision: Canvas-generated noise using `Paint.setPathEffect` with random dots — no external assets needed, scales to any resolution.