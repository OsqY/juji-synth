# Juji-Synth UI Design Overhaul Plan

> Phase: Write design overhaul plan
> Task: write plan
> Date: 2026-07-14
> Inputs: `docs/research/ableton-ui-design.md`, `docs/research/ableton-color-palette.json`, audit of current `ui/theme/*` and `ui/**/*Screen.kt`.
> Goal: move juji-synth from the current "photoreal brushed-metal hardware synth" look to an Ableton-inspired dark, compact, professional, information-dense DAW aesthetic, without changing audio/scheduling logic.

This plan is executable phase-by-phase. Each token and component spec is concrete enough that a single subagent can implement a phase and self-verify against the Acceptance Criteria. Keep all behavior (MIDI, transport, scheduling, playback) untouched; this is a visual/structural migration of the Compose layer only.

---

## Design Philosophy

1. **Target aesthetic — Ableton-inspired dark, compact, professional, information-dense.**
   Neighbor of Live 11/12: *dark grey, never black*; flat, modernist, Bauhaus-reduction. Depth expressed by raising surfaces **lighter** along a grey ramp (Ableton's inverse hierarchy), not by shadows or glows. Two-color system: **neutral grey structure + one accent**, with category hue reserved for meaning (clips, tracks, pad banks) and never decorative.

2. **No emojis in UI — use a minimal icon set instead.**
   Replace all emoji/UI characters currently in code (`🗑`, `▶`, `■`, `↺`, `⋮`, `−`, `+`) with **Material Symbols Outlined** where a standard glyph exists, or **custom SVGs** drawn at a consistent 1.5dp stroke, weight 400. Never mix filled and outlined icons within one surface. Two documented exceptions (kept as vector SVG, not emoji): metronome (no stock symbol) and clip launch triangle/clip-stop square (idiomatic DAW glyphs).

3. **Every component uses available space efficiently.**
   "Adjacent in space, not stacked in time" — show more, pad less. Components snap to the grid; dividers are subtle greys, not heavy rules or padded gaps. Where a screen has a fixed-height strip (transport, device chrome), enforce a single height so muscle memory transfers.

4. **Consistent spacing system (4dp base grid).**
   All paddings, gaps, heights, and corner radii are multiples of 2dp, with 4dp as the primary unit. See Spacing System.

5. **Out of scope (non-goals):**
   - Changing MIDI routing, transport timing, audio engine, or persistence formats.
   - Adding new features. New affordances in this plan are visual restatements of existing behavior.
   - Light theme. This overhaul defines a single dark theme. A light theme is a later, separate token pass.

### What changes at the core (direction summary)

| Axis | Current "Hardware Synth" | Target "Ableton-inspired" |
| --- | --- | --- |
| Depth model | Pure black base `#121214`, panels **darker**-than-black, neon glows, cast shadows | Dark grey base `#3C3C3E`, panels **lighter** via white-overlay ramp; flat, no neon glow |
| Color philosophy | 6 saturated accent colors (amber/cyan/green/pink/orange/red) used decoratively everywhere | Grey structure + **one amber accent**; hue reserved for clips/tracks/pad banks |
| Knob/Fader | Photoreal metal rim, 30 ticks, LED ring glow, radial gradient | "A dial is just a curved slider" — flat line indicator on a ring; minimal track + cap |
| LCD/typography | Green-LCD mono on near-black; `FontFamily.Default`/`Monospace` | Restrained mono readout on dark panel; **IBM Plex Sans + JetBrains Mono + Space Grotesk** (Google Fonts) |
| Icons | Unicode geometric chars + emojis | Material Symbols Outlined, consistent 1.5dp stroke |
| Density | Loose, glow-heavy | Tight, grid-aligned, density-first |

---

## Color System (new)

All tokens live in `app/src/main/java/com/jujidaw/ui/theme/Color.kt`. Names below are the canonical token names to declare. M3 roles are wired in `Theme.kt` (see Phase 1).

### Backgrounds (3 levels) — the structural grey ramp

Ableton raises panels **lighter**. The three named backgrounds are the anchors; intermediate surface steps are derived by white overlay.

| Token | Hex | RGB | Role |
| --- | --- | --- | --- |
| `Bg0` | `#323234` | 50,50,52 | Deepest (screen root, bottom of scroll, gaps between screens) |
| `Bg1` | `#3C3C3E` | 60,60,62 | Base grid (timeline body, sequencer grid, mixer backdrop) — Ableton's canonical `~#3C3C3C` |
| `Bg2` | `#47474A` | 71,71,74 | Raised panel (transport bar, toolbars, device chrome) |

### Surfaces — M3 tonal container steps (lighter = more elevated)

Derived by overlaying white on `Bg1` per Material dark-theme overlay table (0dp 0% → 24dp 16%). Concrete hex used so designers can eyeball-match.

| Token | Hex | Role (Ableton → juji) |
| --- | --- | --- |
| `Surface` | `#3C3C3E` | = `Bg1`. Default container. |
| `SurfaceContainerLow` | `#414143` | Card/strip background, recessed card |
| `SurfaceContainer` | `#48484B` | Device panels, mixer channel strips |
| `SurfaceContainerHigh` | `#505053` | Popovers, dropdown menus, bottom sheets |
| `SurfaceContainerHighest` | `#59595C` | Active/hovered container, selected clip cell fill |
| `OnSurface` | `#E6E6E6` | Body text at ~87% white emphasis |
| `OnSurfaceVariant` | `#A9A9A9` | Secondary labels, axis labels, muted readouts |
| `Outline` | `#5B5B5E` | Borders/dividers (visible but quiet) |
| `OutlineVariant` | `#3A3A3D` | Recessed borders, subtle separators |

### Primary / Secondary / Accent (the "two-color + reserve" system)

| Token | Hex | on-* | Meaning (strict usage) |
| --- | --- | --- | --- |
| `Primary` | `#FEEE4A` | `#1A1A1A` | **The single accent** — Ableton's amber-yellow. Selection highlight, active clip edge, current-step bar, primary CTA. |
| `Secondary` | `#1EBEAF` | `#06231F` | Functional category accent #2 — audio/filter signal flow only (filter cutoff ring, audio-clip category). |
| `Accent` | `#F7F384` | `#1A1A1A` | Lighter amber halo — glow/active-ring behind selected knob/clip. **Low-alpha only** (`copy(alpha = 0.25f)`), never a solid fill. |

Rationale: Ableton deliberately keeps a two-color system; the third hue "muddies the waters." `Primary` is the true accent; `Secondary` is a restrained second functional hue; `Accent` is the same amber used as a halo, not a new color. Saturated brand color appears on **≤2 element types** (selection + primary button) — never decoration.

### State colors (functional, derived from the Ableton hue grid)

| Token | Hex | on-* | Usage |
| --- | --- | --- | --- |
| `StateRecording` / `Error` | `#FC393D` | `#1A0A0A` | Record button (active), REC dot, CPU/error warning, record-arm toggle |
| `StateActive` (playback) | `#44C121` | `#06140A` | Play (running), metronome-on, "this clip is now playing" launch triangle |
| `StateSolo` | `#F4C430` | `#1A1405` | Solo toggle — warm amber, distinct from selection `#FEEE4A` |
| `StateMute` | (none — use `OnSurfaceVariant` outline + 50% alpha of the clip's own color) | — | Mute never invents a new color; it dims the clip's hue |
| `MidiLearn` | `#FD42D2` | `#240A1A` | MIDI-learn ring — pink/rose from grid, distinct from record red |
| `Disabled` | fill `OnSurface.copy(alpha=0.12f)`, text `OnSurface.copy(alpha=0.38f)` | — | Disabled controls per Material dark-theme spec |

### Text (primary / secondary / disabled)

| Token | Hex | Notes |
| --- | --- | --- |
| `TextPrimary` | `#E6E6E6` | ~87% white on `Bg1`; contrast > 15:1 — passes WCAG AA |
| `TextSecondary` | `#A9A9A9` | ~60% white — labels, hints, secondary readouts |
| `TextDisabled` | `#6E6E6E` | ~38% white — disabled labels |

### Clip colors per track — the 14-hue × 5-shade grid

Mirror Ableton's fixed palette so a track/clip color is meaningful and reusable. Declare `ClipColors: List<List<Color>>` (14 columns × 5 rows). Row index semantics: `0` pastel (dim/ghost), `1` smart-glow (selection ring), `2` saturated default (track color), `3` muted mid (inactive), `4` deep (hover/active-fill).

Representative row-2 anchors (full 70-color JSON already in `docs/research/ableton-color-palette.json`):

| Hue col | Row2 hex | Hue col | Row2 hex |
| --- | --- | --- | --- |
| Red/Pink | `#FC393D` | Sky | `#22A5EB` |
| Orange | `#F46C20` | Blue | `#127EBE` |
| Amber/Brass | `#CB9834` | Indigo/Violet | `#8870E1` |
| Yellow | `#FEEE4A` | Magenta | `#B579C4` |
| Lime | `#C0F932` | Pink/Rose | `#FD42D2` |
| Green | `#44C121` | Neutral | `#7B7B7B` |
| Teal/Mint | `#1EBEAF` | | |

Default track/clip color assignment: cycle the 13 non-neutral columns (skip `Neutral`) by track index. `PatternClip` → warm half (cols Red→Green), `AudioClip` → cool half (cols Teal→Blue) + `Secondary` ring, matching current code's amber-vs-cyan convention but using the grid.

### Pad bank colors

Two distinct hues drawn from the grid so finger-drumming banks read instantly without breaking the two-color rule (banks are *category* color):

| Token | Hex | Usage |
| --- | --- | --- |
| `PadBankA` | `#1EBEAF` (Teal) | Bank A pads; velocity fill scales teal→lighter |
| `PadBankB` | `#8870E1` (Indigo) | Bank B pads; velocity fill scales indigo→lighter |
| `PadActiveRing` | `#FEEE4A` (`Primary`) | The struck pad's ring = the global "active/selected" accent, not the bank hue |

### Map current Color.kt → new tokens

| Current token | New token | Notes |
| --- | --- | --- |
| `BgGunmetal` `#121214` | `Bg0` `#323234` | Stop using pure/near-black |
| `BgPanel` `#1E1E22` | `SurfaceContainer` `#48484B` | Invert: panels become **lighter** than the grid |
| `PanelHighlight` `#3A3A40` | `OutlineVariant` `#3A3A3D` | |
| `PanelShadow` `#0E0E10` | (removed) | No dark bevels; depth = lighter surface |
| `PanelDivider` `#2A2A30` | `OutlineVariant` `#3A3A3D` | |
| `TextPrimary` `#E8E8EC` | `TextPrimary` `#E6E6E6` | |
| `TextSecondary` `#9A9AA5` | `TextSecondary`/`OnSurfaceVariant` `#A9A9A9` | |
| `TextMuted` `#606068` | `TextDisabled` `#6E6E6E` | |
| `KnobAmber` `#FFB300` | `Primary` `#FEEE4A` | Collapse 6 decorative accents → 1 amber |
| `KnobCyan` `#00BCD4` | `Secondary` `#1EBEAF` | Used only for filter/audio category |
| `KnobGreen` `#4CAF50` | `StateActive` `#44C121` | Reuse as playback-active, not decoration |
| `KnobPink` `#E91E63` | `MidiLearn` `#FD42D2` | Repurposed for MIDI-learn only |
| `KnobOrange` `#FF9800` | (remove; fold into Solo/Transport tones) | |
| `KnobRed` `#E53935` | `StateRecording` `#FC393D` | |
| `LedAmber/Cyan/…` (alpha glows) | `Accent` `#F7F384` low-alpha | Single halo color, not 6 |
| `KnobFace` `#2D2D35` | `SurfaceContainer` `#48484B` | |
| `KnobRim` `#4A4A52` | `Outline` `#5B5B5E` | |
| `KnobIndicator` `#E8E8EC` | `OnSurface` `#E6E6E6` | Line indicator |
| `LcdBackground` `#0A0A0E` | `SurfaceContainerLow` `#414143` | LCD no longer near-black |
| `LcdText` `#00E676` (green) | `OnSurface` `#E6E6E6` mono | Drop the green-CRT look; restrained mono on dark panel |
| `LcdGlow` | (remove) | No glow |
| `KeyWhite` `#F0F0F2` | `SurfaceContainerHighest` `#59595C`? No — keys stay light | `KeyWhite` `#D0D0D0`, `KeyBlack` `#2A2A2C`, `KeyPressed` `= Primary #FEEE4A` |
| `KeyPressed` `#9B51FF` (purple) | `Primary` `#FEEE4A` | |
| `SeqStepActive` `#6C2BD9` | `Primary` `#FEEE4A` / clip color | Use track/clip hue, not purple |
| `SeqStepCurrent` `#9B51FF` | `Primary` `#FEEE4A` at full + `Accent` halo | |
| `ScopeTrace` `#00FF41` | `Secondary` `#1EBEAF` | CRT green → calm teal trace |
| `TransportGreen/Red/Amber` | `StateActive`/`StateRecording`/`StateSolo` | Reuse state tokens |

---

## Typography System (new)

Declared in `app/src/main/java/com/jujidaw/ui/theme/Type.kt`. Per research §2.3, Ableton Sans is proprietary and cannot ship — and the brief explicitly forbids Inter/Roboto/Arial. Route all fonts through downloadable **Google Fonts** bundled as `res/font/` resources (or Compose `FontFamily` with bundled `.ttf` via `googlefonts` if offline is required).

### Font family choices (distinctive but readable, DAW context)

| Role | Font | Weight(s) | Why |
| --- | --- | --- | --- |
| **UI body / labels / values** | **IBM Plex Sans** | 400 Regular, 600 Semibold | Humanist sans designed at IBM with strong **tabular figures**; engineering aesthetic; instantly distinguishable from Roboto/Inter; excellent at 9–12sp; free on Google Fonts. |
| **Display / section headers / screen titles** | **Space Grotesk** | 300 Light, 500 Medium | Geometric-technical grotesque with character; caps read as "instrument" not "app"; matches Ableton's modernist/Bauhaus reduction. |
| **Monospace / LCD / parameter readouts / BPM / transport position** | **JetBrains Mono** | 400 Regular, 700 Bold | Purpose-built for dense numeric data; ligature-free; tabular by default; engineered look. |
| **(Optional) Retro 7-seg flairopt-in** | **DSEG7 Classic** | 400 | Transport position readout only (`1 | 2 | 3`) for a touch of LCD nostalgia — **must remain readable**. Off by default. |

All four are on Google Fonts. `LabelFontFamily` (currently `FontFamily.Default` = system Roboto) becomes `IBM Plex Sans`. `LcdFontFamily` becomes `JetBrains Mono`.

### Type scale

| Token | Font | Size | Weight | Letter spacing | Usage |
| --- | --- | --- | --- | --- | --- |
| `displaySmall` | Space Grotesk | 18sp | 500 Medium | 0sp | Screen titles, "MIXER"/"SEQUENCER" section bars |
| `titleLarge` | Space Grotesk | 14sp | 500 Medium | 0.5sp | Dialog titles, popover headers |
| `titleSmall` | IBM Plex Sans | 10sp | 600 Semibold | 1.5sp | Section headers ("OSC 1", "FILTER") — ALL CAPS |
| `bodyMedium` | IBM Plex Sans | 12sp | 400 Regular | 0.2sp | Body text, list items |
| `bodySmall` | IBM Plex Sans | 11sp | 400 Regular | 0.2sp | Knob value chip text |
| `labelSmall` | IBM Plex Sans | 10sp | 600 Semibold | 0.3sp | Knob labels, toggles (M/S/R), dropdown labels |
| `captionSmall` | IBM Plex Sans | 9sp | 400 Regular | 0.2sp | Secondary labels, captions, row labels (P1…P16) |
| `monoMedium` (LCD) | JetBrains Mono | 11sp | 400 Regular | 0.4sp | Parameter readouts in knob/fader tooltips, LCD cells |
| `monoLarge` (LCD) | JetBrains Mono | 13sp | 700 Bold | 0.4sp | Transport BPM, bar\|beat\|step position |

All numeric text roles (`bodySmall` value chips, `monoMedium`, `monoLarge`) enable `TextStyle(fontFeatureSettings = "tnum")` for tabular figures so values don't jitter while dragging.

### Map current Type.kt → new type

| Current style | Current spec | New token | New spec |
| --- | --- | --- | --- |
| `titleSmall` | Default Bold 10sp 1.5ls | `titleSmall` | IBM Plex Sans Semibold 10sp 1.5ls |
| `bodySmall` | Monospace Medium 11sp 0.5ls | `bodySmall` + `monoMedium` | IBM Plex Sans 11sp (labels) / JetBrains Mono 11sp (LCD) |
| `labelSmall` | Default Medium 8sp 0.3ls | `labelSmall` | IBM Plex Sans Semibold 10sp 0.3sp (8sp → 10sp for legibility/touch) |
| `bodyMedium` | Monospace Bold 14sp 0.5ls | `monoLarge` | JetBrains Mono Bold 13sp 0.4sp + tabular nums |

---

## Spacing System

4dp base grid. Tokens declared as `object Spacing` (or `Dimens`) in a new `ui/theme/Spacing.kt`:

| Token | Value | Usage |
| --- | --- | --- |
| `xs` | 2dp | Hairline gaps, badge insets, icon-to-label |
| `sm` | 4dp | Default component gap, cell internal padding, divider-to-content |
| `md` | 8dp | Standard component gap, toolbar vertical padding, tile padding |
| `lg` | 12dp | Section padding, popover padding, card inset |
| `xl` | 16dp | Screen edge padding, dialog padding |
| `xxl` | 24dp | Between major sections, dialog content top/bottom |

### Derivative tokens (radii + heights)

| Token | Value |
| --- | --- |
| `RadiusXs` | 2dp (chip, cell) |
| `RadiusSm` | 4dp (button, clip, knob cap) |
| `RadiusMd` | 6dp (panel, card) |
| `RadiusLg` | 8dp (toolbar, sheet corner) |
| `TransportHeight` | 48dp (single transport strip height) |
| `ToolbarHeight` | 40dp (per-screen toolbar) |
| `StepCellSize` | 40dp (sequencer + timeline ruler cell) |
| `PadMinSize` | 64dp (minimum pad touch target, scales up) |
| `KeyMinWhiteWidth` | 28dp (keyboard white key min width) |
| `KnobDefaultSize` | 48dp |
| `FaderTrackWidth` | 4dp (track line), cap width 24dp |
| `TouchTargetMin` | 44dp (Material/ADA minimum; nothing interactive below this) |

### Padding standards by component type

- **Toolbars / transport bar:** horizontal `md` (8dp), vertical `sm` (4dp). Single 48dp height.
- **Panels / device chrome:** padding `sm` (4dp) internal; title bar height 24dp; title bar padding `sm` horizontal, `xs` vertical.
- **Step / clip / pad cells:** content padding `xs` (2dp); cell gap `xs` (2dp) so grid lines read.
- **Knobs:** 48dp control; label/value stack uses `xs` (2dp) gap; the 44dp touch rule means a 36dp knob sits in a 48dp touch area.
- **Faders:** 24dp cap width, 4dp track, 4dp cap-to-edge; channel strip width 64dp.
- **Dialogs / bottom sheets:** `xl` (16dp) horizontal, `lg` (12dp) vertical; corner `RadiusLg`.

---

## Component Inventory

For each existing component: target Ableton reference, color tokens, spacing, typography, 44dp touch target, icon set. Files in `app/src/main/java/com/jujidaw/ui/`.

### 1. TransportBar — `MainScreen.kt` (`PersistentTransportBar`, `TransportMiniButton`, `BpmChip`, `GroupDivider`)

- **Reference:** Ableton top Control Bar — compact icon row: Play/Stop/Record + tempo + position, flat icons, no bevels (research §4.4).
- **Target look:** Single 48dp flat strip on `SurfaceContainer`. Buttons are flat 36dp icon chips; active state = `Primary` tinted background at 15% + `Primary` icon; recording = `StateRecording` filled circle pulsing. Position + BPM are `monoLarge` chips.
- **Color tokens:** `SurfaceContainer` bg, `OutlineVariant` divider, `OnSurface` icons, `Primary` active, `StateActive` play-while-running, `StateRecording` record.
- **Spacing:** horizontal `md` (8dp), components `spacedBy(6dp)`; `GroupDivider` = 1dp × 20dp `OutlineVariant`; height `TransportHeight` 48dp.
- **Typography:** labels none (icon-only); position `monoLarge` `Primary`-tinted; BPM chip `monoLarge`.
- **Touch target:** 36dp buttons inside 48dp row → satisfy 44dp by adding 4dp invisible padding around each icon (touch area extended, visual stays 36dp). Reset button ≥ 44dp (bump from 32dp).
- **Icons (Material Symbols Outlined):** Play=`play_arrow`, Stop=`stop`, Record=`radio_button_checked` (active= same filled), Reset/return-to-zero=`restart_alt`, Loop=`loop`, Punch-in/out=`center_focus_strong`/`center_focus_weak`, Snap=`grid_on`/`grid_off`, Zoom=`zoom_in`/`zoom_out`, Metronome=**custom SVG** (no stock symbol), Tap-tempo=`touch_app`.
- **Removes:** unicode `▶ ■ ↺ − +` glyphs.

### 2. StepSequencer grid cells — `sequencer/SequencerScreen.kt` (`StepCellBox`, header cells, `StepRail`)

- **Reference:** MIDI Note Editor grid — pitch×time, grid is the central surface (research §4.6). Bars/beats as the visible ruler.
- **Target look:** 40dp cells on `Bg1`. Active step = the **track/clip hue** (from `ClipColors`, row-2) with `OnSurface` text. Current step column = `Primary`-tinted vertical band at 15% (`Accent` halo). Inactive = `SurfaceContainerLow`. Beat-grouped every 4 cells with `OutlineVariant` 1dp divider.
- **Color tokens:** `Bg1` grid, `SurfaceContainerLow` inactive, clip-hue active, `Primary`+`Accent` current, `OutlineVariant` gridlines, `TextSecondary` row labels.
- **Spacing:** cell 40dp, gap `xs` (2dp), row labels 36dp wide, header start offset 44dp (unchanged). Velocity bars below = clip hue at value-alpha.
- **Typography:** step numbers `captionSmall` 9sp; row labels `labelSmall` 10sp; velocity readout `monoMedium`.
- **Touch target:** 40dp cells are below 44dp → wrap each cell composable so its **pointerInput/touch rect is 44dp minimum** (visual cell 40dp, tap slop extends). Violin requirement satisfied by `Modifier.size(44.dp)` on the clickable Box with inner visual at 40dp.
- **Icons:** none in cells (numeric only). Velocity popup uses `vertical_align_top`/`unfold_more` optional.

### 3. Clips on timeline — `timeline/TimelineScreen.kt` (`ClipItem`, `ClipContent`, `PatternClip`, `AudioClip`)

- **Reference:** Session clip — title bar with name, clip color = track color (overridable), triangular launch button, square clip-stop (research §4.5). Live 12: slot borders derive from clip color to stop strobing.
- **Target look:** Rounded `RadiusSm` (4dp) clip on `Bg1`. Fill = clip hue row-2 at 60%, edge = clip hue row-2 at 100% (1dp). `PatternClip` warm hue, `AudioClip` cool hue + `Secondary` edge. Muted = 50% alpha + `OnSurfaceVariant` outline. Left edge 6dp launch triangle (`play_arrow`) in `OnSurface`. Trim handle = 2dp `OnSurface` at 50%, hit area 14dp (kept). Replace `⋮`+`🗑 Delete` with `more_vert` icon → dropdown with `delete` outlined icon.
- **Color tokens:** `ClipColors[*][2]` fill (60%), `ClipColors[*][2]` edge (100%), `OnSurface` text, `OutlineVariant` mute, `Secondary` (audio edge).
- **Spacing:** clip padding `xs` (2dp); title text padding `xs`; trim handle 14dp hit / 2dp visual.
- **Typography:** clip name `captionSmall` 9sp Semibold; muted name `TextSecondary`.
- **Touch target:** tap/long-press on whole clip (≥44dp when clip≥44dp tall). Trim handle hit area 14dp wide × full height — bump hit width to 20dp (visual 2dp) for the 44dp rule along drag axis.
- **Icons:** `more_vert` (menu), `delete` outlined (delete action), `play_arrow` (launch triangle), `stop` (clip-stop square if added).

### 4. Knobs — `RealKnob.kt` + `Components.kt` (`SynthKnob`)

- **Reference:** "A dial is just a curved slider" — a line indicator on a circle, zero decoration, no 3D bevels (research §4.1). Push: color ring around the encoder.
- **Target look:** Flat ring. Outer track ring 3dp `SurfaceContainerHigh`; an `Accent` (or clip/category) arc fills the value portion (270°, -135°→+135° preserved). A single 2dp `OnSurface` **line indicator** from center to rim (the "pointer"). No metal rim, no 30 ticks, no LED glow halo beyond a subtle `Accent` low-alpha (0.25) active ring. Value readout `monoMedium` below, label `labelSmall`.
- **Color tokens:** `SurfaceContainerHigh` track ring, `Accent` (or `Secondary`) value arc, `OnSurface` indicator, `Primary` selection ring.
- **Spacing:** default `KnobDefaultSize` 48dp; touch area 48dp (passes 44dp). Label/value stack `xs` gap.
- **Typography:** label `labelSmall`, value `monoMedium`.
- **Touch target:** 48dp (passes 44dp without extra padding).
- **Icons:** none.
- **Library note (research §9.5):** Consider forking `atsushieno/compose-audio-controls` `Knob` for a cleaner line aesthetic; optional in Phase 2.

### 5. Faders — `mixer/MixerScreen.kt` (`VerticalFader`) + `LevelMeter`

- **Reference:** "A slider is just a line" — minimal track + cap; vertical for volume, horizontal for send/pan (research §4.2). Density wins over skeuomorphism.
- **Target look:** 4dp `Outline` track, 24dp-wide cap (`SurfaceContainerHighest` fill, `OnSurface` 1dp border). Drag unchanged (vertical). Level meter = 10dp `SurfaceContainerLow` bar with `StateActive` + `StateRecording` (clip) segments.
- **Color tokens:** `Outline` track, `SurfaceContainerHighest` cap, `OnSurface` cap border, `StateActive`/`StateRecording` meter.
- **Spacing:** channel strip 64dp wide; fader 44dp wide region; meter 10dp + `xs` gap; master fader 36dp wide × 80dp tall (toolbar mini).
- **Typography:** value readout `captionSmall` 9sp; `MST`/`M/S/R` labels `labelSmall`.
- **Touch target:** cap 24dp wide in a 44dp-wide hit zone; vertical hit = full track height. Master mini fader hit width bumped 36→44dp.
- **Icons:** none on fader; M/S/R = text badges per Ableton simplicity.

### 6. Pad grid — `pads/PadsScreen.kt` (`PadsGrid`)

- **Reference:** Random-access clip grid — "the layout of clips does not predetermine their order" (research §4.5). Square cells, bank = category color.
- **Target look:** 4×4 square pads on `Bg1`, `RadiusSm`. Pad fill `SurfaceContainerLow`; bank hue tint at 25%. Struck pad = bank hue at value-alpha + `PadActiveRing` (`Primary`) 2dp ring for 120ms. Selected pad = `Primary` 1dp outline.
- **Color tokens:** `Bg1` bg, `SurfaceContainerLow` rest, `PadBankA`/`PadBankB` bank hues, `PadActiveRing`=`Primary`, `Primary` selection outline.
- **Spacing:** 4dp grid gap (`sm`), cell padding `sm`, min size `PadMinSize` 64dp (scales to fill).
- **Typography:** pad name `captionSmall` 9sp; toolbar labels `labelSmall`.
- **Touch target:** pads are ≥64dp (passes). Bank toggle chips ≥44dp.
- **Icons:** Import=`file_upload`, Chop=`content_cut`, Time-stretch=`timer`, Edit=`tune` (all Outlined); bank A/B = text "A"/"B" badges.

### 7. Keyboard keys — `KeyboardView.kt`

- **Reference:** Piano-roll input; black keys above white; note-name labels (research §4.6, Push keyboard).
- **Target look:** White keys `KeyWhite` `#D0D0D0` with flat top highlight; black keys `KeyBlack` `#2A2A2C`. Pressed = `Primary` `#FEEE4A` fill (replaces purple `#9B51FF`). Note labels `captionSmall` near bottom of each white key (`#A9A9A9`), black-key labels lighter.
- **Color tokens:** `KeyWhite`, `KeyBlack`, `Primary` (pressed), `TextSecondary` labels.
- **Spacing:** white key min width `KeyMinWhiteWidth` 28dp; black key width = 0.55× white; black height = 0.65× total.
- **Typography:** labels `captionSmall` 9sp.
- **Touch target:** keys span full width/height (passes). Octave/shift controls ≥44dp.
- **Icons:** octave `chevron_left`/`chevron_right`, sustain `toggle_on`.

### 8. LCD display — `LcdDisplay.kt`

- **Reference:** Ableton parameter readouts are restrained mono on the dark panel (research §2, §6) — not a green-CRT. Maintain the "small value readout" idea, drop the glow.
- **Target look:** 28dp `SurfaceContainerLow` chip, `RadiusXs` 2dp, 1dp `OutlineVariant` border, **no inner-shadow bevel**, no drawBehind glow. Value `monoMedium` in `OnSurface`; optional label `captionSmall` `TextSecondary` top-start.
- **Color tokens:** `SurfaceContainerLow` bg, `OutlineVariant` border, `OnSurface` value, `TextSecondary` label.
- **Spacing:** horizontal `xs` (4dp), vertical `xs` (2dp); height 28dp.
- **Typography:** value `monoMedium` 11sp; label `captionSmall` 9sp.
- **Touch target:** display is read-only; if tappable (edit), wrap in 44dp hit area.
- **Icons:** none.

### 9. Dropdown selectors — keyboard target, pattern, snap, etc. (`Components.kt` + `MainScreen.kt` `BpmEditDialog`, `timeline/TimelineScreen.kt` pattern row)

- **Reference:** Flat rect selectors; state by fill + label weight (research §4.3).
- **Target look:** `SurfaceContainer` chip, `RadiusSm` 4dp, 1dp `OutlineVariant` border; selected = `Primary` 12% fill + `Primary` label; dropdown menu `SurfaceContainerHigh`. Pattern selector numbered chips same treatment.
- **Color tokens:** `SurfaceContainer` chip, `Primary` selection, `OnSurface` label, `OutlineVariant` border, `SurfaceContainerHigh` menu.
- **Spacing:** chip height 32dp (touch raised to 44dp), horizontal padding `xs`, `spacedBy` `sm`.
- **Typography:** `labelSmall` 10sp.
- **Touch target:** 32dp visual → 44dp touch rect.
- **Icons:** trailing `arrow_drop_down`; pattern chips numeric.

### 10. Tabs — `main/MainScreen.kt` (`NavigationBar` portrait / `NavigationRail` landscape, `MainTab`)

- **Reference:** One-level navigation, no window management (research §3.1). Flat, consistent icons.
- **Target look:** `NavigationBar`/`Rail` on `SurfaceContainer`, `tonalElevation = 0dp`. Selected item = `Primary` indicator pill (`Primary` 15% fill) + `Primary` icon (not `Color.Black` on `Primary` — invert to light-on-dark: icon `OnSurface` unselected → `Primary` selected). Selected label `Primary`.
- **Color tokens:** `SurfaceContainer` bar, `Primary` selected icon/label/indicator-15%, `TextSecondary`/`TextDisabled` unselected.
- **Spacing:** bar height 72dp (portrait incl. transport); rail 80dp wide (landscape).
- **Typography:** label `captionSmall` 9sp (portrait), 9sp (rail).
- **Touch target:** nav items full-width ≥48dp (passes).
- **Icons (Material Symbols Outlined):** Timeline=`view_timeline`, Mixer=`equalizer`, Synth=`tune`, Pads=`grid_view`, Keys=`piano`, Sequencer=`view_module`, Project=`folder`.

### 11. Buttons — transport, loop, punch, zoom, snap, copy/paste/clear, M/S/R

- **Reference:** Flat rectangles/rounded rects; state by fill + label weight, no bevels (research §4.3).
- **Target look:** `SurfaceContainer` base, `RadiusSm`; toggle active = its state color at 25% fill + state color border + state-color label; inactive = `OutlineVariant` border + `OnSurface` label. Loop/punch toggles = `Primary` when on. Copy/Paste/Clear = ghost buttons (`OutlineVariant` border).
- **Color tokens:** `SurfaceContainer` base, state colors per toggle (`StateRecording`/`StateActive`/`StateSolo`/`Primary`), `OutlineVariant`, `OnSurface`.
- **Spacing:** height 32dp visual / 44dp touch; padding `xs`; `spacedBy` `sm`.
- **Typography:** label `labelSmall` 10sp Semibold when active, 400 when off.
- **Touch target:** 44dp (visual 32–36dp, hit extended).
- **Icons:** Copy=`content_copy`, Paste=`content_paste`, Clear=`delete_sweep`, Loop=`loop`, Punch=`center_focus_strong`, Zoom=`zoom_in`/`zoom_out`, Snap=`grid_on`. M/S/R stay **letter badges** (Ableton convention), colored by state.

---

## Migration Plan

Phased so each phase compiles and ships independently. Every phase ends with the build green and the Acceptance Criteria for that phase passing. Theme first (no visual breakage — components keep reading old tokens via aliases), then shared components, then transport, then screens, then polish.

### Phase 1 — Theme (`Color.kt`, `Type.kt`, `Theme.kt`, new `Spacing.kt`)

**Why first:** defines the token vocabulary everything else consumes.

**Steps:**

1. Create `ui/theme/Spacing.kt` with the spacing + radius + height tokens above.
2. Rewrite `Color.kt`: declare all new tokens **and keep the old top-level `val`s as aliases** (e.g. `val BgGunmetal = Bg0`, `val BgPanel = SurfaceContainer`, `val KnobAmber = Primary` … per the mapping table) so un-migrated components still compile and read sensibly. Mark aliases `@Deprecated("Use Bg0", ReplaceWith("Bg0"))`.
3. Rewrite `Type.kt`: bundle IBM Plex Sans / Space Grotesk / JetBrains Mono as `FontFamily` (downloadable via Google Fonts `GoogleFont` provider or bundled `.ttf` in `res/font/`). Define the type scale. Keep `LabelFontFamily`/`LcdFontFamily` val names pointing to new families. Add `fontFeatureSettings = "tnum"` to numeric styles.
4. Rewrite `Theme.kt`: `JujiDawColorScheme` M3 `darkColorScheme` mapping all new tokens to M3 roles (`primary=Primary`, `onPrimary`, `secondary=Secondary`, `background=Bg1`, `surface=Surface`, `surfaceVariant=SurfaceContainerLow`, `onSurface=OnSurface`, `onSurfaceVariant=OnSurfaceVariant`, `outline=Outline`, `outlineVariant=OutlineVariant`, `error=StateRecording`, `surfaceContainer*` via the M3 roles). Keep `JujiDawTheme(content)` signature.
5. Add `ClipColors` data + a `clipHue(trackIndex)` helper.
6. Verify build + lint. No UI changes yet beyond global tonal shift (expected, acceptable).

**Dependencies:** none. **Exit criteria:** app builds; every old color/typography reference still resolves via alias; visual tone is darker-grey/lighter-panel.

### Phase 2 — Shared components (`Components.kt`, `RealKnob.kt`, `LcdDisplay.kt`, `DraggableValue.kt`)

**Why second:** shared controls are reused across all screens; restyling once propagates everywhere.

**Steps:**

1. `RealKnob.kt`: strip the photoreal canvas (metal rim, 30 ticks, radial gradient, LED ring glow). Render flat: track ring (`SurfaceContainerHigh`, 3dp), value arc (`Accent`/`Secondary`), single `OnSurface` 2dp line indicator, optional `Accent` low-alpha active ring. Keep all params/signatures/drag logic unchanged.
2. `Components.kt`: restyle `SynthKnob` (calls updated `RealKnob`), `SynthToggle` (replace "ON"/"OFF" text with a flat toggle: state-colored 25% fill + border + label; keep size 28dp inner / 36dp outer / 44dp touch), `WaveformButton` (flat selected=recessed via `SurfaceContainerLow` darker, not raised — Ableton inverse), and any `DropdownSelector`/`ParameterTooltip`.
3. `LcdDisplay.kt`: drop `drawBehind` inner shadow + green text; use `SurfaceContainerLow` bg, `OutlineVariant` border, `OnSurface` mono value, `MonoFontFamily`.
4. `DraggableValue.kt`: ensure touch slop + 44dp hit areas.
5. Remove **all emojis and UI-character labels** from these files; route through the new icon helper (see Phase 5 for the shared `Icon` helper, but introduce minimal `androidx.compose.material.icons.outlined.*` imports now).
6. Verify build + per-component visual on the Synth screen (knobs/faders/toggles/LCD visible and reading new tokens).

**Dependencies:** Phase 1. **Exit criteria:** knobs, toggles, LCD, waveform buttons flat and token-driven; no emojis in shared components.

### Phase 3 — TransportBar (`MainScreen.kt`: `PersistentTransportBar`, `TransportMiniButton`, `RecordButton`, `BpmChip`, `GroupDivider`, `BpmEditDialog`)

**Steps:**

1. Replace unicode transport glyphs (`▶ ■ ↺`) and `±` nudge with Material Symbols Outlined (`play_arrow`, `stop`, `restart_alt`, `add`/`remove`).
2. Restyle to 48dp flat strip on `SurfaceContainer`; active states use `Primary`/`StateActive`/`StateRecording`; record = filled circle pulsing.
3. Position + BPM = `monoLarge` chips; `GroupDivider` = `OutlineVariant`.
4. Bump reset button 32dp→44dp touch; extend all 36dp buttons to 44dp hit.
5. `BpmEditDialog` → `SurfaceContainerHigh`, `RadiusLg`, `xl` padding, `titleLarge` Space Grotesk.
6. Verify transport on all screens (it's persistent in portrait).

**Dependencies:** Phase 1 + 2. **Exit criteria:** transport reads new tokens, no unicode glyphs, 44dp everywhere.

### Phase 4 — Screens (timeline, sequencer, pads, mixer, keyboard, synth)

**Order within phase** (each sub-step independently verifiable; do one file at a time, build between):

1. `timeline/TimelineScreen.kt`: `TransportStrip`, pattern selector chips (44dp touch), clip color mapping (clip hue → `ClipColors`), replace `⋮`+`🗑` with `more_vert`+`delete` icons, ruler/step cells.
2. `sequencer/SequencerScreen.kt`: `StepCellBox` (clip-hue active, `Primary` current column), header offset, `PatternSelector`, `StepRail`, velocity bars.
3. `pads/PadsScreen.kt`: pad bank hues, `Primary` active ring, toolbar icons (`file_upload`/`content_cut`/`timer`/`tune`).
4. `mixer/MixerScreen.kt`: `VerticalFader` flat, `LevelMeter` tokens, M/S/R state colors, `SynthKnob` pan/sends, `MixerToolbar`, automation `ModalBottomSheet` on `SurfaceContainerHigh`.
5. `KeyboardView.kt`: `KeyPressed` → `Primary`, `KeyWhite`/`KeyBlack`, labels, octave/shift controls.
6. `synth/SynthScreen.kt` + panels (`OscillatorPanel`, `FilterPanel`, `EnvelopePanel`, `LfoPanel`, `EffectsPanel`, `FilterResponseView`, `OscWaveformView`, `LfoAnimationView`): apply `SynthPanel` chrome to new tokens; oscilloscope trace `Secondary`; flat device strips.
7. `main/MainScreen.kt` tabs: swap `Icons.Filled.*` → `Icons.Outlined.*` for the icon set above; selected = `Primary` not `Color.Black`.

**Dependencies:** Phases 1–3. **Exit criteria:** every screen reads new tokens; no emoji anywhere; all interactive elements ≥44dp touch.

### Phase 5 — Polish (animations, micro-interactions)

**Steps:**

1. Pad/clip strike: 120ms `Accent` ring fade. Current-step: subtle `Primary` band pulse.
2. Knob drag: value arc springs; tooltip fades in after long-press.
3. Record blink: 1s `StateRecording` pulse.
4. Tab switch: crossfade 150ms.
5. Optional: `DSEG7` 7-seg transport position (behind a settings flag).
6. Audit: run `grep -RInP '[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}]' app/src/main` to confirm **zero emoji** remain; confirm no mixed filled/outlined icons per surface.

**Dependencies:** Phase 4. **Exit criteria:** animations present and subtle; emoji audit passes; iconography audit passes.

---

## Acceptance Criteria

A phase is "done" only when its checklist is verifiably green. Final overhaul is complete when **every** item below is true.

### Color

- [ ] `Color.kt` declares every token in the Color System tables with exact hex.
- [ ] Old top-level color vals exist only as `@Deprecated` aliases and nothing outside `Color.kt` references them.
- [ ] No `Color(0xFF...)` literal appears in any `*Screen.kt`/`*Panel.kt`/`*Knob.kt` (only theme tokens) — `grep -RIn 'Color(0x' app/src/main/ui` returns only `Color.kt`.
- [ ] Base background is `#3C3C3E`-family, **never** pure/near-black (`#000000`/`#0A0A0E`/`#121214` gone).
- [ ] Raised panels are **lighter** than the grid (invert verified: `SurfaceContainer` > `Bg1` luminance).
- [ ] Single accent `Primary` `#FEEE4A` is the only selection color; `Secondary` appears only on filter/audio category.

### Typography

- [ ] `LabelFontFamily` = IBM Plex Sans; `LcdFontFamily` = JetBrains Mono; display titles use Space Grotesk.
- [ ] No use of `FontFamily.Default`, `FontFamily.Monospace`, `Inter`, `Roboto`, or `Arial` outside `Color/Type`.
- [ ] All numeric/LCD styles have tabular figures (`fontFeatureSettings = "tnum"`).
- [ ] Minimum body/label size is 9sp; knob label dropped from 8sp → 10sp.

### Spacing & Layout

- [ ] `Spacing.kt` exists with the 6 spacing tokens + radii + fixed heights.
- [ ] No raw `<n>.dp` spacing literal outside `Spacing.kt` and component files' structural sizes (transport 48, toolbar 40, cell 40, pad 64) — a `grep` audit passes.
- [ ] Every interactive element's touch rect ≥ 44dp (manual tap-test each, or `Modifier.minimumInteractiveComponentSize()`/explicit 44dp).
- [ ] Transport bar is exactly `TransportHeight` (48dp) on every screen.

### Component inventory

- [ ] Each of the 11 listed components matches its spec sheet (colors, spacing, typography, icons, touch target).
- [ ] Knobs are flat (no metal rim, no 30 ticks, no LED halo) — a `RealKnob` canvas audit shows only ring + arc + indicator.
- [ ] Faders use 4dp `Outline` track + 24dp `SurfaceContainerHighest` cap.
- [ ] LCD is `SurfaceContainerLow` + `OnSurface` mono, no green, no glow.
- [ ] Clips use `ClipColors` (track hue); `PatternClip` warm, `AudioClip` cool.
- [ ] Pad banks use `PadBankA`/`PadBankB`; struck pad ring = `Primary`.
- [ ] Keyboard pressed key = `Primary` (purple `#9B51FF` removed).

### Icons & emoji

- [ ] `grep -RInP '[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}\x{2190}-\x{21FF}\x{25A0}-\x{25FF}\x{2B00}-\x{2BFF}]' app/src/main` returns **zero** emoji or geometric-unicode glyphs used as UI labels (transport `▶■↺⋮−`, `🗑` removed).
- [ ] All icons are Material Symbols **Outlined** (or documented custom SVGs: metronome, launch triangle, clip-stop); no filled/outlined mix within one surface.
- [ ] Tab icons match the mapped set (`view_timeline`/`equalizer`/`tune`/`grid_view`/`piano`/`view_module`/`folder`).

### Migration

- [ ] Phases 1–5 executed in order; build is green after each phase.
- [ ] No MIDI/transport/audio/scheduling logic changed (diff audit: only `ui/**` + `res/font` touched, plus the `Color`/`Type`/`Theme`/`Spacing` files).
- [ ] No component signature breaks callers (public APIs of `RealKnob`, `SynthKnob`, `LcdDisplay`, `VerticalFader` unchanged; only visuals inside).

### Overarching

- [ ] App launches and all 7 tabs render with the new dark-compact aesthetic.
- [ ] Body text on `Bg1` has ≥ 4.5:1 contrast (`TextPrimary #E6E6E6` on `#3C3C3E` ≈ 12.5:1 — passes AA).
- [ ] No neon glow remaining (no LED `0x80…` halos, no green CRT scopes).
- [ ] Density check: a representative screen (Synth or Mixer) shows ≥ the same number of controls visible as before at a standard phone width (information-density non-regression).

---

## Notes for implementing subagents

- **Do not touch** anything under `midi/`, `engine/`, `audio/`, `data/`, `model/`, `project/`, `diagnostics/` except where a UI callback signature change is unavoidable (none expected).
- Keep all `@Composable` public signatures (`RealKnob`, `SynthKnob`, `LcdDisplay`, `VerticalFader`, `PadsScreen`, `TimelineScreen`, `SequencerScreen`, `MixerScreen`, `KeyboardView`, `SynthScreen`, `MainScreen`) stable so ViewModels and `MainScreen`'s tab routing keep compiling.
- When in doubt about an Ableton reference, consult `docs/research/ableton-ui-design.md` (section numbers cited above) and `docs/research/ableton-color-palette.json` for exact 70-color values.
- Fonts: prefer bundling `.ttf` in `app/src/main/res/font/` over Google Fonts runtime download for offline reliability; both are acceptable.
- One writer per phase; run a fresh review-lens after Phase 2 (shared components) and Phase 4 (screens) since those carry the most diff.
