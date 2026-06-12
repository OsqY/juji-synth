package com.jujisynth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujisynth.ui.theme.*

/**
 * Hardware-style panel with brushed metal look, screw heads, and section title.
 */
@Composable
fun SynthPanel(
    title: String,
    accentColor: Color = KnobCyan,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BgPanel)
            .drawBehind {
                // Top-edge highlight (bevel effect)
                drawLine(
                    color = PanelHighlight,
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = 1f
                )
                // Bottom-edge shadow
                drawLine(
                    color = PanelShadow,
                    start = Offset(0f, size.height - 1f),
                    end = Offset(size.width, size.height - 1f),
                    strokeWidth = 1f
                )
                // Screw heads - top left
                drawScrew(Offset(10f, 10f))
                // Screw heads - top right
                drawScrew(Offset(size.width - 10f, 10f))
                // Screw heads - bottom left
                drawScrew(Offset(10f, size.height - 10f))
                // Screw heads - bottom right
                drawScrew(Offset(size.width - 10f, size.height - 10f))
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = title,
            color = accentColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        // Divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .drawBehind {
                    drawLine(
                        color = PanelDivider,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1f
                    )
                }
        )
        // Inner content area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .background(Color(0xFF1A1A1E), RoundedCornerShape(4.dp))
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

private fun DrawScope.drawScrew(center: Offset) {
    // Outer screw head
    drawCircle(
        color = ScrewHead,
        radius = 5f,
        center = center
    )
    // Highlight dot
    drawCircle(
        color = ScrewHighlight,
        radius = 2f,
        center = center + Offset(-0.5f, -0.5f)
    )
    // Screw slot line
    drawLine(
        color = ScrewSlot,
        start = center + Offset(-3f, 0f),
        end = center + Offset(3f, 0f),
        strokeWidth = 1f
    )
}
