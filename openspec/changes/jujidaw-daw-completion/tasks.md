## 1. C++ Automation Handler

- [ ] 1.1 In `SynthInstrument.h`, declare `void applyAutomationParam(int paramIndex, float value);`
- [ ] 1.2 In `SynthInstrument.cpp`, implement `applyAutomationParam` as a switch over `paramIndex` 0–38 that writes the corresponding field in `pendingParams_` and sets `paramsPending_ = true` (reuses the index layout from `setAllParamsFromArray` lines 247–288)
- [ ] 1.3 In `SynthInstrument.h`, add `float automationPrev_[39]` and `float automationTarget_[39]` with `bool automationRamping_[39]` for interpolation state; in `processSynthSample`, add linear ramp loop that advances from prev→target per sample
- [ ] 1.4 In `Transport.cpp` (line 116 `AUTOMATION` case), replace `(void)synth; // TODO` with dispatch logic:
    - 0–38: call `engine.getChannel(track).getInstrument()` → if `SynthInstrument*`, call `.applyAutomationParam(paramIndex, value)`
    - 39–45: build `MixerCommand{type=SetFader(39), SetPan(40), SetMute(41), SetSolo(42), SetArm(43), SetSendA(44), SetSendB(45), value, track, …}` and call `engine.pushMixerCommand(cmd)`
    - else: log and drop
- [ ] 1.5 Define `constexpr int AUTOMATION_SYNTH_PARAM_MAX = 38;` and `constexpr int AUTOMATION_FADER = 39;` … `AUTOMATION_SENDB = 45;` in a new header `AutomationParamIds.h` shared between C++ and JNI
- [ ] 1.6 Build and verify (`./gradlew assembleDebug`)

**Estimated delta:** ~130 lines C++ (1.1–1.6)

## 2. Automation JNI + Kotlin Data Model

- [ ] 2.1 Add `AutomationPoint(position: Long, value: Float)` and `AutomationClip(trackIndex: Int, paramIndex: Int, points: List<AutomationPoint>)` to `ProjectModels.kt`
- [ ] 2.2 Add `val automation: List<AutomationClip> = emptyList()` to `Project` data class
- [ ] 2.3 In `SynthEngine.kt`, add `external fun nativeScheduleAutomation(trackIndex: Int, paramIndex: Int, value: Float, sampleOffset: Long)` and its Kotlin wrapper `fun scheduleAutomation(trackIndex: Int, paramIndex: Int, value: Float, sampleOffset: Long)`
- [ ] 2.4 In `JniBridge.cpp`, add `Java_com_jujidaw_audio_SynthEngine_nativeScheduleAutomation` that calls `SynthEngine::getInstance().getAudioEngine().getEventQueue().push(ScheduledEvent::makeAutomation(track, paramIndex, value, targetSample))`
- [ ] 2.5 Write unit test: round-trip `AutomationClip` through `kotlinx.serialization.encodeToString` → `decodeFromString` and assert equality
- [ ] 2.6 Build and verify

**Estimated delta:** ~80 lines Kotlin + ~10 lines JNI + ~12 lines C++

## 3. TransportController Automation Scheduling

- [ ] 3.1 In `TransportController.kt`, add `val automationClips: MutableList<AutomationClip> = mutableListOf()` and a `loadAutomation(clips: List<AutomationClip>)` method
- [ ] 3.2 In the scheduler coroutine loop, before scheduling note events, iterate `automationClips` and for each clip with points in the current scheduling window, push an `ScheduledEvent::makeAutomation` via `scheduler.scheduleAutomation(track, paramIndex, value, sampleOffset)`
- [ ] 3.3 Handle `targetSample` correctly: convert the AutomationPoint's tick position to an absolute sample offset using the current `TransportController` sample clock (`PPQ`, `TICKS_PER_BEAT`, tempo)
- [ ] 3.4 On transport stop, clear the automation clips from the scheduler (points remain in memory for next play)
- [ ] 3.5 Build and run existing `TransportControllerTest` (should still pass)
- [ ] 3.6 Write a new test: schedule automation events and verify they reach the `FakeSynthEngineScheduler` in the expected order

**Estimated delta:** ~120 lines Kotlin + test

## 4. Automation Lane UI — Manual Draw

