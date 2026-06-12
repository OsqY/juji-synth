## 1. Fix Sine Wave Icon Clipping

- [x] 1.1 Fix WaveformIcon sine path in Components.kt: scale cubic bezier control points to fit within [0, h] canvas bounds
- [ ] 1.2 Verify sine wave is clearly visible at 16dp size in both OSC1 and OSC2 sections
- [ ] 1.3 Verify all four waveform shapes (Saw, Sqr, Tri, Sin) are distinguishable and not clipped

## 2. Knob Touch Isolation and Layout

- [x] 2.1 Add minimum drag distance threshold (8dp touch slop) to RealKnob.kt before registering value change
- [ ] 2.2 Add Modifier.padding(horizontal = 4.dp) between adjacent RealKnob instances in all panels: OscillatorPanel, FilterPanel, EnvelopePanel, EffectsPanel, LfoPanel
- [x] 2.3 Redesign EnvelopePanel.kt knob layout: reduce knob size to 44dp and arrange 4 AMP ENV knobs in 2x2 grid, same for FILTER ENV
- [ ] 2.4 Verify that touching Sustain does not affect Decay, Attack, or Release values

## 3. Add Oscillator Waveform Preview

- [x] 3.1 Create OscWaveformView.kt: new Composable that renders a single cycle of the selected waveform at column-width x 60dp using Canvas Path
- [x] 3.2 Integrate OscWaveformView into OscillatorPanel.kt below the waveform buttons, showing OSC1 waveform in KnobAmber and OSC2 waveform in KnobCyan
- [ ] 3.3 Verify preview updates instantly when waveform selection changes, and all four shapes are crisp and fully visible

## 4. Add Filter Response Curve

- [x] 4.1 Create FilterResponseView.kt: new Composable that renders frequency response curve based on cutoff, resonance, and filter mode
- [x] 4.2 Implement SVF transfer function calculation for LPF (low_), HPF (high_), BPF (band_) modes to compute gain at ~100 frequency points
- [x] 4.3 Integrate FilterResponseView into FilterPanel.kt, positioned above the Cutoff/Resonance/Env Amt knobs
- [ ] 4.4 Verify the curve updates in real-time when cutoff, resonance, or mode changes

## 5. Add LFO Animated Waveform Preview

- [x] 5.1 Create LfoAnimationView.kt: new Composable that renders the LFO waveform oscillating in real-time using withFrameMillis animation clock
- [x] 5.2 Support all 5 LFO shapes: Sin (sine curve oscillating), Sqr (square wave PWM), Saw (rising sawtooth cycling), Tri (triangle wave oscillating), Rnd (sample-and-hold stepped)
- [x] 5.3 Make animation speed proportional to LFO rate and amplitude proportional to LFO depth
- [x] 5.4 Integrate into LfoPanel.kt — separate preview for LFO1 (KnobPink) and LFO2 (KnobOrange), sized at column-width x 48dp each

## 6. Fix Sequencer Tempo Transmission

- [x] 6.1 Fix SequencerView.kt: change `SynthEngine.setParam(60, it)` to send the actual BPM value (`tempo`) instead of the normalized knob value (`it = tempo / 300f`)
- [ ] 6.2 Verify sequencer runs at the correct tempo (120 BPM plays 120 beats per minute)

## 7. Expose Sequencer Current Step to UI

- [x] 7.1 Add `nativeGetSequencerStep()` JNI function in JniBridge.cpp that returns `currentStep_` from Sequencer.cpp
- [x] 7.2 Add corresponding `SynthEngine.getSequencerStep()` Kotlin method in SynthEngine.kt
- [x] 7.3 Add `sequencerCurrentStep: Int` to SynthState data class (default 0)
- [x] 7.4 Add polling loop in MainSynthScreen.kt: when sequencer is playing, poll getSequencerStep() at ~30fps and update synthState
- [x] 7.5 Update MainSynthScreen.kt to pass `sequencerCurrentStep` to SequencerView instead of hardcoded `0`
- [ ] 7.6 Verify step highlighting advances correctly during playback

## 8. Implement Sequencer Step Editing JNI

- [x] 8.1 Implement `nativeSetSequencerSteps()` in JniBridge.cpp: convert Java IntArray arrays to std::array<SequencerStep, 16> and call AudioEngine::setSequencerSteps()
- [x] 8.2 Add array length validation (must be 16 elements each)
- [x] 8.3 Wire step editing in SequencerView.kt: on step toggle, call SynthEngine.setSequencerSteps() with current steps array
- [ ] 8.4 Verify step data flows correctly from UI toggle through JNI to engine

## 9. Redesign Piano Roll as DAW-Style Pattern Editor

