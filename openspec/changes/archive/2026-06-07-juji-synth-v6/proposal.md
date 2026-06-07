## Why

Device testing of v5 revealed four remaining issues and four additional discovered issues. Multi-touch notes still get stuck because `awaitEachGesture` drops events for non-tracked pointers. The preset browser crashes due to duplicate LazyColumn keys. Tooltips are clipped when knobs are near the top of the screen. The sequencer stop button does nothing, MIDI controller is uninstantiated dead code, and there's no error handling for JNI library loading.

## What Changes

- **Fix multi-touch**: Replace `awaitEachGesture` with a single `awaitPointerEventScope` using a shared `activePointers: Map<Long, Int>` that tracks ALL pointers simultaneously. Each pointer's down/move/release events are processed without dropping events for other pointers.
- **Fix preset crash**: Change `key = { it.id }` to `key = { it.name }` in PresetBrowser LazyColumn, so fallback presets with id=0 don't cause DuplicateKeyException.
- **Fix tooltip positioning**: Check knob position relative to container. If knob is in the top 40% of the screen, position tooltip BELOW the knob instead of above.
- **Fix sequencer stop button**: Wire the empty click handler to actually stop the sequencer.
- **Wire MIDI controller**: Instantiate and start MidiController in MainSynthScreen, show connection status indicator.
- **Fix settings screen coroutine leak**: Replace `settingsFlow.collect` with `settingsFlow.first()` for one-time load, or cancel the old coroutine on recomposition.
- **Add octave range label**: Show "C3-B4" range text alongside the center octave display.
- **Add JNI error handling**: Wrap `System.loadLibrary("jujisynth")` in a try-catch, surface error state to UI.

## Capabilities

### New Capabilities
- `multi-touch-fix-v2`: Shared pointer map tracking all touches in a single awaitPointerEventScope

### Modified Capabilities
- `preset-system`: Fix crash by using unique keys in LazyColumn
- `help-system`: Smart tooltip positioning below knob when near screen top
- `sequencer`: Fix stop button wiring
- `midi-connectivity`: Instantiate and start MidiController in main screen

## Impact

- **KeyboardView.kt**: Rewrite gesture handling to use shared `activePointers` map in single `awaitPointerEventScope`
- **PresetBrowser.kt**: Change `key = { it.id }` to `key = { it.name }`
- **Components.kt**: Smart tooltip position detection (above/below based on y-coordinate)
- **MainSynthScreen.kt**: Wire sequencer stop, instantiate MidiController, add octave range label
- **SettingsScreen.kt**: Replace `collect` with `first()` or use `LaunchedEffect` cancellation
- **SynthEngine.kt**: Wrap `System.loadLibrary` in try-catch