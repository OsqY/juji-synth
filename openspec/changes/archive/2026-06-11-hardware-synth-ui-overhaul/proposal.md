## Why

The current UI uses a tab-based layout that hides 6 of 7 panels at any time, making the synth feel like a mobile app rather than a professional instrument. Users must constantly switch tabs to tweak related parameters (e.g., adjusting oscillator waveform then filter cutoff). The knobs are simple circles with no rotation ticks, the panels float without hardware context, and there's no visual hierarchy that maps to how real synthesizers are laid out. A hardware-inspired UI would show all sections simultaneously, use realistic rotary controls, and group parameters into recognizable synth sections (VCO, VCF, VCA, LFO, FX) — making the instrument immediately familiar to anyone who has used a Minilogue, Prophet, or Sub 37.

## What Changes

- **Replace tab-based single-panel layout with persistent multi-section layout**: All 7 sections (OSC, FILTER, ENV, LFO, FX, MOD, SEQ) visible simultaneously on one screen, arranged in a grid that mimics a hardware synth front panel
- **Redesign knobs as photorealistic rotary encoders**: Add concentric tick marks, value arc, pointer needle, shadow depth, and color-coded LED rings. Support both vertical drag and rotary gesture.
- **Add hardware chassis aesthetic**: Brushed metal panels with screw heads, section dividers, beveled edges, dark anodized aluminum background, and subtle inner shadows
- **Add LCD/LED value displays**: Small seven-segment or dot-matrix readouts next to each knob showing the current value (e.g., "440Hz", "0.7s", "Saw")
- **Redesign keyboard area**: Piano keys with proper overhang, velocity shading, octave markers, and a pitch-bend/mod-wheel ribbon controller
- **Add patch bay / cable visual for modulation matrix**: Instead of a list, show a mini patch bay with virtual cables connecting sources to destinations
- **Add scope/oscilloscope visualization**: Small real-time waveform display showing the output signal
- **Move preset browser into a slide-out drawer** instead of a full-screen dialog
- **Keep all existing functionality**: MIDI learn, sequencer, settings, manual — just with new visual presentation

## Capabilities

### New Capabilities
- `hardware-chassis-layout`: Multi-section persistent layout with brushed metal panels, screw heads, section dividers
- `realistic-knob-controls`: Photorealistic rotary encoders with tick marks, value arcs, LED rings, and proper gesture handling
- `lcd-value-displays`: Small hardware-style readouts showing formatted parameter values next to controls
- `patch-bay-modulation`: Visual patch bay with virtual cables for the modulation matrix
- `oscilloscope-display`: Real-time waveform visualization of the synth output
- `slide-out-preset-drawer`: Slide-out panel for preset browsing instead of full-screen dialog

### Modified Capabilities
*(None — this is purely a UI presentation change with no spec-level behavior changes)*

## Impact

- **`MainSynthScreen.kt`**: Complete rewrite of layout — replace tab switcher with persistent grid
- **`Components.kt`**: Major refactor — `SynthKnob` becomes photorealistic rotary encoder
- **`HardwareChassis.kt`**: Expanded to include section dividers, screw heads, brushed metal textures
- **All `*Panel.kt` files**: Minor layout adjustments to fit new grid (column widths, spacing)
- **New files**: `OscilloscopeView.kt`, `PatchBayView.kt`, `LcdDisplay.kt`, `RealKnob.kt`
- **Theme**: New colors for brushed metal, LED glow, anodized aluminum
