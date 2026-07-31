# Pads and Timeline — User Workflow

## Pads Section

The Pads screen is the central sound source in Juji-Synth. It presents a **4×4 grid** (16 pads)
with **two banks (A and B)**, giving **32 pad slots** total. Each pad holds either an imported
audio sample or an independent synthesizer voice.

### What Each Control Does

Select a pad by tapping it on the grid. The pad's current sample name appears above the grid.
Below the grid, two rows of action buttons and a parameter section let you shape the sound.

**Action buttons (above the knobs):**

| Button | What it does |
| -------- | ------------- |
| **Import Audio** | Opens the Android system file picker (`audio/*`). The selected file is converted to WAV via `AudioConverter`, copied into `…/samples/pad_<n>_<ts>.wav`, and loaded into the C++ sampler with `SynthEngine.loadSampleToPad`. |
| **Chop** | Slices the selected pad's loaded sample into 16 equal parts and spreads the slices across all 16 pads in the current bank. Useful for chopping breaks or loops. |
| **Stretch** | Opens the time-stretch dialog. Enter the original BPM and a target BPM; the engine applies async time-stretching with pitch preserved (0 semitones shift). |
| **Edit** | Opens the per-pad edit bottom sheet with all continuous parameters and mode toggles. |

**Continuous parameters (knobs in the Edit sheet):**

| Knob | Range | Description |
| ------ | ------- | ------------- |
| **Tune** | −24 … +24 semitones | Pitch shift relative to the original sample. |
| **Volume** | 0 … 100% | Per-pad gain. |
| **Pan** | −100 … +100 | Stereo position (negative = left, positive = right). |
| **Attack** | 0 … 1 | Amplitude envelope attack time. |
| **Release** | 0 … 1 | Amplitude envelope release time. |
| **Filter** (Cutoff) | 0 … 100% | Low-pass filter cutoff frequency. Only active when the **Filter** toggle is on. |
| **Resonance** | 0 … 100% | Filter resonance/Q. Only active when the **Filter** toggle is on. |

**Mode toggles (in the Edit sheet):**

| Toggle | What it does |
| -------- | ------------- |
| **Reverse** | Plays the sample backward. |
| **One-Shot** | Plays the full sample on every trigger (ignores note-off / key release). |
| **Filter** | Enables the per-pad low-pass filter. |
| **Loop** | Loops the sample playback. |
| **Synth** | Switches the pad from sample playback to an independent per-pad synthesizer. When enabled, a **Root Note** selector appears so the pad responds chromatically to MIDI/keyboard input. |

**Velocity:** Touch Y-position on the pad button maps to MIDI velocity 1–127 (top of the pad = full velocity, bottom = soft).

### How to Import a Sample

1. Navigate to the **Pads** tab.
2. Tap the target pad on the 4×4 grid (e.g. Pad 1) to select it.
3. Tap the **Import Audio** button.
4. The Android file picker opens — browse to an audio file (MP3, WAV, OGG, etc.).
5. The app converts the file to WAV format, copies it into the project's samples directory, and loads it into the pad's sampler slot.
6. The pad button changes color to indicate it now contains a loaded sample.
7. Tap the pad to preview the sound. Touch higher on the pad for louder velocity.

> **Note:** Imported samples are published to `PadSessionStore` for project autosave.
> Sample paths are persisted so they can be restored when you reopen the project.

### How to Assign a Synth to a Pad

1. Select the target pad on the grid.
2. Tap **Edit** to open the per-pad edit sheet.
3. Toggle the **Synth** switch on.
4. The pad switches from sample playback to a per-pad synthesizer voice.
5. A **Root Note** selector appears — set the base note for chromatic playback.
6. To configure the synth's oscillators, filter, envelopes, LFOs, and effects in detail, switch to the **Synth** tab, select the same pad from the pad selector, and edit presets there.

> **Note:** The Synth toggle on PadsScreen sets the mode flag. Full synth parameter
> editing (oscillators, ADSR, LFO, effects, presets) is done through the dedicated
> **Synth** screen, which has its own pad target selector.

### Pad Banks (A/B)

- **Bank A** contains pads 1–16 (global indices 0–15).
- **Bank B** contains pads 17–32 (global indices 16–31).
- Tap the **A/B** toggle button above the grid to switch between banks.
- Each bank has its own independent set of 16 pads. Loading a sample or assigning a synth on Bank A does not affect Bank B.
- The engine's `setSamplerBank()` call is made when you switch, syncing the active bank to the C++ layer.

---

