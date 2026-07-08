# Design: Portable DAW Core Rework

## Architecture overview

Two layers change: the **C++ audio engine** (multi-timbral synths + pad-trigger
transport) and the **Kotlin UI/transport** (pad-triggered scheduling,
unified transport, landscape nav, mixer gesture). The contract between them
is the JNI surface in `SynthEngine.kt` ↔ `JniBridge.cpp`.

### Engine channel model (before vs after)

**Before:** `AudioEngine` hosts channel 0 = `SynthInstrument` (one shared),
channel 1 = `SamplerInstrument` (16 pads, samples only). `PadConfig.synthMode`
is a stub routing to the shared synth.

**After:** `AudioEngine` hosts:

- channel 0 = the arrangement/keyboard "selected pad" synth bus (optional,
  retained for legacy MIDI input); and
- a **fixed pool of `SynthInstrument` instances** `synthForPad_[16]`, one per
  pad, lazily initialized. `SamplerInstrument` no longer owns a
  `synthTarget_` pointer; instead `triggerPad(padIndex)` checks `pad.mode`:
  - `SAMPLE` → play the pad's `SampleBuffer` (existing path).
  - `SYNTH` → call `synthForPad_[padIndex]->noteOn(rootNote + offset, vel)`.

`MasterBus` sums: sampler channel + `synthForPad_[0..15]` → master. Each synth
pad has its own gain/pan on the mixer (exposed via existing
`setChannelFader`/`setChannelPan` extended to pad indices, or a new
`setPadSynthGain(padIndex, dB)`).

### Data model

- `PatternModel.NoteEvent` gains `padIndex: Int = -1` (sentinel = legacy).
- `ClipModel.PatternClip` gains `padIndex: Int = -1`.
- On schedule, `TransportController`:
  - if `note.padIndex >= 0` → `schedulePadTrigger(padIndex, tick, vel)`;
  - else (legacy) → migrate: `padIndex = note % 16`, then pad-trigger.

### Pad-trigger path

```text
Kotlin TransportController.schedulePadTrigger(padIndex, tick, vel)
  -> SynthEngine.nativeSchedulePadTrigger(padIndex, tick, vel)
  -> JniBridge.cpp: nativeSchedulePadTrigger
  -> SynthEngineScheduler / ScheduledEvent::makePadTrigger(padIndex, sample)
  -> Transport::firePendingEvents:
        case PAD_TRIGGER: sampler->triggerPad(padIndex, vel);
                          // SamplerInstrument::triggerPad checks pad.mode
                          //   SAMPLE -> SampleVoice plays
                          //   SYNTH   -> synthForPad_[padIndex]->noteOn(...)
```

This reuses the existing `PAD_TRIGGER` `ScheduledEvent` type (already wired in
`Transport.cpp`), unblocking the timeline + sequencer with minimal C++ change.

### Multi-timbral synth ownership

- `SynthInstrument` instances are owned by `AudioEngine` (not
  `SamplerInstrument`). `SamplerInstrument` holds a non-owning
  `SynthInstrument* synthForPad_[16]` set via
  `AudioEngine::setPadSynthTarget(padIndex, SynthInstrument*)`.
- `PadConfig` changes from `bool synthMode` + `SynthInstrument* synthTarget_`
  (shared) to `enum PadMode { SAMPLE, SYNTH }` + the per-pad synth is resolved
  via `synthForPad_[padIndex]`.
- `SynthViewModel` is reindexed by `padIndex`: `trackStates: Map<Int,
  SynthState>` becomes a real per-pad map; editing pad N opens
  `SynthScreen` for that pad's synth and calls
  `nativeSetSynthParam(padIndex, param, value)` rather than mutating a
  global.

### Preset persistence

- `PresetDatabase` already stores presets; add a `padIndex` column or a sibling
  table `pad_presets(padIndex, presetId)`.
- New JNI: `nativeLoadPresetToPad(padIndex, presetId)` to push a preset into
  `synthForPad_[padIndex]`.
- `ProjectAutosave` serializes per-pad `PadConfig { mode, synthRootNote,
  presetId, samplePath }`.

### Audio import fix

`AudioConverter.writeWavFile` restructure:

1. Decode to PCM (existing).
2. If `channels > 1`, downmix to mono into `monoData` (compute
   `frames = pcmData.size / (channels * 2)`).
3. Build the 44-byte header with `channels=1`, `byteRate = sampleRate * 1 * 2`,
   `blockAlign = 2`, `dataSize = frames * 2`.
