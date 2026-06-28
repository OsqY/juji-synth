package com.jujidaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.audio.SynthEngine
import com.jujidaw.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Real-time oscilloscope display with CRT aesthetic.
 * Polls audio engine waveform buffer at ~30fps.
 */
@Composable
fun OscilloscopeView(
    modifier: Modifier = Modifier
) {
    val scopeSize = 512
    val buffer = remember { FloatArray(scopeSize) }
    var waveform by remember { mutableStateOf(FloatArray(scopeSize)) }

    // Poll waveform data at 30fps
    LaunchedEffect(Unit) {
        while (true) {
            SynthEngine.getWaveform(buffer)
            waveform = buffer.copyOf()
            delay(33) // ~30fps
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ScopeBackground)
            .border(1.dp, Color(0xFF2A2A30), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        // CRT scanlines overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Scanline effect (faint horizontal lines)
            val lineSpacing = 3f
            var y = lineSpacing
            while (y < size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.02f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 0.5f
                )
                y += lineSpacing * 2
            }

            // Grid lines (faint)
            val gridColor = ScopeGrid
            for (i in 0..3) {
                val x = size.width * (i + 1) / 4f
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
            }
            for (i in 0..3) {
                val yPos = size.height * (i + 1) / 4f
                drawLine(gridColor, Offset(0f, yPos), Offset(size.width, yPos), strokeWidth = 0.5f)
            }
        }

        // Waveform trace
        Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            if (waveform.isEmpty()) return@Canvas

            val path = Path()
            val stepX = size.width / waveform.size.toFloat()
            val midY = size.height / 2f
            val ampScale = size.height * 0.4f

            path.moveTo(0f, midY - waveform[0] * ampScale)
            for (i in 1 until waveform.size) {
                val x = i * stepX
                val y = midY - waveform[i] * ampScale
                path.lineTo(x, y.coerceIn(0f, size.height))
            }

            drawPath(
                path = path,
                color = ScopeTrace,
                style = Stroke(width = 1.5f)
            )

            // Glow effect (slightly wider, transparent trace behind)
            drawPath(
                path = path,
                color = ScopeTrace.copy(alpha = 0.2f),
                style = Stroke(width = 4f)
            )
        }

        // Label
        Text(
            text = "SCOPE",
            color = TextMuted,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(3.dp)
        )
    }
}
