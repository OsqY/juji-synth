package com.jujisynth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jujisynth.ui.theme.*

@Composable
fun HardwareChassis(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E1835),  // lighter center
                        Color(0xFF150E28),  // darker edge
                    )
                )
            )
            .border(
                2.dp,
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF3A2A5E), // light edge (top-left)
                        Color(0xFF1A0D2E), // dark edge (bottom-right)
                        Color(0xFF2D1B4E),
                        Color(0xFF0D0520),
                    )
                ),
                RoundedCornerShape(12.dp)
            )
            .padding(8.dp)
    ) {
        // Screw holes at corners
        Canvas(modifier = Modifier.matchParentSize()) {
            val cornerOffset = 14f
            val screwRadius = 3f
            val positions = listOf(
                Offset(cornerOffset, cornerOffset),
                Offset(size.width - cornerOffset, cornerOffset),
                Offset(cornerOffset, size.height - cornerOffset),
                Offset(size.width - cornerOffset, size.height - cornerOffset)
            )
            for (pos in positions) {
                // Screw hole outer ring (dark)
                drawCircle(
                    color = Color(0xFF0D0520),
                    radius = screwRadius + 1f,
                    center = pos
                )
                // Screw hole inner (slightly lighter)
                drawCircle(
                    color = Color(0xFF2A1A4E),
                    radius = screwRadius,
                    center = pos
                )
                // Screw highlight (top-left arc)
                drawCircle(
                    color = Color(0xFF4A3A6E).copy(alpha = 0.5f),
                    radius = screwRadius * 0.5f,
                    center = Offset(pos.x - 0.5f, pos.y - 0.5f)
                )
            }
        }
        content()
    }
}

@Composable
fun SectionDivider(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        PurpleMid.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
            .padding(vertical = 4.dp)
    )
}
