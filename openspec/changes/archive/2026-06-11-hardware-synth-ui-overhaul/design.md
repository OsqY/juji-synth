## Context

The current UI is built with Jetpack Compose and uses a tab-based navigation pattern. `MainSynthScreen` renders a `TabBar` at the top and a single `HardwareChassis` containing one panel at a time (OSC, FILTER, ENV, LFO, FX, MOD, SEQ). The bottom half is a piano keyboard. This works for mobile but feels toy-like.

The existing `Components.kt` has a `SynthKnob` implementation (718 lines) that uses Compose Canvas to draw a circle with an arc and a pointer line. It's functional but flat. The `HardwareChassis.kt` (102 lines) is just a rounded rectangle with a gradient border.

All parameter changes flow through `synthState` (a `mutableStateOf<SynthState>`) and call `SynthEngine.setParam()` or the new bulk apply. MIDI learn is supported. The sequencer and preset browser are separate dialogs.

## Goals / Non-Goals

**Goals:**
- Show all synth sections simultaneously (no tab switching)
- Make knobs look and feel like real rotary encoders
- Give the entire app a hardware synthesizer aesthetic (dark metal, screws, bezels)
- Add real-time oscilloscope visualization
- Replace modulation matrix list with a visual patch bay
- Keep all existing functionality intact (MIDI learn, presets, sequencer, settings)

**Non-Goals:**
- Changing the audio engine or parameter system
- Adding new synthesis features (this is purely UI)
- Supporting landscape orientation differently (portrait primary)
- Custom shaders or OpenGL (Compose Canvas only)

## Decisions

### 1. Layout: Persistent 3-column grid

**Chosen**: Divide the screen into three vertical sections:
- **Left column**: OSC (oscillators + sub + noise + mix), MOD (modulation matrix as patch bay)
- **Center column**: FILTER (cutoff, resonance, env amount, mode), ENV (amp + filter ADSR), LFO (rate, depth, waveform)
- **Right column**: FX (reverb, delay, distortion, chorus), SEQ (16-step grid), small oscilloscope
- **Bottom**: Piano keyboard + transport + preset drawer handle

**Why**: Matches classic analog synth layout — oscillators left, filter/envelope center, effects right. All parameters visible at a glance.

**Alternative rejected**: Single scrollable column (too much scrolling). Two-column (too cramped on phone).

### 2. Knob design: Layered Canvas with gesture support

**Chosen**: Build each knob as a Compose Canvas with multiple layers:
1. Outer shadow (blur + offset)
2. Metal rim (concentric gradient circles)
3. Knob face (radial gradient, darker at edges)
4. Tick marks (60 ticks around the perimeter, highlighted in the active arc)
5. Pointer needle (triangle or line)
6. LED ring (colored glow around the rim when value > 0)

Gesture: vertical drag for fine control, horizontal drag disabled (to prevent accidental changes when scrolling).

**Why**: Canvas gives full control over appearance. Vertical drag is standard in synth apps.

**Alternative rejected**: Image-based knobs (harder to animate, larger APK). Pre-made Compose libraries (none match the exact hardware aesthetic).

### 3. Color scheme: Dark anodized aluminum + accent LEDs

**Chosen**: Replace the purple gradient theme with:
- Background: `#1A1A1E` (dark gunmetal)
- Panel: `#2D2D35` (brushed aluminum dark)
- Panel highlight: `#3D3D45` (top edge light)
- Panel shadow: `#151519` (bottom edge dark)
- Screw head: `#8A8A90` with `#B0B0B8` highlight
- LED ring: color-coded per section (amber=osc, cyan=filter, green=env, pink=lfo, red=fx)
- Text: `#E8E8EC` (off-white)

**Why**: Purple feels like an app. Dark metal feels like a synth.

### 4. Oscilloscope: Tap into audio output

**Chosen**: Add a JNI method `nativeGetWaveform(float[] buffer, int size)` that copies the last N samples from the audio callback into a float array. The Kotlin side polls this at 30fps and draws the waveform.

**Why**: Real-time visualization is essential for a "real synth" feel. The audio callback already has the output buffer.

**Alternative rejected**: Generating fake waveforms from parameter state (doesn't show the actual sound).

### 5. Patch bay: Simplified cable visualization

**Chosen**: Show 8 rows, each with a source jack (left), destination jack (right), and amount knob (center). Active routes draw a Bézier curve between the jacks. Amount controls the cable thickness/brightness.

**Why**: More visual than a list, but simpler than a fully free-form drag-and-drop patch bay.

### 6. Preset drawer: Bottom sheet

**Chosen**: Replace the full-screen `AlertDialog` with a `BottomSheetScaffold` that slides up from the bottom. Shows 3 columns: category list, preset list, preset details.

**Why**: Doesn't block the entire synth. Quick access without losing context.

## Risks / Trade-offs

- **Screen real estate**: Showing all panels simultaneously on a phone screen is tight. We'll use smaller knobs (48dp) and compact spacing. Tablets get larger knobs.
- **Performance**: 30+ Canvas-based knobs + oscilloscope polling could drop frames on low-end devices. Mitigation: use `remember` aggressively, draw oscilloscope at 15fps if needed.
- **Accessibility**: Photorealistic knobs are harder to use with TalkBack. We'll add content descriptions and haptic feedback on value changes.
- **Migration**: Old users may be confused by the new layout. We'll keep the manual screen updated.

## Open Questions

- Should the oscilloscope be in the main layout or a toggleable overlay?
- Should we add a "compact mode" that hides the keyboard for more panel space?
- Do we want blinking LEDs on active LFOs / sequencer steps?
