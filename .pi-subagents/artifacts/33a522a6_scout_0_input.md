# Task for scout

Read-only recon of an Android Kotlin/Compose DAW app at /home/osqy/Desktop/juji-synth. Map the following 7 user-reported issue areas to EXACT files, classes, functions, and line numbers. Return a compressed structured report (no fixes). For each area give: primary file(s), key symbols, how it currently works, and the likely bug location.

ISSUE AREAS:
1. LANDSCAPE/HORIZONTAL NAVBAR: In horizontal mode the navbar shows only Timeline/Mixer/Synth/Pads/Keys — the rest (Sequencer, etc.) is cut off. Find the navbar/BottomBar/NavigationRail implementation in MainScreen.kt and any orientation/landscape layout handling. Report which screens are in the nav list and how nav is rendered in landscape vs portrait.

2. DUPLICATE RECORDING BUTTONS: There appear to be two record buttons; one is very small next to Play. Find all record/RecordButton/Arm components in TransportController.kt, TransportModel.kt, and the transport UI (MainScreen, HardwareChassis.kt, TimelineScreen). Report each record button, its size, and what it does.

3. ODD UI LABELS/BUTTONS: Labels like "Time 1|1|2 BPM", and tiny buttons "L-", "L+", "i+", "O+", and a "P" button that closes the screen when touched. Find these in LcdDisplay.kt, HardwareChassis.kt, ManualScreen.kt, Components.kt, SequencerScreen/View. Report what each does and whether it is user-facing.

4. IMPORTED AUDIO SOUNDS WRONG/CUT: When importing a sound (e.g. a kick), it plays back sounding completely different / cut. Find the sample/audio import path: AudioConverter.kt, SampleBuffer.cpp/.h, SamplerInstrument.cpp/.h, AudioClipPlayer.cpp, AudioEngineManager.kt, PadsViewModel.kt. Report resampling, format conversion, loop/pitch handling, and where sample data could be corrupted/truncated.

5. PADS + TIMELINE NO SOUND: Placing something from a pad into the timeline and hitting play produces no sound. Find how pads trigger timeline clips: ClipModel.kt, PatternModel.kt, TimelineViewModel.kt, TransportController.kt, and the C++ Sequencer/Transport. Report whether pad clips are wired to the sampler/transport playback.

6. SEQUENCER NO SOUND: Adding pad content to the sequencer produces no sound. Map SequencerViewModel.kt, SequencerScreen.kt, SequencerView.kt, and C++ Sequencer.h/cpp. Report whether the sequencer actually schedules/plays pad sounds through the engine.

7. SYNTH-TO-PAD WORKFLOW MISSING: User wants each pad to hold EITHER an imported sample OR a synth (with its own preset), independent per pad, and the keyboard to play the selected pad's content. Find PadsViewModel.kt, PadsScreen.kt, SynthScreen.kt, SynthViewModel.kt, SynthState.kt, SynthEngine.kt, PresetBrowser.kt. Report current per-pad model (sample only?), whether a pad can hold a synth/preset, and how keys route to pads.

8. MIXER SLIDER CLUNKY: MixerScreen.kt / MixerViewModel.kt / RealKnob.kt — report the slider/fader implementation, gesture handling, and where it feels unsmooth (pointer input, recomposition, step quantization).

Also report: the master phone Navbar in MainScreen.kt, project screen list, and any landscape-specific code paths.

Output format: one section per issue area with file:symbol:line refs and a 1-2 sentence "current behavior" + "suspected bug". Keep it tight.

---
**Output:**
Write your findings to exactly this path: /home/osqy/Desktop/juji-synth/.pi-subagents/artifacts/outputs/33a522a6/context.md
This path is authoritative for this run.
Ignore any other output filename or output path mentioned elsewhere, including output destinations in the base agent prompt, system prompt, or task instructions.

## Acceptance Contract
Acceptance level: attested
Completion is not accepted from prose alone. End with a structured acceptance report.

Criteria:
- criterion-1: Return concrete findings with file paths and severity when applicable

Required evidence: review-findings, residual-risks

Finish with a fenced JSON block tagged `acceptance-report` in this shape:
Use empty arrays when no items apply; array fields contain strings unless object entries are shown.
```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "specific proof"
    }
  ],
  "changedFiles": [
    "src/file.ts"
  ],
  "testsAddedOrUpdated": [
    "test/file.test.ts"
  ],
  "commandsRun": [
    {
      "command": "command",
      "result": "passed",
      "summary": "short result"
    }
  ],
  "validationOutput": [
    "validation output or concise summary"
  ],
  "residualRisks": [
    "none"
  ],
  "noStagedFiles": true,
  "diffSummary": "short description of the diff",
  "reviewFindings": [
    "blocker: file.ts:12 - issue found, or no blockers"
  ],
  "manualNotes": "anything else the parent should know"
}
```