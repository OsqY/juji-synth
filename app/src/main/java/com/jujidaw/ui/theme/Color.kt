package com.jujidaw.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Juji-Synth color tokens — Ableton-inspired dark, compact DAW palette.
 *
 * Depth model: panels raise **lighter** along a grey ramp (Ableton inverse hierarchy),
 * never darker-than-black. Two-color system: neutral grey structure + one amber accent
 * (`Primary`), with hue reserved for meaning (clips, tracks, pad banks) and never decorative.
 *
 * See `docs/ui-design-overhaul-plan.md` §Color System.
 */

// ── Backgrounds (3 levels) — the structural grey ramp ────────────────

/** Deepest: screen root, bottom of scroll, gaps between screens. */
val Bg0 = Color(0xFF323234)

/** Base grid: timeline body, sequencer grid, mixer backdrop (Ableton canonical ~#3C3C3C). */
val Bg1 = Color(0xFF3C3C3E)

/** Raised panel: transport bar, toolbars, device chrome. */
val Bg2 = Color(0xFF47474A)

// ── Surfaces — M3 tonal container steps (lighter = more elevated) ───

/** = Bg1. Default container. */
val Surface = Color(0xFF3C3C3E)

/** Card/strip background, recessed card. */
val SurfaceContainerLow = Color(0xFF414143)

/** Device panels, mixer channel strips. */
val SurfaceContainer = Color(0xFF48484B)

/** Popovers, dropdown menus, bottom sheets. */
val SurfaceContainerHigh = Color(0xFF505053)

/** Active/hovered container, selected clip cell fill. */
val SurfaceContainerHighest = Color(0xFF59595C)

/** Body text at ~87% white emphasis. */
val OnSurface = Color(0xFFE6E6E6)

/** Secondary labels, axis labels, muted readouts. */
val OnSurfaceVariant = Color(0xFFA9A9A9)

/** Borders/dividers (visible but quiet). */
val Outline = Color(0xFF5B5B5E)

/** Recessed borders, subtle separators. */
val OutlineVariant = Color(0xFF3A3A3D)

// ── Primary / Secondary / Accent (the "two-color + reserve" system) ─

/** The single accent — Ableton amber-yellow. Selection/active/primary CTA. */
val Primary = Color(0xFFFEEE4A)
val OnPrimary = Color(0xFF1A1A1A)

/** Functional category accent #2 — audio/filter signal flow only. */
val Secondary = Color(0xFF1EBEAF)
val OnSecondary = Color(0xFF06231F)

/** Lighter amber halo — glow/active-ring. Low-alpha only, never a solid fill. */
val Accent = Color(0xFFF7F384)
val OnAccent = Color(0xFF1A1A1A)

// ── State colors (functional, from the Ableton hue grid) ────────────

/** Recording / record-arm / error warning. */
val StateRecording = Color(0xFFFC393D)
val OnStateRecording = Color(0xFF1A0A0A)

/** Playback active / metronome-on / "now playing" launch triangle. */
val StateActive = Color(0xFF44C121)
val OnStateActive = Color(0xFF06140A)

/** Solo toggle — warm amber, distinct from selection Primary. */
val StateSolo = Color(0xFFF4C430)
val OnStateSolo = Color(0xFF1A1405)

/** MIDI-learn ring — pink/rose, distinct from record red. */
val MidiLearn = Color(0xFFFD42D2)
val OnMidiLearn = Color(0xFF240A1A)

// ── Text (primary / secondary / disabled) ───────────────────────────

/** ~87% white on Bg1; contrast > 15:1 — passes WCAG AA. */
val TextPrimary = Color(0xFFE6E6E6)

/** ~60% white — labels, hints, secondary readouts (= OnSurfaceVariant). */
val TextSecondary = Color(0xFFA9A9A9)

/** ~38% white — disabled labels. */
val TextDisabled = Color(0xFF6E6E6E)

// ── Disabled control treatments (Material dark-theme spec) ──────────
val DisabledFill = OnSurface.copy(alpha = 0.12f)
val DisabledText = OnSurface.copy(alpha = 0.38f)

// ── Keyboard ────────────────────────────────────────────────────────
val KeyWhite = Color(0xFFD0D0D0)
val KeyBlack = Color(0xFF2A2A2C)

// ── Pad bank colors (category hues drawn from the grid) ─────────────

/** Bank A pads (Teal). */
val PadBankA = Color(0xFF1EBEAF)

/** Bank B pads (Indigo). */
val PadBankB = Color(0xFF8870E1)

/** Struck pad ring = the global active/selected accent, not the bank hue. */
val PadActiveRing = Primary

