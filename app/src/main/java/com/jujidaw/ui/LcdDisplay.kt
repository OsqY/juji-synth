package com.jujidaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.ui.theme.*

/**
 * Small flat LCD-style value readout: restrained mono on a dark panel.
 *
 * SurfaceContainerLow background, OutlineVariant border, OnSurface mono value (JetBrains
 * Mono). No inner shadow, no green-CRT glow. See plan Phase 2 step 3 and Component
 * Inventory #8.
 */
@Composable
fun LcdDisplay(
    value: String,
    modifier: Modifier = Modifier,
    label: String = "",
    color: Color = OnSurface,
    fontSize: TextUnit = 11.sp,
) {
    Box(
        modifier =
            modifier
                .height(28.dp)
                .clip(RoundedCornerShape(RadiusXs))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusXs))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = TextSecondary,
                style = CaptionSmall,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        Text(
            text = value,
            color = color,
            fontFamily = LcdFontFamily,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
