## Why

The initial juji-synth implementation compiles and produces an APK, but device testing revealed critical usability bugs and a UI that falls far short of the hardware-synth aesthetic target. Notes get stuck because the keyboard never sends note-off events, the PRESET and HELP buttons are non-functional, and the overall appearance resembles a basic prototype rather than a polished instrument like DRC or the Minilogue it references. Additionally, several integration gaps remain: the Room database is defined but never initialized, preset save/load has no UI flow, tap-and-hold tooltips are missing, and the Oboe dependency includes the entire source tree (samples, tests) bloating the build. This change addresses all discovered bugs, overhauls the UI to achieve a realistic hardware synth appearance, and completes the remaining integration work.

## What Changes

- **Fix keyboard note-off**: Replace `detectTapGestures` with proper press/release tracking so notes sound only while the finger is held
- **Fix PRESET/HELP buttons**: Debug and fix the click handlers that currently don't open their dialogs
- **UI overhaul to DRC-like hardware aesthetic**: Redesign all components (knobs, panels, keyboard, buttons) with realistic 3D appearance — shadows, gradients, textures, proper key shapes, metallic/wooden panel surfaces
- **Wire Room database**: Initialize the preset database in the app, connect PresetBrowser to actual DB queries instead of hardcoded data
- **Preset save/load flow**: Add UI for saving modified sounds as user presets, loading presets, and deleting user presets
- **Tap-and-hold tooltips**: Implement long-press gesture on all controls that shows parameter name, current value, and description
- **Settings screen**: Add a settings panel for sample rate (44.1k/48k), buffer size, mono/stereo output
- **Expand factory presets**: Grow from 20 demo presets to 50-100 curated presets across all categories
- **Clean up Oboe dependency**: Trim the Oboe source tree to only include the library source (remove samples, tests, apps)
- **Quality gate**: Run lint, typecheck, and verify the build passes cleanly

## Capabilities

### New Capabilities

- `settings-screen`: User-configurable audio settings (sample rate, buffer size, output mode) with persistence
- `preset-persistence`: Room database integration for saving, loading, and managing user presets with full synth state serialization

### Modified Capabilities

- `oscillator-section`: UI redesigned with realistic hardware appearance (3D knobs, panel textures)
- `filter-section`: UI redesigned with realistic hardware appearance
- `envelope-section`: UI redesigned with realistic ADSR visualization and hardware-style controls
- `lfo-section`: UI redesigned with realistic hardware appearance and waveform visualization
- `effects-section`: UI redesigned with realistic hardware appearance
- `modulation-matrix`: UI redesigned with clearer visual routing display
- `sequencer`: UI redesigned with realistic step grid and transport controls
- `preset-system`: Connected to Room database, save/load/delete flow added, expanded to 50-100 factory presets
- `help-system`: Tap-and-hold tooltips implemented on all controls, manual search functional
- `midi-connectivity`: Device persistence and auto-reconnect verified

## Impact

- **UI layer (Kotlin/Compose)**: Major rewrite of Components.kt (SynthKnob, SynthPanel, SynthToggle, WaveformButton), KeyboardView.kt, and all panel files. New SettingsScreen.kt component.
- **Data layer (Kotlin/Room)**: PresetDatabase initialization in MainActivity or Application class. PresetBrowser refactored to use Flow-based DB queries. New preset save dialog.
- **Keyboard interaction**: Complete rewrite of touch handling in KeyboardView.kt — must use `pointerInput` with `awaitPointerEventScope` for press/release detection.
- **C++ audio engine**: No changes required — all bugs are in the Kotlin UI layer.
- **Build system**: Oboe source tree cleanup (remove ~350 unnecessary files from samples/tests/apps directories).
- **Resources**: New drawable assets for panel textures, knob shadows, key gradients.