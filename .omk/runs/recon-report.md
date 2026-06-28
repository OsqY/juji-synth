# JujiDAW Recon Report

## 1. ARCHITECTURE MAP — JNI bridge + C++ engine ↔ Kotlin

### Native entry point
`app/src/main/cpp/JniBridge.cpp` (988 lines) — single JNI translation unit. All C-exported functions use the `Java_com_jujidaw_audio_SynthEngine_<name>` naming scheme (one Kotlin companion class `com.jujidaw.audio.SynthEngine` is the sole JNI counterpart). 75 `Java_com_jujidaw_audio_SynthEngine_native*` exports == 75 `external fun native*` declarations in `SynthEngine.kt`. **JNI surface is fully symmetric — no orphaned Kotlin externals or unreferenced C++ exports detected.**

### Kotlin bridge object
`app/src/main/java/com/jujidaw/audio/SynthEngine.kt` (330 lines)
- `object SynthEngine { init { System.loadLibrary("jujisynth") } ... }`
- Loads native lib named **`jujisynth"`** (legacy lib name retained after rebrand; CMake target `jujisynth` in `CMakeLists.txt:106`).
- Wraps every native method in an idiomatic Kotlin convenience fun (e.g. `noteOn`, `setChannelFader`, etc.) plus exposes `EffectType` enum (`None/Reverb/Delay/Distortion/Chorus/Filter/Bitcrusher/Compressor/Eq`) and `PerformFxType` constants.

### Engine ownership on Kotlin side
- `com.jujidaw.JujiDawApp` (Application) owns the singleton lifecycle: instantiates `PresetDatabase` (Room "juji-daw-db"), `TransportController`, `MidiRouter`, and calls `AudioEngineManager.ensureStarted()` in `onCreate` (`JujiDawApp.kt:36`).
- `com.jujidaw.audio.AudioEngineManager` (object) is the app-level engine lifecycle wrapper used by `MainActivity` and `JujiDawApp`. Idempotent start/stop, exposes `isRunning: StateFlow<Boolean>` and `lastError: StateFlow<String?>`.

### C++ engine hierarchy (AudioEngine.cpp/h)
`SynthEngine` (the Oboe streaming singleton in `SynthEngine.h`) → owns `AudioEngine engine_` (private). `AudioEngine` is the central dispatcher:
- `AudioEngine::init(sampleRate)` wires:
  - `channels_[16]` of `MixerChannel` (Bus.h / MixerChannel.cpp)
  - `buses_[2]` of `Bus` + `MasterBus masterBus_` → master fader, send routing
  - `synthInstrument_ = std::make_unique<SynthInstrument>()` on channel 0
  - `sampler_ = std::make_unique<SamplerInstrument>()` on channel 1
  - `recorder_` (`AudioRecorder.cpp/h`), `timeStretchWorker_` (`TimeStretchWorker.cpp/h`)
  - `transport_` (`Transport.cpp`, namespace `jujidaw`), `eventQueue_` (`EventQueue.cpp`)
  - `audioClipPlayers_[16]` (`AudioClipPlayer.cpp/h`), `audioClips_` map (id→`SampleBuffer`)
  - `performFilter_` (sweep FX)
- `processAudio(out, numFrames)` is the Oboe callback: runs `processMixerQueue()`, pulls voices from `SynthInstrument`/`SamplerInstrument`, runs inserts & sends, mixdown via `MasterBus`, captures master peak via `scopeBuffer_[512]` and `masterPeak_` atomic.
- Thread-safe mixer control path: `pushMixerCommand(MixerCommand{type,track,slot,value,booleanValue,effectType,paramId})` → ring buffer consumed each audio block (`MixerCommand.h` enum: `SetFader/SetPan/SetMute/SetSolo/SetArm/SetSendLevel/SetBusFader/SetMasterFader/AddInsertEffect/RemoveInsertEffect/SetInsertBypass/SetInsertParam`).
- Mixer-graph state getters (UI polling): `getChannelFaderDb`, `getChannelPan`, `isChannelMute`/`Solo`/`Arm`, `getSendLevel`, `getBusFaderDb`, `getMasterFaderDb`, `getMasterPeak`, `getChannelLevel`.

