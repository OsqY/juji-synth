# Juji Synth — Scout Report: 8 Issue Areas

All paths relative to `/home/osqy/Desktop/juji-synth/`.
Engine owners: Channel 0 → `SynthInstrument` (subtractive synth); Channel 1 → `SamplerInstrument` (pads/imports). This 0/1 split is the root of issues 5, 6, and 7.

---

## 1. LANDSCAPE/HORIZONTAL NAVBAR — items cut off

### Files / symbols

- `app/src/main/java/com/jujidaw/ui/main/MainScreen.kt`
  - `MainTab` enum (lines ~46-55) — entries: `TIMELINE, MIXER, SYNTH, PADS, KEYBOARD, SEQUENCER, PROJECT` (7 items).
  - `MainScreen` composable (lines ~57-115) — landscape branch at lines ~88-114.
  - Landscape rendering: `Column { PersistentTransportBar(...); tabs.forEach { NavigationRailItem(...) } }` inside a fixed `width(80.dp)` column that uses `verticalScroll(rememberScrollState())`.

### Current behavior

Same `MainTab.entries` array drives both portrait `NavigationBar` (bottom) and landscape `NavigationRail`. The rail lives inside a `Column` with `verticalScroll`, so technically all 7 items are reachable by scrolling — but on most phones the column is too short for the scroll affordance to be visible, so the user perceives items after `KEYS` (SEQUENCER, PROJECT) as "cut off".

### Suspected bug

The landscape rail has no visible scrollbar or peek indicators, and `PersistentTransportBar` (44dp tall) pre-pends in the same scrolled column, stealing height. So at the top of the rail users see transport + first ~5 tabs; the rest require invisible scrolling. No code truncates the list — UX scoping/feedback problem, not a data bug.

---

## 2. DUPLICATE RECORDING BUTTONS

### Files / symbols

- `app/src/main/java/com/jujidaw/ui/main/MainScreen.kt`
  - `PersistentTransportBar` (lines ~149-216) — global transport strip always visible on top of nav.
  - `TransportMiniButton` Play(▶/■), Record(●, 32dp), Reset(↺).
- `app/src/main/java/com/jujidaw/ui/timeline/TimelineScreen.kt`
  - `TransportStrip` (lines ~250-360) — a second transport strip rendered inside the Timeline screen with its own Play(40dp) and Record("●", 40dp) buttons via `TransportButton`.
- `app/src/main/java/com/jujidaw/ui/sequencer/SequencerScreen.kt`
  - `SequencerTopBar` (lines ~91-180) — yet another Play(▶/■, 40dp) and Record("●", 40dp) button.
- `app/src/main/java/com/jujidaw/model/TransportModel.kt`
  - `TransportState` (records `recording: Boolean`).
- `app/src/main/java/com/jujidaw/engine/TransportController.kt`
  - `TransportController.setRecording(recording)` — single source of truth.

### Current behavior

1. **Small record button**: `MainScreen.PersistentTransportBar.TransportMiniButton(label="●", 32dp)` — always visible at the top of the nav bar (`TransportController.setRecording` toggle).
2. **Bigger record button**: `TimelineScreen.TransportStrip.TransportButton("●", 40dp)` — appears when Timeline tab is open; calls `TimelineViewModel.toggleRecording()` which also calls `TransportController.setRecording(...)`.

### Suspected bug

Two redundant record buttons exist; the small one in `MainScreen.PersistentTransportBar` is always visible and overlaps the on-screen transport strip inside Timeline/Sequencer, causing the "two record buttons" confusion. Both drive the same engine state; the small one next to Play (the 32dp ● in the master transport bar) is the redundant/smaller one.

---

## 3. ODD UI LABELS/BUTTONS ("Time 1|1|2 BPM", "L-", "L+", "I-", "O+", "P")

### Files / symbols

- `app/src/main/java/com/jujidaw/ui/main/MainScreen.kt:188-205` — `PersistentTransportBar` time LCD:

  ```
  Text("${position.bar + 1}|${position.beat + 1}|${step + 1}", ..., color=KnobAmber)
  Spacer(Modifier.weight(1f))
  Text("%.1f".format(tempoBpm) + " BPM", color=KnobGreen)
  ```

