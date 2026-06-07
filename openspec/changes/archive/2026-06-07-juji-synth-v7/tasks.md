## 1. Fix Multi-Touch Leak

- [x] 1.1 In KeyboardView.kt KeyboardKeys, wrap the while(true) event loop in try/finally
- [x] 1.2 In the finally block, call `onNoteOff` for all notes in `activePointers` and clear the map
- [x] 1.3 Build and verify gesture cancellation properly releases all notes

## 2. Wire Preset Loading

- [x] 2.1 In MainSynthScreen.kt, replace the empty `onSelectPreset` callback with actual loading logic
- [x] 2.2 Look up the preset by name from `dbPresets` or `fallbackPresets`
- [x] 2.3 Deserialize `SynthState` from `preset.parametersJson`
- [x] 2.4 Set `synthState = loadedState` to update the UI
- [x] 2.5 Apply each parameter to the audio engine via `SynthEngine.setParam(id, value)`
- [x] 2.6 Handle fallback presets with `parametersJson = "{}"` gracefully (don't crash)
- [x] 2.7 Build and verify preset selection changes the sound

## 3. Add Black Key Labels

- [x] 3.1 In KeyboardView.kt drawKeyboard, add a second label loop for black keys
- [x] 3.2 Draw abbreviated names (C#, D#, F#, G#, A#) with octave number in 7sp font
- [x] 3.3 Position labels near the top of each black key
- [x] 3.4 Build and verify black key labels are visible

## 4. Remove Duplicate Pitch Bend

- [x] 4.1 In MainSynthScreen.kt, remove the vertical pitch bend strip Box (lines 224-239)
- [x] 4.2 Wire `onPitchBend` to KeyboardView: `onPitchBend = { pitch -> SynthEngine.setParam(51, pitch) }`
- [x] 4.3 Build and verify pitch bend works through the keyboard's built-in strip

## 5. Add Velocity Sensitivity

- [x] 5.1 In MainSynthScreen.kt, add `var notePressTimes by remember { mutableStateOf(mapOf<Int, Long>()) }`
- [x] 5.2 In `onNoteOn`, store the current time for the note
- [x] 5.3 In `onNoteOff`, calculate press duration and derive velocity (40-127 range)
- [x] 5.4 Re-trigger the note with the calculated velocity
- [x] 5.5 Build and verify velocity varies with press speed

## 6. Quality Gate

- [x] 6.1 Run `./gradlew lint`
- [x] 6.2 Run `./gradlew clean assembleDebug`