### DSP modules referenced from AudioEngine / SynthInstrument
`Oscillator`, `Envelope`, `LFO`, `Filter`, `Reverb`, `Delay`, `Distortion`, `Chorus`, `ModulationMatrix`, `SynthVoice`/`SynthParams`, `SamplerVoice`/`SampleBuffer`, `WavWriter`, `TimeStretchWorker` (uses SoundTouch), `AudioClipPlayer`, `AudioRecorder` (with punch in/out, `writeToWav`, `takeBuffer`).

### Transport / sequencer bridge
- C++ `jujidaw::Transport` (`Transport.cpp:12`) maintains sample playhead + loop/punch range.
- C++ `jujidaw::EventQueue` (`EventQueue.cpp`) is the priority queue the Kotlin scheduler pushes into via `nativeScheduleNoteOn/Off`, `nativeSchedulePadTrigger`, `nativeClearScheduledEvents`, `nativeSetTransport`, `nativeSetPlayheadSample`, `nativeSetLoop`.
- Kotlin scheduler: `com.jujidaw.engine.TransportController` (430 lines) owns musical clock (bars/beats/ticks, `PPQ`, `TICKS_PER_BEAT`, `TICKS_PER_STEP`), patterns, arrangement, and runs a coroutine pushing upcoming events to C++ via `SynthEngineScheduler` interface. Default impl `NativeSynthEngineScheduler` delegates straight to `SynthEngine` JNI calls (`SynthEngineScheduler.kt`);
  test impl `FakeSynthEngineScheduler` is used by `TransportControllerTest` (473 lines).
- The internal C++ step sequencer is disabled (`nativeSetSequencerEnabled(false)`) while `TransportController` is playing, re-enabled when stopped (see `TransportController.kt` header comment).

### MIDI layer (`com.jujidaw.midi.*`)
- `MidiRouter` (280 lines) — owns CC→param mapping loaded from `MidiMappingStore`; routes inbound MIDI CC to `SynthEngine.setParam(...)`.
- `MidiController` (178 lines) — scans USB MIDI devices, runs learn callbacks; `onCcLearnCallback` static hook consumed by `SynthScreen` and legacy `MainSynthScreen` for learn UI.
- `MidiDeviceService` (49 lines) — Android `MediaMidiDeviceService` registered in `AndroidManifest.xml:24` with `BIND_MIDI_DEVICE_SERVICE` permission.

## 2. UI ORGANIZATION — Composables, navigation, active vs orphan

**There is NO Jetpack Navigation NavHost.** `MainActivity` → `MainScreen` only. Navigation is a single scaffold with a `MainTab` enum and a `when (tabs[selectedTab])` switch:

`app/src/main/java/com/jujidaw/ui/main/MainScreen.kt:44-69` — `MainTab { PADS, SYNTH, KEYBOARD, SEQUENCER, TIMELINE, MIXER, PROJECT }`, rendered as portrait `NavigationBar` or landscape `NavigationRail` with a custom `PersistentTransportBar` polling `transportController`.

### Active screens (routed from MainScreen)
| Tab | Screen file | LOC | Status |
| --- | --- | --- | --- |
| PADS | `ui/pads/PadsScreen.kt` + `PadsViewModel.kt` | 672 + 359 | active, fully MVVM |
| SYNTH | `ui/MainSynthScreen.kt` | 654 | **active legacy monolith** (the tab routes here, NOT to `SynthScreen`) |
| KEYS | `ui/keyboard/KeyboardScreen.kt` + `KeyboardViewModel.kt` | 431 + 480 | active, MVVM |
| SEQ | `ui/sequencer/SequencerScreen.kt` + `SequencerViewModel.kt` | 959 + 311 | active, MVVM (dual `STEP`/`PIANO_ROLL` modes) |
| TIMELINE | `ui/timeline/TimelineScreen.kt` + `TimelineViewModel.kt` | 1012 + 411 | active, MVVM (TODO-driven stubs remain) |
| MIXER | `ui/mixer/MixerScreen.kt` + `MixerViewModel.kt` | 1066 + 314 | active, MVVM (TODO-driven stubs) |
| PROJECT | `ui/project/ProjectScreen.kt` + `ProjectViewModel.kt` (in `ui/project/`) | 751 + 509 | active, MVVM |

