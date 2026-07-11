# Task for sdd-proposal

Create SDD change 'portable-daw-core-rework' (OpenSpec). Write proposal.md under openspec/changes/portable-daw-core-rework/. Also create openspec/changes/portable-daw-core-rework/.openspec.yaml with schema: spec-driven and created: 2026-07-07.

GOAL: Rework Juji-Synth (Android Kotlin/Compose + C++ audio engine) into a coherent portable DAW where (a) imported samples play back correctly, (b) pads drive every sound source via the transport/sequencer, (c) a pad can hold EITHER an imported sample OR a full independent synth with its own preset, and (d) the phone UI (especially landscape) and transport/mixer controls are consistent and legible.

CONFIRMED PRODUCT DECISIONS (from the user, non-negotiable):
1. Multi-timbral engine: refactor the C++ AudioEngine to host one SynthInstrument per pad/channel, each with its own preset/state. A pad independently holds a sample OR a synth. Keyboard plays the SELECTED pad's loaded content.
2. FL-style sequencer: sequencer rows map directly to pads; a step triggers THAT pad's loaded content (sample or synth). Timeline arrangement clips likewise trigger pads via the existing-unused PAD_TRIGGER ScheduledEvent path. Unify sequencer + timeline around pad-triggered playback.
3. Full consistent UI redesign: ONE global TransportBar (remove per-screen duplicate transport strips), landscape nav as a proper scrollable rail with visible affordance + overflow (no invisible scroll), replace cryptic L-/I-/O+/P buttons with labeled/iconographic controls, redesign mixer fader gesture (unified tap+drag w/ hysteresis, smoothing, debounced level polling).
4. Auto planning + feature-sliced tasks; artifacts in OpenSpec (openspec/).

DETAILED SOURCE-LEVEL CONTEXT (from a scout recon — treat as the explore input; you do NOT need to re-explore broadly, but verify key facts by reading the named files):

Confirmed high-severity bugs:
- AudioConverter.kt writeWavFile writes the WAV header BEFORE downmixing stereo->mono, leaving header (channels/byteRate/blockAlign/dataSize) inconsistent with the mono bytes written -> imported samples play at wrong pitch/tempo, truncated to ~half. app/src/main/java/com/jujidaw/audio/AudioConverter.kt.
- Pattern notes always scheduled on pattern.trackIndex (=0) -> routes to Channel 0 (SynthInstrument), never to the sampler/pads. TransportController.schedulePatternNotes ignores clip.trackIndex and NoteEvent.trackIndex. PAD_TRIGGER ScheduledEvent type exists in C++ (JniBridge.cpp / Transport.cpp firePendingEvents -> sampler->triggerPad) but Kotlin TransportController only calls scheduleNoteOn/scheduleNoteOff. Files: app/src/main/java/com/jujidaw/engine/TransportController.kt, app/src/main/cpp/Transport.cpp, app/src/main/cpp/AudioEngine.cpp (Channel0=Synth, Channel1=Sampler).
- Sequencer: SequencerViewModel toggles steps with note=60, exports Pattern(trackIndex=0); same root cause as above -> no pad sound. app/src/main/java/com/jujidaw/ui/sequencer/SequencerViewModel.kt.
- Synth-per-pad is structurally impossible: single SynthInstrument instance on channel 0; PadConfig.synthMode in SamplerInstrument.cpp is a stub routing to that one synth; SynthViewModel.trackStates mutates one synth. KeyboardViewModel has no 'play selected pad content' target. Files: app/src/main/cpp/SamplerInstrument.cpp/.h, app/src/main/java/com/jujidaw/ui/synth/SynthViewModel.kt, app/src/main/java/com/jujidaw/ui/keyboard/KeyboardViewModel.kt.

