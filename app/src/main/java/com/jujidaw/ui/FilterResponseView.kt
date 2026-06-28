package com.jujidaw.ui

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
import com.jujidaw.ui.theme.KnobCyan
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filter frequency response curve visualizing cutoff, resonance peak,
 * and filter mode (LPF, HPF, BPF).
 *
 * Computes gain at ~100 frequency points using the SVF transfer function
 * approximation for the selected mode.
 */
@Composable
fun FilterResponseView(
    cutoff: Float,        // 0.0-1.0
    resonance: Float,     // 0.0-1.0
    mode: Int,            // 0=LPF, 1=HPF, 2=BPF
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
        val numPoints = 100

        // Map 0-1 cutoff to frequency (20-20000 Hz) using same curve as filter
        val fc = 20.0 + (20000.0 - 20.0) * cutoff * cutoff
        val f = 2.0 * sin(PI * fc / 44100.0)
        val q = 0.5 + 19.5 * resonance

        // Compute gain at each frequency point (log scale)
        val path = Path()
        var first = true

        for (i in 0..numPoints) {
            val t = i.toFloat() / numPoints
    // Log frequency sweep from 10Hz to 22050Hz
    val freq = 10.0 * 2205.0.pow(t.toDouble())

            // SVF coefficient for this frequency
            val fTest = 2.0 * sin(PI * freq / 44100.0)

            // Compute SVF transfer function magnitude
            // Using: H(s) = output / input for the SVF structure
            val f2 = fTest * fTest
            val f02 = f * f
            val denom = f02 * f02 + f2 * f2 + (q * q - 2.0) * f02 * f2
            val denomSafe = if (denom < 1e-15) 1e-15 else denom

            val gain = when (mode) {
                0 -> { // LPF: H = f0^2 / (s^2 + s*q + f0^2)
                    val real = f02 * (f02 - f2)
                    val imag = f02 * q * fTest
                    sqrt(real * real + imag * imag) / denomSafe
                }
                1 -> { // HPF: H = s^2 / (s^2 + s*q + f0^2)
                    val real = f2 * (f2 - f02)
                    val imag = f2 * q * fTest
                    sqrt(real * real + imag * imag) / denomSafe
                }
                2 -> { // BPF: H = s*q / (s^2 + s*q + f0^2)
                    val real = q * fTest * (f02 - f2)
                    val imag = q * fTest * q * fTest
                    sqrt(real * real + imag * imag) / denomSafe
                }
                else -> 0.0
            }

            // Map gain to dB with range -48dB to +12dB, clamp to visible range
            val gainDb = 20.0 * log10(gain.coerceAtLeast(1e-6))
            val normalizedGain = ((gainDb + 48.0) / 60.0).coerceIn(0.0, 1.0)

            val x = pad + t * drawW
            val y = (h - pad) - normalizedGain.toFloat() * drawH

            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = KnobCyan,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Horizontal center line (0dB reference)
        val refY = cy
        drawLine(
            color = KnobCyan.copy(alpha = 0.15f),
            start = Offset(pad, refY),
            end = Offset(w - pad, refY),
            strokeWidth = 1f
        )
    }
}
