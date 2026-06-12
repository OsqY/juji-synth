## Why

The synth UI looks good but has accumulated 15+ usability issues and audio engine bugs from recent refactoring. Waveform icons clip to invisibility, envelope knobs are too dense and cause touch bleed, the sequencer appears broken (tempo sent as normalized 0-1 instead of BPM, currentStep never exposed to UI), the piano roll is a simple toggle grid rather than a usable pattern editor, the filter stutters at high resonance (coefficients recalculated per-sample), noise persists across notes despite the gate, and adjusting chorus randomly resets other parameters to preset defaults. Users can't see what they're editing, can't reliably touch what they intend, and can't build/play patterns. These issues make the synth frustrating to use despite the polished hardware aesthetic.

## What Changes

1. **Fix all broken waveform icons** — Sine wave path clips outside canvas bounds, needs proper scaling
2. **Redesign envelope knob layout** — 4 knobs of 60dp in ≈130dp of space causes overlap, touch bleed. Use 2×2 grid or reduce size with padding
3. **Add touch slop to RealKnob** — Prevent adjacent knob bleed when dragging near edges
4. **Fix sequencer tempo** — Send raw BPM (30-300) instead of normalized 0-1 value; sequencer currently runs at 0.4 BPM
5. **Expose currentStep from engine to UI** — UI always shows step 0, making sequencer appear frozen
6. **Implement sequencer step editing JNI** — `nativeSetSequencerSteps` is a no-op placeholder
7. **Redesign piano roll as DAW-style pattern editor** — Variable-length notes, velocity editing, pattern build → play workflow, playhead sync with sequencer
8. **Implement sequencer recording** — Capture notes played on keyboard into sequencer steps
9. **Implement looping** — Pattern repeats; transport controls (play, stop, record, loop)
10. **Fix filter resonance stutter** — Move coefficient calculation out of per-sample `applyEnvelope()`, add smoothing, clamp Q to stable range
11. **Fix noise gate** — Make noise respond immediately to knob changes and per-voice or gate on per-sample voice activity
12. **Investigate and fix chorus parameter bleed** — Adjusting chorus resets filter resonance/env amount and other effects to preset defaults (confirmed reproducible from fresh start)
13. **Add oscillator waveform preview** — Large real-time curve showing selected waveform shape
14. **Add filter frequency response curve** — Visual showing cutoff + resonance peak
15. **Add animated LFO waveform preview** — Real-time oscillation visualization at current rate

## Capabilities

### New Capabilities
- `osc-waveform-preview`: Real-time oscillator waveform visualization in the OSC panel, showing the selected waveform shape at audible frequency
- `filter-response-curve`: Frequency response graph in the FILTER panel, visualizing cutoff, resonance peak, and mode (LPF/HPF/BPF)
- `lfo-animation`: Animated LFO waveform preview in the LFO panel, oscillating at the configured rate with the selected shape
- `piano-roll-daw`: Full DAW-style piano roll with variable-length notes, velocity lanes, note dragging/resizing, and pattern playback synced with the sequencer
- `sequencer-recording-looping`: Sequencer recording mode (capture keyboard notes into steps), loop playback, and transport controls (record arm, play, stop, loop toggle)
- `knob-touch-isolation`: Touch slop, minimum drag distance, and hit area padding to prevent adjacent knob bleed
- `sequencer-engine-sync`: Proper tempo value transmission, current step exposure from C++ engine to Kotlin UI, and JNI wiring for step editing

### Modified Capabilities
*(None — no existing specs to modify)*

## Impact

- **C++ Engine**: Filter.cpp (remove per-sample coefficient calc, add smoothing), AudioEngine.cpp (noise gating fix, chorus debug), Sequencer.cpp (currentStep exposure, recording mode)
- **JNI Bridge**: JniBridge.cpp (nativeGetSequencerStep, nativeSetSequencerSteps implementation, tempo value correction)
- **Kotlin UI**: Components.kt (sine icon path), RealKnob.kt (touch slop), EnvelopePanel.kt (knob layout), MainSynthScreen.kt (sequencer step polling, transport controls), PianoRollView.kt (complete rewrite), SequencerView.kt (recording mode, loop toggle)
- **New UI Files**: OscWaveformView.kt, FilterResponseView.kt, LfoAnimationView.kt
- **SynthState.kt**: Add sequencer recording state, loop state, piano roll pattern data
- **ParamIds.kt**: Add sequencer step count/current step param IDs
