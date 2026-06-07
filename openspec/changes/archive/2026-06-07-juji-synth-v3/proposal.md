## Why

Juji-synth v2 compiles but is unusable as a musical instrument. Seven critical bugs make knobs feel erratic, keys get stuck, black keys are silent, preset loading does nothing, and the voice engine leaks. The UI shows all parameter panels simultaneously, cramming controls into tiny boxes with no hardware aesthetic — it looks and feels like a toy, not a real synthesizer like DRC or the Minilogue it's meant to emulate. This change fixes every identified bug and fundamentally rearchitects the UI around a tabbed, single-panel-at-a-time layout with proper hardware visual design.

## What Changes

**BREAKING**: Complete UI re-architecture from side-by-side panels to tabbed navigation (DRC-inspired)
**BREAKING**: Single-source-of-truth state model replaces 40+ individual mutableStateOf variables

- **Fix knob gesture capture**: Use `rememberUpdatedState(value)` so drag delta compounds correctly
- **Fix keyboard for multi-touch polyphony**: Replace `detectTapGestures` with `awaitPointerEventScope` tracking individual pointer IDs
- **Fix black key detection**: Add proper hit-testing that distinguishes white and black keys by position
- **Fix glissando support**: Track pointer movement across keys, firing note-off on old key and note-on on new key
- **Fix voice state leak**: Sync `AudioEngine::voiceActive_` with `SynthVoice::active_` after envelope completion
- **Fix preset loading**: Wire `onSelectPreset` to deserialize JSON and apply all parameters via `SynthEngine.setParam()`
- **Single-source-of-truth SynthState**: Replace 40+ state variables with one `SynthState` object updated atomically
- **Tabbed navigation**: Oscillators, Filter, Envelope, LFO, Effects, Sequencer each get a tab. Only one panel visible at a time, full-width layout
- **Hardware chassis frame**: Outer border with gradient, inner shadow, panel texture, screw-hole indicators
- **3D keyboard**: Proper key shapes with shadows, depth on press, pitch bend strip
- **Large 3D knobs**: 80-100dp knobs with radial gradient, metallic rim, shadow, highlight, rotation indicator
- **Preset loading actually works**: Deserialize SynthState from JSON, apply all params

## Capabilities

### New Capabilities

- `tabbed-navigation`: DRC-style tab system allowing switching between synth parameter panels with only one panel visible at a time
- `multi-touch-keyboard`: Polyphonic keyboard supporting 4 simultaneous touches with proper white/black key detection and glissando
- `single-source-state`: Unified SynthState object replacing all individual parameter state variables, atomically updatable for preset loading
- `hardware-chassis`: Outer frame with gradient border, inner shadow, panel texture, and hardware-style dividers between sections

### Modified Capabilities

- `oscillator-section`: Now rendered as full-width tab panel with large 3D knobs (80-100dp), proper spacing, hardware-style layout
- `filter-section`: Full-width tab panel, large 3D knobs, visible envelope curve overlay
- `envelope-section`: Full-width tab panel with large ADSR visualization, 3D knobs for each stage
- `lfo-section`: Full-width tab panel with waveform visualization and large controls
- `effects-section`: Full-width tab panel, large 3D knobs for all effects
- `modulation-matrix`: Now a dedicated tab or sub-tab, visual routing with drag-to-connect
- `sequencer`: Bottom section, always visible in a collapsed form, expands for editing
- `preset-system`: Loading now actually applies parameters via JNI. Save/load wired through single-source state
- `help-system`: Tooltips work with new knob components
- `midi-connectivity`: Multi-touch keyboard properly handles MIDI note-on/note-off for polyphony

## Impact

- **Components.kt**: Complete rewrite of SynthKnob to 3D 100dp hardware knob. New gesture handling with `rememberUpdatedState`.
- **KeyboardView.kt**: Complete rewrite — multi-touch pointer tracking, black key detection, glissando, 3D key rendering
- **MainSynthScreen.kt**: Complete rewrite — tabbed navigation, single SynthState, new save/load preset wiring
- **All panel files**: Converted to full-width tab layouts, large knobs, hardware aesthetic
- **AudioEngine.cpp**: Fix voiceActive_ sync with SynthVoice::active_
- **SynthState.kt**: Becomes the single source of truth for UI state
- **New files**: TabNavigation.kt, HardwareChassis.kt, PitchBendStrip.kt