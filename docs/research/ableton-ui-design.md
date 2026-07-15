# Ableton Live UI Design System — Research Report

> Phase: Research Ableton + audit current UI
> Task: research-ableton
> Date: 2026-07-14
> Scope: Color, typography, layout, components, iconography, dark mode, spacing, open-source kits, Material 3 adaptation, Compose libraries.

Sources are cited inline with URLs. A consolidated source list is at the end.

---

## 1. Color Palette

### 1.1 Neutral ramp (dark-grey backgrounds, panels, text)

Ableton Live's neutral greys come from the right-most column (col 14) of the Live 10 clip/track color grid (70 colors, 14×5). These are the structural greys the whole UI is built from:

| Token role | Hex | RGB | Name (color.pizza) |
| --- | --- | --- | --- |
| Brightest text / clip edge highlight | `#FFFFFF` | 255,255,255 | White |
| Light panel / disabled text | `#D0D0D0` | 208,208,208 | Ancestral Water |
| Mid-grey dividers / inactive labels | `#A9A9A9` | 169,168,169 | Ultimate Gray |
| Track header / secondary surface | `#7B7B7B` | 123,124,125 | Namara Grey |
| Darkest background / session grid base | `#3C3C3C` | 60,59,60 | Shisha Coal |

The default Live dark theme background sits around `#3C3C3C`-ish for the lowest surface; raised panels (Browser, Device Chain, Clip View) step **lighter** along this grey ramp — the opposite of what most apps do, and the core of Ableton's depth model (see §6).

Live 12 stores theme colors as **HEX values** in `.ask` "skin" files; Live 10/11 used ARGB integers. Custom themes edit these files directly.

### 1.2 Clip / track accent palette (the 14-column hue grid)

