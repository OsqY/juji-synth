package com.jujidaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.jujidaw.ui.theme.*

/**
 * Hardware chassis wrapper with dark gunmetal aesthetic.
 * Includes screw heads at corners, subtle grid pattern, and bevelled edges.
 */
@Composable
fun HardwareChassis(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                // Top/left highlight (bevel illusion)
                drawLine(
                    color = PanelHighlight,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = PanelHighlight,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                // Bottom/right shadow (bevel illusion)
                drawLine(
                    color = PanelShadow,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = PanelShadow,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )

                // Subtle grid pattern (faint lines at 30dp spacing)
                val gridSpacing = 30.dp.toPx()
                val gridColor = Color.White.copy(alpha = 0.03f)
                // Vertical lines
                var x = gridSpacing
                while (x < size.width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += gridSpacing
                }
                // Horizontal lines
                var y = gridSpacing
                while (y < size.height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += gridSpacing
                }

                // 4 screw heads at corners
                val cornerOffset = 14.dp.toPx()
                val screwOuterRadius = 3.dp.toPx()
                val screwInnerRadius = 1.dp.toPx()
                val screwSlotLen = 2.5.dp.toPx()
                val positions = listOf(
                    Offset(cornerOffset, cornerOffset),
                    Offset(size.width - cornerOffset, cornerOffset),
                    Offset(cornerOffset, size.height - cornerOffset),
                    Offset(size.width - cornerOffset, size.height - cornerOffset)
                )
                for (pos in positions) {
                    // Outer screw head
                    drawCircle(
                        color = ScrewHead,
                        radius = screwOuterRadius,
                        center = pos
                    )
                    // Inner highlight dot
                    drawCircle(
                        color = ScrewHighlight,
                        radius = screwInnerRadius,
                        center = Offset(pos.x - 0.5f, pos.y - 0.5f)
                    )
                    // Screw slot (tiny line through center)
                    drawLine(
                        color = ScrewSlot,
                        start = Offset(pos.x - screwSlotLen, pos.y),
                        end = Offset(pos.x + screwSlotLen, pos.y),
                        strokeWidth = 1f
                    )
                }
            }
            .background(BgGunmetal)
            .border(1.dp, PanelHighlight.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        content = content
    )
}