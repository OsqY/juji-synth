# Juji-Synth DAW — User Guide

## Overview

Portable DAW for Android built with Kotlin/Compose + a C++ audio engine (oboe/AudioEngine,
48 kHz). The app presents 7 tabs — **Timeline, Mixer, Synth, Pads, Keyboard, Sequencer,
Project** — with a persistent transport bar that stays visible across every screen (portrait
bottom bar / landscape side rail). Pads are the central sound source: every pad holds either an
imported sample or an independent synthesizer, and the sequencer rows, timeline clips, and
keyboard all reference pads.

## Architecture

- **Kotlin/Compose UI layer.** Each screen is a `@Composable` with its own `ViewModel`. A
  singleton `TransportController` (`JujiDawApp.instance.transportController`) is shared across
  screens; ViewModels poll its `transportState` and the native playhead sample to derive
  `Bar|Beat|Step` and the current step index.
- **`TransportController` (musical clock + scheduler).** Owns bars/beats/ticks (PPQ), the
  `Arrangement`, and up to 16 `Pattern`s. A scheduler coroutine (single-threaded,
  `Dispatchers.Default.limitedParallelism(1)`) runs every ~50 ms with a ~100 ms lookahead,
  converts upcoming musical events into sample offsets, and pushes them to the C++ engine
  through a `SynthEngineScheduler` (`NativeSynthEngineScheduler` → JNI). Two playback paths
  run side by side:
  - **Pattern launcher** — live sequencer pattern playback, with bar-boundary pattern
    switching (`queuePattern`) and a `pendingSwitchSample` calculated as the next bar line.
  - **Arrangement** — timeline clips (`PatternClip` / `AudioClip`) scheduled by their tick
    range. Suppressed while `isSequencerMode == true` so the launcher and an arrangement clip
    referencing the same pattern can't double-fire.
  - Scheduled events are de-duplicated per `(startSample, track, kind, note/pad)`; the key set
    is cleared on stop / seek / loop wrap / pattern switch so legitimate re-triggers still
    fire.
- **C++ `AudioEngine`.** Channel layout (from `AudioEngine::init`):
  - **Channel 0** hosts a `SynthInstrument` (the global/multi-timbral synth, fed by
    `scheduleNoteOn/Off` from the transport).
  - **Channel 1** hosts a `SamplerInstrument` (the 16-pad sampler), paired back to the
    `AudioEngine` so synth-pad mode can route to per-pad synths.
  - **`synthForPad_[16]`** — a lazily-created pool of per-pad `SynthInstrument` instances,
    one per pad, each with its own preset. Enabled via `setPadSynthEnabled` /
    `applyPadSynthState`.
  - Mixer buses (`buses_`, `masterBus_`) follow; a background time-stretch worker is owned by
    the engine.
- **Event queue (lock-free SPSC).** The scheduler thread produces events; the real-time audio
  callback consumes them. `PAD_TRIGGER` events (produced when a note carries a `padIndex`) are
  routed directly to the `SamplerInstrument` / the matching pad synth instead of the global
  synth voice. Events carry an absolute `targetSample` so they fire at the right playhead
  position rather than collapsing onto the next audio buffer.

## Screens & Workflows

### 1. Pads (heart of the DAW)

Each of the 32 pads (4×4 grid, **Bank A** + **Bank B**) holds either an **imported sample**
or an **independent synth** toggled per-pad.

- **Import Audio:** launches the system file picker (`audio/*`), converts to WAV via
  `AudioConverter`, copies into `…/samples/pad_<n>_<ts>.wav`, and loads it with
  `SynthEngine.loadSampleToPad`. Imported sample paths + pad params are published to
  `PadSessionStore` so they can be restored by `ProjectAutosave.autoLoad`.
- **Chop:** slices the selected pad's sample into 16 equal slices laid out across the current
  bank.
- **Stretch:** async time-stretch by BPM ratio (original → target BPM) via the background
  worker; pitch preserved (0 semitones).
- **Edit sheet (per-pad):** Tune, Volume, Pan, Attack, Release, Filter Cutoff, Resonance,
  plus Reverse / One-Shot / Filter toggles (sample mode) or **Synth** mode toggle + Root Note +
  preset chips. Toggling Synth mode calls `setPadSynthEnabled(true)` and applies
  `defaultSynthParams()`; picking a preset reloads the default state (named-preset
  parameter sets are a future addition).
- **Velocity:** pad touch Y position → 1..127 (top = full velocity).

Pads are the source for the timeline and sequencer: tapping a pad fires
`SynthEngine.triggerPad`, and pattern/clip notes that carry a `padIndex` become `PAD_TRIGGER`
events in the engine.

### 2. Sequencer

FL-style **step sequencer**: 16 rows, **each row = 1 pad (P1–P16)**. Toggling a step toggles a
`NoteEvent` whose `padIndex` equals the row; when the scheduler hits it, the pad's loaded
content (sample or per-pad synth) plays through channel 1 / the pad synth pool.