Newly created clips inherit the track color; the user picks from a fixed 14-hue × 5-shade grid. Full hex list is in the project at `docs/research/ableton-color-palette.json` (mirrored from [danhemerlein/ableton-colors](https://github.com/danhemerlein/ableton-colors)). Representative anchors per hue column (row 2, the saturated track default):

| Hue | Hex | Name |
| --- | --- | --- |
| Red/Pink | `#FC393D` | Coral Red |
| Orange | `#F46C20` | Apocalyptic Orange |
| Amber/Brass | `#CB9834` | Gomashio Yellow |
| Yellow | `#FEEE4A` | Rape Blossoms |
| Lime | `#C0F932` | Grass Stain Green |
| Green | `#44C121` | Harlequin Green |
| Teal/Mint | `#1EBEAF` | Tealish |
| Cyan | `#31E9FD` | Sparky Blue |
| Sky | `#22A5EB` | Button Blue |
| Blue | `#127EBE` | Tall Ships |
| Indigo/Violet | `#8870E1` | Matt Purple |
| Magenta | `#B579C4` | Wisteria |
| Pink/Rose | `#FD42D2` | Mat Dazzle Rose |
| Neutral | `#7B7B7B` | Namara Grey |

Each hue also has a row 1 pastel (lighter, less saturated), row 3-4 muted mid, and row 5 deep/dark variant — useful for active/inactive/dimmed states of the same category.

### 1.3 Functional / state highlights

- **Selection / active focus:** a warm amber-yellow accent (close to `#FEEE4A` / `#F7F384`) is the canonical "currently editing" highlight.
- **Play/launch trigger:** Live 12 updated deactivated/inaudible clip colors to contrast against empty slots; the Session slot horizontal border now uses a shade **derived from surrounding clips** instead of neutral grey to reduce color-strobing.
- **Error/CPU:** CPU meter and warning states lean on the red column (`#FC393D`).
- Live 12 added **Color Intensity, Brightness, Grid Line Intensity, and Color Hue** sliders in Theme & Colors, plus a **high-contrast** accessibility option and screen-reader support.

**Design intent (Eric Carl, Principal Designer):** Live was deliberately a **two-color system** — neutral grey structure + one accent. Introducing a third color "muddies the waters," so category color should be used sparingly and meaningfully.

---

## 2. Typography

### 2.1 The custom typeface: Ableton Sans

- **Font family:** "Ableton Sans" / "AbletonSans" — a **custom proprietary typeface**, not a stock font.
- **Foundry:** [Letters from Sweden](https://lettersfromsweden.se/ableton/), art direction by A Color Bright. 7 fonts in the family.
- **Concept:** stokes spiral outward to echo "turning the knob" — the central gesture of Live. Designed specifically for digital + physical (Push) interfaces.
- **Timeline:** first shipped on **Push 2**, then adopted across the desktop app in **Live 10 (2018)**.
- **Live 12 (2025)** introduced a **newly redrawn interface font** — noticeably different/lighter than Live 11's, which some users find harder to read; this triggered accessibility work.

### 2.2 Actual font files in the Live bundle

From the [ableton-font-replacer](https://github.com/madebycm/ableton-font-replacer) source, the canonical UI font files are:

- `AbletonSans-Light.ttf`
- `AbletonSansSmall-Bold.ttf`
- `AbletonSansSmall-Regular.ttf`
- `AbletonSansSmall-RegularItalic.ttf`

So the system uses a **Small regular / Small bold / Light** pairing: small bold for labels & values, small regular for body, light for larger display/headers. Pre-Live-10 the GUI used **Arial** (manual/box used a customized **Avenir**).

### 2.3 Font recommendations (for a non-Ableton reimplementation)

Ableton Sans is proprietary — you cannot legally ship it. Recommended open substitutes ranked by fidelity:

| Use | First choice (free/open) | Rationale |
| --- | --- | --- |
| UI labels & values (AbletonSansSmall) | **Inter** (or **Atkinson Hyperlegible**) | Inter is the field default for dense data UIs; Atkinson is what the community uses to replace Ableton fonts for readability. |
| Display / section headers (AbletonSans-Light) | **Inter** at Light/Extralight weight | Reproduces the airy, technical sans feel. |
| Numeric / parameter readouts | **Inter** tabular figures, or **JetBrains Mono** for exact value columns | Tabular nums prevent jitter while dragging. |
| Monospaced debug / MIDI | **JetBrains Mono** / **IBM Plex Mono** | Matches the "engineering aesthetic" Eric Carl describes. |

Sizes: Live is compact — UI label text ≈ 10–11px @1x; parameter values ≈ 11–12px; section titles ≈ 13px. Weights: Regular (400) for body, Semibold/Medium (600) for active values, Light (300) for large titles.

---

## 3. Layout Grid & Information Density

Ableton's layout philosophy, from [Eric Carl's "Designing for Authenticity" talk](https://ericcarl.link/blog/ableton-live-and-designing-for-authenticity/):

### 3.1 Foundational principles

1. **"Like an instrument," not "software for music."** Dialogues are styled like Live, not like the OS. Ideally used full-screen. Immediate and intuitive.
2. **One window, no window management** — "Window management has nothing to do with being a musician." Everything on the same level.
3. **"Adjacent in space" not "stacked in time."** Optimize for large screens that display a lot at once (vs. mobile's drill-down).
4. **"Toollessness."** One pointer, direct manipulation of musical content — no tool-switching modes.
5. **Sandbox / undirected.** The Session grid affords random access; nothing predetermines order. Like hashi (chopsticks): "use me however you want."
6. **Bauhaus / Modernist** aesthetic — beautiful, modern, classic design object. Stark contrast to skeuomorphic hardware-look DAWs of the era. "Flat design" ~15 years early.

### 3.2 Concrete layout

- **Control Bar** (top): transport, tempo, scale/MIDI, CPU meter, view toggles, browser toggle. Fixed, mission-critical (cockpit reference).
- **Browser** (left): two-column split, draggable divider; expandable to full height.
- **Main view** (center): toggles between **Session** (clip launcher grid, columns=tracks, rows=scenes) and **Arrangement** (linear timeline + overview).
- **Detail area** (bottom): Device Chain + Clip View (now viewable simultaneously in Live 12).
- **Three-view model:** Session / Arrangement / Detail, all sharing one window. View ratios are user-adjustable (drag dividers); mixer can now show inside Arrangement.

### 3.3 Density

- High information density is deliberate and **expected** — "grid interfaces for music" are an emerging standard with ~18 recurring element types (NIME survey).
- **Detail Level slider** in the status bar controls interface density globally — a key pattern to borrow.
- Device height is fixed/opinionated so the device chain forms a consistent horizontal strip.

### 3.4 Six modern design principles (Eric Carl, paraphrased)

1. Creative expression should be free-flowing and uninterrupted.
2. Solve design problems as simply as possible (complexity allowed, simplicity preferred).
3. Afford in-depth control grounded in reliability and trust.
4. Meet concrete musician needs while supporting open-ended paths.
5. Visually appealing + humble/restrained, grounded in strong functionality.
6. Offer stability for long practice while evolving to stay relevant.

---

## 4. Component Design Language

### 4.1 Knobs / dials

- **Reductionist:** "a dial is just a curved slider." A knob is drawn as a line (indicator) on a circle — zero decoration. No 3D bevels, no photoreal hardware.
- Interaction: click-and-drag **vertically** (up = increase), fine-grain with modifier. Knob must visually resemble its physical counterpart "enough to make operation obvious."
- Max for Live devices use `live.dial` / `live.numbox` objects; production guidelines require parameters be given **meaningful names** matching the UI label (not `live.dial[3]`).
- Push hardware: encoders with a **color ring** around each encoder indicating value/selection; touched/released events are first-class.

### 4.2 Sliders / faders

- Vertical faders for mixer volume; horizontal for send/pan. "A slider is just a line" — minimal track + cap.
- Drift case study: sliders were considered to communicate hardware inspiration but **rejected because they aren't space-efficient** — a recurring Ableton rule: density wins over skeuomorphism.

### 4.3 Buttons & toggles

- Buttons are flat rectangles/rounded rects, not embossed. State conveyed by fill color + label weight, not by bevels.
- On/off toggles placed at signal-flow boundaries (Drift's oscillator/filter routing) — UI must honestly represent signal flow. Misrepresenting it was rejected in review.

### 4.4 Transport

- Top Control Bar: Play/Stop/Record + tempo (BPM) + metronome toggle + quantization + loop + tap tempo. Compact icon row.
- Metronome icon is itself lore: a designer repurposed a blinking airport light — example of personal/idiosyncratic flourishes inside the neutral system.

### 4.5 Clips & the Session grid

- Each Session cell = a **clip** with a triangular launch button on its left edge; square **Clip Stop** button per-track column. Random access: "the layout of clips does not predetermine their order."
- Clip properties in Clip View: title bar holds Activator toggle + color swatch + name. Clip color defaults to track color, overridable.

### 4.6 Sequencer / MIDI editor

- MIDI Note Editor: piano-roll style — pitch (rows) × time (columns), notes as horizontal bars, velocity as vertical bars/stripes below. Grid lines on bar/beat subdivisions. The grid itself is the central interaction surface.

### 4.7 Device chrome

- Devices sit in a horizontal scrolling strip of fixed height; each device is a self-contained panel with a title bar, collapsible. Cross-device consistency is "extreme" and opinionated so users transfer muscle memory.

---

## 5. Iconography

- **Minimal, line-based, flat.** Consistent with the Bauhaus/Modernist reduction. Icons are *abstracted to essential nature* — "zero decoration, zero distraction."
- Stroke-based rather than filled blocks; consistent (thin) stroke weight across the set.
- Idiosyncratic/personal touches allowed on a few icons (metronome from an airport light) — the balance between "seemingly objective" and "personal/subjective."
- No skeuomorphic hardware replicas. A heavily stylized/skeuomorphic style is explicitly avoided because it would "dictate too much of that relationship."
- **Recommendation:** for a reimplementation, use a single-weight **outline icon set** (Lucide / Phosphor / Material Symbols "Outlined") at a consistent 1.5px stroke, never mixing filled and outlined within one context.

---

## 6. Dark Mode Approach (Background Hierarchy & Contrast)

### 6.1 Ableton's model

- Background is **dark grey (`~#3C3C3C`)**, **never pure black**. Depth is expressed by raising surfaces **lighter** along the grey ramp (`#7B7B7B`, `#D0D0D0`…).
- Inverse hierarchy vs. Material: raised panels get *lighter* fill, not shadow.
- Two-color system (grey structure + one accent) keeps the eye calm and lets category color carry meaning.
- Live 12 mitigations: deactivated clips recolored for contrast; slot borders derive from clip colors to stop strobing; user-adjustable intensity/brightness/hue; high-contrast accessibility mode.

### 6.2 Material dark-theme best practices (adaptable to Ableton-style)

From [Material dark theme spec](https://m2.material.io/design/color/dark-theme.html) (M2; M3 refines elevation into discrete `surface-container` tonal steps but the rules carry forward):

- **Use dark grey, not black** — `#121212` baseline surface (Ableton uses a warmer `~#3C3C3C`).
- **Elevation = lighter surface**, via semi-transparent white **overlay** (0% at 0dp → 16% at 24dp). Table: 0dp 0% · 1dp 5% · 2dp 7% · 3dp 8% · 4dp 9% · 6dp 11% · 8dp 12% · 12dp 14% · 16dp 15% · 24dp 16%.
- **Contrast target:** dark surface ↔ white body text ≥ **15.8:1**, so even the lightest elevated surface passes WCAG **AA 4.5:1** for text.
- **Desaturate accents:** saturated colors vibrate against dark and fail contrast. Use **lighter tones (200–50)** of the brand hue, not the saturated 500/600.
- **Surface overlays for states** match content color; primary-colored containers use white overlays. Disabled = 12% white fill + 38% white text.
- **Text emphasis opacity** (white on dark): high 87% / medium & hint 60% / disabled 38%.
- **Baseline error color:** `#CF6679` (light theme error + 40% white overlay).
- Reserve full-saturation brand color for **1–2 elements only** (logo / primary CTA).
- Don't use light glows for elevation; use cast shadows (kept dark).

### 6.3 M3 specifics to apply

- Discrete tonal roles: `surface`, `surface-container` (low/medium/high/highest), `on-surface`, `on-surface-variant`, `primary`, `on-primary`, `primary-container`, `outline`, `outline-variant`.
- M3 dark scheme still recommends keeping large surfaces dark; accent goes on small surfaces.
- Generate a tonal palette from one seed color (your accent hue) so every state is mathematically consistent — easier than hand-picking the 14×5 grid.

---

## 7. Spacing & Padding

- **Tight, density-first.** Ableton optimizes for "adjacent in space" — show everything, minimize white space, let the user manage focus.
- **Grid alignment over breathing room.** Components snap to a consistent module; the MIDI editor's bar/beat grid and the Session's cell grid are the visual rulers.
- **Fixed device height → consistent horizontal strip** (no per-device vertical variance). Controls within a device are grouped by signal-flow stage with thin dividers, not large gaps.
- **Dividers are subtle greys** (≈ `#A9A9A9` / dimmed), not heavy rules.
- **Detail Level** slider globally trades density for breathability — borrow this as the single density control rather than many individual paddings.
- Recommendation: base unit **4px** (everything multiples of 4: 4/8/12/16/24), knob default hit-target **≥ 32–40dp** (per Push/M4L guidance ~48dp), fader track width ~4–6px, knobs ~40–50dp.

---

## 8. Open-Source Ableton-Inspired Kits & Design Systems

### 8.1 Web (React)

- **[cutoff/audio-ui](https://github.com/cutoff/audio-ui)** ⭐ — professional open-source React component library for DAW/plugin/audio UIs. Knob, Slider, etc. Dual GPL-3.0 / commercial. Docs: [cutoff.dev/audio-ui](https://cutoff.dev/audio-ui/docs/latest/getting-started/introduction).
- **[meincdev/rds](https://github.com/meincdev/rds)** — "Reba Design System," music-native React + Radix + Tailwind, with design tokens + music-specific components.
- **[HowdyMoto/Knobs](https://github.com/HowdyMoto/Knobs)** — rotatable knobs + vertical faders for audio web apps; custom SVG knobs, multiple rotation modes.
- **[soniqaudio/tone](https://github.com/soniqaudio/tone)** — open-source web DAW inspired by FL Studio + Ableton (piano roll, playlist/arrangement).
- **[Hornfisk/drawdio](https://github.com/Hornfisk/drawdio)** — Svelte mockup tool: place knobs/faders → export PNG/SVG/JSON. Good for design exploration.

### 8.2 Figma

- **[Knob Creator](https://www.figma.com/community/plugin/1030842037042371583/knob-creator)** — plugin to generate knob image stacks for DAW/VST themes.
- **[Figma Knob Creator (standalone)](https://navelpluisje.github.io/figma-knob-creator/)** — export filmstrips.
- **[VST GUI Pro](https://www.figma.com/community/plugin/1563956754324486889/vst-gui-pro)** — animated knobs/faders/meters/buttons → PNG sprite sheets (JUCE/VSTGUI-ready).
- **[Free UI Knobs](https://www.figma.com/community/file/1401287748758736696/free-ui-knobs-w-t-f-sosa)** — free knob component set.
- Material offers an official [Dark Theme Design Kit for Figma](https://storage.googleapis.com/mio-assets/resources/Material%20Dark%20Theme%20Design%20Kit.fig) (Apache-2.0) — useful neutral + elevation starting point.

### 8.3 Theme-tooling (Ableton-native)

- **[AritxOnly/Qt-AbletonLive_ThemeEditor](https://github.com/AritxOnly/Qt-AbletonLive_ThemeEditor)** — Live 12 theme editor (HEX-based `.ask` files).
- **[danhemerlein/ableton-colors](https://github.com/danhemerlein/ableton-colors)** — the 70-color Live 10 palette as JSON (source of §1 hex codes).
- **[madebycm/ableton-font-replacer](https://github.com/madebycm/ableton-font-replacer)** — font file inventory + accessibility swaps.

### 8.4 Ableton's own open specs

- **[Ableton Push 2 interface spec](https://github.com/Ableton/push-interface/blob/main/doc/AbletonPush2MIDIDisplayInterface.asc)** — full LED color table, display pixels, encoder/pad/touchstrip events. Reference-grade for hardware-style component design.
- **[Ableton maxdevtools — M4L production guidelines](https://github.com/Ableton/maxdevtools/blob/main/m4l-production-guidelines/m4l-production-guidelines.md)** — official device UI consistency rules.

---

## 9. Compose-Specific Libraries for DAW-style UI

Target stack: Jetpack Compose / Compose Multiplatform (Android + Desktop).

### 9.1 Knobs & rotary controls

- **[atsushieno/compose-audio-controls](https://github.com/atsushieno/compose-audio-controls)** ⭐ — the most relevant lib. `Knob`, `ImageStripKnob` (filmstrip-based, like VSTs), `DiatonicKeyboardWithControllers`, expression sliders. Compose Multiplatform. MIT. (See also the [Knob.kt gist](https://gist.github.com/atsushieno/90551a7a4d6dd411e00ba4bfdb59eac6).)
- **[sinasamaki/ChromaDial](https://github.com/sinasamaki/ChromaDial)** — circular dial/knob for Compose Multiplatform; configurable sweep/start angles.
- **[Quantum3600/skeuo-compose](https://github.com/Quantum3600/skeuo-compose)** — SkeuoSwitch, SkeuoSlider (tactile). Good if you want a *hint* of physicality without going skeuomorphic.
- **[JumpingKeyCaps/ComposeRetroUiKit](https://github.com/JumpingKeyCaps/ComposeRetroUiKit)** — rotary knobs inspired by Marshall amps, LED bars, VU meters. Reference for *what to tone down* for an Ableton look.

### 9.2 Sliders / faders

- Stock `androidx.compose.material3.Slider` / `RangeSlider` cover linear; for vertical.mixer faders wrap in `Modifier.rotate` or implement a custom `VerticalSlider` (stock lacks vertical). compose-audio-controls also has fader primitives.

### 9.3 XY pads / 2D joysticks

- **[erz05/JoyStick](https://github.com/erz05/JoyStick)** — modernized Kotlin + Compose `Joystick` composable; dead zone, clamping, normalized output. Adapts cleanly to an XY pad.
- **[manalkaff/JetStick](https://github.com/manalkaff/JetStick)** — minimal Compose virtual joystick returning normalized x/y.
- **[yoimerdr/compose-virtualjoystick-multiplatform](https://github.com/yoimerdr/compose-virtualjoystick-multiplatform)** — Compose Multiplatform (Android/iOS/desktop/web) joystick.
- **[Swordfish90/PadKit](https://github.com/Swordfish90/PadKit)** / **[piepacker/JamPadCompose](https://github.com/piepacker/JamPadCompose)** — Compose Multiplatform gamepad/clip-pad container (ControlAnalog, ControlButton). Useful pattern for a session clip grid.

### 9.4 Full DAW reference implementations

- **[CuriousNikhil/compose-audio-plugin-ui](https://github.com/CuriousNikhil/compose-audio-plugin-ui)** — experimental audio plugin UI in Compose.
- **[ParsleyJ/dawrio](https://github.com/ParsleyJ/dawrio)** — experimental DAW for Android (Compose + AAudio/JNI). Reference architecture for a Compose DAW.

### 9.5 Recommendation for juji-synth

Start from **compose-audio-controls** for knobs/strips, fork its `Knob` and restyle to Ableton's flat-line aesthetic (single-color indicator, no bevel). Use **ChromaDial** if you want a cleaner, more customizable dial API. Build the clip-grid and XY pad on top of **PadKit**/**JoyStick** primitives. Lean on **Material 3's** tonal `surface-container` roles + your accent hue to get the elevation-as-lighter-grey hierarchy automatically.

---

## 10. Key Findings Summary (for the design direction)

1. **Greys, not black.** Background ~`#3C3C3C`; raised surfaces step *lighter* (`#7B7B7B` → `#D0D0D0`). Map this onto M3 `surface-container` tonal roles.
2. **Two-color system:** neutral grey structure + exactly one accent per context. Use the 14-hue clip grid only for *category* (drum/bass/lead…), never for decoration.
3. **Reductionism:** "a dial is a curved slider, a slider is a line." Flat, line-based, no skeuomorphic bevels.
4. **Density first, with a global Detail Level control** rather than many padding knobs.
5. **Honesty:** UI must represent signal flow — group controls by stage, place on/off toggles at routing boundaries.
6. **Typography:** proprietary Ableton Sans → use **Inter** (UI) + **JetBrains Mono** (numeric), Light/Medium/Regular.
7. **Icons:** single-weight outline set (Lucide/Phosphor), consistent ~1.5px stroke.
8. **Compose:** compose-audio-controls (knobs) + ChromaDial + PadKit/JoyStick; restyle flat.

---

## Sources

1. Eric Carl (Ableton Principal Designer), *Ableton Live and Designing for Authenticity* — <https://ericcarl.link/blog/ableton-live-and-designing-for-authenticity/> (and talk page <https://www.trojan-unicorn.com/talks/704-ableton-live-and-designing-for-authenticity>)
2. Ableton Live 10 color palette (70 hex) — <https://github.com/danhemerlein/ableton-colors>
3. Ableton Sans typeface (Letters from Sweden) — <https://lettersfromsweden.se/ableton/>
4. Ableton font file inventory + accessibility swaps — <https://github.com/madebycm/ableton-font-replacer>
5. Ableton Reference Manual v12 — Session/Arrangement/Clip View/Editing MIDI/Accessibility/Live Concepts: <https://www.ableton.com/en/live-manual/12/>
6. Live 12 Release Notes (UI/color changes) — <https://www.ableton.com/en/release-notes/live-12/>
7. M4L Production Guidelines (device UI consistency) — <https://github.com/Ableton/maxdevtools/blob/main/m4l-production-guidelines/m4l-production-guidelines.md>
8. Ableton Push 2 MIDI/Display Interface spec — <https://github.com/Ableton/push-interface/blob/main/doc/AbletonPush2MIDIDisplayInterface.asc>
9. Material Design dark theme spec (M2; rules carry to M3) — <https://m2.material.io/design/color/dark-theme.html>
10. Material Design 3 color system — <https://m3.material.io/styles/color/overview>
11. NIME: *Towards UI Guidelines for Musical Grid Interfaces* + *A Survey on Musical Grid Interface Standards* — <https://nime.pubpub.org/pub/grid-ui-guidelines/release/1> , <https://nime.org/proc/nime2022_53/>
12. AudioUI React lib — <https://github.com/cutoff/audio-ui> ; docs <https://cutoff.dev/audio-ui/docs/latest/getting-started/introduction>
13. Reba Design System (React) — <https://github.com/meincdev/rds>
14. compose-audio-controls (Compose) — <https://github.com/atsushieno/compose-audio-controls>
15. ChromaDial (Compose MP) — <https://github.com/sinasamaki/ChromaDial>
16. SkeuoCompose / ComposeRetroUiKit — <https://github.com/Quantum3600/skeuo-compose> , <https://github.com/JumpingKeyCaps/ComposeRetroUiKit>
17. Compose joysticks/XY — <https://github.com/erz05/JoyStick> , <https://github.com/manalkaff/JetStick> , <https://github.com/yoimerdr/compose-virtualjoystick-multiplatform> , <https://github.com/Swordfish90/PadKit>
18. Compose DAW reference — <https://github.com/CuriousNikhil/compose-audio-plugin-ui> , <https://github.com/ParsleyJ/dawrio>
19. Figma knob tools — <https://www.figma.com/community/plugin/1030842037042371583/knob-creator> , <https://www.figma.com/community/plugin/1563956754324486889/vst-gui-pro> , <https://www.figma.com/community/file/1401287748758736696/free-ui-knobs-w-t-f-sosa>