- `app/src/main/java/com/jujidaw/ui/timeline/TimelineScreen.kt:285-327` — `TinyButton` (20dp, 7sp):
  - `TinyButton("L-", onLoopStart)` — sets loop START to playhead.
  - `TinyButton("L+", onLoopEnd)` — sets loop END to playhead.
  - `TinyButton("I-", onPunchIn)` — sets punch-IN to playhead.
  - `TinyButton("O+", onPunchOut)` — sets punch-OUT to playhead.
  - `TransportButton("P", ..., onClick=onTogglePunch, 36dp)` — toggles punch on/off.
- `app/src/main/java/com/jujidaw/ui/SequencerView.kt` (legacy) — uses plain `SynthKnob`/box for transport, has 28dp Play + 28dp Reset buttons.
- `app/src/main/java/com/jujidaw/ui/LcdDisplay.kt` — generic 28dp display widget, no labels of its own.
- `app/src/main/java/com/jujidaw/ui/HardwareChassis.kt` — pure visual chassis wrapper; no labels.
- `app/src/main/java/com/jujidaw/ui/ManualScreen.kt` — static help screen; no dynamic labels.

### Current behavior

- The `Time 1|1|2 BPM` rendering is actually two separate `Text` elements in `PersistentTransportBar` (Time → Spacer → BPM). They appear concatenated on narrow screens because the `Spacer(Modifier.weight(1f))` only fills empty space; if row width is short both texts are adjacent. Could feel like "1|1|2 BPM" as one label.
- "L-/L+/I-/O+" are 20dp micro controls with 7sp font — barely legible. "I-" and "O+" are the punch markers; asymmetric labels ("I-" / "O+") rather than "I+ / O-" are confusing.
- The "P" button in `TimelineScreen.TransportStrip` is a Punch toggle (`onTogglePunch`). It does **not** close the screen — but visually it's a one-letter 36dp button inline with Play/Record, so the user interpreted tap-and-nothing-happens as "closes the screen".

### Suspected bug

UX/labeling issues:

- Inconsistent label vocabulary ("L-", "L+", "I-", "O+") and asymmetric text on punch controls.
- "P" button has no tooltip/help and no state explanation beyond a color change when `punchEnabled`.
- Time/BPM concatenation caused by `Spacer(Modifier.weight(1f))` collapsing on narrow widths.
- All are user-facing but undefined.

---

## 4. IMPORTED AUDIO SOUNDS WRONG/CUT — HIGH SEVERITY (concrete bug found)

### Files / symbols

- `app/src/main/java/com/jujidaw/audio/AudioConverter.kt`
  - `AudioConverter.convertToWav(context, uri, targetPath)` — decodes via `MediaCodec` to PCM bytes.
  - `AudioConverter.writeWavFile(pcmData, sampleRate, channels, path)` — writes the WAV. **Bug here (lines ~84-118).**
- `app/src/main/cpp/SampleBuffer.cpp`
  - `SampleBuffer::loadFromWav` (reads WAV header and PCM; uses `channels_` and `formatSampleRate` from the `fmt` chunk).
- `app/src/main/cpp/SamplerInstrument.cpp` — `noteOn` reads pad buffer; `SamplerVoice::start()` resamples.
- `app/src/main/cpp/SamplerVoice.cpp:14-45` — `start()` sets `playbackSpeed = getSpeedForSemitones(pitch) * (buf->getSampleRate() / sampleRate)` — resampling is applied at playback time.
- `app/src/main/java/com/jujidaw/ui/pads/PadsViewModel.kt:importSample` — pipeline: `pickAudio → AudioConverter.convertToWav → SynthEngine.loadSampleToPad` (native).

### Current behavior / Bug

`AudioConverter.writeWavFile` writes a RIFF/WAVE header that lies about the channel count and data size when the input is stereo:

```kotlin
val header = ByteBuffer.allocate(44).apply {
    ...
    putShort(channels.toShort())          // ORIGINAL channel count (e.g. 2)
    putInt(byteRate)                       // byteRate = sampleRate * channels * 2
    putShort(blockAlign.toShort())          // = channels * 2
    ...
    putInt(dataSize)                       // pcmData.size = ORIGINAL stereo byte count
}
out.write(header.array())
if (channels == 1) {
    out.write(pcmData)                      // OK
} else {
    val downmixed = ByteArray(dataSize / channels)  // half the size declared in header
    for (f in 0 until frameCount) { ... average channels ... }
    out.write(downmixed)                  // WRITE MONO data
}
```

Then C++ `SampleBuffer::loadFromWav` reads: `channels_ = formatChannels (=2)`, `chunkSize = dataSize (=original stereo size)`, attempts to read ~2× more bytes than actually exist on disk. `file.gcount()` returns the smaller amount; sample count is then computed as `read / (sizeof(int16_t) * channels_)` = half the actual mono samples, and stereo interleaving is misinterpreted over mono data.

Effect: imported file plays at wrong pitch/tempo, often truncated to ~half its length. Importing a stereo kick (common!) is the exact pattern that triggers this.

A secondary observation: `SampleBuffer::loadFromWav` consumes `formatSampleRate` as-is without resampling at load time (correct — `SamplerVoice` does resample at playback). So the only corruption vector is the WAV header mismatch.

### Suspected bug (confirmed)

`AudioConverter.writeWavFile` writes the header before downmixing, then writes the data, leaving the WAV header's `channels`, `byteRate`, `blockAlign`, and `data` size fields inconsistent with the bytes that actually follow. Mono-only imports work; stereo (the common case) produces corrupted/truncated audio.

---

## 5. PADS + TIMELINE NO SOUND — HIGH SEVERITY (wiring bug)

### Files / symbols

- `app/src/main/java/com/jujidaw/model/ClipModel.kt`
  - `PatternClip(trackIndex, patternId, transpose, durationTicks, ...)`.
- `app/src/main/java/com/jujidaw/model/PatternModel.kt`
  - `Pattern.trackIndex: Int = 0`  — default 0.
  - `NoteEvent(trackIndex: Int = 0)` — per-note mixer-channel target.
- `app/src/main/java/com/jujidaw/ui/timeline/TimelineViewModel.kt`
  - `addPatternClip(trackIndex, startTick, patternId, ...)` — creates clip with trackIndex of click location.
  - Writes back: `transportController.loadArrangement(newArr)` — but pattern data still routed via `pattern.trackIndex`.
- `app/src/main/java/com/jujidaw/engine/TransportController.kt`
  - `schedulePatternClip(clip, ...)` calls `schedulePatternNotes(..., trackIndex = clip.trackIndex, ...)`.
  - `schedulePatternNotes(... trackIndex: Int = pattern.trackIndex, ...)` — **always uses pattern.trackIndex, ignores clip.trackIndex and NoteEvent.trackIndex**.
  - Calls `scheduleNoteOn(track, note, velocity)` → `scheduler.scheduleNoteOn(track, note, velocity)`.
- `app/src/main/java/com/jujidaw/audio/SynthEngine.kt:nativeScheduleNoteOn(trackIndex, note, velocity, targetSample)`.
- `app/src/main/cpp/JniBridge.cpp:611-619` — `nativeScheduleNoteOn` pushes `ScheduledEvent::makeNoteOn(trackIndex, ...)`.
- `app/src/main/cpp/Transport.cpp:firePendingEvents`:

  ```
  case NOTE_ON: { instr = engine.getChannel(track).getInstrument(); instr->noteOn(note, velocity); }
  ```

- `app/src/main/cpp/AudioEngine.cpp:init` — Channel 0 hosts SynthInstrument; Channel 1 hosts SamplerInstrument. Only channel 1 routes to pads.

### Current behavior