- **16 patterns** (selectable 1–16), with **Copy / Paste / Clear** clipboard actions. Pattern
  switches are queued to the transport (`queuePattern`) and take effect at the next bar
  boundary.
- **View modes:** `STEP` (16×16 grid, velocity per step via long-press popup, 0..127) and
  `PIANO_ROLL` (64 steps, C2–C6, drag to move/resize notes, long-press for velocity slider,
  optional automation lane overlay). Only the active view mode is exported to the transport.
- **Transport:** Play/Record via the global transport bar; the screen polls
  `transportController.currentStep` at ~30 fps and highlights the current column plus the
  bottom step rail. BPM can be nudged ±1 here.

### 3. Timeline (Arrangement)

Arrangement editor: 16 track lanes (T1–T16) with pattern/audio clips, horizontal zoom/scroll,
snap-to-grid editing, and an automation lane.

- **Pad strip at top:** tap P1–P16 to drop a pad-trigger `PatternClip` on the selected track at
  the playhead (cached pad-trigger patterns live at ids 1000–1015).
- **Pattern selector:** pick the active pattern (1–16) the pad clip will reference.
- **Clips:**
  - **Tap** → toggle mute.
  - **Long-press** → drag to move (snaps tick + track).
  - **Right-edge handle** → horizontal-drag to trim duration (min one step).
  - **Three-dot menu (top-right)** → Delete.
- **Automation lane:** pick a param (Filter Cutoff, Amp Level, LFO Rate, Master Vol, …), tap
  to add points, drag to move, "DEL LAST" to remove the rightmost point.
- **Toolbar:** Loop on/off + set Loop Start/End to playhead; Punch on/off + set Punch In/Out
  to playhead; playhead nudge ◀/▶ (one step); snap selector (Bar / 1/4 / 1/8 / 1/16); zoom
  +/−; BPM + time LCD.
- The timeline disables the internal step sequencer on entry (`setSequencerEnabled(false)`)
  and re-enables it on exit, so arrangement and launcher playback don't overlap.

### 4. Mixer

16 horizontally-scrollable **channel strips** + a **master strip**.

- Per channel: vertical **fader** (−60..+12 dB, drag, jump-to-touch, dB-quantized), **pan**
  knob, **Mute/Solo/Arm**, **Send A/B** knobs, 4-slot **Insert FX** chain (add / bypass /
  remove / reorder), and a gradient **level meter** (green→amber→red, LED segments).
- **Master strip** with its own fader + meter.
- **Perform FX grid** (8 toggles): Stutter, Gate, Cutter, Reverse, Dly Freeze, Flt Sweep,
  Bitcrush, Tape Stop (engine integration pending).
- **Level metering** polled from `SynthEngine.getChannelLevel(i)` at ~300 ms (master uses
  index 16 until a dedicated getter ships).
- **MIDI Learn:** long-press a fader/pan/M/S/R/send to bind the next incoming MIDI CC; the
  app-wide `MidiRouter` persists the mapping.
- **Automation sheet:** per-channel param chips with a drawable automation lane overlay.

### 5. Synth

