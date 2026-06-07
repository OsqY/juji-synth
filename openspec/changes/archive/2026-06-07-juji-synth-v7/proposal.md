## Why

Device testing revealed three persistent issues after v6: notes still get stuck on multi-touch (missing try/finally in gesture cleanup), presets don't change the sound (onSelectPreset ignores the preset name), and black keys lack note labels. Two additional issues were discovered: duplicate pitch bend strips (one inside KeyboardView unused, one outside) and no velocity sensitivity.

## What Changes

- **Fix multi-touch leak**: Add `try { ... } finally { activePointers.values.forEach { onNoteOff(it) } }` to the keyboard gesture coroutine so all notes are released when the gesture detector is cancelled (octave change, screen rotation)
- **Wire preset loading**: In `onSelectPreset`, look up the preset by name from DB or fallback, deserialize `SynthState` from `parametersJson`, set `synthState = loadedState`, and apply all params to the audio engine via `SynthEngine.setParam()` for each parameter
- **Add black key labels**: Draw abbreviated note names on black keys using smaller font near the top of each key
- **Fix duplicate pitch bend**: Remove the vertical pitch bend strip Box outside KeyboardView. Wire `onPitchBend` to the KeyboardView's built-in strip instead
- **Add velocity sensitivity**: Track press duration on keyboard keys → shorter press = lower velocity, longer press = higher velocity (40-127 range)

## Capabilities

### New Capabilities
- `velocity-sensitivity`: Dynamic note velocity based on press speed (faster press = louder)

### Modified Capabilities
- `multi-touch-fix-v2`: Add try/finally cleanup block to prevent note leaks on gesture cancellation
- `preset-system`: Wire onSelectPreset to actually deserialize and apply parameters
- `keyboard-note-labels`: Add labels on black keys (C#, D#, F#, G#, A#)
- `sequencer` (implicit): Clean up duplicate pitch bend strip

## Impact

- **KeyboardView.kt**: Add try/finally around gesture loop. Add black key label rendering. Add velocity calculation.
- **MainSynthScreen.kt**: Wire onSelectPreset to load + apply parameters. Wire onPitchBend to KeyboardView. Remove duplicate pitch bend Box. Connect sequencer stop button.
- **PresetBrowser.kt**: Keep existing key fix (already done).