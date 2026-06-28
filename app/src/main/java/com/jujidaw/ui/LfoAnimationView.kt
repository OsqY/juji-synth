package com.jujidaw.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

/**
 * Animated LFO waveform preview that oscillates in real-time
 * at the configured rate with the selected shape.
 *
 * @param waveform 0=Sin, 1=Sqr, 2=Saw, 3=Tri, 4=Rnd
 * @param rate 0.0-1.0 LFO rate (mapped to 0.01-50 Hz)
 * @param depth 0.0-1.0 LFO depth (amplitude of oscillation)
 * @param accentColor color for the waveform
 * @param modifier optional modifier
 */
@Composable
fun LfoAnimationView(
    waveform: Int,
    rate: Float,
    depth: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    // Map 0-1 rate to frequency (0.1-10 Hz for visual clarity)
    val freq = 0.1f + 9.9f * rate
    var phase by remember { mutableStateOf(0f) }

    // Animate phase continuously
    LaunchedEffect(freq) {
        var lastFrameMs = 0L
        while (true) {
            withInfiniteAnimationFrameMillis {
                val deltaMs = if (lastFrameMs == 0L) 0L else it - lastFrameMs
                lastFrameMs = it
                val deltaPhase = (deltaMs.toFloat() / 1000f) * freq
                phase = (phase + deltaPhase) % 1f
            }
        }
    }

    val amplitude = depth.coerceIn(0.05f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val w = size.width
        val h = size.height
        val pad = 4f
        val drawW = w - pad * 2f
        val drawH = h - pad * 2f
        val cy = h * 0.5f
        val steps = 50
        val sw = 2.5f

        val path = Path()
        var first = true

        // Generate one cycle of the waveform, offset by phase
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            // Apply phase offset so waveform appears to oscillate
            val angle = (t + phase) % 1f
            val x = pad + t * drawW

            val value = when (waveform) {
                0 -> sin(angle * 2f * PI.toFloat()) // Sine
                1 -> if (angle < 0.5f) 1f else -1f // Square
                2 -> 1f - 2f * angle // Saw (falling)
                3 -> 4f * kotlin.math.abs(angle - 0.5f) - 1f // Triangle
                4 -> { // Random sample-and-hold (steps)
                    val stepIdx = (angle * 8).toInt()
                    val seeds = listOf(
                        0.2f, -0.8f, 0.6f, -0.3f, 0.9f, -0.5f, 0.1f, -0.7f
                    )
                    seeds[stepIdx % seeds.size]
                }
                else -> 0f
            }

            val y = cy - value * drawH * 0.45f * amplitude

            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = accentColor,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Horizontal center line
        drawLine(
            color = accentColor.copy(alpha = 0.12f),
            start = Offset(pad, cy),
            end = Offset(w - pad, cy),
            strokeWidth = 1f
        )
    }
}
