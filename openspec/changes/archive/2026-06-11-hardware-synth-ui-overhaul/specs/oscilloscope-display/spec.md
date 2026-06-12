## ADDED Requirements

### Requirement: A real-time oscilloscope SHALL display the output waveform
The synth UI SHALL include a small oscilloscope display that shows the actual audio output waveform in real-time. The display SHALL update at 30fps.

#### Scenario: Waveform visualization
- **WHEN** a note is playing
- **THEN** the oscilloscope shows a live waveform
- **AND** the waveform matches the audible sound (saw, square, etc.)

### Requirement: Oscilloscope SHALL have a CRT-style appearance
The display SHALL use a dark screen with a bright green trace, scanline effect, and subtle screen curvature to mimic a vintage CRT oscilloscope.

#### Scenario: CRT aesthetic
- **WHEN** viewing the oscilloscope
- **THEN** the trace is bright green on dark background
- **AND** scanlines are faintly visible
- **AND** the display has rounded corners
