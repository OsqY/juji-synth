## 1. Fix Preset Crash

- [x] 1.1 Read PresetBrowser.kt and find the `fallbackPresets` declaration
- [x] 1.2 Replace `List<Triple<String,String,String>>` with `List<PresetEntity>` — each entry maps name/category/description/isFactory/parametersJson
- [x] 1.3 Build and verify preset browser opens without crashing
- [x] 1.4 Verify fallback presets display correctly when database is unavailable

## 2. Add Scroll to Panels

- [x] 2.1 Add `Modifier.verticalScroll(rememberScrollState())` to the content Column inside HardwareChassis in MainSynthScreen.kt
- [x] 2.2 Remove hardcoded `height(180.dp)` on EffectsPanel dividers, replace with `fillMaxHeight()` with padding
- [x] 2.3 Build and verify all panels scroll when content overflows
- [x] 2.4 Verify FX tab no longer overflows

## 3. Fix Multi-Touch Keyboard

- [x] 3.1 Rewrite keyboard gesture handling: replace `awaitPointerEventScope` while-loop with `forEachGesture` + per-pointer `awaitPointerEventScope` coroutines
- [x] 3.2 Add octaveOffset parameter to KeyboardView (default 3)
- [x] 3.3 Add `pointerInput(octaveOffset)` key so gesture detector recreates on octave change
- [x] 3.4 Track each pointer independently with its own lifecycle (press → drag → release)
- [x] 3.5 Ensure gesture cancellation (from new pointer arrival) triggers note-off in a finally block
- [x] 3.6 Add octaveOffset * 12 to calculated note values
- [x] 3.7 Build and test multi-touch with 2-3 simultaneous touches

## 4. Add Note Labels to Keyboard

- [x] 4.1 Create `android.graphics.Paint` for note label text rendering inside `drawKeyboard`
- [x] 4.2 Draw note name (C, D, E, F, G, A, B) + octave number on each white key
- [x] 4.3 Draw label near the bottom center of each white key
- [x] 4.4 Ensure labels update when octaveOffset changes
- [x] 4.5 Build and verify note labels are visible on keys

## 5. Add Octave Shift Buttons

- [x] 5.1 Add `var octaveOffset by remember { mutableStateOf(3) }` to MainSynthScreen
- [x] 5.2 Add octave up (+) and octave down (-) buttons in the bottom bar, before the keyboard
- [x] 5.3 Add octave display showing current octave (e.g., "C3")
- [x] 5.4 Constrain octave range: min 1, max 7
- [x] 5.5 Pass octaveOffset to KeyboardView
- [x] 5.6 Build and verify octave buttons shift all notes

## 6. Wire Tooltip System

- [x] 6.1 Add `var showTooltip by remember { mutableStateOf(false) }` and `var tooltipText by remember { mutableStateOf("") }` to SynthKnob in Components.kt
- [x] 6.2 Add `detectTapGestures(onLongPress = { ... })` to the knob's pointer input that sets tooltip visible and populates tooltip text from label + valueDisplay + paramTooltips
- [x] 6.3 Render a ParameterTooltip `Box` above the knob when showTooltip is true
- [x] 6.4 Add `LaunchedEffect(showTooltip)` to auto-dismiss the tooltip after 2 seconds
- [x] 6.5 Build and verify long-press on any knob shows tooltip with name and description

## 7. Quality Gate

- [x] 7.1 Run `./gradlew lint` and fix errors
- [x] 7.2 Run `./gradlew clean assembleDebug` and verify clean build
- [x] 7.3 Install APK on device and test all 6 fixes
