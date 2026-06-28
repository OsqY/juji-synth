# Design: jujidaw-daw-completion

## Context

JujiDAW is a C++/Oboe audio engine (16-channel mixer, synth + sampler voices, lock-free note queue, insert/send FX, offline render) surfaced by a Jetpack Compose MVVM UI. The rebrand (v0/0a) consolidated the codebase. The v14 lock-free note queue and v15 default-effect fixes are already in place.

This change fills three remaining gaps standing between "a lot of stuff" and "a functional phone DAW":

1. **Automation is unimplemented.** `Transport.cpp:116` switch over `ScheduledEventType::AUTOMATION` reaches `dynamic_cast<SynthInstrument*>(instr)` and then `(void)synth; // TODO: map paramIndex to SynthAutomation field`. Every automation event the Kotlin `TransportController` schedules is dropped on the floor. The plumbing already exists: `ScheduledEvent.h` has `makeAutomation(track, paramIndex, value, targetSample)` and `TransportController` already creates these events — but the consumer doesn't do anything.
2. **There is no multi-timbral state.** There is exactly one `SynthInstrument` (channel 0) and one `SamplerInstrument` (channel 1). Every UI assumes a single global `SynthState`. The 16 `MixerChannel`s exist in C++ but each Kotlin "track" cannot carry its own synth preset.
3. **Two UI layers coexist.** `MainScreen.kt` is the active scaffold with tabs `PADS, SYNTH, KEYBOARD, SEQUENCER, TIMELINE, MIXER, PROJECT`, and the `SYNTH` tab routes to a legacy 654-line monolith `MainSynthScreen`. The new phone-first `ui/synth/SynthScreen` exists but is unreachable (its KDoc literally says *"Wire into the bottom-navigation graph"*). Legacy `KeyboardView`/`SequencerView`/`PianoRollView`/`OscilloscopeView`/`PatchBayView`/`HardwareChassis` overlap with the modern MVVM screens and bloat the APK.

The chosen user is a **beat-maker/producer** sketching on a phone; the home surface is an **arrangement-first** timeline with quick-start templates.

## Goals / Non-Goals

**Goals:**
- Map every `AUTOMATION` event to a real destination (synth param or mixer command) so automation playback actually works.
- Provide manual draw + live touch recording for synth (39 params) and per-channel mixer (7 params) targets.
- Give every DAW track its own `SynthState` preset and route its notes through its assigned `MixerChannel`, reusing the existing C++ mixer.
- Remove the legacy synth-only UI files, route `MainTab.SYNTH` → `ui/synth/SynthScreen`, reorder tabs arrangement-first, set `TIMELINE` as default home with a quick-start template first-run empty state.

**Non-Goals:**
- Per-track **insert FX separate from the channel strip.** The mixer already owns 16 channels × 4 inserts; per-track inserts are already the channel's inserts.
- Per-track **per-voice** synthesis (instantiating a `SynthInstrument` per track). All tracks share one `SynthInstrument` + `SamplerInstrument`. Voice stealing is global. This is a deliberate phone-CPU budget.
- **LFO-driven automation recording.** LFO modulation already runs (v14); recording an LFO's output into an automation lane is a future capability.
- **Master-bus / bus FX automation.** Only the 16 channel strips are automated in this slice.
- **Offline render of automation.** The existing real-time export path is untouched.
- **Official DI / Hilt refactor for `TransportController`.** Confirmed as a false positive: every VM already fetches the singleton via `JujiDawApp.instance.transportController` (lazy). Formal DI is deferred.

## Decisions

### Decision 1: Reuse the `setAllParamsFromArray` order as the automation paramIndex map for synth

**Choice.** Define the synth automation `paramIndex` space as 0–38, **identical** to the index layout already used by `SynthInstrument::setAllParamsFromArray` (see `SynthInstrument.cpp:247-288`).

| Range | Destination |
|---|---|
| 0–8 | `oscillators.*` (osc1 level, osc2 level, osc1 wf, osc2 wf, detune, sub, noise, oscMix, sync) |
| 9–12 | `filter.*` (cutoff, res, mode, envAmount) |
| 13–20 | `envelopes.*` (amp A/D/S/R + filter A/D/S/R) |
| 21–26 | `lfos.*` (lfo1 rate/depth/wf, lfo2 rate/depth/wf) |
| 27–34 | `effects.*` (reverb mix/decay, delay mix/time/fb, dist drive/mix, bypass) |
| 35–37 | `effects.chorus.*` (rate/depth/mix) |
| 38 | `master.volume` |

