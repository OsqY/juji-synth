## Why

Currently the MIDI controller has hardcoded CC mappings (CC1 → filter cutoff, CC64 → sustain, etc.). Users want to control any visible parameter with their MIDI hardware — knobs, sliders, buttons on any controller. There's no way to assign a MIDI CC to a specific on-screen control without code changes. This change adds a MIDI Learn workflow: enter learn mode, tap a control, press a button/knob on the MIDI controller, done.

## What Changes

- **Add MIDI Learn button** in the toolbar (next to PRESET, SAVE, HELP). Enters/leaves learn mode.
- **Learn mode visual feedback**: When active, all mappable controls (knobs, waveform buttons, toggles) get a pulsing highlight. Tapped control becomes "selected" (glows brighter).
- **MIDI learn flow**: Tap a control → selected → press any MIDI CC → mapping stored → control glows green (mapped) → back to learn mode to map more controls.
- **MIDI mapping data**: Store CC→ParamId mappings in DataStore. When a mapped CC arrives, apply it to the mapped parameter.
- **Mapping persistence**: Mappings survive app restarts via DataStore.
- **Clear mappings**: Ability to clear all mappings or clear a single mapping (long-press a mapped control in learn mode).
- **Visual indicator**: Mapped controls show a small dot or ring when NOT in learn mode, so users can see which controls are MIDI-mapped.

## Capabilities

### New Capabilities
- `midi-learn`: Complete MIDI learn workflow with tap-to-select, CC capture, mapping storage, and persistence
- `midi-mapping-visuals`: Visual feedback during learn mode (pulsing highlights, selected state, mapped indicators)

### Modified Capabilities
- `midi-connectivity`: CC messages now checked against the mapping table before falling through to default behavior

## Impact
- **New file**: `MidiMappingStore.kt` — DataStore-based persistence for CC→ParamId mappings
- **New file**: `MidiLearnState.kt` — State management for learn mode (active, selected control, pending mapping)
- **MainSynthScreen.kt**: Add MIDI Learn button in toolbar, add learn mode state, wire mapping application
- **Components.kt**: Add `mapped` indicator overlay to `SynthKnob` (small colored dot)
- **MidiController.kt**: When a CC arrives, check mapping store first before default CC handling
- **Each panel file**: Pass learn mode + selected state to SynthKnob for visual feedback