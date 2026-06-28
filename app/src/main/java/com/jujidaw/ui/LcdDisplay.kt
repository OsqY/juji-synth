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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.ui.theme.*

/**
 * Small LCD-style rectangular value display with hardware aesthetic.
 */
@Composable
fun LcdDisplay(
    value: String,
    modifier: Modifier = Modifier,
    label: String = "",
    color: Color = LcdText,
    fontSize: TextUnit = 11.sp
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(LcdBackground)
            .border(1.dp, Color(0xFF2A2A30), RoundedCornerShape(3.dp))
            .drawBehind {
                // Inner shadow (top-left)
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.15f),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(3f)
                )
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 7.sp,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
        Text(
            text = value,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