Reference path bug: `ProjectViewModel.kt` lives under `ui/project/`, NOT `project/` — only `ProjectModels.kt`/`ProjectRepository.kt` are under `project/`.

### Orphaned / not-yet-wired screen
`ui/synth/SynthScreen.kt` (383) + `SynthViewModel.kt` (237) — exists with its own MVVM, but **MainScreen does not route to it** (line 178 still `MainTab.SYNTH -> MainSynthScreen()`). Its KDoc explicitly says *"TODO for integrator: Wire into the bottom-navigation graph (Group 14)"* and that per-track synth isolation is stubbed. This is a duplicate of the Synth tab functionality with the new phone-first MVVM design.

### Legacy composables still pulled in transitively (via MainSynthScreen)
`MainSynthScreen.kt` directly references (at the listed lines):
- `HardwareChassis` (line 145) — 124 lines
- `PatchBayView` (line 177) — 172 lines
- `SequencerView` (line 261) — 124 lines (legacy step grid)
- `OscilloscopeView` (line 290) — 120 lines
- `KeyboardView` (line 503) — 498 lines (legacy keyboard)
- `PianoRollView` (line 519) — 413 lines (legacy piano roll)
- `PresetBrowser` (line 542) — 323 lines
- `ManualScreen` (line 565) — 201 lines
- `SettingsScreen` (line 570) — 212 lines

So these legacy files are **still compiled and active** through the SYNTH tab — they are NOT dead code yet, but they overlap with the new MVVM screens `SequencerScreen` (which has its own `PIANO_ROLL` mode) and the orphaned `SynthScreen`.

### Shared UI atoms (used by both legacy and new screens via `com.jujidaw.ui.*`)
`Components.kt` (660), `RealKnob.kt` (247), `SynthPanel.kt` (119), `LcdDisplay.kt` (73), `OscillatorPanel.kt` (71), `EnvelopePanel.kt` (212), `FilterPanel.kt` (61, pulls `FilterResponseView`), `LfoPanel.kt` (202, pulls `LfoAnimationView`), `EffectsPanel.kt` (233), `OscWaveformView.kt` (82), `OscilloscopeView.kt` (120). New `SynthScreen.kt` and the modern screens reuse these via `import com.jujidaw.ui.*` and `import com.jujidaw.ui.theme.*`. Theme (`Color.kt`/`Theme.kt`/`Type.kt`) → `JujiDawTheme` is the app-level Material theme used by `MainActivity`.

## 3. DATA / MODEL

`com.jujidaw.model` (7 files, 716 LOC):
- `TransportModel.kt` (70): `TransportState`, `TransportPosition(bar, beat, tick)`, constants `PPQ=96`, `TICKS_PER_BEAT`, `TICKS_PER_STEP`, `TimeSignature`.
- `PatternModel.kt` (85): `Pattern(id, name, steps, lengthSteps)`, `StepPattern`/`PianoRollPattern`/`NoteEvent`.
- `ClipModel.kt` (112): `Clip` sealed type → `PatternClip`, `AudioClip(startTick, durationTicks, clipId, trackIndex)`, conversions to/from `TransportController` models.
- `SynthState.kt` (204): `SynthState` data class + `ParamIds` constants matching C++ param ids (0–62). `ModulationRoute`.
- `Preset.kt` (32): `Preset`/`PresetVoice`.
- `MidiLearnState.kt` (26) + `MidiTarget.kt` (87): learn mode + param-id→MidiTarget resolution.

`com.jujidaw.data` (4 files, 503 LOC):
- `PresetDatabase.kt` (53): Room DB with `PresetDao` + `seedFactoryPresets(db)`; `PresetEntity` defined inline.
- `FactoryPresetSeeder.kt` (260): 20+ factory presets (init data insert at first launch).
- `MidiMappingStore.kt` (136): SharedPreferences-backed CC→paramId mapping persistence; `MidiMapping` value class.
- `SettingsDataStore.kt` (54): DataStore preferences wrapper.

