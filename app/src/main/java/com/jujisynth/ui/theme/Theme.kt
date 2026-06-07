package com.jujisynth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JujiSynthColorScheme = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurpleMid,
    onPrimaryContainer = PurpleLight,
    secondary = PurpleSecondary,
    onSecondary = Color.White,
    secondaryContainer = PurpleMid,
    onSecondaryContainer = PurpleLight,
    tertiary = PurpleAccent,
    onTertiary = PurpleDark,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgPanel,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = PurpleMid,
    outlineVariant = Color(0xFF3A2A5E),
)

@Composable
fun JujiSynthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JujiSynthColorScheme,
        content = content
    )
}