- Pattern notes always scheduled on `pattern.trackIndex` (= 0 by default; pattern created by UI never overrides this).
- Therefore arrangement playback sends NoteOn to **Channel 0 (Synth)**, NOT the sampler channel.
- Even if a clip is placed on Track 1 (Sampler), the C++ routes the note to `sampler.noteOn(midiNote=60 for default track notes)` — `SamplerInstrument::padIndexFromNote(60)` returns `bank*16 + (60 % 16) = 12`, i.e. pad 12 of bank A. Unless the user loaded a sample on pad 12, nothing plays.
- The user's mental model ("I placed my pad on the timeline") doesn't exist as data — there is no `PadClip` or `triggerPadAtTick`. Pattern clips are pure MIDI patterns with `note = 60` (C4), not pad-trigger events.

### Suspected bug (structural)

There is **no path** from timeline clips to per-pad sampler triggers. `ScheduledEvent` has a `PAD_TRIGGER` type (defined in JniBridge.cpp:631 and Transport.cpp:firePendingEvents case PAD_TRIGGER → `sampler->triggerPad`), but the Kotlin `TransportController` only calls `scheduleNoteOn` / `scheduleNoteOff`, never `schedulePadTrigger`. The `PadConfig.synthMode` and `triggerPad` APIs exist on the engine but are never invoked by the arrangement/transport scheduler.

---

## 6. SEQUENCER NO SOUND — HIGH SEVERITY (same root cause as #5)

### Files / symbols

- `app/src/main/java/com/jujidaw/ui/sequencer/SequencerViewModel.kt`
  - `toggleStep(track, step)` — default note per track = `StepTrack.defaultNote = 60` (C4). Track index retained per NoteEvent, but pattern's `trackIndex = 0` overrides it on export.
  - `syncActivePatternToTransport()` — exports `Pattern(id, trackIndex=0, notes=[NoteEvent(note=60, trackIndex=trackIndex, ...)])` → `transportController.loadPatterns(allPatterns)`.
  - `togglePlay()` — `transportController.queuePattern(id); transportController.play()`.
- `app/src/main/java/com/jujidaw/ui/sequencer/SequencerScreen.kt`
  - `StepCellBox` — toggling step just calls `onStepToggle(track, step)` which writes a step with `note = track.defaultNote (= 60)`.
- `app/src/main/java/com/jujidaw/ui/SequencerView.kt` (legacy) — different code path: uses `SynthEngine.setSequencerSteps(...)` + internal C++ `Sequencer::process` whose `onNoteEvent` callback goes to `SynthEngine::getInstance().getAudioEngine().noteOn` = `synthInstrument_->noteOn` (channel 0 synth).
- `app/src/main/cpp/Sequencer.h/.cpp` — internal C++ step sequencer, callback `onNoteEvent`. Disabled during DAW transport playback (see `TransportController.play`: `scheduler.setSequencerEnabled(false)`).

### Current behavior

- Modern `SequencerScreen` exports steps as a `Pattern`, hands it to `TransportController`, and on play → `schedulePatternNotes` → `scheduleNoteOn(trackIndex = pattern.trackIndex = 0, note=60)`.
- The note goes to Channel 0 (Synth). Unless the synth has audio producing oscillators configured, no sound.
- The Sequencer grid never references pad indices — there is no UI mapping from "track row" → "pad" or "sampler channel". Each track row just plays whatever instrument is on that channel (Track 1 → Sampler, requires user to put steps on row 2; but the pattern is exported with trackIndex=0 anyway).
- Pattern's `trackIndex = 0` overrides per-note trackIndex in `TransportController.schedulePatternNotes` (bug #5's root code).

### Suspected bug

1. Sequencer steps ignore pads entirely — no pad/sampler integration per step.
2. Per-note `trackIndex` is silently discarded; `schedulePatternNotes` uses `pattern.trackIndex` (= 0). Even if a user manually placed steps on row T2 → they still play on the synth channel.
3. Legacy `SequencerView` (if anyone uses it) talks only to SynthEngine.noteOn → synth.

---

## 7. SYNTH-TO-PAD WORKFLOW MISSING — STRUCTURAL GAP

### Files / symbols

