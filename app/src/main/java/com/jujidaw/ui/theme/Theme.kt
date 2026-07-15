package com.jujidaw.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * M3 dark color scheme mapped to the Ableton-inspired token vocabulary.
 *
 * Depth = lighter surfaces, not shadows. Neutral grey structure + single amber accent.
 * `surfaceTint` is a neutral grey (not the amber Primary) so elevated surfaces stay grey
 * instead of picking up an accent tint.
 */
private val JujiDawColorScheme =
    darkColorScheme(
        // Accent
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = SurfaceContainer,
        onPrimaryContainer = Primary,
        inversePrimary = Primary,
        secondary = Secondary,
        onSecondary = OnSecondary,
        secondaryContainer = SurfaceContainerLow,
        onSecondaryContainer = Secondary,
        tertiary = Accent,
        onTertiary = OnAccent,
        tertiaryContainer = SurfaceContainerLow,
        onTertiaryContainer = Accent,
        // Structure
        background = Bg1,
        onBackground = OnSurface,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = SurfaceContainerLow,
        onSurfaceVariant = OnSurfaceVariant,
        surfaceTint = SurfaceContainer, // neutral lift, no amber tint
        inverseSurface = SurfaceContainerHighest,
        inverseOnSurface = Bg1,
        // Container tiers (lighter = more elevated)
        surfaceContainerLowest = Bg1,
        surfaceContainerLow = SurfaceContainerLow,
        surfaceContainer = SurfaceContainer,
        surfaceContainerHigh = SurfaceContainerHigh,
        surfaceContainerHighest = SurfaceContainerHighest,
        // Lines
        outline = Outline,
        outlineVariant = OutlineVariant,
        // State / functional
        error = StateRecording,
        onError = OnStateRecording,
        errorContainer = StateRecording.copy(alpha = 0.25f),
        onErrorContainer = OnStateRecording,
        scrim = Color(0xFF000000),
    )

@Composable
fun JujiDawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JujiDawColorScheme,
        typography = SynthTypography,
        content = content,
    )
}