## Timeline Section

The Timeline (Arrangement) screen is where you arrange clips into a song. It displays
**16 track lanes** with pattern and audio clips, a transport toolbar, horizontal zoom and scroll,
snap-to-grid editing, and an automation lane.

### Layout Overview

```
┌──────────────────────────────────────────────────┐
│  Select | Delete | Pads | Patterns | Undo | Redo  │
│  [ A1 | A2 | A3 | A4 | A5 ]  ← scroll sources    │
├──────────────────────────────────────────────────┤
│  Transport Strip                                  │
│  Row 1: Loop On/Off | Loop Start | Loop End       │
│         Punch On/Off | Punch In | Punch Out       │
│  Row 2: ◀ Nudge ▶ | Snap selector | Zoom +/−    │
├──────────────────────────────────────────────────┤
│  T1  ████████░░░░░░████████░░░░░░░░░░            │  ← Track 1
│  T2  ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░            │  ← Track 2
│  T3  ░░░░████████████░░░░░░░░░░░░░░░░            │  ← Track 3
│  …   (16 tracks total)                            │
├──────────────────────────────────────────────────┤
│  Automation Lane                                  │
│  (Filter Cutoff, Amp Level, LFO Rate, Master Vol) │
└──────────────────────────────────────────────────┘
```

- Each track lane (**T1–T16**) is a horizontal row where clips sit and its matching mixer channel.
- **Pattern clips** appear in amber; **audio clips** appear in cyan.
- Muted clips are dimmed.
- The playhead (vertical line) shows the current playback position.
- Horizontal scrolling follows the playhead when **Follow** is enabled.

> **Important:** When you enter the Timeline, the internal step sequencer is
> automatically disabled (`setSequencerEnabled(false)`) so arrangement playback
> and the step launcher don't fire simultaneously. It re-enables when you leave.

### Technical rendering model

The Timeline is a viewport-based editor. Musical state is kept in ticks; pixels
exist only at the boundary where Compose renders the viewport or resolves a
pointer gesture.

```text
Timeline state
     |
     v
Visible tick range
     |
     +--> Grid marks
     +--> Ruler marks
     +--> Visible clips
     +--> Playhead viewport position
```

#### State and coordinate conversion

- `Arrangement.clips` stores each clip's stable `id`, `startTick`,
  `durationTicks`, `trackIndex`, source reference, and mute state. Pad,
  pattern, and audio clips all use the same musical coordinate contract.
- `TimelineTransform` in
  `app/src/main/java/com/jujidaw/ui/timeline/TimelineEditingMath.kt` is the
  single conversion boundary. It maps ticks to viewport pixels, viewport
  pixels back to ticks, converts durations, snaps input, and computes the
  visible tick range.
- `horizontalScrollPx` translates the virtual musical content; it is never
  stored as a clip position. `TimelineScreen` currently supplies a fixed
  200-bar virtual musical extent to `maxScroll()`, which clamps the viewport
  within that extent rather than deriving a full-width layout from clip count.
- Pointer x-coordinates are converted with `viewportPxToTick()` (or its snap
  variant) before a clip is created, moved, resized, or selected. Rendering
  uses `tickToViewportPx()` and `durationToPx()`.

#### Virtualized rendering

`TimelineScreen` asks the transform for the visible tick range and passes that
range through `timelineVisibleClips()`. Only clips intersecting the range are
composed, with the clip being moved or resized pinned while its preview is
outside the range. Grid and ruler marks are generated from visible bars and
ticks, and the playhead uses the same transform as clips, so all vertical
indicators share one coordinate system. The implementation does not create a
full-width content layout.

#### Zoom, scroll, and playback

Zoom is limited to the supported `0.2×–5×` range. Pinch and toolbar zoom call
`zoomAroundAnchor()`: the tick under the gesture anchor is calculated before
the zoom and the new scroll is clamped so that tick remains under the anchor.
Horizontal drag changes only the viewport scroll. When Follow is enabled during
playback, the playhead may adjust the viewport; starting an edit or an explicit
zoom disables follow so the user's viewport is preserved.

#### Gesture ownership and editing history

`TimelineGestureState` arbitrates input in this order: pinch, Delete tool,
resize handle, clip body, ruler scrub, then the empty Timeline background.
Once a gesture enters a state it cannot silently become another operation.
Delete strokes collect unique clip IDs and commit once. Move, resize, add,
delete, duplicate, paste, mute, and Trash restoration are recorded as command
objects in `TimelineEditHistory`; one completed gesture therefore creates one
undo/redo transaction.

