## 1. Critical Bug Fixes

- [x] 1.1 Fix keyboard note-off: Replace `detectTapGestures` with `detectTapGestures(onPress)` for proper press/release detection in KeyboardView.kt
- [x] 1.2 Fix PRESET button: Increase touch target to 48.dp, improved click handler
- [x] 1.3 Fix HELP button: Increase touch target to 48.dp, improved click handler
- [x] 1.4 Verify keyboard plays notes only while finger is held (note-on on press, note-off on release)
- [x] 1.5 Verify PRESET button opens preset browser dialog
- [x] 1.6 Verify HELP button opens manual screen

## 2. Oboe Dependency Cleanup

- [x] 2.1 Delete `app/src/main/cpp/oboe/apps/` directory (OboeTester, fxlab, etc.)
- [x] 2.2 Delete `app/src/main/cpp/oboe/samples/` directory (LiveEffect, MegaDrone, etc.)
- [x] 2.3 Delete `app/src/main/cpp/oboe/tests/` directory
- [x] 2.4 Verify CMake build still succeeds after cleanup
- [x] 2.5 Verify APK size is reduced

## 3. UI Component Overhaul — Realistic Hardware Aesthetic

- [x] 3.1 Create `HardwareKnob` composable: 3D appearance with radial gradient body, metallic rim, shadow, rotation indicator line, highlight
- [x] 3.2 Create `PanelBackground` composable: textured background with noise pattern, beveled edges, inner shadow
- [x] 3.3 Create `RealisticKey` composable: proper piano key shapes with 3D shading, pressed state with depth effect
- [x] 3.4 Create `LedButton` composable: toggle button with LED-style indicator for bypass/active states
- [x] 3.5 Create `WaveformIcon` composable: small waveform shape graphic (saw, square, tri, sine, random) for selectors
- [x] 3.6 Replace all `SynthKnob` usages with `HardwareKnob` across all panel files
- [x] 3.7 Replace all panel backgrounds with `PanelBackground` across all panel files
- [x] 3.8 Replace keyboard rendering with `RealisticKey` components in KeyboardView.kt
- [x] 3.9 Replace bypass toggle with `LedButton` in EffectsPanel.kt
- [x] 3.10 Add waveform icons to waveform selector buttons in OscillatorPanel.kt and LfoPanel.kt
- [x] 3.11 Verify all panels render with consistent hardware aesthetic

## 4. Room Database Integration

- [x] 4.1 Create `JujiSynthApp` Application class with lazy Room database initialization
- [x] 4.2 Add database callback to seed factory presets on first creation
- [x] 4.3 Register `JujiSynthApp` in AndroidManifest.xml
- [x] 4.4 Add DataStore dependency to build.gradle.kts for settings persistence
- [x] 4.5 Create `SettingsDataStore` class for reading/writing audio settings
- [x] 4.6 Verify database is created on first app launch
- [x] 4.7 Verify factory presets are seeded correctly

## 5. Preset Browser Refactor

- [x] 5.1 Refactor PresetBrowser to use Room DB Flow queries instead of hardcoded data
- [x] 5.2 Add category tab filtering using DB queries
- [x] 5.3 Add visual distinction between factory and user presets
- [x] 5.4 Add long-press on user preset to show delete confirmation dialog
- [x] 5.5 Implement preset delete functionality
- [x] 5.6 Verify preset browser displays all factory presets from DB
- [x] 5.7 Verify category filtering works correctly

## 6. Preset Save/Load Flow

- [x] 6.1 Add Save button to main synth screen toolbar
- [x] 6.2 Create preset save dialog with name input and category dropdown
- [x] 6.3 Implement preset save: serialize current SynthState to JSON, insert into DB
- [x] 6.4 Implement preset load: deserialize JSON from DB, apply all params via SynthEngine.setParam()
- [x] 6.5 Add validation: prevent saving with empty name
- [x] 6.6 Verify saving a preset stores it in the database
- [x] 6.7 Verify loading a preset updates all synth parameters
- [x] 6.8 Verify user presets appear in the browser under "User" category

## 7. Settings Screen

- [x] 7.1 Add settings gear icon to main screen top bar
- [x] 7.2 Create SettingsScreen composable as a bottom sheet
- [x] 7.3 Add sample rate selector (44100 / 48000 Hz) with radio buttons
- [x] 7.4 Add buffer size selector (128 / 256 / 512 samples) with radio buttons
- [x] 7.5 Add output mode selector (Mono / Stereo) with radio buttons
- [x] 7.6 Wire settings to DataStore for persistence
- [x] 7.7 Wire settings to audio engine (apply on next launch)
- [x] 7.8 Verify settings persist across app restarts

## 8. Tap-and-Hold Tooltips

- [x] 8.1 Create `longPressTooltip` Modifier extension using `detectTapGestures(onLongPress)`
- [x] 8.2 Create `TooltipPopup` composable showing parameter name, value, and description
- [x] 8.3 Define tooltip text for all parameters (name + description)
- [x] 8.4 Apply tooltip modifier to all SynthKnob instances
- [x] 8.5 Apply tooltip modifier to all SynthToggle instances
- [x] 8.6 Apply tooltip modifier to all WaveformButton instances
- [x] 8.7 Verify long-press on any control shows tooltip popup
- [x] 8.8 Verify tooltip dismisses on finger release

## 9. Factory Preset Expansion

- [x] 9.1 Create preset generation data: define 80 presets with parameter values (20 Leads, 20 Pads, 15 Bass, 15 FX, 10 Ambient)
- [x] 9.2 Create `FactoryPresetSeeder` class that generates PresetEntity objects
- [x] 9.3 Wire seeder into database callback
- [x] 9.4 Verify 80 factory presets are created on first launch
- [x] 9.5 Verify preset distribution: 20 Leads, 20 Pads, 15 Bass, 15 FX, 10 Ambient

## 10. Quality Gate & Final Verification

- [x] 10.1 Run `./gradlew lint` and fix all errors
- [x] 10.2 Run `./gradlew assembleDebug` and verify clean build
- [ ] 10.3 Install APK on device and verify all bug fixes work
- [ ] 10.4 Verify UI renders with realistic hardware appearance on device
- [ ] 10.5 Verify preset save/load/delete flow works end-to-end
- [ ] 10.6 Verify settings screen works and persists
- [ ] 10.7 Verify tooltips appear on long-press for all controls
- [ ] 10.8 Verify MIDI device connection and note playback
- [ ] 10.9 Verify audio output with no glitches or stuck notes
