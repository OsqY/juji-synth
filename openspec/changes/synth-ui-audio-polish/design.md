## Context

The synth uses a 3-column persistent hardware-style layout. All audio processing happens in C++ (Oboe) with state mirrored to Kotlin via JNI. The UI is Jetpack Compose with Canvas-based custom widgets. The previous overhaul gave us the visual foundation (RealKnob, PatchBay, Oscilloscope, theme), but revealed 15+ usability issues during device testing. Key architectural constraints:

- **Audio-critical path**: Filter.cpp `applyEnvelope()` is called every sample from `SynthVoice::process()` — expensive `sin()` calculations and coefficient changes destabilize the SVF
- **State sync**: Kotlin `SynthState` data class mirrors C `SynthParams`. Tempo currently sent as normalized 0-1 ratio instead of raw BPM due to copy-paste from knob value patterns
- **Sequencer isolation**: `currentStep` is hardcoded to 0 in the UI; `nativeSetSequencerSteps` is a no-op; there's no JNI bridge to read sequencer state back from the engine
- **Knob density**: Four 60dp knobs in ~130dp-wide columns with no touch slop

## Goals / Non-Goals

**Goals:**
- Fix all waveform icons so every shape is clearly visible at 16dp
- Eliminate adjacent knob touch bleed with proper hit-test isolation
- Make sequencer functional: correct tempo, step highlighting, JNI-wired editing
- Build a DAW-style piano roll with variable-length notes, velocity editing, and pattern playback
- Add sequencer recording mode (capture keyboard input into steps)
- Add loop playback toggle
- Fix filter resonance stutter by moving coefficient calc out of per-sample loop
- Fix noise gate so noise stops immediately when knob is at 0
- Reproduce and fix chorus→other-params bleed bug
- Add three real-time curve visualizations: oscillator waveform, filter response, LFO animation

**Non-Goals:**
- Multi-track or MIDI file export
- Polyphonic aftertouch
- Visual redesign of existing panel layouts beyond knob spacing
- Real-time oscilloscope redesign (already functional)

## Decisions

### 1. Piano Roll Data Model
**Decision:** Store patterns as `List<NoteEvent>` in SynthState rather than a fixed grid.

```kotlin
data class PianoRollNote(
    val note: Int,          // MIDI note 0-127
    val startStep: Float,   // fractional step position
    val duration: Float,    // in steps (1.0 = one beat at 4/4)
    val velocity: Int,      // 0-127
    val muted: Boolean = false
)

data class SynthState(
    ...
    val pianoRollNotes: List<PianoRollNote> = emptyList(),
    val pianoRollLength: Int = 16,  // steps
    val sequencerRecording: Boolean = false,
    val sequencerLooping: Boolean = true,
    ...
)
```

**Rationale:** A flat list of note events is more flexible than a fixed grid — supports arbitrary note lengths, overlapping notes, and easy serialization. The sequencer's 16-step grid maps naturally: each step = 1 beat at current tempo.

**Alternative considered:** Fixed 16-step × 48-note grid (like current but with note length). Rejected because it's less flexible and harder to support recording.

### 2. Sequencer Recording Mode
**Decision:** Add a record-arm toggle. When armed, notes played on the keyboard are captured into the selected track (piano roll or step sequencer).

```
State machine:
  IDLE → user arms record → RECORD_ARMED → user plays notes → RECORDING
  RECORDING → user presses stop → IDLE (pattern captured)
  RECORD_ARMED → user disarms → IDLE
```

Recorded notes snap to the nearest 1/16th note grid position and are quantized to the current tempo.

### 3. Filter Coefficient Calculation
**Decision:** Remove `sin()` call from `Filter::applyEnvelope()`. Pre-calculate `f_` and `q_` in `Filter::setCutoff()`, `Filter::setResonance()`, and `Filter::setEnvelopeAmount()` — only when parameters actually change. `applyEnvelope()` then only modulates effectiveCutoff and linearly interpolates `f_` to the target.