- `app/src/main/java/com/jujidaw/model/SynthState.kt`
  - `SynthState` is one global synth state. No per-pad synth state model.
- `app/src/main/java/com/jujidaw/ui/synth/SynthViewModel.kt`
  - `trackStates: MutableMap<Int, SynthState> = mutableMapOf(0 to ...)` — NOTE comment "Per-track SynthState map for multi-timbral routing" — but **only channel 0 hosts SynthInstrument (a single instance in AudioEngine)**, so changing tracks only mutates the one synth's parameters; cannot produce multiple timbres.
- `app/src/main/java/com/jujidaw/audio/SynthEngine.kt` — JNI bridge only exposes ONE SynthInstrument on channel 0.
- `app/src/main/java/com/jujidaw/ui/pads/PadsViewModel.kt`
  - `PadParams(synthMode: Boolean = false, synthRootNote: Int = 60, ...)` — per-pad config exists.
- `app/src/main/java/com/jujidaw/ui/pads/PadsScreen.kt`
  - `PadEditSheet` — has "Synth" toggle and "Root Note" stepper. When synthMode=true, sampler pads PLC non-sample controls.
- `app/src/main/cpp/SamplerInstrument.h`
  - `PadConfig { bool synthMode = false; int synthRootNote = 60; ... };`
  - `SamplerInstrument::setSynthTarget(SynthInstrument* synth)` — one global synth pointer.
- `app/src/main/cpp/SamplerInstrument.cpp:noteOn`:

  ```
  if (pad.synthMode) {
      if (synthTarget_) {
          int targetNote = pad.synthRootNote + (midiNote - (activeBank_ * 16 + (padIndex % 16)));
          synthTarget_->noteOn(targetNote, velocity);
      }
      return;
  }
  ```

- `app/src/main/java/com/jujidaw/ui/synth/SynthScreen.kt`
  - `SynthScreen` lets users pick track (1-16) — but `SynthViewModel.trackStates` only changes channel 0's instrument.
- `app/src/main/java/com/jujidaw/ui/keyboard/KeyboardViewModel.kt`
  - `KeyboardTarget`: `Synth | SamplerA | SamplerB | Track(index)`.
  - `sendNoteOn(...)` — when target=Synth calls `SynthEngine.noteOn` (channel 0 only); when SamplerA/B calls `SynthEngine.triggerPad(note % 16, velocity)`.
- `app/src/main/java/com/jujidaw/ui/PresetBrowser.kt` — single preset DB; no per-pad preset concept.

### Current behavior

- A pad CAN hold either a sample (default, `synthMode=false`) or be a synth trigger (`synthMode=true`).
- BUT all synth-mode pads share the **single** `SynthInstrument` on channel 0. There is no per-pad synth voice or per-pad preset.
- `SynthScreen.PresetBrowser` works only against the channel-0 synth state — there's no concept of "Pad N's own synth preset".
- Keyboard targets: each pad per-pad synth is impossible — pressing a key maps to `triggerPad(note % 16, velocity)` which then routes through the one global SynthInstrument with the current global preset. So "keyboard plays the selected pad's content" works only if the selected pad has a sample buffer, not if the pad has a synth preset.

### Suspected bug / structural gap

- `PadConfig.synthMode` is a stub — there is exactly one SynthInstrument instance in the C++ engine. "Per-pad synth with own preset" is not implementable without refactoring AudioEngine to host N synth instruments (or a multi-timbral synth).
- `KeyboardViewModel` doesn't have a "play selected pad's content" target; it goes by global `triggerPad`.

---

## 8. MIXER SLIDER CLUNKY

### Files / symbols

- `app/src/main/java/com/jujidaw/ui/mixer/MixerScreen.kt`
  - `VerticalFader` (lines ~413-518) — the fader the user is dragging.
  - Two `pointerInput` blocks: `detectTapGestures { onTap=jump, onLongPress=MIDI learn }` + `detectDragGestures { change, dragAmount -> ... }`.
  - Fader value range `-60f..12f` (dB).