- [ ] 4.1 Create `AutomationLaneOverlay.kt` composable in `ui/sequencer/` that renders a scrollable lane with:
    - Horizontal axis aligned to the piano-roll's note grid
    - Vertical axis for the parameter's 0.0–1.0 range
    - Small circles at each `AutomationPoint`
    - Lines connecting adjacent points
- [ ] 4.2 Add gesture handlers to `AutomationLaneOverlay`:
    - Tap on empty space → create point at that position/value
    - Long-press existing point → show delete affordance, tap delete → remove point
    - Drag existing point → move to new position/value, live-update the result curve
- [ ] 4.3 Integrate `AutomationLaneOverlay` into `SequencerScreen` piano-roll mode: add a "Show automation" toggle button; when active, show lane(s) below the piano-roll keys for the selected param
- [ ] 4.4 Create `MixerAutomationSheet` composable in `ui/mixer/` that shows automation lanes per-channel per-param (reuses the same `AutomationLaneOverlay` internally) with a param picker dropdown
- [ ] 4.5 Wire `SequencerScreen` automation state to `SequencerViewModel`: `selectedAutomationParam: Int?` and `automationPointsForSelectedParam: List<AutomationPoint>`
- [ ] 4.6 Wire `MixerAutomationSheet` state to `MixerViewModel`: `mixerUiState.automationParams`
- [ ] 4.7 Build and verify

**Estimated delta:** ~250 lines Kotlin (composables + state wiring)

## 5. Automation Lane UI — Live Touch Recording

- [ ] 5.1 Add `automationArmedParams: MutableSet<Int>` to `TimelineViewModel` / `SequencerViewModel` / `MixerViewModel` (per param arm state)
- [ ] 5.2 Add arm toggle button (small circle icon next to each param label) in the automation lane UI; when on, a red indicator pulse
- [ ] 5.3 On any `SynthEngine.setParam(id, value)` call while the transport is playing AND the param is armed:
    - Record the current transport position (`TransportController.transportState.samplePosition`)
    - Create an `AutomationPoint(samplePosition, value)` and add it to the active `AutomationClip`
    - Deduplicate: if the last recorded point is within 50ms of the current position, update the last point's value instead of appending (20 Hz downsampling)
- [ ] 5.4 On transport stop, flush the recorded clips into the `automationClips` list (they are already added point-by-point; just ensure the clip is saved)
- [ ] 5.5 Clear armed params on transport stop (avoid accidental overdub next play)
- [ ] 5.6 Build and verify

**Estimated delta:** ~120 lines Kotlin

## 6. Multi-timbral — Per-Track SynthState

- [ ] 6.1 Extract `SynthState.toParamsArray(): FloatArray` from `MainSynthScreen.kt` lines 617–660 into a shared utility: create `SynthStateExtensions.kt` under `model/` with the extension function
- [ ] 6.2 In `TimelineViewModel`, add `val trackStates: MutableMap<Int, SynthState> = mutableMapOf()`; initialise track 0 with default SynthState; include in project save/load
- [ ] 6.3 In `SynthViewModel`, add `val trackStates: MutableMap<Int, SynthState>` and `var activeTrack: Int`; on `setActiveTrack(index)`, check `trackStates.getOrPut(index) { factoryDefaultSynthState() }`, call `SynthEngine.resetEffects()` then `SynthEngine.applySynthState(it.toParamsArray())`
- [ ] 6.4 Wire `TimelineScreen` track selection → `SynthTab` now switches SynthViewModel's active track → triggers the `applySynthState` swap flow
- [ ] 6.5 Persist per-track `SynthState` in `Project.json`: add `val trackSynthStates: Map<Int, SynthState.Serialized>` to `Project` (serialise via `SynthStateSerializer` or inline float array)
- [ ] 6.6 On project load, restore `trackStates` map from the JSON; on first play, apply the currently-focused track's state
- [ ] 6.7 Build and verify (multi-timbral state round-trip test)

**Estimated delta:** ~180 lines Kotlin

## 7. UI Cleanup — Reroute + Reorder + SynthScreen Audit

