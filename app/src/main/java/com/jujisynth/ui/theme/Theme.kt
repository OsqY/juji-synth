package com.jujisynth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HardwareSynthColorScheme = darkColorScheme(
    primary = KnobCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A3A4A),
    onPrimaryContainer = KnobCyan,
    secondary = KnobAmber,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3A3020),
    onSecondaryContainer = KnobAmber,
    tertiary = KnobGreen,
    onTertiary = Color.Black,
    background = BgGunmetal,
    onBackground = TextPrimary,
    surface = BgPanel,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF2A2A30),
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A3A40),
    outlineVariant = PanelHighlight,
)

@Composable
fun JujiSynthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HardwareSynthColorScheme,
        content = content
    )
}