- `app/src/main/java/com/jujidaw/ui/mixer/MixerViewModel.kt:setChannelFader` — calls `SynthEngine.setChannelFader(trackIndex, clamped)` then updates UI state; `recordAutomationIfArmed` runs on every tick.
- `app/src/main/java/com/jujidaw/ui/RealKnob.kt` — used elsewhere; knob drag-increment is `-dragAmount.y / 200f` with 8dp touch-slop.

### Current behavior — clunkiness sources

1. Two separate `pointerInput` blocks (`detectTapGestures` for tap/long-press + `detectDragGestures`) run in parallel; Compose may sometimes route a touch as a tap that should have started a drag.
2. `onTap` jumps the fader to the tap Y directly — no hysteresis between tap-jump and drag-continue. User attempting a small drag sometimes triggers a tap-jump first.
3. Drag sensitivity: one screen-pixel = `range / size.height` of dB. The full range is 72 dB; on a typical 160dp fader that's ~0.45 dB per pixel — no quantization/smoothing.
4. Every drag delta calls both `SynthEngine.setChannelFader` (JNI → mixer UI thread → audio mixer command) AND `updateChannel { it.copy(faderDb = clamped) }` — both branches cause recomposition per drag pixel. The CANVAS-based fader redraws the entire `VerticalFader` body on every value change. The 16-strip row plus master strip all live in a horizontally-scrolled `Row` — recomposition churns.
5. `MixerViewModel.startLevelPolling` polls all 16 channel levels every 200ms via JNI, causing additional recompositions overlaid on drag.
6. No velocity curve: linear 1:1 mapping. No sub-step quantization or dB-step snapping.

### Suspected bug / UX

- Parallel `detectTapGestures` + `detectDragGestures` cause tap/drag contention.
- High-frequency recomposition from per-delta state mutations and JNI mixer-command pushes.
- No quantization or smoothing; linear response feels mechanical.
- The `RealKnob` knob UI uses an 8dp touch-slop, but `VerticalFader` does not — different drag thresholds from the rest of the app.

---

## BONUS: MASTER PHONE NAVBAR / LANDSCAPE OBSERVATIONS

- `MainScreen.PersistentTransportBar` is the "master phone navbar" — drawn at top of both portrait `NavigationBar` wrapper and landscape `NavigationRail` column. In landscape it stacks above the rail items, consuming ~44dp and contributing to "tabs below fold" perception (issue #1).
- `ProjectScreen.kt` is a normal tab; its `ProjectList` uses scrollable `LazyColumn` of `ProjectInfo`. No landscape-specific layout. Toolbar wraps on narrow screens because the toolbar TransportStrip-like(`ProjectToolbar`, not detailed here) packs transport + project actions in a single Row.
- Only **landscape-specific** code path is in `MainScreen.kt:88-114`: switches from `NavigationBar` (bottom) to `NavigationRail` in a 80dp-wide vertically-scrolled Column. No other screen has its own landscape branch — they all rely on Column/Row that may not fit well in landscape.

---

# Severity Summary

| # | Issue | Severity | Cause |
| - | ----- | -------- | ----- |
| 1 | Landscape navbar cut off | Medium (UX) | Vertical scroll without affordance; transport eats column height |
| 2 | Duplicate record buttons | Low (UX cleanup) | Multiple transport strips coexist |
| 3 | Odd labels | Low (labels) | Confusing micro buttons & asymmetric text; no tooltips |
| 4 | Imported audio wrong/cut | **High** | `AudioConverter.writeWavFile` writes stale stereo header before downmixing to mono |
| 5 | Pads→Timeline no sound | **High** | Pattern notes routed to channel 0 (synth) — no pad-trigger path in TransportController |
| 6 | Sequencer no sound | **High** | Same root cause as #5; steps export plain MIDI notes on channel 0 |
| 7 | Synth-to-pad missing | **High (structural)** | Single SynthInstrument instance in engine; `synthMode` is a stub |
| 8 | Mixer slider clunky | Medium (UX/perf) | Tap/drag contention + per-pixel recomposition + JNI thrash |