## Why

Device testing of juji-synth v3 revealed 6 distinct bugs and missing features that prevent it from being a usable musical instrument. Multi-touch keyboard notes get stuck (consuming all 4 voices and creating distortion), the app crashes when selecting a preset, the FX panel overflows without scrolling, the keyboard has no note labels or octave shift, and the tooltip system exists as dead code never wired to any control. All 6 issues have identifiable root causes and targeted fixes.

## What Changes

- **Fix multi-touch keyboard**: Replace `awaitPointerEventScope` while-loop with `awaitEachGesture` (one coroutine per pointer) so pointer release events are never lost to gesture cancellation
- **Fix preset crash**: Replace `fallbackPresets` type from `List<Triple>` to `List<PresetEntity>` to match the type expected by the browser
- **Fix FX panel overflow**: Add `Modifier.verticalScroll()` to panels, remove hardcoded `height(180.dp)` on dividers
- **Add note labels to keyboard**: Draw "C3", "D3", etc. on white keys using `nativeCanvas.drawText()`
- **Add octave shift**: Octave up/down buttons on keyboard row, shift all key notes by ±12 semitones
- **Wire tooltip system**: Add `detectTapGestures(onLongPress)` to `SynthKnob` that shows a `ParameterTooltip` popup with the parameter name, current value, and description

## Capabilities

### New Capabilities

- `octave-shift`: Octave up/down buttons with visual display of current octave range
- `keyboard-note-labels`: Note name labels (C3, D3, etc.) drawn on each white key
- `scrollable-panels`: Vertical scrolling for effects and other content-heavy panels

### Modified Capabilities

- `multi-touch-keyboard`: Fix stuck notes by using per-pointer `awaitEachGesture` coroutines instead of single while-loop. Add octave shift buttons and note labels.
- `preset-system`: Fix crash on preset selection by correcting `fallbackPresets` type to `List<PresetEntity>`
- `help-system`: Wire tooltip data/long-press to actually display tooltips on knobs
- `effects-section`: Add vertical scroll, remove hardcoded divider heights

## Impact

- **KeyboardView.kt**: Gesture handling rewrite (awaitEachGesture). Added octave offset state, note label drawing in Canvas, octave up/down buttons.
- **PresetBrowser.kt**: Fix `fallbackPresets` type from `List<Triple>` to `List<PresetEntity>` with proper field mapping
- **EffectsPanel.kt** (and other panels): Add `Modifier.verticalScroll()`, remove hardcoded heights
- **Components.kt**: Add long-press handler to `SynthKnob` that triggers tooltip display. Add tooltip popup rendering above the knob.
- **MainSynthScreen.kt**: Add octave state, pass to KeyboardView