```cpp
// Called when param changes (via JNI):
void Filter::setCutoff(double cutoff) {
    cutoff_ = std::clamp(cutoff, 0.0, 1.0);
    recalcCoefficients();
}

void Filter::recalcCoefficients() {
    double fc = 20.0 + (20000.0 - 20.0) * cutoff_ * cutoff_;
    fc = std::clamp(fc, 20.0, 20000.0);
    targetF_ = 2.0 * std::sin(M_PI * fc / sampleRate_);
    targetF_ = std::min(targetF_, 0.95);  // 0.95 instead of 1.0 for stability
    q_ = 0.5 + 19.5 * resonance_;
    if (resonance_ > 0.95) {
        q_ = std::min(q_, 20.0);  // cap Q to prevent self-oscillation stutter
    }
}

// Called every sample — cheap lerp:
void Filter::applyEnvelope(double envValue) {
    effectiveF_ += (targetF_ - effectiveF_) * 0.1f;  // one-pole smooth
    double mod = envValue * envAmount_;
    // ... modulate cutoff using smoothed effectiveF_
}
```

**Rationale:** The SVF state variables (low_, band_) accumulate instabilities when coefficients jump. Smoothing the coefficient transition prevents audible artifacts while keeping the filter responsive.

**Alternative considered:** Block-rate coefficient update (every N samples). Rejected — still produces audible steps.

### 4. Touch Slop in RealKnob
**Decision:** Add minimum drag distance threshold and use unique `pointerInput` keys per knob instance.

```kotlin
.pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { /* record start position */ },
        onDrag = { change, dragAmount ->
            if (/* distance from start > 8dp */) {
                change.consume()
                // apply delta
            }
        }
    )
}
```

Also add `Modifier.padding(horizontal = 4.dp)` between adjacent knobs in all panels.

### 5. Chorus Bug Investigation Strategy
**Decision:** Add Android logging to `setAllParamsFromArray()` and `swapParamsIfNeeded()` to log all parameter values when chorus is adjusted. Also log when `applySynthStateToEngine()` is called from Kotlin. This will reveal:

- Is `applySynthState` being called unexpectedly?
- Are `pendingParams_` fields holding stale values from previous operations?

The user confirmed this reproduces from a fresh start, so we can instrument and ask for a test build.

**Hypothesis:** `pendingParams_` retains old preset values for fields that aren't explicitly set by individual `setParam()` calls. If the engine starts, a preset loads (filling pendingParams_), then the user adjusts chorus (which sets only chorus fields in pendingParams_), and then `swapParamsIfNeeded()` copies the WHOLE pendingParams_ to currentParams_ — BUT the other fields still have the preset values, not the current UI state. This shouldn't cause a bug normally, UNLESS there's a code path where `applySynthStateToEngine` is called between the chorus adjustment and the swap.

### 6. Curve Graph Architecture
**Decision:** Three separate `@Composable` Canvas views, each updated via `LaunchedEffect` with a polling loop.

- **OscWaveformView**: Renders a single cycle of the selected waveform using the same Path logic as `WaveformIcon` but at panel-filling size (~column width × 60dp). No C++ integration needed — purely visual.
- **FilterResponseView**: Renders a frequency response curve based on cutoff, resonance, and mode. Uses the SVF transfer function equation to compute gain vs frequency. Canvas drawPath with cubic beziers for smooth curves.
- **LfoAnimationView**: Renders the LFO waveform oscillating in real-time. Uses `withFrameMillis` for animation clock, draws the waveform shape cycling through 0-2π phase. Syncs with LFO rate parameter.

## Risks / Trade-offs

- **[Risk] Chorus bug may be a memory corruption** → Mitigation: Add logging first, consider ASAN build if logging doesn't reveal the cause
- **[Risk] Filter smoothing may change sound character** → Mitigation: Make smoothing coefficient configurable, start with fast response (0.1 coefficient) that's inaudible but prevents stutter
- **[Risk] Piano roll + sequencer integration is complex** → Mitigation: Ship incremental — first make the sequencer work correctly, then add piano roll as a separate view, then add recording/looping
- **[Risk] New pattern data model doesn't match existing preset serialization** → Mitigation: `pianoRollNotes` defaults to empty list, presets without this field deserialize correctly with defaults
- **[Trade-off] Canvas graphs at 30fps polling adds UI thread load** → Mitigation: Use `withFrameMillis` for animation (pauses when not visible), keep draw calls simple (under 100 paths)

## Open Questions

- Chorus bug root cause: need logging to identify
- Piano roll playback: should it drive the engine's noteOn/noteOff directly, or feed into the sequencer's step events? Currently leaning toward direct engine control with sequencer tempo sync
- Recording quantize resolution: 1/16th notes? 1/32nd? Start with 1/16th
