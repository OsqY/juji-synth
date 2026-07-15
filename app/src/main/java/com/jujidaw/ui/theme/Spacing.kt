package com.jujidaw.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing system for the Ableton-inspired dark, compact, information-dense aesthetic.
 *
 * 4dp base grid. All paddings, gaps, heights, and corner radii are multiples of 2dp,
 * with 4dp as the primary unit. See `docs/ui-design-overhaul-plan.md` §Spacing System.
 */
object Spacing {
    /** Hairline gaps, badge insets, icon-to-label. */
    val xs = 2.dp

    /** Default component gap, cell internal padding, divider-to-content. */
    val sm = 4.dp

    /** Standard component gap, toolbar vertical padding, tile padding. */
    val md = 8.dp

    /** Section padding, popover padding, card inset. */
    val lg = 12.dp

    /** Screen edge padding, dialog padding. */
    val xl = 16.dp

    /** Between major sections, dialog content top/bottom. */
    val xxl = 24.dp
}

// ── Corner radii ────────────────────────────────────────────────────

/** Chip, cell. */
val RadiusXs = 2.dp

/** Button, clip, knob cap. */
val RadiusSm = 4.dp

/** Panel, card. */
val RadiusMd = 6.dp

/** Toolbar, sheet corner. */
val RadiusLg = 8.dp

// ── Fixed structural heights / widths ───────────────────────────────

/** Single transport strip height. */
val TransportHeight = 48.dp

/** Per-screen toolbar height. */
val ToolbarHeight = 40.dp

/** Sequencer + timeline ruler cell. */
val StepCellSize = 40.dp

/** Minimum pad touch target (scales up). */
val PadMinSize = 64.dp

/** Keyboard white key min width. */
val KeyMinWhiteWidth = 28.dp

/** Default knob control size. */
val KnobDefaultSize = 48.dp

/** Fader track line width. */
val FaderTrackWidth = 4.dp

/** Fader cap width. */
val FaderCapWidth = 24.dp

/** Material/ADA minimum interactive touch target; nothing interactive below this. */
val TouchTargetMin = 44.dp