Full subtractive synth editor (global synth on channel 0 **or** a selected pad's synth).

- **Track selector** (T1–T16) for the global, multi-timbral synth state map; selecting a pad
  via `selectPad` switches to that pad's synth in the `synthForPad_` pool.
- Panels: **OSC** (2 oscillators), **FILTER** (LP/HP/BP), **ENV** (ADSR), **LFO** (×2),
  **FX** (reverb / delay / chorus / distortion), **MOD** (patch-bay modulation routes).
- **Presets:** load from / save to a Room `PresetDatabase` (`PresetDao`), with category
  filtering. Loaded preset JSON is decoded into a `SynthState` and pushed to the engine.
- **MIDI Learn** per parameter, plus a **PANIC** button (`SynthEngine.panic()`).
- Parameter edits route to the global synth (`setParam` / `applySynthState`) or the per-pad
  synth (`setPadSynthParam` / `applyPadSynthState`) depending on whether a pad is selected.

### 6. Keyboard

Performance keyboard with chromatic **Grid** (4×6, ~2 octaves) and classic **Piano** views.

- **Target dropdown:** Synth, Sampler A, Sampler B, Track 1–16, **Pad 1–16**. Selecting a pad
  target routes keystrokes to that pad's loaded content (sample or per-pad synth); selecting a
  track uses `scheduleNoteOn/Off` so notes land in the arrangement timeline. The target is also
  pushed to the app-wide `MidiRouter` so external MIDI follows the same routing.
- **Octave shift** (base C2–C4), **Scale + Root** selector (Major/Minor/Pentatonic/Blues/Dorian/
  Mixolydian) with **scale lock**, **velocity-from-touch-Y**, **aftertouch** (mapped to MOD
  WHEEL until per-note aftertouch ships).
- **Note Repeat** (rate 1/4–1/32) and **Arpeggiator** (Up/Down/Up-Down/Random, rate 1/4–1/32,
  1–4 octave range). Both run as coroutine jobs synced to BPM.
- Hold toggle, view-mode toggle, and stuck-note cleanup on target switch.

### 7. Project

Load/save projects. **Autosave is a work in progress** — `PadSessionStore` +
`ProjectAutosave.autoLoad` reconcile imported sample paths and pad params across restarts, but
restoration is partial and imported samples may not persist reliably between sessions.

## Transport Controls

The global transport bar is always visible (top of the nav in landscape, above the bottom nav
in portrait):

- **▶ / ■** Play/Stop — toggles `TransportController.play()`/`stop()`; stop clears scheduled
  events, releases held notes, resets `currentStep`, and re-enables the internal step
  sequencer.
- **●** Record arm — toggles recording; when punch is enabled in the arrangement, the punch
  in/out sample range is computed from the arrangement's `punchInTick`/`punchOutTick` and
  pushed to the engine via `setPunchRange`.
- **↺** Reset — stop + seek to `TransportPosition()` (bar/beat/step zero).
- **Time LCD** — `Bar+1 | Beat+1 | Step+1` (monospace amber), refreshed every 50 ms.
- **BPM** — tap to open a numeric + slider dialog clamped to 30..300; calls `setTempo`.

The **Timeline** toolbar adds Loop toggle + Loop Start/End-to-playhead, Punch toggle + Punch
In/Out-to-playhead, playhead nudge ◀/▶, snap selector, and zoom +/−.

## Key Concepts

- **Pads = sound sources.** Everything starts from a pad: load a sample or assign a synth
  (Bank A/B, 32 pads total). Pad touch Y → velocity 1..127.
- **Sequencer rows = pads (1:1).** Step = play that pad's sound (row P1 triggers pad 1,
  etc.). Pattern switches queue at the next bar boundary.
- **Timeline = arrangement of clips.** `PatternClip`s reference patterns (including cached
  pad-trigger patterns); `AudioClip`s reference sample files. Clips can be placed by tapping
  the pad strip or the empty lane.
- **Transport = musical clock.** Pattern launcher (sequencer) and Arrangement (timeline) run
  side by side; the launcher is suppressed while `isSequencerMode` is true to avoid
  double-firing. Loop wrap re-seeks the playhead and clears stale scheduled events.
- **The C++ engine runs on the audio callback.** Events are pushed from Kotlin via a
  lock-free SPSC `EventQueue`; the scheduler thread produces, the audio callback consumes.
- **Routing:** notes with a `padIndex` become `PAD_TRIGGER` events → sampler / per-pad synth
  (channel 1 / `synthForPad_` pool); notes without → `NoteOn/Off` → channel 0 global synth.
- **Velocity:** pad sensitivity from touch Y position (1..127). Song imports are converted to
  WAV before loading; time-stretch preserves pitch across a BPM ratio.

## Known Limitations

- **Autosave / project persistence:** imported sample paths and pad params are published to
  `PadSessionStore` and `ProjectAutosave.autoLoad`, but restoration is incomplete — imported
  samples may not reliably survive an app restart (work in progress).
- **BPM editing:** editable via the persistent transport bar dialog (30..300) and the
  sequencer's ±1 nudge; the Timeline transport strip displays BPM but does not expose its own
  edit control.
- **Sample-accurate scheduling:** events carry an absolute `targetSample` and fire when the
  playhead reaches them, but resolution is bounded by the audio buffer size (they land on the
  buffer containing the target sample), not sample-accurate.
- **Multi-timbral synth:** 16 per-pad synth instances (`synthForPad_`), each with its own
  preset/state, but all sum through the channel-1 sampler bus / master bus (no independent
  mixer bus per pad synth yet).
- **Per-track synth isolation on the Synth screen** is built around a per-track `SynthState`
  map, but the engine still treats channel 0 as a single shared synth voice; full per-track
  voice allocation is pending.
- **Slice start/end and choke group** UI exists in the pad edit sheet but the C++ engine does
  not yet expose `setPadSlice` / choke-group handlers (marked TODO in `PadParamIds`).
- **Master level meter** uses `getChannelLevel(16)` as a placeholder until a dedicated
  `nativeGetMasterLevel` is added.
- **Perform FX grid** (Stutter/Gate/Cutter/Reverse/Dly Freeze/Flt Sweep/Bitcrush/Tape Stop)
  toggles UI only; engine integration is pending.
- **Aftertouch** is mapped to MOD WHEEL as a channel-pressure placeholder; per-note aftertouch
  is not yet exposed by the engine.
- **Insert FX reorder** updates the UI but there is no native reorder API yet.