// ── Clip colors per track — the 14-hue × 5-shade grid ───────────────
// 14 columns (col 0..13) × 5 rows. Row index semantics (JSON natural order):
//   0 pastel (dim/ghost), 1 saturated representative (track color),
//   2 midtint, 3 muted mid (inactive), 4 deep (hover/active-fill).
// Column 13 is the Neutral grey ramp (white → black); excluded from clip cycling.
// Source: docs/research/ableton-color-palette.json (70 colors verbatim).
val ClipColors: List<List<Color>> =
    listOf(
        listOf(Color(0xFFFD95A7), Color(0xFFFC393D), Color(0xFFE0685D), Color(0xFFC5928C), Color(0xFFAD3436)),
        listOf(Color(0xFFFDA43A), Color(0xFFF46C20), Color(0xFFFDA378), Color(0xFFB68259), Color(0xFFA75135)),
        listOf(Color(0xFFCB9834), Color(0xFF98714E), Color(0xFFD2AC75), Color(0xFF98836B), Color(0xFF714F42)),
        listOf(Color(0xFFF7F384), Color(0xFFFEEE4A), Color(0xFFEDFEB2), Color(0xFFBFB96E), Color(0xFFDAC229)),
        listOf(Color(0xFFC0F932), Color(0xFF8BFD70), Color(0xFFD2E39C), Color(0xFFA6BC25), Color(0xFF85952B)),
        listOf(Color(0xFF32FD42), Color(0xFF44C121), Color(0xFFBACF79), Color(0xFF7EAF52), Color(0xFF559E38)),
        listOf(Color(0xFF39FDAA), Color(0xFF1EBEAF), Color(0xFF9CC38F), Color(0xFF8AC2BA), Color(0xFF1B9B8E)),
        listOf(Color(0xFF65FEE8), Color(0xFF31E9FD), Color(0xFFD5FDE2), Color(0xFF9CB3C3), Color(0xFF266383)),
        listOf(Color(0xFF8DC6FD), Color(0xFF22A5EB), Color(0xFFCEF1F8), Color(0xFF86A5C1), Color(0xFF1B3393)),
        listOf(Color(0xFF5682E1), Color(0xFF127EBE), Color(0xFFB9C2E2), Color(0xFF8494CA), Color(0xFF3154A0)),
        listOf(Color(0xFF93A9FC), Color(0xFF8870E1), Color(0xFFCDBCE3), Color(0xFFA596B4), Color(0xFF624EAB)),
        listOf(Color(0xFFD670E2), Color(0xFFB579C4), Color(0xFFAE9AE3), Color(0xFFBEA0BD), Color(0xFFA24EAB)),
        listOf(Color(0xFFE3569F), Color(0xFFFD42D2), Color(0xFFE5DCE1), Color(0xFFBB7296), Color(0xFFCA326E)),
        listOf(Color(0xFFFFFFFF), Color(0xFFD0D0D0), Color(0xFFA9A9A9), Color(0xFF7B7B7B), Color(0xFF3C3C3C)),
    )

/**
 * Representative track-color hue per chromatic column (plan §Color "row-2 anchors").
 * Per the plan: "cycle the 13 non-neutral columns (skip Neutral)". The representative
 * anchor table enumerates 12 chromatic hues + Neutral; the Neutral entry is skipped,
 * leaving these 12 chromatic hues cycled by track index.
 */
val ClipHueAnchors: List<Color> =
    listOf(
        Color(0xFFFC393D), // Red/Pink
        Color(0xFFF46C20), // Orange
        Color(0xFFCB9834), // Amber/Brass
        Color(0xFFFEEE4A), // Yellow
        Color(0xFFC0F932), // Lime
        Color(0xFF44C121), // Green
        Color(0xFF1EBEAF), // Teal/Mint
        Color(0xFF22A5EB), // Sky
        Color(0xFF127EBE), // Blue
        Color(0xFF8870E1), // Indigo/Violet
        Color(0xFFB579C4), // Magenta
        Color(0xFFFD42D2), // Pink/Rose
    )

/** Default track/clip color: cycle the chromatic hues (Neutral skipped) by track index. */
fun clipHue(trackIndex: Int): Color {
    val size = ClipHueAnchors.size
    val idx = ((trackIndex % size) + size) % size
    return ClipHueAnchors[idx]
}

// ══════════════════════════════════════════════════════════════════════
// DEPRECATED ALIASES — old "hardware synth" tokens, mapped to new tokens.
// Kept so un-migrated components still compile during the phase-by-phase overhaul.
// Each points to its replacement per the plan's mapping table. Do not use in new code.
// ══════════════════════════════════════════════════════════════════════

// ── Chassis / Background ─────────────────────────────────────────────
@Deprecated("Use Bg0", ReplaceWith("Bg0"))
val BgGunmetal = Bg0

@Deprecated("Use SurfaceContainer", ReplaceWith("SurfaceContainer"))
val BgPanel = SurfaceContainer

@Deprecated("Use OutlineVariant", ReplaceWith("OutlineVariant"))
val PanelHighlight = OutlineVariant

@Deprecated("Removed: depth = lighter surface, not dark bevel. Use Bg0.", ReplaceWith("Bg0"))
val PanelShadow = Bg0

@Deprecated("Use OutlineVariant", ReplaceWith("OutlineVariant"))
val PanelDivider = OutlineVariant

// ── Screw heads (hardware styling removed) ───────────────────────────
@Deprecated("Removed: no screw chrome. Use Outline.", ReplaceWith("Outline"))
val ScrewHead = Outline

