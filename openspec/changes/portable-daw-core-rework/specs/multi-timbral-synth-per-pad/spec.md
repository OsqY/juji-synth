# Spec: Multi-Timbral Synth Per Pad

## Requirements

- **R1** The C++ `AudioEngine` SHALL host one `SynthInstrument` instance per
  pad (fixed pool, indexed by `padIndex`), each with its own `SynthParams`
  and preset state — not a single shared synth.
- **R2** A pad SHALL be in exactly one mode: **Sample** (holds a
  `SampleBuffer`) or **Synth** (owns a `SynthInstrument` instance + preset).
- **R3** When a pad is in Synth mode, `sampler->triggerPad(padIndex)` SHALL
  delegate `noteOn` to that pad's owned `SynthInstrument`, using the pad's
  `synthRootNote` as the base pitch and the incoming MIDI note as the offset.
- **R4** `SynthViewModel` SHALL address a specific pad's synth (by
  `padIndex`), not a global synth. Editing a pad's synth parameters SHALL
  only affect that pad's instance.
- **R5** `PresetDatabase` SHALL allow loading/saving a preset bound to a
  pad: `nativeLoadPresetToPad(padIndex, presetId)`.
- **R6** `KeyboardViewModel` SHALL expose a "Play Selected Pad" target: key
  presses route to the selected pad's loaded content (sample voice for a
  sample pad, that pad's `SynthInstrument` for a synth pad).
- **R7** `MasterBus` SHALL sum all synth-pad channels + the sampler channel
  into the master, with per-pad gain/pan available on the mixer.

## Scenarios

### S1: Two synth pads, different presets

- **Given** pad 1 and pad 2 are both in Synth mode, pad 1 preset = "Lead",
  pad 2 preset = "Bass"
- **When** the user holds a chord across both pads (e.g. via keyboard
  "Play Selected Pad" target switching, or both triggered in the sequencer)
- **Then** both presets are audible simultaneously and independently
  (multi-timbral).

### S2: Switch a pad from sample to synth

- **Given** pad 3 has a loaded sample
- **When** the user switches pad 3 to Synth mode and selects a preset
- **Then** triggering pad 3 plays the synth preset, not the sample.

### S3: Keyboard plays selected pad content

- **Given** pad 4 is selected and in Synth mode with a preset
- **When** the user presses a key in the keyboard (target = Play Selected Pad)
- **Then** pad 4's `SynthInstrument` plays at the pressed note.

### S4: Preset persistence per pad

- **Given** pad 7 is in Synth mode with a tweaked preset
- **When** the project is saved and reloaded
- **Then** pad 7's synth preset is recalled.