**Why.** Passing the existing array-layout index straight through to the automation handler means the same lookup table serves both preset loading (`setAllParamsFromArray`) and automation events. No second source of truth for "what is param 13?".

**Alternative considered.** Define a separate, more semantic enum (`PARAM_OSC1_LEVEL = 0`, ...). Rejected — it would duplicate the layout and drift the moment someone reorders `SynthParams`.

**Implementation note.** Add `void SynthInstrument::applyAutomationParam(int paramIndex, float value)` which is a **single switch** that writes to `pendingParams_.*` and sets `paramsPending_ = true`. The body is the inverse of `setAllParamsFromArray`'s array write per index, but for a single index. The audio thread calls it inside the `AUTOMATION` switch case. Because it writes `pendingParams_` only (never `currentParams_`), it threads through the same `swapParamsIfNeeded` mechanism used by preset loads — no new race surfaces.

### Decision 2: Mixer automation via `MixerCommand` push (paramIndex 39–45)

**Choice.** Range 39–45 routes each target to a `MixerCommandType` on the track's `MixerChannel`:

| paramIndex | `MixerCommandType` |
|---|---|
| 39 | `SetFader` |
| 40 | `SetPan` |
| 41 | `SetMute` (value > 0.5) |
| 42 | `SetSolo` (value > 0.5) |
| 43 | `SetArm` (value > 0.5) |
| 44 | `SetSendA` |
| 45 | `SetSendB` |

The handler builds a `MixerCommand` and calls `engine.pushMixerCommand(cmd)`. Nothing else in C++ changes. The mixer command ring buffer (`AudioEngine::mixerQueue_`) is already a lock-free SPSC and is drained on the audio thread at the top of `processAudio`.

**Why.** Mixer params are not inside `SynthParams`; synthesizing a fake `SynthParams` path for them would be contorted. The mixer command queue is the existing, proven lock-safe control surface.

**Alternative considered.** Define a separate mixer-automation path that writes the channel struct in place. Rejected — would need its own synchronization where the queue already does the job.

**Why the spec/proposal said 39–54.** Earlier estimate rounded generously; the concrete reusable mixer types total 7. The spec cap is "39–45" in practice and "unknown params drop". The original spec text will be reconciled after design (see Open Questions).

### Decision 3: Linear interpolation between automation points

**Choice.** Interpolation happens **on the audio thread inside `applyAutomationParam`'s caller**, not in `TransportController`. For each parameter the engine keeps the previous scheduled value and the next scheduled value; per audio block it linearly ramps toward the next value. The `TransportController` schedules **discrete** automation events (one point → one `AUTOMATION` scheduled event with `targetSample`); interpolation is a render-time concern, not a scheduling concern.

**Why.** Scheduling thousands of interpolated sub-events from Kotlin would flood the `EventQueue`. Better: schedule the user-drawn/keyframes (tens to hundreds), ramp the gap on the audio thread.

**Trade-off.** Live touch recording writes a high-density stream of points while recording; we downsample to a configurable rate (default ~20 Hz, i.e. one recorded point every 50 ms transport clock). Manual draw points are scheduled verbatim.

### Decision 4: Per-track `SynthState` is a Kotlin map, swapped on track focus