Confirmed UX issues:
- Landscape nav: MainScreen.kt MainScreen landscape branch renders NavigationRail in 80dp Column + verticalScroll with no visible scroll affordance; PersistentTransportBar(44dp) prepended in same column steals height -> SEQUENCER/PROJECT perceived as cut off. 7 tabs: TIMELINE, MIXER, SYNTH, PADS, KEYBOARD, SEQUENCER, PROJECT.
- Duplicate record buttons: MainScreen.PersistentTransportBar (32dp record, always on) duplicates TimelineScreen.TransportStrip record (40dp) and SequencerScreen.SequencerTopBar record (40dp).
- Cryptic labels: TimelineScreen tiny L-/L+/I-/O+ (20dp, 7sp) punch/loop markers; 'P' punch toggle; 'Time 1|1|2 BPM' concatenation from two Texts + weight spacer collapsing.
- Mixer fader clunky: MixerScreen.VerticalFader runs parallel detectTapGestures + detectDragGestures (tap/drag contention, no hysteresis), per-pixel state mutation + JNI setChannelFader per delta, 200ms level polling recompositions.

KEY FILES (for the spec/design agents downstream — include a 'Affected files' map in the proposal): app/src/main/java/com/jujidaw/{audio/{AudioConverter.kt,SynthEngine.kt,AudioEngineManager.kt},engine/{TransportController.kt,SynthEngineScheduler.kt},ui/main/MainScreen.kt,ui/timeline/{TimelineScreen.kt,TimelineViewModel.kt},ui/sequencer/{SequencerScreen.kt,SequencerViewModel.kt},ui/pads/{PadsScreen.kt,PadsViewModel.kt},ui/synth/{SynthScreen.kt,SynthViewModel.kt},ui/keyboard/{KeyboardScreen.kt,KeyboardViewModel.kt},ui/mixer/{MixerScreen.kt,MixerViewModel.kt},ui/{HardwareChassis.kt,Components.kt,LcdDisplay.kt,RealKnob.kt},model/{SynthState.kt,ClipModel.kt,PatternModel.kt,TransportModel.kt,Preset.kt},data/PresetDatabase.kt} and C++ app/src/main/cpp/{AudioEngine.cpp,SynthEngine.h/.cpp,SamplerInstrument.cpp/.h,Transport.cpp,Sequencer.h/.cpp,SamplerVoice.cpp,ScheduledEvent.h,JniBridge.cpp}.

COORDINATION WITH EXISTING CHANGES: Two in-flight OpenSpec changes already exist:
- openspec/changes/synth-ui-audio-polish (specs: sequencer-engine-sync, knob-touch-isolation, sequencer-recording-looping, piano-roll-daw, lfo-animation, osc-waveform-preview, filter-response-curve). OVERLAPS this change on sequencer-engine-sync and knob/mixer-touch. STATE in the proposal that this new change SUPERSEDES the 'sequencer-engine-sync' and touch-isolation/mixer-fader aspects of synth-ui-audio-polish. Do NOT delete those specs (archiving is a separate step); just note the supersedence in Non-goals / Scope.
- openspec/changes/juji-synth-v11 (sequencer spec, modulation-matrix, midi-connectivity, help-system, chorus). Note overlap on the sequencer spec and state this change takes the authoritative approach (rows=pads).

NON-GOALS (state explicitly): MIDI learn polish, modulation matrix, chorus/reverb FX tuning, LFO animation cosmetics, filter-response curve plotting, osc waveform preview styling — these belong to the in-flight changes above and are out of scope here except where a touch/fader fix unavoidably touches the same gesture code.

Deliver proposal.md with: Why, What Changes (grouped into slices: A-AudioImport, B-TransportPadRouting, C-SequencerRowsPads, D-MultiTimbralSynth, E-UIConsistencyTransport, F-LandscapeNav, G-MixerFader), Affected files map, Scope/Non-goals, Impact/implications, open questions (if any), relation to in-flight changes. Keep under ~700 words of prose but the Affected-files map may be a table.

## Acceptance Contract
Acceptance level: checked
Completion is not accepted from prose alone. End with a structured acceptance report.

Criteria:
- criterion-1: Implement the requested change without widening scope

Required evidence: changed-files, tests-added, commands-run, residual-risks, no-staged-files

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