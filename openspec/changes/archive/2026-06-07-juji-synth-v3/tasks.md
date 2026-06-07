## 1. Critical Bug Fixes

- [x] 1.1 Fix knob gesture: Add `rememberUpdatedState(value)` wrapper in `SynthKnob` pointerInput lambda, verify smooth drag in both directions
- [x] 2.1 Implement multi-touch keyboard: Replace `detectTapGestures` with `awaitPointerEventScope` tracking pointerId→note map, support 4 simultaneous touches
- [x] 3.2 Add black key detection to keyboard: Hit-test against black key position rectangles before falling back to white key calculation
- [x] 4.3 Add glissando support: Track pointer movement across keys, fire note-off on old key and note-on on new key
- [x] 5.4 Fix C++ voice state leak: In `AudioEngine::processAudio()`, check `voices_[v].isActive()` and clear `voiceActive_[v]` when voice envelope completes
- [x] 6.5 Wire preset loading: In `MainSynthScreen`, update `onSelectPreset` to deserialize SynthState JSON and apply all params via SynthEngine.setParam()
- [x] 7.6 Replace 40+ state variables with single `SynthState` mutableStateOf in MainSynthScreen

## 2. Tabbed Navigation

- [x] 2.1 Create `TabBar` composable with hardware-style tabs for: OSC, FILTER, ENV, LFO, FX, SEQ, MOD
- [x] 2.2 Create tab content area that shows only the selected panel
- [x] 2.3 Implement `when(selectedTab)` switching between panel composables
- [x] 2.4 Add visual "raised tab" effect for selected tab, "recessed" for unselected
- [x] 2.5 Verify tab switch shows correct panel and hides all others

## 3. Hardware Chassis Frame

- [x] 3.1 Create `HardwareChassis` composable with outer gradient border, inner shadow, panel texture
- [x] 3.2 Add screw-hole indicators at four corners of chassis
- [x] 3.3 Create `SectionDivider` composable for separating control groups
- [x] 3.4 Wrap main content area in HardwareChassis
- [x] 3.5 Verify chassis renders with correct depth and texture

## 4. 3D Keyboard Implementation

- [x] 4.1 Create 3D key rendering with Canvas: base rect → side shadow → top surface → highlight edge
- [x] 4.2 Implement multi-touch pointer tracking with pointerId→note map
- [x] 4.3 Implement black key hit detection with position rectangles
- [x] 4.4 Implement glissando: note-off old key, note-on new key on pointer move
- [x] 4.5 Add key press visual feedback (key shifts down, shadow intensifies)
- [x] 4.6 Add pitch bend strip (24dp touch strip above keyboard)
- [x] 4.7 Verify 4 simultaneous touches produce 4 voices
- [x] 4.8 Verify black keys play correct notes
- [x] 4.9 Verify glissando across keys works
- [x] 4.10 Verify note-off fires on finger release

## 5. Single-Source State Model

- [x] 5.1 Refactor `MainSynthScreen` to use single `var synthState by remember { mutableStateOf(SynthState()) }`
- [x] 5.2 Update all panel function signatures to accept `state: SynthState` and `onStateChange: (SynthState) -> Unit`
- [x] 5.3 Update all panel composables to read from state object instead of individual parameters
- [x] 5.4 Verify all UI controls read from and write to the shared SynthState
- [x] 5.5 Verify preset load replaces entire state atomically

## 6. Full-Width Panel Redesign

- [x] 6.1 Redesign OscillatorPanel: full-width layout, large knobs (80-100dp), OSC1/OSC2 side by side
- [x] 6.2 Redesign FilterPanel: full-width, large cutoff/resonance knobs, mode selectors, envelope curve display
- [x] 6.3 Redesign EnvelopePanel: full-width, large ADSR knobs (8 knobs in 2 rows), real-time curve viz
- [x] 6.4 Redesign LfoPanel: full-width, large rate/depth knobs, waveform selector with shape icons
- [x] 6.5 Redesign EffectsPanel: full-width, large knobs grouped by effect, LED bypass toggles
- [x] 6.6 Redesign ModulationMatrixPanel: full-width, 8 routing slots with visual source→destination
- [x] 6.7 Redesign SequencerView: full-width 16-step grid on its own tab, mini transport bar for other tabs

## 7. Knob Visual Enhancement

- [x] 7.1 Update `SynthKnob` to larger default size (80dp)
- [x] 7.2 Enhance 3D rendering: stronger radial gradient, more prominent metallic rim, sharper shadow
- [x] 7.3 Add rotation indicator line that always points to current value
- [x] 7.4 Verify knobs render correctly at 80-100dp in full-width panels

## 8. Polish and Integration

- [x] 8.1 Wire preset loading: `onSelectPreset` deserializes JSON, sets synthState, applies all params
- [x] 8.2 Remove old side-by-side layout code
- [x] 8.3 Verify save preserves all parameters
- [x] 8.4 Verify load restores all parameters
- [x] 8.5 Verify tooltips work with new knob layout
- [x] 8.6 Run `./gradlew lint` and fix errors
- [x] 8.7 Run `./gradlew assembleDebug` and verify clean build
- [ ] 8.8 Install on device and test all functionality