**Choice.** Add `trackStates: MutableMap<Int, SynthState>` to `TimelineViewModel` (and mirror in `SynthViewModel` for the focused-track editor). New tracks initialised with a default `SynthState` (the "Bright Lead" factory preset's normalised values). On `selectTrack(trackIndex)`:

```kotlin
// pseudo
val state = trackStates.getOrPut(trackIndex) { defaultTrackSynthState() }
SynthEngine.resetEffects()
SynthEngine.applySynthState(state.toParamsArray())
```

The `toParamsArray()` already exists (see `MainSynthScreen.applySynthStateToEngine`, lines 617–660) — extract it into a shared `SynthState.toParamsArray(): FloatArray` extension so both `MainSynthScreen` (until deleted) and the new `SynthViewModel` share it.

**Why.** Zero new C++. The single `SynthInstrument` swaps its `SynthParams` whenever the focused track changes. Notes already carry `trackIndex` and route to the existing `MixerChannel[trackIndex]`. There is no requirement that the *currently-edited* preset matches the *currently-playing* notes — a track plays whatever its notes play, and its sound is whatever was last loaded for it. This is exactly how the single-synth, multi-MIDI-channel pattern works on most mobile DAWs.

**Alternative considered.** Per-track `SynthInstrument` instances in C++. Rejected — multiplies voice allocations and CPU linearly with track count; phones are CPU-bound during audio callbacks.

**Alternative considered.** Only swap when transport is stopped. Rejected — this would surprise the user editing preset B while preset A is playing. The swap is atomic (via the same `setAllParamsFromArray` → `swapParamsIfNeeded` mechanism) and is safe mid-playback.

### Decision 5: Automation data model — `AutomationClip` keyed by `trackIndex` + `paramIndex`

**Choice.** Add to `ProjectModels.kt`:

```kotlin
@Serializable
data class AutomationPoint(val position: Long, val value: Float)

@Serializable
data class AutomationClip(
    val trackIndex: Int,
    val paramIndex: Int,
    val points: List<AutomationPoint> = emptyList()  // sorted by position
)
```

Project carries `val automation: List<AutomationClip> = emptyList()`. On transport play, `TransportController` flattens each clip into scheduled `AUTOMATION` events (using the same `nativeScheduleAutomation(trackIndex, paramIndex, value, sampleOffset)` JNI proposed below). One clip per (track, param) — multiple clips on the same (track, param) are merged (newest wins by `position`).

**Why a list of clips, not a map.** JSON serialisation of `List<AutomationClip>` is trivial with `kotlinx.serialization` and matches how `patterns`/`arrangement` are already modelled. A `Map<Pair<Int,Int>, List<AutomationPoint>>` would require a custom serializer for the tuple key.

### Decision 6: New JNI call `nativeScheduleAutomation` (or reuse existing scheduling)

**Choice.** Verify whether `TransportController` already exposes a path that pushes `AUTOMATION` events. If not, add `SynthEngine.scheduleAutomation(trackIndex, paramIndex, value, sampleOffset)` → JNI `nativeScheduleAutomation` → `SynthEngine::getInstance().getAudioEngine().getEventQueue().push(ScheduledEvent::makeAutomation(...))`. The `EventQueue` already accepts `ScheduledEvent` of any type, so the only wiring is the JNI binding and the Kotlin helper.

(Verified during design: `EventQueue::push` is generic; `ScheduledEvent::makeAutomation` already exists. This is a small addition, not a new abstraction.)

### Decision 7: Legacy UI deletion order — delete after reroute, with a build gate

**Choice.** The phone-ui-cleanup work proceeds in this strict order:

1. Reroute `MainScreen.kt:178` `MainTab.SYNTH -> MainSynthScreen()` to `MainTab.SYNTH -> SynthScreen()`.
2. Reorder `MainTab` values to `TIMELINE, MIXER, SYNTH, PADS, KEYBOARD, SEQUENCER, PROJECT` and set the default selected tab to `TIMELINE`.
3. Add `TimelineScreen` first-run empty state with the three quick-start template cards.
4. Build to confirm `SynthScreen` and reordered tabs compile and run.
5. Then delete the legacy files (`MainSynthScreen, HardwareChassis, PatchBayView, KeyboardView, SequencerView, PianoRollView, OscilloscopeView, OscWaveformView, SynthPanel, LcdDisplay`).
6. Build again. Any remaining import to a deleted file is a compile error that must be removed (these should be zero because step 1 rerouted the only live `MainSynthScreen` reference; legacy files only referenced one another).

**Why.** Deleting first risks leaving dangling references that fail far from their cause. Rerouting first makes the build verify correctness incrementally.

**Migration/rollback.** Every step is a separate commit; `git revert` the offending commit restores the pre-deletion state. No data migration — the legacy UI did not own stored data beyond the `SynthState` it pushed to the engine, and that path is preserved by `SynthScreen`.

### Decision 8: Quick-start template injection reuses `TransportController` + `PresetDatabase`

**Choice.** Templates are authored in Kotlin (`Template.kt`), each one returns a `Project(name, bpm, patterns=[…], arrangement=…, mixerState=…, automation=[])`. "4-on-Floor" injects a pattern carrying a 16-step kick/snare/hat row + a simple basslined track; "Trap Loop" injects 808-style kicks and a hat-roll track + pad track; "Blank" injects an empty arrangement at 120 BPM with one pattern + one track. Tapping a card calls `ProjectViewModel.loadProjectFromTemplate(template)`, which constructs the `Project`, swaps the `TransportController` state, and persists a new project for subsequent edits.

**Why.** `ProjectViewModel` already has `loadProject` and `saveProject`. Template injection is built on the same `Project` model and engine-load path — no template-specific engine hooks.

## Risks / Trade-offs

- **[Risk] Single `SynthInstrument` voice stealing across all tracks audibly churns when many tracks play.** → Mitigation: document this in `SynthViewModel` (polyphony mode note) and keep MAX_VOICES at 8 (current). If users complain, raise `MAX_VOICES` (a `constexpr`, trivial to bump) and re-test on low-end devices. No code surgery.

- **[Risk] Live-touch automation recording at high density floods the `EventQueue`.** → Mitigation: downsample recorded points to **20 Hz by default** (one point per 50 ms transport clock). Manual draw points are never denser than the user's finger.

- **[Risk] Automation swap during playback audibly glitches the synth (params swap between blocks).** → Mitigation: `applyAutomationParam` writes only `pendingParams_`; `swapParamsIfNeeded` performs the swap at the next `processAudio` boundary, which is already the safe swap point preset loads use. Linear interpolation inside the block (Decision 3) makes single-point automation continuous, removing zipper noise.

- **[Risk] Legacy files referenced by tests or docs beyond `MainScreen`.** → Mitigation: a repo-wide grep for each removed symbol is a task in the cleanup slice; any hit becomes an explicit reroute/follow-up.

- **[Risk] Template injection misjudges the existing `Arrangement`/`Pattern` setters.** → Mitigation: template authoring reuses only the already-serialised fields; `Template.kt` is unit-tested by constructing a `Project`, round-tripping it through `Project` JSON, and asserting the decoded equals the original.

- **[Risk] `paramIndex` 39–45 drift if someone adds mixer commands later.** → Mitigation: define explicit `const int MIXER_AUTOMATION_FADER = 39;` etc. in `SynthParams.h` (or a new `AutomationParams.h`) so additions are intentional, not positional.

## Migration Plan

1. Apply automation C++ handler (Decision 1 + 2 + 6). Build. JVM tests unchanged.
2. Add Kotlin automation data model + persistence (Decision 5). Build + unit test round-trip.
3. Add automation lane UI in `SequencerScreen` (piano-roll overlay) and `MixerScreen` (per-channel sheet). Manual draw + live touch. Build.
4. Add per-track `SynthState` map to `TimelineViewModel`/`SynthViewModel` + `toParamsArray()` extension (Decision 4). Build.
5. Reroute + reorder `MainScreen` tabs (Decision 7 steps 1–4). Build. Confirm `SynthScreen` reachable.
6. Delete legacy UI files (Decision 7 step 5). Build. Confirm no dangling references.
7. Add first-run empty state quick-start templates (Decision 8). Build + unit test template round-trip.
8. Run `./gradlew lint` + `./gradlew testDebugUnitTest` as the final gate.

Each step is its own commit, no step depends on a later step's artifact. Rollback at any step is `git revert`.

## Open Questions

1. **Spec/proposal mismatch.** The proposal and spec cap mixer automation at `paramIndex` 39–54 (16 values). Design narrows it to 39–45 (7 reusable `MixerCommandType`s). Resolution: **adopt 39–45** and update the spec's `automation-system` requirement wording to "39–45" with "unknown params drop". (Decision纪录. To apply before sdd-tasks.)

2. **Automation arm default per session.** When the user arms a param and stops the transport, is the arm cleared? Default: **yes, clear arm on stop** to avoid accidental overdubbing on next play. (To confirm at tasks.)

3. **`MAX_VOICES` for multi-timbral polyphony.** Currently 8. With 4 simultaneous tracks each holding a chord this can choke. Decision: keep 8 for this slice, document the knob, revisit if beta feedback requests it. (To confirm at tasks.)

4. **Template sample preload.** "Trap Loop" and "4-on-Floor" can't ship copyrighted samples. Decision: synthesise drum hits via the synth's noise/short-envelope path instead of bundling samples. (Confirmed during tasks.)

5. **`ui/synth/SynthScreen` parity.** It is currently incomplete (KDoc "TODO for integrator"). The reroute forces a quick audit of what SynthScreen can/cannot do vs. legacy `MainSynthScreen`. If SynthScreen lacks a panel (e.g. modulation matrix), carry it over before deleting `MainSynthScreen`. The audit is task 0 of the UI slice.