#### Autosave and constraints

`TimelineViewModel.updateArrangement()` records the command, refreshes the
transport arrangement, and schedules the debounced `ProjectAutosave` only
after the transaction is committed. Undo and redo restore the complete
timeline snapshot, including deleted clips and selection state.

Inputs are sanitized at the transform and model boundaries: ticks and
durations are non-negative/positive, tracks are limited to the 16 lanes, and
zoom and scroll are finite and clamped. `AudioClip` requires a nonblank path;
autosave resolves project-relative paths but absolute paths are currently also
accepted and remain an M15 security-audit concern. The current device
evidence covers the physical SM-G998W profile;
other density/orientation profiles remain explicitly pending. Frame-time
profiling is not claimed by the viewport tests; a profiler trace is the next
step only if device evidence shows a remaining performance issue.

### Transport Bar Controls (Play, Record, BPM, Loop, Punch)

The **global transport bar** is always visible at the bottom of the screen (portrait) or side
rail (landscape), regardless of which tab you're on.

| Control | Description |
| --------- | ------------- |
| **▶ / ■** | **Play / Stop.** Toggles playback via `TransportController.play()`/`stop()`. Stop clears scheduled events, releases held notes, resets the step counter, and re-enables the internal step sequencer. |
| **●** | **Record arm.** Toggles recording. When punch is enabled, recording is confined to the punch in/out range. |
| **↺** | **Reset.** Stops playback and seeks the playhead back to bar 1, beat 1, step 1. |
| **Time LCD** | Displays `Bar+1 | Beat+1 | Step+1` in monospace amber. Refreshes every 50 ms. |
| **BPM** | Tap the BPM display to open a numeric entry + slider dialog. Valid range: **30–300 BPM**. Calls `setTempo()` on the engine. |

The **Timeline toolbar** (above the track lanes) adds these controls:

| Control | Description |
| --------- | ------------- |
| **Loop: On/Off** | Toggles the loop region. When enabled, playback wraps between Loop Start and Loop End. |
| **Loop Start** | Sets the loop start point to the current playhead position. |
| **Loop End** | Sets the loop end point to the current playhead position. |
| **Punch: On/Off** | Toggles punch recording. When first enabled, it creates a one-bar range from the current playhead; recording is then confined to the punch range. |
| **● Punch In** | Sets the punch-in point to the current playhead position. |
| **● Punch Out** | Sets the punch-out point to the current playhead position. |
| **◀ / ▶** | Nudges the playhead backward or forward by one step (snap-dependent tick amount). |
| **Snap** | Selects **Free**, **Bar**, **1/4**, **1/8**, or **1/16**. The active resolution is drawn in the timeline; Free keeps the exact pointer position. |
| **Swing** | Cycles the non-destructive global groove amount. It delays alternating 1/16 positions during playback without moving stored clips. |
| **Zoom +/− / pinch** | Adjusts horizontal zoom (range: 0.2× to 5×); two-finger pinch uses the gesture baseline and keeps the tick under the gesture anchored. |

### How to Place a Pad on the Timeline

1. Open **Timeline** and choose **Pads** or **Patterns**. The bordered source box shows five large choices at a time and scrolls independently.
2. Tap a pad (`A1–A16`, `B1–B16`) or pattern (`P1–P16`); the choice also becomes the active draw tool.
3. Tap anywhere in a timeline row to place that source at that musical position.
4. The row is the clip's mixer destination. Moving a clip to another row also changes its mixer routing.
5. The tool stays active for repeated entry. Select **Select** when you want to edit rather than add clips.

> **Note:** Timeline starts with a 1/16 snap and new pads use one grid cell as their gate. After you resize a pad, subsequent pads reuse that duration for the current Timeline session.

### How to Move, Delete, Trim Clips

| Action | Gesture |
| -------- | --------- |
| **Select** | Tap a clip within its exact musical bounds. Long-press-drag an empty area still selects an intersecting range. |
| **Move** | Select a clip, then drag its body. A group keeps its relative timing and rows. |
| **Trim** | Drag a selected clip's upper-left or upper-right handle. The edge previews continuously and snaps when released. |
| **Erase** | Activate **Delete**, then tap a clip or drag across several clips. One eraser stroke is one undoable edit. |
| **Undo / Redo** | Use the toolbar buttons or Ctrl+Z / Ctrl+Y. Ctrl+Shift+Z also redoes. History lasts for the current editing session. |
| **Copy / duplicate / mute / delete** | Use the contextual selection toolbar. Paste anchors the copied group at the playhead and selected row. |

