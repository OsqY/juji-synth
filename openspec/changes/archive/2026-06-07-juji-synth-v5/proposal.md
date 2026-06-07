## Why

Device testing of v4 revealed that the tooltip overlay blocks all knob drag gestures, making the synth unplayable. The `fillMaxHeight` inside `verticalScroll` breaks EffectsPanel divider rendering. These are critical regressions introduced by v4's implementation — the tooltip system and scroll fixes need a fundamentally different approach.

## What Changes

- **Fix tooltip + drag conflict**: Remove the transparent overlay. Instead, combine both long-press detection and drag handling into a single `pointerInput` block using `awaitEachGesture`. On finger-down, start a timer. If the finger moves more than a threshold → drag mode (adjust value). If the finger stays still for 500ms → tooltip mode (show popup, auto-dismiss).
- **Fix scroll divider sizing**: Replace `fillMaxHeight` on EffectsPanel dividers with a fixed small height (like `height(120.dp)` which fits within the scroll area). The scroll itself already works — `verticalScroll` on the chassis content is correct.
- **Remove stale APK risk**: Add `octaveOffset` display to the tab bar area so users can see the octave change visually.
- **Gesture cleanup**: Ensure no overlapping gesture detectors compete for the same touch region.

## Capabilities

### New Capabilities
- `combined-gesture-knob`: Single gesture handler in SynthKnob that detects both drag (value change) and long-press (tooltip) within one `pointerInput` block

### Modified Capabilities
- `help-system`: Tooltip triggered by long-press INSIDE the knob's gesture handler, not via a separate overlay
- `effects-section`: Divider heights use fixed values suitable for scrollable layout
- `octave-shift`: Octave display visible in the UI to confirm changes