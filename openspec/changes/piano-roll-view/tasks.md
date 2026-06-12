## 1. Panic Button (finish implementation)

- [x] 1.1 Add JNI bridge in JniBridge.cpp: `Java_com_jujisynth_audio_SynthEngine_nativePanic` that calls `SynthEngine::getInstance().getAudioEngine().panic()`
- [x] 1.2 Add PANIC button in MainSynthScreen.kt toolbar (next to PRESET, SAVE, etc.) with red styling and text "PANIC"
- [x] 1.3 On click: call `SynthEngine.panic()`, reset `activeNotes = emptySet()`, show Toast
- [x] 1.4 Build and verify

## 2. Improve Black Key Separation

- [x] 2.1 In KeyboardView.kt drawKeyboard, change `blackKeyWidth` from `keyWidth * 0.6f` to `keyWidth * 0.55f`
- [x] 2.2 Add a subtle lighter border on the right edge of each black key
- [x] 2.3 Ensure the gap between black and white keys is visible
- [x] 2.4 Build and verify

## 3. Create Piano Roll View

- [x] 3.1 Create `PianoRollView.kt` at `ui/` — grid composable with MIDI note rows and 16 columns
- [x] 3.2 Y-axis: MIDI notes C2 to C7 (scrollable vertically), display note labels
- [x] 3.3 X-axis: 16 steps, each step is a tappable cell (24×16dp)
- [x] 3.4 Active cells: filled purple background. Inactive: dark background
- [x] 3.5 Tap cell: toggles note on/off. Plays note briefly when tapped.
- [x] 3.6 Build and verify

## 4. View Toggle in MainSynthScreen

- [x] 4.1 Add `var keyboardMode by remember { mutableStateOf("piano") }` state ("piano" or "roll")
- [x] 4.2 Add toggle button between octave controls and keyboard area
- [x] 4.3 When roll mode: show PianoRollView instead of KeyboardView
- [x] 4.4 Pass notes from PianoRollView to onNoteOn/onNoteOff
- [x] 4.5 Build and verify

## 5. Quality Gate

- [x] 5.1 Run `./gradlew lint`
- [x] 5.2 Run `./gradlew clean assembleDebug`