`com.jujidaw.project` (2 files, 410 LOC):
- `ProjectModels.kt` (84): `Project`, `ProjectInfo`, `Arrangement`, `Clip`, `Pattern`, `NoteEvent` (canonical domain models the transport uses).
- `ProjectRepository.kt` (326): file-system project save/load (`internal storage "projects/"`), audio clip import from ContentResolver Uri, and `exportMix`/`exportStems` which drive `SynthEngine.startOfflineRender`/`startOfflineRenderForTrack` and orchestrate playback through `TransportController` (see §4).

`com.jujidaw.diagnostics.RuntimeHealth.kt` (105): runtime assertions / log helpers, unconnected from UI.

## 4. ISSUES — with file:line refs and severity

### 4.1 Rebrand consistency
- `SynthEngine.kt:14` (init) loads `System.loadLibrary("jujisynth")` while the Kotlin package is `com.jujidaw`. The native lib target is still `jujisynth` in `CMakeLists.txt:106` and `project("jujisynth")` at line 2. **(Medium — works, incomplete rebrand)** Leaving the lib name as `jujisynth` is harmless but inconsistent with the new brand. The old `com.jujisynth` package has been entirely deleted (git `D` entries all confirmed staged); no lingering `com.jujisynth` references found in `app/src/main` or `app/src/test`.
- `AndroidManifest.xml` uses `.JujiDawApp`, `.MainActivity`, `.midi.MidiDeviceService` and theme `Theme.JujiDaw`; strings.xml `app_name="Juji DAW"`; themes.xml `Theme.JujiDaw`. Build config `namespace="com.jujidaw"`, `applicationId="com.jujidaw.app"` (build.gradle.kts:10–14). **Conflict-free.**

### 4.2 Duplicate / orphaned UI
- `ui/main/MainScreen.kt:178` — `MainTab.SYNTH -> MainSynthScreen()` routes to the legacy 654-line `MainSynthScreen` instead of the new MVVM `ui/synth/SynthScreen.kt`. **(High — duplicate Synth UI; new phone-first rebuild is unreachable from the app.)**
- `ui/synth/SynthScreen.kt:51` — KDoc TODO "Wire into the bottom-navigation graph" confirms it was intentionally left unwired.
- `ui/SequencerView.kt` (legacy) + `ui/sequencer/SequencerScreen.kt` (new MVVM with `PIANO_ROLL` mode) and `ui/PianoRollView.kt` (legacy) overlap functionally. Currently both compile: the legacy versions are still reachable via `MainSynthScreen`; the new `SequencerScreen` is the active Sequencer tab.
- `ui/KeyboardView.kt` (legacy, 498) ≤ `ui/keyboard/KeyboardScreen.kt` (new). Legacy keyboard only reachable from `MainSynthScreen`; new is the active Keyboard tab.

