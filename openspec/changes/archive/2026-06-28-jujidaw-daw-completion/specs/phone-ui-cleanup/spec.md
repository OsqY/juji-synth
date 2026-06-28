# phone-ui-cleanup Specification

## Purpose

Remove the legacy synth-only UI layer (`MainSynthScreen`, `HardwareChassis`, `PatchBayView`, legacy keyboard/sequencer/piano-roll/oscilloscope/waveform/synth-panel/LCD), reroute the SYNTH tab to the new phone-first `SynthScreen`, reorder navigation tabs for an arrangement-first workflow, and provide a quick-start template system for the first-run empty state.

## ADDED Requirements

### Requirement: Legacy files removed
The following files SHALL be deleted from the source tree. No code outside the deleted files SHALL reference them after removal.

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

#### Scenario: No compilation errors after deletion
- **WHEN** the deleted files are removed and the project is rebuilt
- **THEN** `./gradlew assembleDebug` completes with BUILD SUCCESSFUL
- **AND** no import of `com.jujidaw.ui.MainSynthScreen`, `.HardwareChassis`, `.PatchBayView`, `.KeyboardView`, `.SequencerView`, `.PianoRollView`, `.OscilloscopeView`, `.OscWaveformView`, `.SynthPanel`, or `.LcdDisplay` exists in the remaining codebase

### Requirement: SYNTH tab routes to new SynthScreen
`MainTab.SYNTH` in `MainScreen.kt` SHALL route to `ui/synth/SynthScreen` instead of `ui/MainSynthScreen`.

#### Scenario: SYNTH tab opens new SynthScreen
- **WHEN** the user taps the SYNTH tab
- **THEN** `ui/synth/SynthScreen` is rendered
- **AND** the new screen has full parameter control (oscillators, filter, envelopes, LFO, effects, master)
- **AND** presets can be loaded from `PresetBrowser`

### Requirement: Tab order is arrangement-first
The `MainTab` enum in `MainScreen.kt` SHALL be ordered as:
1. `TIMELINE` (default home tab)
2. `MIXER`
3. `SYNTH`
4. `PADS`
5. `KEYBOARD`
6. `SEQUENCER`
7. `PROJECT`

#### Scenario: Tab order visible in navigation bar
- **WHEN** the app is in portrait mode
- **THEN** the bottom `NavigationBar` shows tabs in the order above
- **WHEN** the app is in landscape mode
- **THEN** the `NavigationRail` shows tabs in the same order
- **AND** the TIMELINE icon is the first/leftmost item

### Requirement: TIMELINE is the default home tab
On app launch, the selected tab SHALL default to `TIMELINE`.

#### Scenario: Default tab on launch
- **WHEN** the app starts
- **THEN** the TIMELINE tab is selected
- **AND** the `TimelineScreen` is the visible content

### Requirement: First-launch empty state shows quick-start templates
When no recent projects are available (first launch after install) or the project list is empty, TIMELINE SHALL display a set of quick-start template cards. Each template, when tapped, pre-populates a project with tempo, a pattern, and basic mixer state.

#### Scenario: Empty state card with templates
- **WHEN** the app launches for the first time (no saved projects)
- **THEN** the TIMELINE screen shows a "Quick start" section with at least 3 template cards:
  - **"4-on-Floor"**: 128 BPM, one pattern (kick-hat-snare-hat 16-step) with a simple bass line
  - **"Trap Loop"**: 140 BPM, one pattern with 808-style kick, hi-hat rolls, and a synth pad
  - **"Blank"**: 120 BPM, empty arrangement, one empty pattern, one track
- **AND** tapping a template creates and loads a new project with those settings

#### Scenario: Templates only on empty
- **WHEN** the user has one or more saved projects
- **THEN** the template cards are NOT shown
- **AND** the TIMELINE screen shows the saved project list instead

### Requirement: Persistent transport bar unchanged
The persistent transport bar in `MainScreen` (play/stop/record buttons, BPM display, playhead position) SHALL remain functional across all tabs after the UI cleanup.

#### Scenario: Transport works in all tabs
- **WHEN** the user is on any tab (TIMELINE, MIXER, SYNTH, etc.)
- **THEN** the transport bar shows the current playhead position
- **AND** play/stop/record buttons affect the single `TransportController` singleton
