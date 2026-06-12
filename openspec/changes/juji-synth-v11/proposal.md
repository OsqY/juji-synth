## Why

Comprehensive audit of the codebase revealed that several major features exist as scaffolding but are never wired to the audio engine. The sequencer, modulation matrix, MIDI controller, and settings screen are all UI-only — they don't actually change sound output. Additionally, the effects section lacks a chorus (one of the most important synth effects), the keyboard is fixed at 2 octaves with no scrolling, and there's no sustain pedal support or arpeggiator. This change wires all existing scaffolding and adds the most impactful missing features.

## What Changes

- **Wire MIDI controller**: Instantiate and start `MidiController` in MainSynthScreen with `DisposableEffect` lifecycle
- **Wire sequencer to C++ engine**: Send sequencer tempo, playing state, and step data to the C++ engine via JNI
- **Wire modulation matrix to C++ engine**: Send active modulation routes to the C++ engine via JNI
- **Make settings actually apply**: On settings change, restart the Oboe audio stream with new sample rate/buffer. For mono/stereo, update the output channel count.
- **Fix manual search**: Wire the search text field to filter manual sections in real-time
- **Add chorus effect**: Stereo modulated delay line for thickening pads, leads, and polyphonic sounds
- **Add keyboard scrolling**: Horizontal drag to scroll the keyboard beyond 2 octaves
- **Add sustain pedal (MIDI CC64)**: Handle CC64 messages to hold released notes

## Capabilities

### New Capabilities
- `chorus-effect`: Stereo modulated delay chorus with rate, depth, and mix controls
- `keyboard-scrolling`: Horizontal drag scroll for accessing more than 2 octaves
- `sustain-pedal`: MIDI CC64 support for holding notes after key release
- `settings-apply`: Audio engine restart when sample rate/buffer change

### Modified Capabilities
- `midi-connectivity`: Instantiate and start MidiController in main screen, add CC64 sustain handling
- `sequencer`: Wire playing state, tempo, and steps to C++ engine via JNI
- `modulation-matrix`: Wire active routes to C++ engine via JNI
- `help-system`: Fix search filtering

## Impact
- **MainSynthScreen.kt**: Add MIDI controller instantiation, sequencer JNI calls, mod matrix JNI calls, settings restart logic, keyboard scroll state
- **KeyboardView.kt**: Add horizontal scroll state, wider effective range
- **MidiController.kt**: Add CC64 sustain pedal handling, expose connection status
- **JniBridge.cpp**: Add new JNI calls for sequencer steps, mod routes
- **EffectsPanel.kt**: Add Chorus section with rate/depth/mix knobs
- **Reverb.h/.cpp**: Rename or keep, add Chorus.h/.cpp
- **AudioEngine.h/.cpp**: Add Chorus processor, handle new JNI calls
- **ManualScreen.kt**: Fix search filtering
- **SynthEngine.kt**: Add new native methods for sequencer steps, mod routes
- **CMakeLists.txt**: Add Chorus.cpp to build
- **SettingsScreen.kt**: Add callback/effect when settings change (restart engine)