### 4.3 Stubs / placeholders / "not implemented"
- TIMELINE: `ui/timeline/TimelineScreen.kt:46` (TODO head), `:48` waveform placeholder to replace with real decoded PCM overview; `ui/timeline/TimelineViewModel.kt:87` TODO poll per-track level from C++ mixer when API available; `:23` TODO provide singleton `TransportController` via a custom DI factory (each VM currently fakes one — see §4.4).
- MIXER: `ui/mixer/MixerScreen.kt:186` "Automation placeholder sheet"; `:1030` literal placeholder text "Automation editor is a placeholder."; `ui/mixer/MixerViewModel.kt:79` TODO list; `:215` and `:225` TODO claims "engine does not expose a nativeReorderInsertEffect" — **STALE TODO: the JNI bridge DOES expose `nativeReorderChannelInserts` (JniBridge.cpp:783-788) and `SynthEngine.nativeReorderChannelInserts` (SynthEngine.kt:213). UI only swaps slots and never calls the engine reorder API. (Medium — wrong TODO, lost wiring.)** `:239` Perform FX shows a toast tagged `(TODO: engine integration)` even though `nativeTriggerPerformFx` exists and is wired through `SynthEngine.triggerPerformFx(type)`. (Medium — UI doesn't persist perform-FX state in engine.) `:253-255` `addAutomationPoint` is a TODO-only toast; no automation queue wired to C++.
- PADS: Slice/choke group fully stubbed — `ui/pads/PadsViewModel.kt:33,57,58,59,275,285,296,319` and `ui/pads/PadsScreen.kt:568,570,620,626` "Slice (TODO: engine)" / "Choke Group (TODO)". Engine has no `setPadSlice` or choke support yet.
- KEYBOARD: `ui/keyboard/KeyboardViewModel.kt:370-371` aftertouch maps to MOD_WHEEL as a placeholder because the engine exposes no per-note aftertouch.
- SYNTH (new): `ui/synth/SynthViewModel.kt:79` TODO load per-track state when multi-timbral support is added (all tracks share one engine).
- SEQUENCER: `ui/sequencer/SequencerViewModel.kt:78` comment: factory wiring (TODO below) — see also `SequencerScreen.kt:47` "TODO for integrator".
- PROJECT: `ui/project/ProjectViewModel.kt:438` TODO after recording stop retrieve audio buffer from engine; `:471` engine does not expose per-track fader/pan/mute/solo; `:484` insert FX restoration is TODO; `:497-502` Restore insert FX per slot / bus insert FX TODOs. `ProjectRepository.kt:42-50` header documents that `exportMix`/`exportStems` rely on real-time playback + engine recording (not true offline render of the engine sequencer); they call `SynthEngine.startOfflineRender` but actually drive `TransportController.play()` for `totalMs+500ms` then stop — i.e. a hybrid real-time capture. (Low/Medium — exports work for short arrangements but are not synchronous offline.)

### 4.4 TransportController injection / singleton mismatch
- `JujiDawApp` exposes `transportController: TransportController` (singleton).
- `MainScreen.kt:160` correctly fetches the app's `transportController` for the persistent transport bar.
- Modern VMs (`TimelineViewModel`, `PadsViewModel`, `SequencerViewModel`, `MixerViewModel`, `ProjectScreen` paths) take `TransportController` as a constructor default — when wired through `viewModel()` they currently receive a brand-new `TransportController(48000, 100, 50, defaultScope, NativeSynthEngineScheduler())` rather than the app singleton. KDoc at `ui/timeline/TimelineViewModel.kt:23` calls this out explicitly as a TODO for an integrator-supplied DI factory. **(High — transport/sequencer/timeline actions performed on the screen-local VM controller are decoupled from the app controller's clock → beat-inconsistent transport between the toolbar and the screens.).** Verify per-VM wiring before assuming replay; the screens still call `viewModel()` defaults.

### 4.5 Engineering TODOs left in C++
- `cpp/Transport.cpp:116` — `TODO: map paramIndex to SynthAutomation field` inside the `AUTOMATION` event handler. Automation events are essentially dropped on the floor today (the receiver cast is `(void)synth;`). (High — automation does not actually reach the synth from the sequencer/transport scheduler.)

### 4.6 Build sanity
- `app/build.gradle.kts:22-23` `abiFilters` includes `"x86"` (alongside `arm64-v8a`, `armeabi-v7a`, `x86_64`). SoundTouch has `cpu_detect_x86.cpp` and x86-optimized SSE sources but no `arm32` Neon is needed for `armeabi-v7a` (the cmake adds `-mfpu=neon`). Should still compile, just enlarges APK.
- CMakeLists lists `LFO.cpp` in SOURCES (`CMakeLists.txt:49`) and `LFO.h` (line 94) — present on disk (`LFO.cpp`/`LFO.h` are in cpp dir). All SOURCE/header files referenced in CMake exist on disk (verified via `find`).
- No `tests`/`testInstrumented` wired to native engine; only JVM tests under `app/src/test/java/com/jujidaw/{engine,model}/` — they target `TransportController` and `ClipModel`/`TransportModel`, no UI tests.

## 5. Line-count summary per major dir

| Directory | Files | LOC (key dirs) |
| --- | --- | --- |
| `cpp/` (engine, excluding oboe+soundtouch) | 56 files | ~6,000+ LOC core (JniBridge 988, AudioEngine cpp+h ~700, Transport 132, Sequencer ~300, MixerChannel/Bus/MasterBus ~500, SynthInstrument/SamplerInstrument ~700, TimeStretchWorker/WavWriter/AudioRecorder/AudioClipPlayer ~400, effects ~400, SynthVoice/Oscillator/Envelope/LFO/Filter/ModulationMatrix ~600) |
| `cpp/oboe` (vendored) | ~120 | upstream Oboe library (unchanged, used as git submodule-ish vendored dir) |
| `cpp/soundtouch` (vendored) | ~30 | upstream SoundTouch (unchanged) |
| `java/com/jujidaw/audio` | 3 | SynthEngine 330, AudioEngineManager 90, TimeStretchListener 30 |
| `java/com/jujidaw/engine` | 2 | TransportController 430, SynthEngineScheduler 53 |
| `java/com/jujidaw/midi` | 3 | 507 total |
| `java/com/jujidaw/model` | 7 | 716 total |
| `java/com/jujidaw/data` | 4 | 503 total |
| `java/com/jujidaw/project` | 2 | 410 total (ProjectModels 84 + ProjectRepository 326) |
| `java/com/jujidaw/diagnostics` | 1 | 105 |
| `java/com/jujidaw/ui` (legacy atom/panels) | 22 | ~5,200 total (MainSynthScreen 654, Components 660, KeyboardView 498, PresetBrowser 323, ManualScreen 201, EffectsPanel 233, EnvelopePanel 212, etc.) |
| `java/com/jujidaw/ui/{main,synth,keyboard,pads,sequencer,mixer,timeline,project}` | 15 | ~8,225 (MixerScreen 1066, TimelineScreen 1012, SequencerScreen 959, ProjectScreen 751, PadsScreen 672, KeyboardScreen 431, SynthScreen 383 + matching VMs) |
| `java/com/jujidaw/ui/theme` | 3 | 154 |
| `test/java/com/jujidaw` | 4 | 713 (TransportControllerTest 473, FakeSynthEngineScheduler 139, ClipModelTest 63, TransportModelTest 38) |
| Manifest + res | 5 | small |

Total Kotlin main source ≈ 16k LOC across com.jujidaw; tests 713 LOC.

## 6. WHAT IS ACTUALLY WIRED UP vs SCAFFOLDING — assessment

### Fully wired and working (conceptually)
- Native audio engine: Oboe stream lifecycle (start/stop/fallback levels), `AudioEngine::processAudio`, mixer board (16 ch + 2 sends + master), synth + sampler channels, insert FX chain, send FX (reverb/delay/chorus/distortion/filter), master metering/oscilloscope buffer, master volume, pitch bend / mod wheel, MIDI CC routing. JNI surface tested symmetric (75/75).
- Engine lifecycle owned by Application; works across activity transitions.
- Step sequencer (legacy) — fully wired through `MainSynthScreen`'s `SequencerView` and direct `nativeSetSequencerSteps` path while transport is stopped.
- TransportController scheduler loop → C++ `EventQueue` → `nativeSchedule*` plays pattern and arrangement clips of `NoteEvent` and `PadTrigger`. Pattern launch and queue logic unit-tested in `TransportControllerTest`.
- Audio clip timeline playback (`loadAudioClip`/`startAudioClip`/`stopAudioClip`) wired from VMs through JNI to `AudioClipPlayer`.
- WAV writer (`nativeWritePadToWav`, `nativeWriteRecordingToWav`) + offline render (`nativeStartOfflineRender`/`ForTrack`/`stop`) wired through `ProjectRepository.exportMix/exportStems`.
- Preset persistence: Room DB + factory seeder + `PresetBrowser`/`SynthViewModel`/`MainSynthScreen` preset entry points.
- MIDI learn + device scanning wired end-to-end.

### Partially wired / scene-local TransportController bug
- The transport toolbar (`MainScreen.PersistentTransportBar`) talks to the app-scope `transportController`, while modern feature ViewModels often instantiate their own. Until the integrator supplies a DI factory (TODOs at the VM heads), `TIMELINE`, `MIXER`, `SEQUENCER`, `PADS`, `PROJECT` may drive transport on a *different* TransportController than the play/stop in the toolbar.

### Stubs / placeholders still in user-visible UI
- Mixer: automation editor sheet (literal placeholder text), perform-FX UI shows toast even when engine API exists (wiring gap).
- Mixer: insert reorder only swaps UI state — never calls `nativeReorderChannelInserts` (stale TODO in `MixerViewModel.kt:215`).
- Pads: slice start/end and choke group displayed in UI but never reach the engine.
- Timeline: audio-clip waveform is a placeholder; per-track meter TODO.
- Keyboard: aftertouch faked as channel-pressure → mod wheel.
- Project: exportMix/exportStems perform real-time playback + delay() capture rather than pure offline render (works for short arrangement; brittle for long ones).
- Automation events fired from `TransportController` arrive at C++ `Transport.cpp:116` AUTOMATION handler which drops them (`TODO: map paramIndex to SynthAutomation field`). **No sequencer/transport automation actually modulates audio today.**

### Suspended rebrand work
- Native library still named `jujisynth` (CMake target + `System.loadLibrary`). Otherwise the migration `com.jujisynth → com.jujidaw` is complete: package refs, manifest, theme, app_name, namespace, applicationId, and DB name all say JujiDAW.

### Compile assessment
Conceptually the project should build assuming the toolchain matches (NDK with C++20, Oboe + SoundTouch vendored, Android Gradle Plugin supports `cmake` block at build.gradle.kts:54). No leftover references to deleted `com.jujisynth` package in the working tree. The duplicate screens don't break compilation (legacy and new both compile, just only one is reachable per tab right now). Risk: the duplicate `SynthScreen` is dead weight and the modern VM singleton-mismatch might produce user-visible transport desync at runtime even though the app builds.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Recon report includes concrete file:line references for every claim: JNI bridge symmetry counts, MainScreen routing at MainScreen.kt:178, legacy refs at MainSynthScreen.kt lines 145-570, stub TODOs in Transport.cpp:116 / MixerViewModel.kt:215-255 / PadsViewModel.kt:33-319 / TimelineViewModel.kt:23-87 / ProjectViewModel.kt:438-502, lib-name rebrand mismatch SynthEngine.kt:14 + CMakeLists.txt:2,106, transport singleton mismatches with TODO references."
    }
  ],
  "changedFiles": [],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    { "command": "find ... -name '*.kt'/'.cpp'/'.h'", "result": "passed", "summary": "Enumerated all Kotlin + C++ source under app/src/main and app/src/test" },
    { "command": "git status --short", "result": "passed", "summary": "Confirmed staged deletions of com.jujisynth package + untracked new com.jujidaw + cpp files" },
    { "command": "grep -c Java_com_jujidaw_audio_SynthEngine_native JniBridge.cpp vs grep -c 'external fun' SynthEngine.kt", "result": "passed", "summary": "75 JNI exports == 75 Kotlin externals, no orphaned JNI surface" },
    { "command": "grep -rln 'com.jujisynth' app/src", "result": "passed", "summary": "No remaining references to old package name in main/test source" }
  ],
  "validationOutput": [
    "Read-only recon; no edits performed. Findings written to /home/osqy/Desktop/juji-synth/context.md",
    "Architecture, UI nav, model/data, TODO/FIXME/stubs, line counts all covered with file:line citations."
  ],
  "residualRisks": [
    "Singleton TransportController per-VM wiring not verified by actually running the app; flagged as High based on KDoc TODOs and constructor defaults.",
    "Native build (NDK toolchain, ABIs, exact AGP/Compose versions) was not actually executed in this scout run; compile assertion is static only.",
    "Legacy MainSynthScreen still compiles; whether all its dependencies (e.g. unused PresetBrowser/ManualScreen/SettingsScreen modal sheets) remain visually reachable was only statically traced from MainSynthScreen.kt:145-570.",
    "Project export path uses delay(totalMs+500ms) real-time capture rather than truly blocking offline render — correctness for very long arrangements unverified."
  ],
  "noStagedFiles": true,
  "diffSummary": "No diff produced — reconnaissance only. Reported on staged rebrand (com.jujisynth → com.jujidaw) + newly added cpp engine modules and modern MVVM UI screens.",
  "reviewFindings": [
    "blocker: none — no compile-stopping defect identified statically.",
    "high: ui/main/MainScreen.kt:178 routes SYNTH tab to legacy MainSynthScreen instead of new MVVM ui/synth/SynthScreen — duplicate synth UI, the new screen is unreachable.",
    "high: cpp/Transport.cpp:116 AUTOMATION event handler drops events (`(void)synth; TODO`) — automation from sequencer/transport scheduler never reaches the synth audio.",
    "high: Modern ViewModels in ui/{sequencer,mixer,timeline,pads,project,synth} use constructor-default TransportController(48000, 100, 50, ..., NativeSynthEngineScheduler()) rather than JujiDawApp.transportController singleton (TimelineViewModel.kt:23 TODO) — transport state may desync between the persistent toolbar and feature screens.",
    "medium: SynthEngine.kt:14 System.loadLibrary(\"jujisynth\") + CMakeLists.txt:2,106 still name the native lib 'jujisynth' while everything else is branded JujiDAW — harmless but incomplete rebrand.",
    "medium: ui/mixer/MixerViewModel.kt:215-225 has a stale TODO claiming the engine lacks a reorder API, but JNI exposes nativeReorderChannelInserts (JniBridge.cpp:783 → SynthEngine.kt:213) and UI never calls it.",
    "medium: ui/mixer/MixerViewModel.kt:239 togglePerformFx shows a 'TODO: engine integration' toast even though nativeTriggerPerformFx exists (SynthEngine.kt:214) — UI state not pushed to engine.",
    "medium: ui/mixer/MixerViewModel.kt:253-255 addAutomationPoint is a toast-only stub — no automation queue wired to engine.",
    "medium: ui/pads/PadsScreen.kt:568-626 / PadsViewModel.kt:33-319 — slice start/end and choke group UI visible but engine never receives slice/choke commands (no native API exists).",
    "medium: ProjectRepository.kt:219-276,284-322 exportMix/exportStems use TransportController.play + delay(totalMs+500) instead of true synchronous offline render — brittle for long arrangements.",
    "low: ui/timeline/TimelineScreen.kt:46-48 audio-clip waveform is a placeholder; per-track level polling is a TODO (TimelineViewModel.kt:87).",
    "low: ui/keyboard/KeyboardViewModel.kt:370-371 aftertouch maps to mod-wheel placeholder (engine has no per-note aftertouch).",
    "low: ProjectScreen's ProjectViewModel lives at ui/project/ (not project/) — path asymmetry, only ProjectModels and ProjectRepository are under model-package project/.",
    "low: app/build.gradle.kts:22 includes 'x86' ABI filter enlarging APK without clear benefit."
  ],
  "manualNotes": "Recon-only scout; no files were modified. The project appears ~90% wired for the active tabs (Pads/Keys/Seq/Timeline/Mixer/Project via MainScreen + legacy Synth) but has two systemic issues to fix before shipping: (1) modern screens need to share the app-scope TransportController singleton, and (2) the legacy MainSynthScreen ⇄ new SynthScreen split must be resolved so the SYNTH tab actually routes to the rebuilt UI. The new MVVM screens still rely on shared legacy atom composables (RealKnob, SynthPanel, LcdDisplay, OscillatorPanel, EnvelopePanel, FilterPanel, LfoPanel, EffectsPanel) which themselves import AudioEngineManager/SynthEngine directly to set params — panel components bypass the VM and call the engine singleton, mixing one-way and two-way binding inside the new screens."
}
```