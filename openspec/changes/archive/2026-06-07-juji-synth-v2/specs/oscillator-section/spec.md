## MODIFIED Requirements

### Requirement: Oscillator level control
Each oscillator SHALL have an independent level control (0-100%) rendered as a realistic 3D hardware-style knob with metallic rim, radial gradient body, rotation indicator line, and shadow. The knob SHALL display its current value below.

#### Scenario: OSC2 level at zero
- **WHEN** OSC2 level is set to 0%
- **THEN** only OSC1 contributes to the audio output

#### Scenario: OSC2 level at maximum
- **WHEN** OSC2 level is set to 100%
- **THEN** OSC2 contributes maximally to the audio output

#### Scenario: Knob visual feedback
- **WHEN** user drags the oscillator level knob
- **THEN** the knob rotates smoothly with the indicator line tracking the value, and the value display updates in real-time

## ADDED Requirements

### Requirement: Hardware-style oscillator panel
The oscillator section SHALL be rendered with a realistic hardware panel appearance including: brushed metal or dark textured background, beveled edges, section label in engraved style, and proper spacing between controls matching hardware synth layouts.

#### Scenario: Panel appearance
- **WHEN** the oscillator panel is displayed
- **THEN** it has a textured background, beveled border, and controls arranged in a layout resembling a hardware synthesizer front panel

### Requirement: Waveform selector with visual indicator
Waveform selectors SHALL display the selected waveform shape as a small icon/graphic alongside the text label, with the selected option highlighted using a glowing accent color.

#### Scenario: Selected waveform visual
- **WHEN** user selects the sawtooth waveform
- **THEN** the sawtooth button shows a small sawtooth wave icon and glows with the accent color