4. Write header then mono data.

`SampleBuffer::loadFromWav` is unchanged (it already trusts the header). Add
an assertion/log when `dataSize` does not match `file.gcount()`.

### UI: unified transport + landscape nav

- `MainScreen.PersistentTransportBar` becomes the sole transport (R1 of
  `unified-transport-controls`). `TimelineScreen.TransportStrip` and
  `SequencerScreen.SequencerTopBar` lose Play/Record/Reset; they keep only
  screen-specific actions (e.g. loop region editors) that delegate state to
  transport.
- Landscape: replace the 80 dp `Column { verticalScroll }` rail with a
  **compact `NavigationRail`** (icon-only, 56 dp wide) showing the first
  N tabs that fit, plus an overflow "More" button opening a dropdown/drawer
  for the rest. The transport bar lives in a `Row` beside the rail, not
  inside the scrolled column. This satisfies `landscape-navigation` R2/R3.

### UI: control clarity

- Replace `TinyButton("L-") …` with `IconTextButton(Icons.Filled.SkipToStart,
  "Loop Start")` etc. Use Material icons + a short label; long-press shows a
  toast help text.
- Time/BPM: wrap in a `Row` with `Spacer(8.dp)` between the two `Text`s and
  give each a fixed `widthIn(min = …)` so they never concatenate; use
  `Text(…, maxLines = 1, overflow = Ellipsis)` for BPM.

### UI: mixer fader gesture

- Introduce `DraggableValueController(min, max, step, sensitivity)` in
  `Components.kt` (or a new `ui/DraggableValue.kt`). It exposes a single
  `pointerInput` that:
  1. on DOWN, record start position and value;
  2. if drag distance > `touchSlop (8.dp)`, enter DRAG mode and update value
     by `-dragAmount.y * sensitivity`, quantized to `step`;
  3. if drag never exceeds slop on UP, treat as tap (jump to Y for fader, or
     no-op for knob).
- `VerticalFader` and `RealKnob` both use `DraggableValueController`,
  satisfying R5 (consistent thresholds).
- Hoist fader drawn state: replace per-delta `updateChannel` recomposition
  with a `mutableStateOf` for the fader thumb Y only; push the value to the
  engine via a debounced `LaunchedEffect` (e.g. every 16 ms frame, not per
  delta).
- Level polling: change `startLevelPolling` interval from 200 ms to ≥300 ms and
  read levels into a `Channel`/`mutableStateListOf` that the meter Canvas
  observes without recomposing the strip Row.

## Migration

- `ProjectAutosave` / `ProjectRepository`: on load, if a `Pattern`'s
  `NoteEvent`s lack `padIndex`, set `padIndex = note % 16`.
- `PadConfig.synthMode: Boolean` → `PadMode { SAMPLE, SYNTH }`;
  `synthMode=true` maps to `PadMode.SYNTH`; `synthTarget_` pointer is dropped.
- Old projects with `synthMode=true` pads: migrate to `PadMode.SYNTH` with the
  pad bound to `synthForPad_[padIndex]` and the last-known preset (or a
  default factory preset) loaded.

## Risk table

| Risk | Mitigation |
| --- | --- |
| C++ ABI change breaks existing autosaves | Versioned project schema + migration step above |
| 16 synth instances memory pressure | Lazy init; only pads in SYNTH mode allocate a synth |
| Removing legacy `SequencerView` breaks users | Keep `SequencerView.kt` but route through pad-trigger; retire only if unused |
| Mixer fader behavior regression | Shared controller + instrumented test for tap/drag hysteresis |
| Review workload on slice D | Chained PRs: D1 (engine pool + JNI), D2 (Kotlin VM + preset DB), D3 (Keyboard target + UI) |

## Decisions on in-flight overlaps

- **Supersedes** `synth-ui-audio-polish/specs/sequencer-engine-sync` — this
  change owns the authoritative sequencer→engine contract (PAD_TRIGGER, rows
  = pads).
- **Supersedes** the knob/mixer touch-isolation aspects of
  `synth-ui-audio-polish` — replaced by the shared `DraggableValueController`.
- `juji-synth-v11/specs/sequencer` is overlapping; this change is
  authoritative for playback routing. The v11 spec's pattern-editing UX is
  not touched here.

## No code

This design is architecture/data-flow only. Implementation tasks live in
`tasks.md`.