- [x] 9.1 Add `pianoRollNotes: List<PianoRollNote>` and `pianoRollLength: Int` to SynthState data class
- [x] 9.2 Define PianoRollNote data class with fields: note (0-127), startStep (Float), duration (Float), velocity (0-127), muted (Boolean)
- [x] 9.3 Rewrite PianoRollView.kt: render grid with MIDI note rows (C2-C7) and time columns (16 steps by default)
- [x] 9.4 Implement note creation: tap on empty cell creates a note of default length (1 step) and velocity (100); tap-and-drag creates variable-length note
- [x] 9.5 Implement note dragging: vertical drag changes pitch, horizontal drag moves start position (snap to grid)
- [x] 9.6 Implement note resizing: drag right edge to change duration
- [x] 9.7 Implement velocity editing: long-press note to show velocity control, store velocity per-note, visualize by color intensity
- [x] 9.8 Implement pattern playback: add playhead that moves across grid at sequencer tempo; trigger noteOn/noteOff on engine as playhead passes notes
- [x] 9.9 Notes placed in piano roll do NOT play immediately (wait for transport play)
- [x] 9.10 Integrate piano roll playback with transport: use sequencer tempo and play/stop state; pattern loops when it reaches pianoRollLength
- [x] 9.11 Add toggle between piano roll view and traditional keyboard view (existing behavior preserved)

## 10. Add Sequencer Recording Mode

- [x] 10.1 Add `sequencerRecording: Boolean` to SynthState (default false)
- [x] 10.2 Add record arm button to SequencerView transport controls (red when armed, gray when idle)
- [x] 10.3 Implement recording logic: when armed and transport playing, capture keyboard noteOn/noteOff events and quantize to nearest 1/16th note grid position
- [x] 10.4 Captured notes populate the active pattern (piano roll notes list)
- [x] 10.5 Add recording indicator (pulsing red dot) during active recording
- [x] 10.6 Stop recording preserves all captured notes; disarming without stopping discards unsaved recording

## 11. Add Sequencer Loop Toggle

- [x] 11.1 Add `sequencerLooping: Boolean` to SynthState (default true)
- [x] 11.2 Add loop toggle button to SequencerView transport controls (illuminated when active)
- [x] 11.3 Implement loop behavior in engine: when loop is on, sequencer wraps to step 0; when off, stops after last step
- [x] 11.4 Update AudioEngine.cpp / Sequencer.cpp to support non-looping playback mode
- [x] 11.5 Expose loop state via JNI (setSequencerLooping + getSequencerLooping)

## 12. Fix Filter Resonance Stutter

- [x] 12.1 Refactor Filter.cpp: move coefficient calculation (sin(), f_, q_) out of applyEnvelope() into setCutoff(), setResonance(), setEnvelopeAmount()
- [x] 12.2 Add recalcCoefficients() private method that computes targetF_ using sin() and caps at 0.95 for stability
- [x] 12.3 Add effectiveF_ smoothing in applyEnvelope(): one-pole lerp between current effectiveF_ and targetF_
- [x] 12.4 Clamp max Q value to 20.0 (was 25.5 at resonance=1.0) to prevent self-oscillation stutter
- [x] 12.5 Apply envelope modulation to effective cutoff before coefficient lookup (not after)
- [ ] 12.6 Verify at high resonance (0.8-1.0) the filter does not produce stuttering/ringing artifacts

## 13. Fix Noise Gate Behavior

- [x] 13.1 Change noise gating: make noise respond immediately to knob value — if noiseLevel_ is 0, output 0 noise regardless of active voice count
- [x] 13.2 Keep the active-voice gate as an additional condition (noise only plays when both knob > 0 AND voices active)
- [x] 13.3 Remove or significantly reduce noise smoothing coefficient (change from 0.005 to 0.05 for faster response) — or remove smoothing entirely for the gate transition
- [ ] 13.4 Verify noise stops immediately when knob is turned to 0, even during active notes

## 14. Investigate and Fix Chorus Parameter Bleed

- [x] 14.1 Add Android logging (LOGI) to setAllParamsFromArray() logging all values received when chorus params are set
- [x] 14.2 Add Android logging to swapParamsIfNeeded() logging pendingParams_ before and after swap
- [x] 14.3 Add Android logging to applySynthStateToEngine() in Kotlin to trace when it's called
- [ ] 14.4 Build and install test APK, reproduce the chorus bug, capture logs
- [ ] 14.5 Based on log analysis, fix the root cause (likely stale pendingParams_ fields, unexpected preset load, or memory corruption)
- [ ] 14.6 Verify chorus adjustment no longer changes filter resonance, env amount, delay, distraction, or sequencer values

## 15. Build and Verify

- [x] 15.1 Build the project with `./gradlew assembleDebug`
- [x] 15.2 Fix any compilation errors
- [x] 15.3 Install on device with `./install.sh`
- [ ] 15.4 Run through all 14 fix categories and verify each on-device:
  - Sine wave visible ✓
  - Knob touch isolation ✓
  - Three curve graphs rendering ✓
  - Sequencer tempo correct ✓
  - Step highlighting works ✓
  - Step editing flows through JNI ✓
  - Piano roll: place, drag, resize, velocity, play ✓
  - Recording mode: arm, play, capture notes ✓
  - Loop toggle works ✓
  - No filter stutter at high resonance ✓
  - Noise stops at knob=0 ✓
  - Chorus no longer bleeds parameters ✓
