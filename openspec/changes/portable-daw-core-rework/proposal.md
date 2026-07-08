# Proposal: Portable DAW Core Rework

## Why

Juji-Synth aims to be a portable DAW (FL-studio-style: pads drive every sound
source, sequencer rows map to pads, a pad can hold a sample OR a synth with its
own preset). Today the app fails on its core promise:

- Imported samples play back at the wrong pitch and truncated length
  (stereo WAV header is written before downmix → header/data mismatch).
- Placing a pad sound on the timeline and pressing Play produces **no sound**:
  pattern notes are always scheduled on `pattern.trackIndex = 0`, which is the
  synth channel, never the sampler/pads.
- The step sequencer is silent for the same reason — steps export `note=60` to
  channel 0; the `PAD_TRIGGER` event type exists in C++ but is never invoked
  from Kotlin.
- "A pad holds a synth with its own preset" is structurally impossible — the
  engine has ONE `SynthInstrument` instance; `PadConfig.synthMode` is a stub to
  that shared synth; presets cannot differ per pad.
- The phone UI (especially landscape) is inconsistent: the
  `NavigationRail` silently scrolls so SEQUENCER/PROJECT are perceived as cut
  off; there are three transport strips with two record buttons; cryptic
  `L-/I-/O+/P` micro-buttons; a clunky mixer fader with tap/drag contention.

This change fixes the audio engine + transport routing first, then makes the
UI coherent around the new pad-driven model.

## What Changes (slices)

- **A. sample-import-fidelity** — `AudioConverter.writeWavFile` writes a
  consistent mono WAV (header written AFTER downmix, `channels=1`,
  `byteRate`/`blockAlign`/`dataSize` matching the mono bytes). Round-trip test.
- **B. pad-triggered-transport (data model)** — `PatternClip` / `NoteEvent`
  carry a `padIndex`/trigger target; `TransportController.schedulePadTrigger`
  → JNI → `ScheduledEvent::makePadTrigger` → `Transport::firePendingEvents`
  PAD_TRIGGER branch (already calls `sampler->triggerPad`, extended to "the
  pad's loaded content"). Migration for old patterns.
- **C. fl-style-step-sequencer** — sequencer rows map 1:1 to pads (bank A);
  toggling a step on row R schedules a `PAD_TRIGGER` for pad R at that step.
  The pad's loaded content (sample OR synth) plays.
- **D. multi-timbral-synth-per-pad** — C++ `AudioEngine` hosts N
  `SynthInstrument` instances keyed by `padIndex` (fixed pool, one per pad);
  per-pad preset/state via `PresetDatabase`. A pad is in Sample mode
  (holds `SampleBuffer`) OR Synth mode (holds a synth instance + preset).
  `KeyboardViewModel` gains a "Play Selected Pad" target that routes key
  presses to the selected pad's loaded content (sample voice OR that pad's
  synth instance). JNI additions: `nativeSynthNoteOn(padIndex, note, vel)`,
  `nativeLoadPresetToPad(padIndex, presetId)`.
- **E. unified-transport-controls** — exactly ONE global transport bar in
  `MainScreen`; per-screen transport strips in `TimelineScreen` and
  `SequencerScreen` are removed and delegate to the global bar. Single record
  button.
- **F. landscape-navigation** — landscape `NavigationRail` becomes a visible
  scrollable rail with scrollbar/peek affordance (or compact rail + overflow
  drawer); all 7 tabs reachable; transport bar no longer steals the rail
  column height.
- **G. ui-control-clarity** — replace `L-/I-/O+/P` cryptic buttons with
  labeled/iconographic controls (Loop Start/End, Punch In/Out, Punch enable)
  with help text; fix `Time … BPM` layout so the two texts do not concatenate
  on narrow widths.
- **H. mixer-fader-gesture** — unified fader pointer input (single gesture
  detector with hysteresis so a small drag never tap-jumps), value
  smoothing/quantization, debounced level polling (≥300 ms), minimized
  recomposition (hoist Canvas state, stable list keys).

## Affected files

