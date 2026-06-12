## Why

Device testing reveals 6 issues making the app hard to use. The keyboard's black keys are nearly invisible against the background due to near-identical dark purples. Note labels are unreadable gray-on-white. Loading a preset while holding a note causes an audio cutout/stutter because the engine applies new envelope parameters to currently-playing voices. The preset browser filter resets every time the dialog opens. The help section lacks depth — the sequencer section is too brief for beginners. And the sequencer grid steps are tiny and can't be played by tapping.

## What Changes

- **Fix keyboard contrast**: Lighten `KeyBlack` from `0xFF1A1A2E` to `0xFF3A3A5E`. Set note label text to `0xFF333333` on white keys and `0xFFB0B0C0` on black keys.
- **Fix preset stutter**: In `applyModulationMatrix()`, skip updating ADSR envelope parameters on voices that are currently active (playing). Only update oscillator levels, filter, and effects. This prevents held notes from being interrupted by preset loads.
- **Fix preset filter persistence**: Lift `selectedCategory` state to MainSynthScreen and pass it to PresetBrowser so it persists across dialog open/close.
- **Expand help section**: Add "Sequencer Walkthrough" (step-by-step guide with examples), "MIDI Learn" guide, "Chorus Effect" docs. Fix search filtering.
- **Improve sequencer grid**: Increase step size from 20×16dp to 28×22dp for easier tapping. Add tap-to-preview — tapped step plays its note briefly when sequencer is stopped. Show note name inside the step (e.g., "C4") instead of just a dot.
- **Fix note label contrast**: Darken white key labels to `0xFF333333` for readability. Lighten black key labels to `0xFF9A9AB0` for visibility against dark keys.

## Capabilities

### Modified Capabilities

- `keyboard-note-labels`: New label colors for readability on both white and black keys. Black key color lightened for visibility.
- `preset-system`: Filter state persists across dialog sessions. ADSR envelope parameters not applied to active voices during preset load.
- `help-system`: Expanded with sequencer tutorial, MIDI learn guide, chorus docs. Search now actually filters content.
- `sequencer`: Larger tappable steps, tap-to-preview note audition, note name display inside step cells.
- `multi-touch-fix-v2`: applyModulationMatrix no longer updates envelopes on active voices.

## Impact

- **Color.kt**: Change `KeyBlack` from `0xFF1A1A2E` to `0xFF3A3A5E`
- **KeyboardView.kt**: Change label `TextStyle` colors on white and black keys
- **AudioEngine.cpp**: In `applyModulationMatrix()`, skip `voices_[i].setAmpEnvelope()` and `setFilterEnvelope()` when voice is active
- **MainSynthScreen.kt**: Add `selectedPresetCategory` state, pass to PresetBrowser
- **PresetBrowser.kt**: Accept external `selectedCategory` state instead of `remember`ing internally
- **ManualScreen.kt**: Add 3 new ManualSection entries (Sequencer Walkthrough, MIDI Learn, Chorus). Fix search to properly filter sections.
- **SequencerView.kt**: Larger step boxes, tap-to-preview, note name text inside each active step