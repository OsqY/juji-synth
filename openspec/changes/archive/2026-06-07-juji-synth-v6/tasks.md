## 1. Fix Preset Crash

- [x] 1.1 In PresetBrowser.kt, change LazyColumn key from `key = { it.id }` to `key = { it.name }`
- [x] 1.2 Build and verify preset browser opens without crashing

## 2. Fix Sequencer Stop Button

- [x] 2.1 In MainSynthScreen.kt, find the empty stop button click handler and replace with `synthState = synthState.copy(sequencerPlaying = false)`
- [x] 2.2 Build and verify stop button stops the sequencer

## 3. Fix Settings Screen Coroutine Leak

- [x] 3.1 In SettingsScreen.kt, replace `settingsFlow.collect { ... }` with `settingsDataStore?.getSettings()` one-time load
- [x] 3.2 Build and verify settings screen loads correctly

## 4. Fix Tooltip Positioning

- [x] 4.1 In Components.kt SynthKnob, add `onGloballyPositioned` to the knob Box to track its y-coordinate relative to the screen
- [x] 4.2 Calculate if the knob is in the top 40% of the screen → position tooltip BELOW
- [x] 4.3 Otherwise position tooltip ABOVE (existing behavior)
- [x] 4.4 Build and verify tooltip appears below when knob is near screen top

## 5. Fix Multi-Touch Keyboard

- [x] 5.1 In KeyboardView.kt KeyboardKeys composable, rewrite pointerInput: replace `awaitEachGesture` with `awaitPointerEventScope` + shared `HashMap<Long, Int>` activePointers
- [x] 5.2 Process ALL pointer events in a single while(true) loop: press → noteOn, release → noteOff, move → glissando
- [x] 5.3 Remove the per-pointer filtering (`find { it.id == down.id }`) that drops other pointers' events
- [x] 5.4 Build and verify 4 simultaneous touches work without stuck notes

## 6. Wire MIDI Controller

- [x] 6.1 In MainSynthScreen.kt, add `val midiController = remember { MidiController(context) }`
- [x] 6.2 Add `DisposableEffect(Unit)` that calls `midiController.startScanning()` and `onDispose { midiController.stopScanning() }`
- [x] 6.3 Build and verify MIDI controller starts on screen display

## 7. Add JNI Error Handling

- [x] 7.1 In SynthEngine.kt, wrap `System.loadLibrary("jujisynth")` in try-catch, set `var isLoaded = false` if it fails
- [x] 7.2 In MainSynthScreen, check `SynthEngine.isLoaded` before calling native methods, show error state if not loaded
- [x] 7.3 Build and verify

## 8. Quality Gate

- [x] 8.1 Run `./gradlew lint`
- [x] 8.2 Run `./gradlew clean assembleDebug`