@Deprecated("Removed: no screw chrome. Use SurfaceContainerHighest.", ReplaceWith("SurfaceContainerHighest"))
val ScrewHighlight = SurfaceContainerHighest

@Deprecated("Removed: no screw chrome. Use OutlineVariant.", ReplaceWith("OutlineVariant"))
val ScrewSlot = OutlineVariant

// ── Text ─────────────────────────────────────────────────────────────
// (TextPrimary / TextSecondary are redefined to new hex above, not deprecated.)
@Deprecated("Use TextDisabled", ReplaceWith("TextDisabled"))
val TextMuted = TextDisabled

// ── Knob accents (collapse 6 decorative accents → 1 amber + functional state) ─
@Deprecated("Use Primary", ReplaceWith("Primary"))
val KnobAmber = Primary

@Deprecated("Use Secondary", ReplaceWith("Secondary"))
val KnobCyan = Secondary

@Deprecated("Use StateActive", ReplaceWith("StateActive"))
val KnobGreen = StateActive

@Deprecated("Use MidiLearn", ReplaceWith("MidiLearn"))
val KnobPink = MidiLearn

@Deprecated("Fold into StateSolo", ReplaceWith("StateSolo"))
val KnobOrange = StateSolo

@Deprecated("Use StateRecording", ReplaceWith("StateRecording"))
val KnobRed = StateRecording

// ── LED ring glows → single Accent halo, low-alpha ───────────────────
@Deprecated("Use Accent (low-alpha only)", ReplaceWith("Accent"))
val LedAmber = Accent.copy(alpha = 0.25f)

@Deprecated("Use Accent (low-alpha only)", ReplaceWith("Accent"))
val LedCyan = Accent.copy(alpha = 0.25f)

@Deprecated("Use Accent (low-alpha only)", ReplaceWith("Accent"))
val LedGreen = Accent.copy(alpha = 0.25f)

@Deprecated("Use Accent (low-alpha only)", ReplaceWith("Accent"))
val LedPink = Accent.copy(alpha = 0.25f)

@Deprecated("Use Accent (low-alpha only)", ReplaceWith("Accent"))
val LedOrange = Accent.copy(alpha = 0.25f)

@Deprecated("Use Accent (low-alpha only)", ReplaceWith("Accent"))
val LedRed = Accent.copy(alpha = 0.25f)

// ── Knob metal ───────────────────────────────────────────────────────
@Deprecated("Use SurfaceContainer", ReplaceWith("SurfaceContainer"))
val KnobFace = SurfaceContainer

@Deprecated("Use Outline", ReplaceWith("Outline"))
val KnobRim = Outline

@Deprecated("Use OnSurface", ReplaceWith("OnSurface"))
val KnobIndicator = OnSurface

// ── LCD Display ──────────────────────────────────────────────────────
@Deprecated("Use SurfaceContainerLow", ReplaceWith("SurfaceContainerLow"))
val LcdBackground = SurfaceContainerLow

@Deprecated("Use OnSurface (mono)", ReplaceWith("OnSurface"))
val LcdText = OnSurface

@Deprecated("Removed: no glow", ReplaceWith("Color.Transparent"))
val LcdGlow = Color.Transparent

// ── Keyboard ──────────────────────────────────────────────────────────
@Deprecated("Use Primary", ReplaceWith("Primary"))
val KeyPressed = Primary

@Deprecated("Use Primary", ReplaceWith("Primary"))
val KeyPlaying = Primary

@Deprecated("Use OutlineVariant", ReplaceWith("OutlineVariant"))
val KeyBorder = OutlineVariant

// ── Sequencer ────────────────────────────────────────────────────────
@Deprecated("Use Primary or clip hue", ReplaceWith("Primary"))
val SeqStepActive = Primary

@Deprecated("Use SurfaceContainerLow", ReplaceWith("SurfaceContainerLow"))
val SeqStepInactive = SurfaceContainerLow

@Deprecated("Use Primary", ReplaceWith("Primary"))
val SeqStepCurrent = Primary

// ── Oscilloscope ─────────────────────────────────────────────────────
@Deprecated("Use SurfaceContainerLow", ReplaceWith("SurfaceContainerLow"))
val ScopeBackground = SurfaceContainerLow

@Deprecated("Use Secondary", ReplaceWith("Secondary"))
val ScopeTrace = Secondary

@Deprecated("Use OutlineVariant", ReplaceWith("OutlineVariant"))
val ScopeGrid = OutlineVariant

// ── Transport / buttons ───────────────────────────────────────────────
@Deprecated("Use StateActive", ReplaceWith("StateActive"))
val TransportGreen = StateActive

@Deprecated("Use StateRecording", ReplaceWith("StateRecording"))
val TransportRed = StateRecording

@Deprecated("Use StateSolo", ReplaceWith("StateSolo"))
val TransportAmber = StateSolo

// ── MIDI learn ───────────────────────────────────────────────────────
@Deprecated("Use MidiLearn (low-alpha glow)", ReplaceWith("MidiLearn"))
val MidiLearnGlow = MidiLearn.copy(alpha = 0.25f)
