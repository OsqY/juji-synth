package com.jujisynth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Real-time oscillator waveform preview showing a single cycle
 * of the selected waveform shape at panel-filling size.
 *
 * @param waveform 0=saw, 1=square, 2=triangle, 3=sine
 * @param accentColor color for the waveform stroke
 * @param modifier optional modifier
 */
@Composable
fun OscWaveformView(
    waveform: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val w = size.width
        val h = size.height
        val pad = 4f
        val drawW = w - pad * 2f
        val drawH = h - pad * 2f
        val cy = h * 0.5f
        val sw = 2.5f

        val path = when (waveform) {
            0 -> Path().apply { // Saw: diagonal up, sharp drop
                moveTo(pad, h - pad)
                lineTo(w - pad, pad)
                lineTo(w - pad, h - pad)
            }
            1 -> Path().apply { // Square: low-high-low, 50% duty
                val midX = pad + drawW * 0.5f
                moveTo(pad, cy)
                lineTo(pad, pad)
                lineTo(midX, pad)
                lineTo(midX, h - pad)
                lineTo(w - pad, h - pad)
                lineTo(w - pad, cy)
            }
            2 -> Path().apply { // Triangle: up then down
                moveTo(pad, h - pad)
                lineTo(pad + drawW * 0.5f, pad)
                lineTo(w - pad, h - pad)
            }
            3 -> Path().apply { // Sine: smooth sine wave (scaled to fit bounds)
                val steps = 40
                val phaseStep = (2.0 * Math.PI / steps).toFloat()
                for (i in 0..steps) {
                    val angle = i * phaseStep
                    val x = pad + (i.toFloat() / steps) * drawW
                    val y = cy - kotlin.math.sin(angle) * drawH * 0.45f
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            else -> Path()
        }

        drawPath(
            path = path,
            color = accentColor,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
