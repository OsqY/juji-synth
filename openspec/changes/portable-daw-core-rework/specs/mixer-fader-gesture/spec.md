# Spec: Mixer Fader Gesture

## Requirements

- **R1** `MixerScreen.VerticalFader` SHALL use a single unified pointer-input
  gesture detector that distinguishes tap vs. drag via hysteresis, so a small
  drag does not first trigger a tap-jump.
- **R2** The fader SHALL apply value smoothing and/or step quantization
  (e.g. 0.1 dB steps) so dragging feels continuous, not per-pixel jumpy.
- **R3** Level polling (`MixerViewModel.startLevelPolling`) SHALL be debounced
  to ≥300 ms and SHALL NOT cause recomposition on every drag delta.
- **R4** The fader's `Canvas`/drawn state SHALL be hoisted so per-delta value
  changes do not recompose the entire 16-strip row.
- **R5** A shared `DraggableValue` controller SHALL be used for both the
  fader and `RealKnob` so drag thresholds are consistent across the app.

## Scenarios

### S1: Small drag does not tap-jump

- **Given** the user is dragging a mixer fader
- **When** the user starts a small drag of < 8 dp
- **Then** the fader does not jump to the touch Y (no tap-jump); it begins
  following the drag smoothly.

### S2: Smooth drag

- **Given** the user drags a fader across its full range
- **When** the drag is in progress
- **Then** the value updates smoothly (no visible stutter), and the 16-strip
  row does not recompose per pixel.

### S3: Level polling doesn't thrash

- **Given** the mixer is open
- **When** the user is not interacting
- **Then** level meter updates happen at most every 300 ms, not every frame.
