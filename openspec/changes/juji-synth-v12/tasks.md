## 1. Fix Keyboard Contrast

- [x] 1.1 In Color.kt, change KeyBlack from `Color(0xFF1A1A2E)` to `Color(0xFF3A3A5E)`
- [x] 1.2 In KeyboardView.kt, change white key label text color from `Color(0xFF707080)` to `Color(0xFF333333)`
- [x] 1.3 In KeyboardView.kt, change black key label text color from `Color(0xFF505060)` to `Color(0xFF9A9AB0)`
- [x] 1.4 Build and verify black keys are visible against background

## 2. Fix Preset Stutter

- [x] 2.1 In AudioEngine.cpp applyModulationMatrix(), add a check: if a voice is active, skip `setAmpEnvelope()` and `setFilterEnvelope()` calls
- [x] 2.2 Only update oscillator levels, waveforms, detune, filter cutoff, filter resonance, filter mode on active voices
- [x] 2.3 Ensure `noteOn()` (which calls applyModulationMatrix + noteOn on the voice) still applies envelope params to NEW voices since the voice was just `init()`-ed
- [x] 2.4 Build and verify held notes don't cut out on preset load

## 3. Fix Preset Filter Persistence

- [x] 3.1 In MainSynthScreen.kt, add `var presetCategory by remember { mutableStateOf("All") }` state
- [x] 3.2 Update PresetBrowser signature to accept `selectedCategory: String` and `onCategoryChange: (String) -> Unit`
- [x] 3.3 Replace internal `var selectedCategory by remember` in PresetBrowser with external state
- [x] 3.4 Pass the state from MainSynthScreen to PresetBrowser
- [x] 3.5 Build and verify filter persists across dialog open/close

## 4. Expand Help Section

- [x] 4.1 Add "Sequencer Walkthrough" section to manualSections with step-by-step usage guide
- [x] 4.2 Add "MIDI Learn" section explaining the learn workflow
- [x] 4.3 Add "Chorus Effect" section explaining chorus parameters
- [x] 4.4 Fix search filtering: ensure filteredSections correctly updates when searchQuery changes
- [x] 4.5 Build and verify

## 5. Improve Sequencer Grid

- [x] 5.1 In SequencerView.kt, increase step Box size from 20×16dp to 28×22dp
- [x] 5.2 Add note name display (e.g., "C4") instead of just a "●" dot for active steps
- [x] 5.3 Add helper function to convert MIDI note number to note name (e.g., 60 → "C4")
- [x] 5.4 When sequencer is stopped and user taps a step, briefly play the step's note (preview mode)
- [x] 5.5 Build and verify

## 6. Quality Gate

- [x] 6.1 Run `./gradlew lint`
- [x] 6.2 Run `./gradlew clean assembleDebug`
