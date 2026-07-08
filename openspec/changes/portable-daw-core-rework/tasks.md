# Tasks: Portable DAW Core Rework

Each slice is sized so ONE small agent can implement it end-to-end. Follow
Strict TDD: RED → GREEN → REFACTOR, recording evidence. Slices A and B are
independent; C depends on B; D depends on B; E/F/G/H are independent of the
audio slices and can run in parallel after B.

Legend: `[D]` = dependency on another slice. `📋 chained-PR candidate` =
estimated > 250 changed lines (review-workload guard).

---

## Slice A — sample-import-fidelity (independent, ship first)

- [ ] A1 — `AudioConverter.writeWavFile` rewrite — file: `app/src/main/java/com/jujidaw/audio/AudioConverter.kt` — acceptance: header written AFTER downmix; `channels=1`, `byteRate=sampleRate*1*2`, `blockAlign=2`, `dataSize=frames*2` match mono bytes — test: JVM unit test `AudioConverterTest` asserting header fields + that `SampleBuffer.loadFromWav` reads `frames` samples for a stereo source (use a small generated WAV fixture).
- [ ] A2 — `SampleBuffer.loadFromWav` consistency assert — file: `app/src/main/cpp/SampleBuffer.cpp` — acceptance: log/assert when `dataSize` != `file.gcount()` — test: instrumented test loading the fixture from A1.
- [ ] A3 — Manual smoke: import a real stereo kick, trigger pad, confirm pitch/duration match source — acceptance: user/CI tape confirmation.

## Slice B — pad-triggered-transport data model (independent, blocks C & D)

- [ ] B1 — Add `padIndex` to `NoteEvent` and `PatternClip` — files: `app/src/main/java/com/jujidaw/model/PatternModel.kt`, `app/src/main/java/com/jujidaw/model/ClipModel.kt` — acceptance: `padIndex: Int = -1` sentinel; default -1 — test: JVM unit test on model serialization round-trip.
- [ ] B2 — `TransportController.schedulePadTrigger(padIndex, tick, velocity)` — files: `app/src/main/java/com/jujidaw/engine/TransportController.kt`, `app/src/main/java/com/jujidaw/audio/SynthEngine.kt`, `app/src/main/cpp/JniBridge.cpp`, `app/src/main/cpp/ScheduledEvent.h` — acceptance: new JNI `nativeSchedulePadTrigger` pushes `ScheduledEvent::makePadTrigger`; `Transport::firePendingEvents` PAD_TRIGGER branch calls `sampler->triggerPad` — test: instrumented test asserting a `PAD_TRIGGER` event is scheduled and `sampler->triggerPad` is invoked (mock or spy).
- [ ] B3 — Route pad clips via `schedulePadTrigger` — file: `TransportController.schedulePatternNotes` / `schedulePatternClip` — acceptance: when `note.padIndex >= 0` use pad-trigger; else migrate `padIndex = note % 16` — test: unit test covering both branches.
- [ ] B4 — Project migration — files: `app/src/main/java/com/jujidaw/project/ProjectRepository.kt`, `ProjectAutosave.kt` — acceptance: on load, legacy `NoteEvent` without `padIndex` get `padIndex = note % 16` — test: unit test on a legacy fixture JSON.

## Slice C — fl-style-step-sequencer [D: B]

- [ ] C1 — `StepTrack` carries `padIndex` (row R = pad R) — file: `app/src/main/java/com/jujidaw/ui/sequencer/SequencerViewModel.kt` — acceptance: row R maps to `padIndex=R` — test: unit test `row_to_pad_mapping`.
- [ ] C2 — `toggleStep` writes a `PAD_TRIGGER` event, not `note=60` — file: `SequencerViewModel.kt` — acceptance: `syncActivePatternToTransport` exports `NoteEvent(padIndex=...)` and the transport calls `schedulePadTrigger` — test: unit test asserting exported pattern has `padIndex` per row.
- [ ] C3 — Sequencer UI row labels show pad name/content type — file: `app/src/main/java/com/jujidaw/ui/sequencer/SequencerScreen.kt` — acceptance: row label shows `Pad R: <sample name | Synth preset>` — test: UI snapshot or manual.
- [ ] C4 — Disable legacy `SequencerView.kt` internal note path (or route through pad-trigger) — file: `app/src/main/java/com/jujidaw/ui/SequencerView.kt`, `app/src/main/cpp/Sequencer.h/.cpp` — acceptance: no `noteOn` to channel 0 synth from the step grid — test: manual smoke.

## Slice D — multi-timbral synth-per-pad [D: B] 📋 chained-PR candidate

> Recommended split into chained PRs: D1 (C++ engine pool + JNI), D2 (Kotlin
> VM + preset DB migration), D3 (Keyboard "Play Selected Pad" + SynthScreen
> reindex).

