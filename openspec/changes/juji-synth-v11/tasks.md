## 1. Wire MIDI Controller

- [x] 1.1 In MainSynthScreen.kt, add `val midiController = remember { MidiController(context) }` after the settingsDataStore line
- [x] 1.2 Add `DisposableEffect(Unit) { midiController.startScanning(); onDispose { midiController.stopScanning() } }`
- [x] 1.3 Add import for `com.jujisynth.midi.MidiController`
- [x] 1.4 Build and verify

## 2. Fix Manual Search

- [x] 2.1 In ManualScreen.kt, add filteredSections list that filters manualSections based on searchQuery
- [x] 2.2 Replace `val section = manualSections[selectedSection]` with filtered sections
- [x] 2.3 Build and verify search filters content

## 3. Wire Sequencer to C++ Engine

- [x] 3.1 In MainSynthScreen.kt, add `SynthEngine.setParam(60, synthState.sequencerTempo)` and `SynthEngine.setParam(61, if (synthState.sequencerPlaying) 1f else 0f)` calls
- [x] 3.2 In SynthEngine.kt, add `external fun nativeSetSequencerSteps(notes: ByteArray, velocities: ByteArray, gates: ByteArray, automation: ByteArray)` and wrapper
- [x] 3.3 In JniBridge.cpp, add `nativeSetSequencerSteps` implementation that calls `engine.setSequencerSteps()`
- [x] 3.4 In AudioEngine.cpp, ensure `setSequencerPlaying` actually starts/stops the C++ sequencer
- [x] 3.5 Build and verify

## 4. Wire Modulation Matrix to C++ Engine

- [x] 4.1 In SynthEngine.kt, add `external fun nativeSetModulationRoute(index: Int, source: Int, destination: Int, amount: Float, active: Boolean)`
- [x] 4.2 In JniBridge.cpp, add implementation that calls `engine.setModulationRoute()`
- [x] 4.3 In MainSynthScreen.kt, call the new method in the modulation route change handler
- [x] 4.4 Build and verify

## 5. Make Settings Apply

- [x] 5.1 In MainSynthScreen.kt, add a `var pendingSettingsRestart by remember { mutableStateOf(false) }`
- [x] 5.2 Pass a callback to SettingsScreen that sets the flag
- [x] 5.3 In a LaunchedEffect, when flag is true: SynthEngine.stop() + SynthEngine.start()
- [x] 5.4 Build and verify settings restart the engine

## 6. Add Chorus Effect

- [x] 6.1 Create Chorus.h with rate, depth, mix controls and modulated delay line
- [x] 6.2 Create Chorus.cpp with the delay line + LFO modulation implementation
- [x] 6.3 Add Chorus to AudioEngine.h (as member) and AudioEngine.cpp (process + params)
- [x] 6.4 Add JNI param IDs 55-57 for chorus rate/depth/mix
- [x] 6.5 Add chorus controls to EffectsPanel.kt (3 knobs)
- [x] 6.6 Add Chorus.cpp to CMakeLists.txt
- [x] 6.7 Build and verify

## 7. Add Keyboard Scrolling

- [x] 7.1 In KeyboardView.kt, add `keyboardScrollOffset` parameter (default 0)
- [x] 7.2 In noteAtPosition and drawKeyboard, add scrollOffset to key position calculations
- [x] 7.3 Add horizontal drag gesture for scrolling
- [x] 7.4 In MainSynthScreen.kt, add scroll state and pass to KeyboardView
- [x] 7.5 Build and verify

## 8. Add Sustain Pedal

- [x] 8.1 In MidiController.kt, add `sustainOn` flag and `sustainedNotes` set
- [x] 8.2 Handle CC64 in handleMidiMessage
- [x] 8.3 Modify note-off to check sustain state
- [x] 8.4 Build and verify

## 9. Quality Gate

- [x] 9.1 Run `./gradlew lint`
- [x] 9.2 Run `./gradlew clean assembleDebug`
