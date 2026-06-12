## 1. MIDI Mapping Data Layer

- [x] 1.1 Create `MidiMappingStore.kt` at `data/` — DataStore-based persistence for CC→ParamId mappings
- [x] 1.2 Define `MidiMapping` data class: `ccNumber: Int, paramId: Int, minValue: Float, maxValue: Float`
- [x] 1.3 Implement `saveMappings(mappings: List<MidiMapping>)`, `loadMappings(): Flow<List<MidiMapping>>`, `clearMappings()`
- [x] 1.4 Handle deserialization errors gracefully (clear corrupted mappings)
- [x] 1.5 Build and verify

## 2. MIDI Learn State

- [x] 2.1 Create `MidiLearnState.kt` at `model/` — state management for learn workflow
- [x] 2.2 Define `MidiLearnMode` enum: `IDLE`, `LEARN_ACTIVE`, `CONTROL_SELECTED`
- [x] 2.3 Add `selectedParamId: Int?` state, `ccCapture: Int?` state
- [x] 2.4 Implement `enterLearnMode()`, `exitLearnMode()`, `selectControl(paramId)`, `captureCC(ccNumber)`, `storeMapping()`
- [x] 2.5 Build and verify

## 3. MIDI Learn Button + State in MainSynthScreen

- [x] 3.1 Add `var midiLearnMode by remember { mutableStateOf(MidiLearnMode.IDLE) }` state
- [x] 3.2 Add `var selectedParamId by remember { mutableStateOf<Int?>(null) }` state
- [x] 3.3 Add MIDI Learn button in the toolbar (between SAVE and HELP buttons) — toggles learn mode
- [x] 3.4 Wire `MidiMappingStore` — load mappings on startup, save on change
- [x] 3.5 Build and verify

## 4. Visual Feedback in Components

- [x] 4.1 Add `learnMode: Boolean` and `isSelected: Boolean` and `isMapped: Boolean` parameters to `SynthKnob`
- [x] 4.2 In learn mode: draw pulsing amber border around the knob (animated alpha)
- [x] 4.3 When selected: draw bright white border
- [x] 4.4 When mapped (outside learn mode): draw small green dot in corner
- [x] 4.5 When learn mode active: tapping a knob sets it as selected instead of changing its value
- [x] 4.6 Build and verify

## 5. Wire Learn Mode to Panels

- [x] 5.1 Add `learnMode` and `selectedParamId` state parameters to all panel composables (OscillatorPanel, FilterPanel, etc.)
- [x] 5.2 Update `SynthKnob` calls in each panel to pass `learnMode`, `isSelected`, and `isMapped`
- [x] 5.3 When learn mode active and knob tapped: set `selectedParamId` to that knob's param ID (don't change value)
- [x] 5.4 Build and verify

## 6. Wire MidiController to Use Mappings

- [x] 6.1 In `MidiController.kt`, add reference to `MidiMappingStore` and load mappings
- [x] 6.2 In `handleMidiMessage`, when a CC arrives: check mapping table first
- [x] 6.3 If mapped: apply value to `SynthEngine.setParam(mappedParamId, scaledValue)`
- [x] 6.4 If not mapped: fall through to default CC handling
- [x] 6.5 When in learn mode: capture the CC number and create mapping
- [x] 6.6 Build and verify

## 7. Clear Mappings

- [x] 7.1 Add long-press on MIDI Learn button to clear all mappings (with confirmation toast)
- [x] 7.2 Add long-press on a mapped control in learn mode to clear that single mapping
- [x] 7.3 Build and verify

## 8. Quality Gate

- [x] 8.1 Run `./gradlew lint`
- [x] 8.2 Run `./gradlew clean assembleDebug`