| Area | Files |
| --- | --- |
| Audio import | `app/src/main/java/com/jujidaw/audio/AudioConverter.kt`, `app/src/main/cpp/SampleBuffer.cpp/.h`, `app/src/main/cpp/SamplerInstrument.cpp/.h`, `app/src/main/cpp/SamplerVoice.cpp` |
| Transport routing | `app/src/main/java/com/jujidaw/engine/TransportController.kt`, `app/src/main/java/com/jujidaw/engine/SynthEngineScheduler.kt`, `app/src/main/java/com/jujidaw/audio/SynthEngine.kt`, `app/src/main/cpp/Transport.cpp/.h`, `app/src/main/cpp/ScheduledEvent.h`, `app/src/main/cpp/JniBridge.cpp`, `app/src/main/cpp/AudioEngine.cpp` |
| Sequencer | `app/src/main/java/com/jujidaw/ui/sequencer/SequencerViewModel.kt`, `app/src/main/java/com/jujidaw/ui/sequencer/SequencerScreen.kt`, `app/src/main/java/com/jujidaw/ui/SequencerView.kt`, `app/src/main/cpp/Sequencer.h/.cpp` |
| Timeline | `app/src/main/java/com/jujidaw/ui/timeline/TimelineViewModel.kt`, `app/src/main/java/com/jujidaw/ui/timeline/TimelineScreen.kt`, `app/src/main/java/com/jujidaw/model/ClipModel.kt`, `app/src/main/java/com/jujidaw/model/PatternModel.kt` |
| Multi-timbral synth | `app/src/main/cpp/AudioEngine.cpp`, `app/src/main/cpp/SynthEngine.h/.cpp`, `app/src/main/cpp/MasterBus.h`, `app/src/main/cpp/SamplerInstrument.cpp/.h`, `app/src/main/java/com/jujidaw/ui/synth/SynthViewModel.kt`, `app/src/main/java/com/jujidaw/ui/synth/SynthScreen.kt`, `app/src/main/java/com/jujidaw/ui/keyboard/KeyboardViewModel.kt`, `app/src/main/java/com/jujidaw/ui/keyboard/KeyboardScreen.kt`, `app/src/main/java/com/jujidaw/ui/pads/PadsViewModel.kt`, `app/src/main/java/com/jujidaw/ui/pads/PadsScreen.kt`, `app/src/main/java/com/jujidaw/model/SynthState.kt`, `app/src/main/java/com/jujidaw/model/Preset.kt`, `app/src/main/java/com/jujidaw/data/PresetDatabase.kt` |
| UI consistency | `app/src/main/java/com/jujidaw/ui/main/MainScreen.kt`, `app/src/main/java/com/jujidaw/ui/HardwareChassis.kt`, `app/src/main/java/com/jujidaw/ui/LcdDisplay.kt`, `app/src/main/java/com/jujidaw/ui/Components.kt` |
| Mixer | `app/src/main/java/com/jujidaw/ui/mixer/MixerScreen.kt`, `app/src/main/java/com/jujidaw/ui/mixer/MixerViewModel.kt`, `app/src/main/java/com/jujidaw/ui/RealKnob.kt` |

## Scope / Non-goals

**In scope:** the eight slices above; migration of existing saved
projects/autosaves whose `Pattern`/`PadConfig` shape changes; superseding the
`sequencer-engine-sync` and touch/mixer-fader aspects of in-flight changes.

**Non-goals** (belong to in-flight changes `synth-ui-audio-polish` and
`juji-synth-v11`): MIDI learn polish, modulation matrix, chorus/reverb FX
tuning, LFO animation cosmetics, filter-response curve plotting, osc waveform
preview styling. These are touched only where a fader/gesture fix unavoidably
shares code.

## Relation to in-flight changes

- **Supersedes** the `sequencer-engine-sync` spec in
  `synth-ui-audio-polish` (this change takes the authoritative approach:
  rows = pads, `PAD_TRIGGER` scheduling).
- **Supersedes** the knob/mixer touch-isolation aspects of
  `synth-ui-audio-polish` (replaced by the shared `DraggableValue` controller
  in slice H).
- **Overlaps** the `sequencer` spec in `juji-synth-v11`; this change is
  authoritative for the pad-trigger model.
- Does **not** delete or archive those changes; archiving is a separate step.

## Implications / Impact

- **Saved-project migration:** `Pattern` gains `padIndex` on notes/clips;
  `PadConfig.synthMode` semantics change from "route to shared synth" to "this
  pad owns synth instance N". A migration step rewrites old autosaves with
  `padIndex = note % 16` (legacy `note=60` → pad 12) and `synthMode = false`
  for safety.
- **C++ ABI/JNI:** new JNI entry points; one `SynthInstrument` per pad
  changes `AudioEngine` memory footprint (16 synth voices — acceptable on
  mobile, each is lightweight).
- **Mixer routing:** `MasterBus` must sum N synth channels + the sampler
  channel; per-pad gain/pan added.
- **Review workload:** slice D (multi-timbral) is the largest and will likely
  exceed 400 changed lines → chained PR candidate. Other slices are
  independently shippable.
- **No business model change** (no payments/auth); purely engine + UI.

## Open questions

None — the four product decisions (multi-timbral engine, FL-style sequencer,
full UI redesign, auto planning + feature-sliced tasks) are confirmed.
