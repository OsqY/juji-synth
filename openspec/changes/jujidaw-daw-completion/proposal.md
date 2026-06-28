## Why

JujiDAW has a powerful C++ audio engine (16-channel mixer, synth + sampler, insert/send FX, lock-free note queue, offline render) and the beginnings of a phone-first MVVM UI. But it is not yet a functional phone DAW: automation is entirely unimplemented (`Transport.cpp:116` drops every `AUTOMATION` event), all tracks share a single synth state (no per-channel presets), and two UI layers coexist — a legacy synth-only monolith (`MainSynthScreen` / `HardwareChassis` / `PatchBayView` / legacy `KeyboardView` / `SequencerView` / `PianoRollView`) hides the new phone-first redesign. Users see "a lot of stuff" without the transport sync, automation, multi-timbral capability, or coherent UI that defines a DAW.

This change fills those three gaps in priority order: automation → multi-timbral routing → phone-first UI cleanup.

## What Changes

### 1. Automation system (synth + mixer params)
- Implement `Transport.cpp:116` `AUTOMATION` event handler: map `paramIndex` to the corresponding field in `SynthParams` / `MixerChannel` and apply the value at the scheduled sample position.
- Add an automation lane UI to `SequencerScreen` (piano-roll) and per-channel automation to `MixerScreen`. Support both manual draw (point-by-point editing on a lane) and live touch recording (arm a param, turn a knob while the transport is playing, and record the movement into the lane).
- Persist automation data in `ProjectModels` (per-clip and per-arrangement automation tracks).
- Non-goal per this change: LFO-driven automation recording. LFO routing already works (v14); LFO-to-automation-lane is a future capability.

### 2. Multi-timbral routing (one engine, MIDI-route tracks to channels)
- The single `SynthInstrument` + `SamplerInstrument` remains the audio source; each DAW track maps to a MIDI channel (0–15) routed to the corresponding `MixerChannel` (0–15). This reuses the 16-channel mixer already wired in C++.
- Per-channel presets: each track stores its own `SynthState` (previously shared globally). On track selection, the engine loads that track's preset into the synth via `SynthEngine.applySynthState()`. This is the same `setAllParamsFromArray` path that `MainSynthScreen.applySynthStateToEngine` already uses.
- `SequencerViewModel` / `TimelineViewModel` already tag notes with a `trackIndex`; the engine respects it through `MixerChannel`. No C++ instrument-per-track is needed — phone CPU budget is better spent on audio quality than cloning synths.
- Non-goal: per-track insert FX separate from the channel strip. The mixer already supports per-insert FX per channel (16 ch × 4 slots) — that's sufficient.

### 3. Phone-first UI cleanup
- Delete `MainSynthScreen.kt`, `HardwareChassis.kt`, `PatchBayView.kt`, legacy `KeyboardView.kt`, `SequencerView.kt`, `PianoRollView.kt`, `OscilloscopeView.kt`, `OscWaveformView.kt`, `SynthPanel.kt`, `LcdDisplay.kt` (was rendered obsolete by the MVVM screens).
- Reroute `MainTab.SYNTH` → `ui/synth/SynthScreen` (already exists, unused).
- Reorder tabs for arrangement-first phone DAW: **TIMELINE → MIXER → SYNTH → PADS → KEYBOARD → SEQUENCER → PROJECT**.
- TIMELINE as the default home tab, with an empty-state showing quick-start templates ("4-on-floor", "Trap loop", "Blank") on first launch (or no recent projects) to reduce the adoption friction. Each template pre-populates a tempo, a pattern, and basic mixer state.
- Keep the persistent transport bar in `MainScreen` as the global anchor.

### Breaking changes
- `MainSynthScreen` is removed. Any preset-loading path that calls `SynthEngine.applySynthStateToEngine` through the legacy screen must migrate to `SynthScreen` or `SequencerScreen`'s `PresetBrowser` usage.
- Legacy `KeyboardView` → `KeyboardScreen` path is the sole keyboard now.

## Capabilities

### New Capabilities
- `automation-system`: Automation recording (live touch + manual draw) for synthesizer parameters (the 39 `SynthParams` fields) and per-channel mixer parameters (fader, pan, mute, solo, send levels). Automation is persisted per-clip and in the arrangement, and is synchronised to the transport playhead via `TransportController` → C++ `EventQueue` → `Transport.cpp` handler.
- `multi-timbral-routing`: Each DAW track carries its own `SynthState` preset. On track focus the engine loads that preset via `SynthEngine.applySynthState()`. Notes target a MIDI channel tracked to a `MixerChannel`. This is zero new C++ — it is only Kotlin-side `trackState` in `SynthViewModel`/`TimelineViewModel` plus the existing 16-channel mixer.
- `phone-ui-cleanup`: Legacy synth-only UI files removed. SYNTH tab routes to the new `SynthScreen`. Tab order is arrangement-first (TIMELINE home). First-launch empty state offers quick-start templates. The persistent transport bar is the DAW backbone UI.

### Modified Capabilities
None. No existing spec (`effects-section`, `lfo-section`, `lock-free-note-queue`, `oscillator-section`, `preset-system`) changes behavior.

## Impact

| Area | Files | Change |
|---|---|---|
| C++ automation handler | `Transport.cpp` (line 116 `TODO`) | Implement `AUTOMATION` event: map `paramIndex` to `SynthParams::*` or `MixerCommand` depending on param range. Non-trivial: 1-file, ~80 lines |
| Kotlin automation data model | `TransportModel.kt`, `ProjectModels.kt` | Add `AutomationClip`, `AutomationPoint` types, `Arrangement.automation: Map<String, List<AutomationPoint>>`. ~50 lines |
| Kotlin automation recording | `SequencerViewModel`, `MixerViewModel` | Add automation-armed state, record knob movements from `SynthEngine.setParam` callbacks into the active automation clip. ~100 lines total |
| Kotlin automation lane UI | `SequencerScreen` (piano-roll overlay), `MixerScreen` (per-channel automation sheet) | Draw automation points + line segments. ~150–200 lines |
| Kotlin per-track state | `TimelineViewModel`, `SynthViewModel`, `ProjectViewModel` | `SynthState` per track in a map; load on track selection. ~80 lines |
| Kotlin UI cleanup | `MainSynthScreen.kt` (deleted), `HardwareChassis.kt` (deleted), `PatchBayView.kt` (deleted), legacy `KeyboardView.kt`, `SequencerView.kt`, `PianoRollView.kt`, `OscilloscopeView.kt`, `OscWaveformView.kt`, `SynthPanel.kt`, `LcdDisplay.kt` (deleted) | ~2,200 lines removed; reroute navigation in `MainScreen.kt:178` (SYNTH tab) |
| Kotlin tab reorder + empty state | `MainScreen.kt` (tab enum + when) | Reorder `MainTab` values, add empty-state quick-start template infrastructure. ~60 lines |
| Kotlin project persistence | `ProjectRepository.kt` (save/load) | Serialise per-track `SynthState` + automation data in the project JSON. ~30 lines |