- [ ] D1 — C++ `synthForPad_[16]` pool in `AudioEngine` — files: `app/src/main/cpp/AudioEngine.cpp/.h`, `app/src/main/cpp/MasterBus.h` — acceptance: `AudioEngine::getPadSynth(padIndex)` returns a per-pad `SynthInstrument*`; lazy-allocated only for SYNTH pads; summed into `MasterBus` — test: instrumented test newing a pad synth and asserting it appears in the master mix.
- [ ] D2 — `SamplerInstrument::triggerPad` delegates to per-pad synth in SYNTH mode; remove shared `synthTarget_` — files: `app/src/main/cpp/SamplerInstrument.cpp/.h`, `SamplerVoice.cpp` — acceptance: `PadConfig.mode = SYNTH` → `synthForPad_[padIndex]->noteOn(...)`; SAMPLE → existing path — test: instrumented test with two SYNTH pads holding different presets, assert both audible & independent.
- [ ] D3 — `PadConfig.synthMode: Boolean` → `enum PadMode { SAMPLE, SYNTH }` migration — files: `SamplerInstrument.h`, `ProjectModels.kt`, `PadsViewModel.kt`, migration in `ProjectRepository` — acceptance: old `synthMode=true` → `PadMode.SYNTH` with default preset — test: migration unit test.
- [ ] D4 — JNI `nativeSynthNoteOn(padIndex, note, vel)`, `nativeLoadPresetToPad(padIndex, presetId)`, `nativeSetSynthParam(padIndex, param, value)` — files: `SynthEngine.kt`, `JniBridge.cpp` — acceptance: new entry points route to `synthForPad_[padIndex]` — test: JNI smoke.
- [ ] D5 — `SynthViewModel` reindexed by `padIndex`; `SynthScreen` edits the selected pad's synth — files: `app/src/main/java/com/jujidaw/ui/synth/SynthViewModel.kt`, `SynthScreen.kt`, `app/src/main/java/com/jujidaw/data/PresetDatabase.kt` (add `pad_presets` table/column) — acceptance: editing pad N affects only pad N's synth — test: unit test that mutating pad 1 params does not change pad 2.
- [ ] D6 — `KeyboardViewModel` "Play Selected Pad" target — files: `KeyboardViewModel.kt`, `KeyboardScreen.kt` — acceptance: target routes key presses to the selected pad's content (sample voice or that pad's synth) — test: unit test asserting `sendNoteOn` with target=SelectedPad(pad=3, mode=SYNTH) calls `nativeSynthNoteOn(3, …)`.

## Slice E — unified-transport-controls (independent)

- [ ] E1 — Remove `TimelineScreen.TransportStrip` Play/Record/Reset; delegate to global bar — file: `app/src/main/java/com/jujidaw/ui/timeline/TimelineScreen.kt` — acceptance: no transport buttons rendered inside Timeline tab — test: UI test asserting no `TransportButton` in Timeline.
- [ ] E2 — Remove `SequencerScreen.SequencerTopBar` Play/Record; delegate — file: `app/src/main/java/com/jujidaw/ui/sequencer/SequencerScreen.kt` — acceptance: same as E1 for Sequencer — test: UI test.
- [ ] E3 — `MainScreen.PersistentTransportBar` is the sole transport in both orientations — file: `app/src/main/java/com/jujidaw/ui/main/MainScreen.kt` — acceptance: exactly one Record button app-wide — test: UI test counting record buttons = 1.

## Slice F — landscape-navigation (independent)

- [ ] F1 — Replace 80 dp scrolled rail with compact `NavigationRail` + overflow drawer — file: `MainScreen.kt` landscape branch — acceptance: all 7 tabs reachable without invisible scroll; transport bar in a separate `Row`, not the rail column — test: UI test in landscape asserting all 7 `MainTab` labels are visible or reachable via overflow.

## Slice G — ui-control-clarity (independent)

- [ ] G1 — Replace `L-/L+/I-/O+/P` with labeled/iconographic controls — file: `TimelineScreen.kt` — acceptance: controls read "Loop Start/End", "Punch In/Out", "Punch Enable"; long-press shows help — test: UI test asserting the new labels are present.
- [ ] G2 — Fix Time/BPM layout (spacing, `maxLines=1`, ellipsis) — file: `MainScreen.kt` PersistentTransportBar — acceptance: on narrow width, Time and BPM do not concatenate — test: UI test on a narrow `ComposeRule` asserting two distinct `Text` nodes.

## Slice H — mixer-fader-gesture (independent)

- [ ] H1 — `DraggableValueController` shared gesture controller — file: new `app/src/main/java/com/jujidaw/ui/DraggableValue.kt` (or `Components.kt`) — acceptance: single pointer-input with touch-slop hysteresis, value quantization — test: unit/UI test asserting a < 8 dp drag does not tap-jump.
- [ ] H2 — `VerticalFader` uses `DraggableValueController`; hoist drawn state; debounced engine push — file: `MixerScreen.kt` — acceptance: per-delta recomposition eliminated; value updates smooth — test: UI test dragging the fader and asserting smooth value transition.
- [ ] H3 — `RealKnob` uses `DraggableValueController` — file: `RealKnob.kt` — acceptance: consistent drag threshold with fader — test: UI test.
- [ ] H4 — Debounce level polling to ≥300 ms; minimize recomposition — file: `MixerViewModel.kt` — acceptance: polling interval ≥300 ms; meter updates without recomposing the strip Row — test: unit test on polling interval.

---

## Definition of Done

- [ ] All slices A–H have RED/GREEN test evidence.
- [ ] Manual smoke (instrumented or device):
  - [ ] Import a stereo kick → plays at original pitch and ~original duration.
  - [ ] Place pad 1 (kick) on the timeline → on Play, kick is heard at the clip tick.
  - [ ] Sequencer row 1 (pad 1) step 1 → on Play, kick is heard.
  - [ ] Two synth pads with different presets → both audible & independent.
  - [ ] Landscape shows all 7 tabs (no invisible cutoff).
  - [ ] Exactly one Record button app-wide.
  - [ ] Mixer fader drags smoothly, no tap-jump.
- [ ] Chained PRs opened for slice D sub-parts if review-workload guard
      triggers (>400 changed lines).
- [ ] `sdd-apply` then `sdd-verify` run against these tasks before archive.
