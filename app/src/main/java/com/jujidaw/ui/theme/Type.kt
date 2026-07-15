package com.jujidaw.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jujidaw.R

/**
 * Juji-Synth typography — Ableton-inspired DAW type system.
 *
 * Three bundled Google Fonts (OFL, shipped as res/font for offline reliability):
 *   - IBM Plex Sans     — UI body / labels / values (strong tabular figures)
 *   - Space Grotesk     — display / section headers / screen titles
 *   - JetBrains Mono    — monospace / LCD / parameter readouts (tabular by default)
 *
 * All numeric/LCD styles enable `fontFeatureSettings = "tnum"` so values don't jitter
 * while dragging. Min body/label size is 9sp; knob label bumped 8sp → 10sp for legibility.
 *
 * See `docs/ui-design-overhaul-plan.md` §Typography System.
 */

// ── Bundled font families ────────────────────────────────────────────

/** UI body / labels / values — IBM Plex Sans Regular(400) + SemiBold(600). */
val IbmPlexSans =
    FontFamily(
        Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    )

/** Display / section headers — Space Grotesk Light(300) + Medium(500). */
val SpaceGrotesk =
    FontFamily(
        Font(R.font.space_grotesk_light, FontWeight.Light),
        Font(R.font.space_grotesk_medium, FontWeight.Medium),
    )

/** Monospace / LCD / readouts — JetBrains Mono Regular(400) + Bold(700). */
val JetBrainsMono =
    FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )

// Back-compat aliases pointing to the new families.
val LabelFontFamily: FontFamily = IbmPlexSans
val LcdFontFamily: FontFamily = JetBrainsMono

// ── Type scale ───────────────────────────────────────────────────────
private const val TNUM = "tnum"

/** Screen titles, "MIXER"/"SEQUENCER" section bars. */
val DisplaySmall =
    TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        letterSpacing = 0.sp,
    )

/** Dialog titles, popover headers. */
val TitleLarge =
    TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
    )

/** Section headers ("OSC 1", "FILTER") — ALL CAPS. */
val TitleSmall =
    TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
    )

/** Body text, list items. */
val BodyMedium =
    TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    )

/** Knob value chip text (tabular figures). */
val BodySmall =
    TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = TNUM,
    )

/** Knob labels, toggles (M/S/R), dropdown labels. */
val LabelSmall =
    TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.3.sp,
    )

/** Secondary labels, captions, row labels (P1…P16). */
val CaptionSmall =
    TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        letterSpacing = 0.2.sp,
    )

/** Parameter readouts in knob/fader tooltips, LCD cells (tabular figures). */
val MonoMedium =
    TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = TNUM,
    )

/** Transport BPM, bar|beat|step position (tabular figures). */
val MonoLarge =
    TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = TNUM,
    )

/** M3 Typography mapping for components that resolve styles via MaterialTheme. */
val SynthTypography =
    Typography(
        displaySmall = DisplaySmall,
        titleLarge = TitleLarge,
        titleSmall = TitleSmall,
        bodyMedium = BodyMedium,
        bodySmall = BodySmall,
        labelSmall = LabelSmall,
    )
