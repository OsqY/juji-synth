## ADDED Requirements

### Requirement: Real-time oscillator waveform preview
The OSC panel SHALL display a real-time waveform preview showing the currently selected waveform shape (saw, square, triangle, or sine) at panel-filling size.

- The preview SHALL render a single cycle of the selected waveform using Canvas Path drawing
- The preview SHALL update when the user changes the waveform selection
- The preview SHALL be clearly visible — no clipping, no invisible elements
- The preview SHALL use the oscillator's accent color (KnobAmber for OSC1, KnobCyan for OSC2)
- The preview size SHALL be at minimum column-width × 60dp

#### Scenario: Changing waveform updates preview
- **WHEN** user taps a waveform button (Saw, Sqr, Tri, Sin)
- **THEN** the waveform preview immediately updates to show the selected shape
- **AND** each waveform shape is clearly distinguishable and fully visible

#### Scenario: Sine wave is visible
- **WHEN** user selects Sine waveform for OSC1
- **THEN** the preview shows a complete sine wave curve without clipping
- **AND** the sine wave does NOT share the broken rendering of the old WaveformIcon