- [ ] 7.1 In `MainScreen.kt`, change `MainTab.SYNTH -> MainSynthScreen()` to `MainTab.SYNTH -> SynthScreen()`
- [ ] 7.2 Reorder `MainTab` values to: `TIMELINE, MIXER, SYNTH, PADS, KEYBOARD, SEQUENCER, PROJECT`
- [ ] 7.3 Set default selected tab to `TIMELINE` (change `selectedTab` initialisation from `PADS` to `TIMELINE` or `0`)
- [ ] 7.4 Audit `ui/synth/SynthScreen.kt` against the legacy `MainSynthScreen`: does SynthScreen have all panels (oscillators, filter, envelopes, LFO x2, effects, master, sequencer controls, modulation matrix, preset browser, keyboard)?
    - For each missing panel, either add it to SynthScreen or document it as a planned follow-up
- [ ] 7.5 If any panels were missing (step 7.4), add them to `SynthScreen` before deleting legacy
- [ ] 7.6 Build and confirm: SYNTH tab opens SynthScreen, all tabs render in the new order, TIMELINE is default

**Estimated delta:** ~80 lines Kotlin (reroute + reorder) + audit effort (read-only, no delta)

## 8. UI Cleanup — Delete Legacy Files

- [ ] 8.1 Grep entire `app/src/main/java/com/jujidaw/` for imports/references to each symbol:
    - `MainSynthScreen`, `HardwareChassis`, `PatchBayView`, `KeyboardView`, `SequencerView`, `PianoRollView`, `OscilloscopeView`, `OscWaveformView`, `SynthPanel`, `LcdDisplay`
- [ ] 8.2 For any remaining reference found, reroute it or document why it cannot be removed yet
- [ ] 8.3 Delete the following files:
    - `app/src/main/java/com/jujidaw/ui/MainSynthScreen.kt`
    - `app/src/main/java/com/jujidaw/ui/HardwareChassis.kt`
    - `app/src/main/java/com/jujidaw/ui/PatchBayView.kt`
    - `app/src/main/java/com/jujidaw/ui/KeyboardView.kt`
    - `app/src/main/java/com/jujidaw/ui/SequencerView.kt`
    - `app/src/main/java/com/jujidaw/ui/PianoRollView.kt`
    - `app/src/main/java/com/jujidaw/ui/OscilloscopeView.kt`
    - `app/src/main/java/com/jujidaw/ui/OscWaveformView.kt`
    - `app/src/main/java/com/jujidaw/ui/SynthPanel.kt`
    - `app/src/main/java/com/jujidaw/ui/LcdDisplay.kt`
- [ ] 8.4 Build and verify (`./gradlew assembleDebug`)

**Estimated delta:** ~2,200 lines removed (\~0 net positive), strictly deletions. Gate: build must succeed.

## 9. UI Cleanup — Quick-Start Templates + Empty State

- [ ] 9.1 Create `Template.kt` under `ui/project/` with three template functions:
    - `fourOnFloorTemplate()`: returns `Project("4-on-Floor", bpm=128, patterns=[one 16-step pattern with kick/snare/hat on tracks 0/1/2, one bass line on track 3], arrangement=[clip range for 8 bars])`
    - `trapLoopTemplate()`: returns `Project("Trap Loop", bpm=140, patterns=[808 kick/hat-roll/pad on tracks 0/1/2])`
    - `blankTemplate()`: returns `Project("Untitled", bpm=120, patterns=[1 empty 16-step pattern], arrangement=[])`
    - Drum hits use the engine's noise/short-envelope path (osc 3=sine + env attack 0.001, decay 0.05)
- [ ] 9.2 Add `loadProjectFromTemplate(template: Project)` to `ProjectViewModel`: calls `saveProject(template)`, then `loadProject(template.name)`, then `TransportController.loadArrangement(template.arrangement)`
- [ ] 9.3 In `TimelineScreen`, add a `showTemplates: Boolean` state: when `projectRepository.listProjects().isEmpty()`, show the template cards instead of an empty arrangement
- [ ] 9.4 Design the template cards UI: three cards in a horizontal row with icon + title + brief description; tapping a card calls `projectViewModel.loadProjectFromTemplate(...)`
- [ ] 9.5 Write unit test: round-trip each template project through JSON, assert key fields (bpm, pattern count, arrangement clips)
- [ ] 9.6 Build and verify

**Estimated delta:** ~180 lines Kotlin

## 10. Quality Gate

- [ ] 10.1 Run `./gradlew lint`
- [ ] 10.2 Run `./gradlew clean assembleDebug testDebugUnitTest`
- [ ] 10.3 Resolve any lint errors or test failures
