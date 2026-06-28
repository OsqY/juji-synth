package com.jujidaw.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

// ──────────────────────────────────────────────
// Tooltip system
// ──────────────────────────────────────────────

/**
 * Holds the current tooltip display state.
 * Used together with [ParameterTooltip] to show context-sensitive help.
 */
data class TooltipData(
    val visible: Boolean = false,
    val name: String = "",
    val description: String = "",
    val value: String = ""
)

/**
 * A compact tooltip popup styled to match the hardware synth aesthetic.
 * Place inside a full-size Box overlay to position it freely.
 *
 * Example usage:
 * ```kotlin
 * Box(modifier = Modifier.fillMaxSize()) {
 *     // ... main UI ...
 *     if (tooltipData.visible) {
 *         ParameterTooltip(
 *             text = buildString {
 *                 append(tooltipData.name)
 *                 if (tooltipData.value.isNotEmpty()) append(": ${tooltipData.value}")
 *             },
 *             modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
 *         )
 *     }
 * }
 * ```
 */
@Composable
fun ParameterTooltip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BgGunmetal.copy(alpha = 0.95f))
            .border(1.dp, KnobCyan, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Descriptions for synth parameters, used by the tooltip system.
 * Map keys match knob/slider labels displayed in the UI.
 */
val paramTooltips: Map<String, String> = mapOf(
    "OSC1 Level" to "Controls the volume of Oscillator 1",
    "OSC2 Level" to "Controls the volume of Oscillator 2",
    "Cutoff" to "Sets the frequency point where the filter begins to attenuate",
    "Resonance" to "Emphasizes frequencies at the cutoff point. High values cause self-oscillation",
    "Attack" to "Time for the sound to reach peak level after pressing a key",
    "Decay" to "Time for the sound to fall from peak to sustain level",
    "Sustain" to "Level held while the key is pressed",
    "Release" to "Time for the sound to fade after releasing the key",
    "LFO Rate" to "Speed of the low-frequency modulation",
    "LFO Depth" to "Intensity of the modulation effect",
    "Reverb Mix" to "Balance between dry (original) and wet (reverberated) signal",
    "Delay Mix" to "Balance between dry and delayed signal",
    "Delay Time" to "Time between echo repeats",
    "Delay Fdbk" to "Number of echo repeats",
    "Dist Drive" to "Amount of harmonic saturation applied to the signal",
    "Volume" to "Master output volume",
    "Tempo" to "Sequencer playback speed in beats per minute",
)

/**
 * Reusable knob component with hardware synth aesthetic.
 * Drag up/down to change value.
 * Renders a realistic 3D knob with shadow, metallic rim, radial gradient body,
 * indicator line, and arc track.
 */
@Composable
fun SynthKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    valueDisplay: String = "",
    accentColor: Color = KnobAmber,
    size: Dp = 80.dp,
    minValue: Float = 0.0f,
    maxValue: Float = 1.0f,
    // MIDI Learn parameters
    learnMode: Boolean = false,
    isSelected: Boolean = false,
    isMapped: Boolean = false,
    onLearnSelect: (() -> Unit)? = null
) {
    val animatedValue by animateFloatAsState(targetValue = value, label = "knob")
    val currentValue by rememberUpdatedState(value)
    val currentLearnMode by rememberUpdatedState(learnMode)
    val currentOnLearnSelect by rememberUpdatedState(onLearnSelect)
    var showTooltip by remember { mutableStateOf(false) }
    var knobPosition by remember { mutableStateOf(Offset.Zero) }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier.width(size + 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Wrapper Box to stack knob, long-press overlay, and tooltip popup
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                knobPosition = coordinates.positionInRoot()
            }
        ) {
            // Knob with combined drag + long-press gesture handler
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(BgPanel)
                    .border(2.dp, PanelHighlight, CircleShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.nanoTime()
                            var dragMode = false
                            var lastY = down.position.y

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.find { it.id == down.id } ?: break

                                if (!change.pressed) {
                                    // Learn mode: short tap selects the control
                                    if (currentLearnMode) {
                                        val tapElapsed = System.nanoTime() - downTime
                                        val tapDistance = (change.position - down.position).getDistance()
                                        if (tapElapsed < 400_000_000L && tapDistance < 8.dp.toPx()) {
                                            currentOnLearnSelect?.invoke()
                                        }
                                    }
                                    change.consume()
                                    break
                                }

                                val elapsed = System.nanoTime() - downTime
                                val distance = (change.position - down.position).getDistance()

                                when {
                                    // 500ms hold without significant movement → long-press = tooltip
                                    !dragMode && elapsed > 500_000_000L && distance < 8.dp.toPx() -> {
                                        showTooltip = true
                                        change.consume()
                                        break
                                    }
                                    // Finger moved significantly → enter drag mode
                                    distance > 8.dp.toPx() -> {
                                        dragMode = true
                                        showTooltip = false
                                    }
                                }

                                if (dragMode) {
                                    val delta = -(change.position.y - lastY) / 200f
                                    if (delta != 0f) {
                                        val newValue = (currentValue + delta).coerceIn(minValue, maxValue)
                                        onValueChange(newValue)
                                    }
                                    lastY = change.position.y
                                }

                                change.consume()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(size)) {
                    val cx = size.toPx() / 2f
                    val cy = size.toPx() / 2f
                    val radius = size.toPx() / 2f - 3f
                    val strokeWidth = 4f
                    val arcSize = size.toPx() - strokeWidth - 4f
                    val arcOffset = (size.toPx() - arcSize) / 2f
                    val normalizedValue = animatedValue.coerceIn(0f, 1f)

                    // 1. Outer shadow behind knob (offset down-right, translucent)
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.40f),
                        radius = radius,
                        center = Offset(cx + 2.5f, cy + 2.5f)
                    )

                    // 2. Knob body with radial gradient (lighter center to dark edge)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF3E3E54),
                                Color(0xFF28283E),
                                Color(0xFF16162A)
                            ),
                            center = Offset(cx - radius * 0.25f, cy - radius * 0.30f),
                            radius = radius
                        ),
                        radius = radius,
                        center = Offset(cx, cy)
                    )

                    // 3. Metallic rim (thin ring with silver/gray gradient)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF9999AA),
                                Color(0xFF555570),
                                Color(0xFF3A3A50),
                                Color(0xFF666680)
                            ),
                            center = Offset(cx, cy),
                            radius = radius
                        ),
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.5f)
                    )

                    // 4. Specular highlight (small bright arc at top-left)
                    drawArc(
                        color = Color.White.copy(alpha = 0.18f),
                        startAngle = 225f,
                        sweepAngle = 50f,
                        useCenter = false,
                        topLeft = Offset(cx - radius * 0.45f, cy - radius * 0.55f),
                        size = Size(radius * 0.9f, radius * 0.9f),
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )

                    // 5. Background arc track (refined with subtle gradient)
                    drawArc(
                        color = PanelHighlight.copy(alpha = 0.25f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(arcOffset, arcOffset),
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // 6. Value arc with accent color
                    val sweep = normalizedValue * 270f
                    if (sweep > 0f) {
                        drawArc(
                            color = accentColor,
                            startAngle = 135f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(arcOffset, arcOffset),
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // 7. Rotation indicator line (from center to edge)
                    val angleDeg = 135f + sweep
                    val angleRad = angleDeg * (PI.toFloat() / 180f)
                    val indicatorLen = radius * 0.65f
                    val endX = cx + indicatorLen * cos(angleRad)
                    val endY = cy + indicatorLen * sin(angleRad)

                    drawLine(
                        color = accentColor,
                        start = Offset(cx, cy),
                        end = Offset(endX, endY),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )

                    // 8. Center dot
                    drawCircle(
                        color = accentColor,
                        radius = 3.5f,
                        center = Offset(cx, cy)
                    )

                    // 9. Learn mode pulsing highlight
                    if (learnMode) {
                        if (isSelected) {
                            // Solid white border for selected control
                            drawCircle(
                                color = Color.White.copy(alpha = 0.6f),
                                radius = radius + 4f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 3f)
                            )
                        } else {
                            // Pulsing amber border for mappable controls
                            drawCircle(
                                color = KnobAmber.copy(alpha = pulseAlpha),
                                radius = radius + 3f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2.5f)
                            )
                        }
                    }

                    // 10. Mapped indicator (small green dot in top-right corner)
                    if (isMapped && !learnMode) {
                        val dotRadius = 4f
                        val dotX = cx + radius * 0.7f
                        val dotY = cy - radius * 0.7f
                        drawCircle(color = KnobGreen, radius = dotRadius, center = Offset(dotX, dotY))
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f),
                            radius = dotRadius * 0.5f,
                            center = Offset(dotX - 0.5f, dotY - 0.5f)
                        )
                    }
                }
            }

            // Tooltip popup with smart positioning (above if knob is low, below if knob is high)
            if (showTooltip) {
                val tooltipText = buildString {
                    append(label)
                    if (valueDisplay.isNotEmpty()) append(": $valueDisplay")
                    val desc = paramTooltips[label]
                    if (!desc.isNullOrEmpty()) append("\n$desc")
                }
                val tooltipAbove = knobPosition.y > 400f
                Box(
                    modifier = Modifier
                        .align(if (tooltipAbove) Alignment.TopCenter else Alignment.BottomCenter)
                        .offset(y = if (tooltipAbove) (-8).dp else 8.dp)
                ) {
                    ParameterTooltip(text = tooltipText)
                }
            }
        }

        if (label.isNotEmpty()) {
            Text(
                text = label, color = TextSecondary, fontSize = 9.sp,
                fontWeight = FontWeight.Medium, maxLines = 1,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (valueDisplay.isNotEmpty()) {
            Text(
                text = valueDisplay, color = accentColor, fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Auto-dismiss tooltip after 2 seconds
    LaunchedEffect(showTooltip) {
        if (showTooltip) {
            delay(2000)
            showTooltip = false
        }
    }
}

// SynthPanel has been moved to SynthPanel.kt

/**
 * Horizontal slider. (Unchanged — kept as-is per spec.)
 */
@Composable
fun SynthSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    accentColor: Color = KnobCyan
) {
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(text = label, color = TextSecondary, fontSize = 8.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Slider(
            value = value, onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor, activeTrackColor = accentColor,
                inactiveTrackColor = PanelHighlight.copy(alpha = 0.3f)
            ),
            modifier = Modifier.height(20.dp)
        )
    }
}

/**
 * Toggle button with hardware LED aesthetic.
 * Glowing effect when active, more pronounced border, inner highlight.
 */
@Composable
fun SynthToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabledColor: Color = KnobCyan,
    disabledColor: Color = PanelHighlight.copy(alpha = 0.4f)
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Outer container provides consistent space for glow ring
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            // Outer glow ring when active
            if (checked) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = enabledColor.copy(alpha = 0.18f),
                        cornerRadius = CornerRadius(12f, 12f),
                        size = size,
                        style = Stroke(width = 8f, cap = StrokeCap.Round)
                    )
                    drawRoundRect(
                        color = enabledColor.copy(alpha = 0.10f),
                        cornerRadius = CornerRadius(14f, 14f),
                        size = Size(size.width - 4f, size.height - 4f),
                        topLeft = Offset(2f, 2f),
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )
                }
            }

            // Inner button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (checked) enabledColor else disabledColor)
                    .border(
                        1.5.dp,
                        if (checked) enabledColor.copy(alpha = 0.9f)
                        else PanelHighlight.copy(alpha = 0.3f),
                        RoundedCornerShape(5.dp)
                    )
                    .clickable { onCheckedChange(!checked) },
                contentAlignment = Alignment.Center
            ) {
                // Inner highlight glow when active (LED feel)
                if (checked) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.20f),
                            cornerRadius = CornerRadius(5f, 5f),
                            size = size,
                            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                        )
                    }
                }
                Text(
                    text = if (checked) "ON" else "OFF",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 8.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * Waveform selector button with hardware 3D effect.
 * Selected = raised appearance (lighter top edge, darker bottom).
 * Unselected = recessed appearance (darker top edge).
 */
@Composable
fun WaveformButton(
    waveform: Int,
    currentWaveform: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = KnobAmber
) {
    val names = listOf("Saw", "Sqr", "Tri", "Sin")
    val isSelected = waveform == currentWaveform

    val bgColor = if (isSelected) accentColor.copy(alpha = 0.25f) else BgPanel
    val borderColor = if (isSelected) accentColor else PanelHighlight.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // 3D edge shading via Canvas overlay
        Canvas(modifier = Modifier.matchParentSize()) {
            if (isSelected) {
                // Raised: light top edge, dark bottom edge
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.Black.copy(alpha = 0.20f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            } else {
                // Recessed: dark top edge, subtle light bottom edge
                drawLine(
                    color = Color.Black.copy(alpha = 0.12f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WaveformIcon(
                waveform = waveform,
                color = if (isSelected) accentColor else TextSecondary,
                size = 16.dp
            )
            Text(
                text = names.getOrElse(waveform) { "?" },
                color = if (isSelected) accentColor else TextSecondary,
                fontSize = 7.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * Small waveform shape icon drawn via Canvas Path.
 * @param waveform 0=saw, 1=square, 2=tri, 3=sine
 */
@Composable
fun WaveformIcon(
    waveform: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        val sw = 1.5f

        val path = when (waveform) {
            0 -> Path().apply { // Saw: diagonal up, sharp drop
                moveTo(0f, h)
                lineTo(w, 0f)
                lineTo(w, h)
            }
            1 -> Path().apply { // Square: low-high-low
                moveTo(0f, h * 0.5f)
                lineTo(0f, 0f)
                lineTo(w * 0.5f, 0f)
                lineTo(w * 0.5f, h)
                lineTo(w, h)
                lineTo(w, h * 0.5f)
            }
            2 -> Path().apply { // Triangle: up then down
                moveTo(0f, h)
                lineTo(w * 0.5f, 0f)
                lineTo(w, h)
            }
            3 -> Path().apply { // Sine: smooth sine wave curve (scaled to fit bounds)
                moveTo(0f, h * 0.5f)
                cubicTo(
                    w * 0.2f, h * 0.05f,
                    w * 0.25f, h * 0.05f,
                    w * 0.5f, h * 0.5f
                )
                cubicTo(
                    w * 0.75f, h * 0.95f,
                    w * 0.8f, h * 0.95f,
                    w, h * 0.5f
                )
            }
            else -> Path()
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
