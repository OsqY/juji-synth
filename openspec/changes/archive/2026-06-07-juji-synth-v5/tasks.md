## 1. Fix Tooltip + Drag Conflict

- [x] 1.1 Read Components.kt SynthKnob — remove the transparent overlay Box with `detectTapGestures(onLongPress)` at lines ~270-279
- [x] 1.2 Rewrite the knob's `pointerInput(Unit)` block: replace `detectDragGestures` with `awaitEachGesture` that detects both drag and long-press
- [x] 1.3 In the gesture: on finger down, track elapsed time. If finger moves >8dp before 500ms → drag mode. If 500ms passes without movement → tooltip mode
- [x] 1.4 Keep `showTooltip` state, `LaunchedEffect` auto-dismiss, and the existing `ParameterTooltip` popup rendering
- [x] 1.5 Build and verify knobs drag immediately (no delay)
- [x] 1.6 Build and verify long-press (hold still 500ms) shows tooltip

## 2. Fix EffectsPanel Divider Sizing

- [x] 2.1 Read EffectsPanel.kt — replace `fillMaxHeight(0.8f)` on both dividers with `height(120.dp)`
- [x] 2.2 Build and verify dividers render correctly in scrollable layout

## 3. Quality Gate

- [x] 3.1 Run `./gradlew lint`
- [x] 3.2 Run `./gradlew clean assembleDebug`
- [x] 3.3 Install on device and verify all fixes
