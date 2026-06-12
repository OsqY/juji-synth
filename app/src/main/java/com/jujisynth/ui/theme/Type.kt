package com.jujisynth.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Monospace font for LCD-style value displays (system monospace fallback)
val LcdFontFamily = FontFamily.Monospace

// Condensed sans-serif for compact labels
val LabelFontFamily = FontFamily.Default

val SynthTypography = Typography(
    // Section headers (e.g., "OSC 1", "FILTER")
    titleSmall = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
    ),
    // Knob value display (LCD style)
    bodySmall = TextStyle(
        fontFamily = LcdFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
    ),
    // Knob label (below knob)
    labelSmall = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 8.sp,
        letterSpacing = 0.3.sp,
    ),
    // Parameter value readout
    bodyMedium = TextStyle(
        fontFamily = LcdFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)