Clip colors:

- **Amber** = pattern clip (references a sequencer pattern)
- **Cyan** = audio clip (references a sample file)
- **Dimmed** = muted clip

### Loop and Punch Recording

**Loop playback:**

1. Move the playhead to where you want the loop to start.
2. Tap **Loop Start** in the timeline toolbar.
3. Move the playhead to where you want the loop to end.
4. Tap **Loop End**.
5. Tap **Loop: On** to enable looping.
6. Press **▶ Play** — playback wraps between the start and end points.
7. The loop region is synced to the C++ engine via `SynthEngine.setLoop(enabled, startSample, endSample)`.
8. On loop wrap, the playhead re-seeks and stale scheduled events are cleared.

**Punch recording:**

1. Arm recording with the **●** button in the global transport bar.
2. Set the punch-in point: move the playhead, then tap **● Punch In**.
3. Set the punch-out point: move the playhead, then tap **● Punch Out**.
4. Tap **Punch: On** to enable punch mode.
5. Press **▶ Play** — recording is active only between the punch-in and punch-out points. Outside that range, playback plays back existing clips without recording.
6. The punch range is pushed to the engine as a sample range via `setPunchRange()`.

### Draw Sources and Recording

- Pad selection comes from **Pads** and pattern selection comes from **Seq**. Pattern 1 is not Pad 1; it plays the content programmed in Pattern 1.
- While recording, live pad hits are captured to the single armed mixer row. If no row is armed, recording uses the last timeline row you touched.
- Recorded events quantize to the active grid, or retain their exact timing in Free mode. Swing remains non-destructive and is applied at playback.

---

## Typical User Workflow

Here is a step-by-step example of building a simple beat:

### 1. Import a Kick Drum to Pad 1

- Open the **Pads** tab.
- Tap **Pad 1** in the 4×4 grid to select it.
- Tap **Import Audio**.
- Browse to a kick drum sample (WAV, MP3, etc.) and select it.
- The sample is converted to WAV, loaded into the pad, and the pad button changes color.
- Tap the pad to preview — touch higher for louder hits.

### 2. Go to Sequencer, Enable Steps on Row 1

- Switch to the **Sequencer** tab.
- You see a 16×16 grid. Row **P1** corresponds to Pad 1.
- Tap cells in row P1 to toggle steps on/off (e.g. steps 1, 5, 9, 13 for a four-on-the-floor pattern).
- Each active step will trigger Pad 1's kick sample when the sequencer plays through it.
- Use the **pattern selector** (1–16) to choose which pattern you're editing.

### 3. Go to Timeline and Draw Pad 1

- Switch to the **Timeline** tab.
- Activate the **Pad A1** tool; it reflects the pad selected on Pads.
- Tap the row where you want the kick routed, for example **T1**.
- A pad-trigger clip appears at the tapped grid position, using the current pad length.
- This clip will play Pad 1's kick when the timeline reaches it.

### 4. Adjust BPM by Tapping the BPM Display

- In the global transport bar (visible at the bottom), tap the **BPM** number.
- A dialog opens with a numeric input and slider (range 30–300).
- Enter your desired tempo (e.g. 120) and confirm.
- The engine updates its tempo in real time.

### 5. Press Play

- Tap **▶** in the global transport bar.
- The playhead advances. The clip on T1 triggers Pad 1's kick at the right moments.
- The time LCD shows the current Bar | Beat | Step.

### 6. Adjust Loop Region if Needed

- Move the playhead to the start of the section you want to loop.
- Tap **Loop Start** in the timeline toolbar.
- Move the playhead to the end of the section.
- Tap **Loop End**.
- Tap **Loop: On**.
- Playback now repeats the selected region. Adjust the start/end points by repositioning the playhead and tapping the buttons again.

---

## Quick Reference

| Screen | Purpose |
| -------- | --------- |
| **Pads** | Load samples, assign synths, shape per-pad sound (tune, volume, pan, filter, envelope) |
| **Sequencer** | Program step patterns (16 rows × 16/64 steps, 16 patterns) |
| **Timeline** | Arrange clips into a song (16 tracks, pattern + audio clips, automation) |
| **Mixer** | Channel levels, pan, mute/solo, send effects |
| **Synth** | Full subtractive synth editor (oscillators, filter, ADSR, LFO, FX, presets) |
| **Keyboard** | Live performance input (chromatic grid or piano, note repeat, arpeggiator) |
| **Project** | Save/load projects, export audio |
