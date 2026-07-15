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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    val value: String = "",
)

/**
 * A compact tooltip popup styled to match the flat Ableton-inspired aesthetic.
 * SurfaceContainerHigh background with an OutlineVariant border.
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainerHigh)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(text, color = OnSurface, style = BodySmall)
    }
}

/**
 * Descriptions for synth parameters, used by the tooltip system.
 * Map keys match knob/slider labels displayed in the UI.
 */
val paramTooltips: Map<String, String> =
    mapOf(
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
 * Reusable flat knob component. Delegates the dial rendering to the flat [RealKnob]
 * aesthetic (track ring + value arc + OnSurface indicator). Drag up/down to change
 * value; long-press to show a tooltip. Signature and drag logic are unchanged.
 */
@Composable
fun SynthKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    valueDisplay: String = "",
    accentColor: Color = Primary,
    size: Dp = 80.dp,
    minValue: Float = 0.0f,
    maxValue: Float = 1.0f,
    // MIDI Learn parameters
    learnMode: Boolean = false,
    isSelected: Boolean = false,
    isMapped: Boolean = false,
    onLearnSelect: (() -> Unit)? = null,
) {
    val animatedValue by animateFloatAsState(targetValue = value, label = "knob")
    val currentValue by rememberUpdatedState(value)
    val currentLearnMode by rememberUpdatedState(learnMode)
    val currentOnLearnSelect by rememberUpdatedState(onLearnSelect)
    var showTooltip by remember { mutableStateOf(false) }
    var knobPosition by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val trackStrokePx = with(density) { 3.dp.toPx() } // 3dp track ring + value arc
    val indicatorStrokePx = with(density) { 2.dp.toPx() } // 2dp line indicator
    val ringOffsetPx = with(density) { 4.dp.toPx() } // active/selection ring offset

    Column(
        modifier = modifier.width(size + 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Wrapper Box to stack knob, long-press overlay, and tooltip popup
        Box(
            modifier =
                Modifier.onGloballyPositioned { coordinates ->
                    knobPosition = coordinates.positionInRoot()
                },
        ) {
            // Knob with combined drag + long-press gesture handler
            Box(
                modifier =
                    Modifier
                        .size(size)
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
                                        // 500ms hold without significant movement -> long-press = tooltip
                                        !dragMode && elapsed > 500_000_000L && distance < 8.dp.toPx() -> {
                                            showTooltip = true
                                            change.consume()
                                            break
                                        }

                                        // Finger moved significantly -> enter drag mode
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
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(size)) {
                    val cx = size.toPx() / 2f
                    val cy = size.toPx() / 2f
                    val knobRadius = size.toPx() / 2f - trackStrokePx / 2f
                    val arcDiameter = knobRadius * 2f
                    val arcTopLeft = Offset(cx - knobRadius, cy - knobRadius)
                    val arcStart = 135f // gap at bottom; 270-degree sweep preserved
                    val arcSweep = 270f
                    val normalizedValue = animatedValue.coerceIn(0f, 1f)
                    val valueSweep = normalizedValue * arcSweep

                    // 1. Track ring (SurfaceContainerHigh, 3dp)
                    drawArc(
                        color = SurfaceContainerHigh,
                        startAngle = arcStart,
                        sweepAngle = arcSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = Size(arcDiameter, arcDiameter),
                        style = Stroke(width = trackStrokePx, cap = StrokeCap.Round),
                    )

                    // 2. Value arc
                    if (valueSweep > 0f) {
                        drawArc(
                            color = accentColor,
                            startAngle = arcStart,
                            sweepAngle = valueSweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = Size(arcDiameter, arcDiameter),
                            style = Stroke(width = trackStrokePx, cap = StrokeCap.Round),
                        )
                    }

                    // 3. Single OnSurface line indicator (center -> rim, 2dp)
                    val pointerAngleDeg = arcStart + valueSweep
                    val pointerRad = pointerAngleDeg * (PI.toFloat() / 180f)
                    val pointerLen = knobRadius
                    drawLine(
                        color = OnSurface,
                        start = Offset(cx, cy),
                        end =
                            Offset(
                                cx + pointerLen * cos(pointerRad),
                                cy + pointerLen * sin(pointerRad),
                            ),
                        strokeWidth = indicatorStrokePx,
                        cap = StrokeCap.Round,
                    )

                    // 4. Accent low-alpha active ring (learn) / Primary selection ring
                    if (isSelected) {
                        drawCircle(
                            color = Primary,
                            radius = knobRadius + ringOffsetPx,
                            center = Offset(cx, cy),
                            style = Stroke(width = indicatorStrokePx),
                        )
                    } else if (learnMode) {
                        drawCircle(
                            color = accentColor.copy(alpha = 0.25f),
                            radius = knobRadius + ringOffsetPx,
                            center = Offset(cx, cy),
                            style = Stroke(width = indicatorStrokePx),
                        )
                    }

                    // 5. Mapped indicator (small Primary dot, top-right)
                    if (isMapped && !learnMode) {
                        val dotRadius = with(density) { 2.dp.toPx() }
                        val dotX = cx + knobRadius * 0.7f
                        val dotY = cy - knobRadius * 0.7f
                        drawCircle(color = Primary, radius = dotRadius, center = Offset(dotX, dotY))
                    }
                }
            }

            // Tooltip popup with smart positioning (above if knob is low, below if knob is high)
            if (showTooltip) {
                val tooltipText =
                    buildString {
                        append(label)
                        if (valueDisplay.isNotEmpty()) append(": $valueDisplay")
                        val desc = paramTooltips[label]
                        if (!desc.isNullOrEmpty()) append("\n$desc")
                    }
                val tooltipAbove = knobPosition.y > 400f
                Box(
                    modifier =
                        Modifier
                            .align(if (tooltipAbove) Alignment.TopCenter else Alignment.BottomCenter)
                            .offset(y = if (tooltipAbove) (-8).dp else 8.dp),
                ) {
                    ParameterTooltip(text = tooltipText)
                }
            }
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = TextSecondary,
                style = LabelSmall,
                maxLines = 1,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        if (valueDisplay.isNotEmpty()) {
            Text(
                text = valueDisplay,
                color = accentColor,
                style = MonoMedium,
                maxLines = 1,
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
    accentColor: Color = Secondary,
) {
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(text = label, color = TextSecondary, fontSize = 8.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors =
                SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = OutlineVariant.copy(alpha = 0.3f),
                ),
            modifier = Modifier.height(20.dp),
        )
    }
}

/**
 * Flat toggle: state-colored 25% fill + border + label (the label replaces the old
 * ON/OFF text). Visuals are 28dp inner / 36dp outer inside a 44dp touch target.
 */
@Composable
fun SynthToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabledColor: Color = Secondary,
    disabledColor: Color = OnSurfaceVariant,
) {
    // 44dp invisible touch target wrapping the 36dp outer / 28dp inner visual
    Box(
        modifier =
            modifier.then(
                Modifier
                    .size(DraggableValueController.minTouchTarget)
                    .clickable { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Outer visual frame (36dp)
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            // Inner button: flat 25% fill + border + label
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(RadiusXs))
                        .background(
                            if (checked) {
                                enabledColor.copy(alpha = 0.25f)
                            } else {
                                SurfaceContainer
                            },
                        ).border(
                            1.dp,
                            if (checked) enabledColor else OutlineVariant,
                            RoundedCornerShape(RadiusXs),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        color = if (checked) enabledColor else disabledColor,
                        style = LabelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Flat waveform selector. Selected = recessed (darker SurfaceContainerLow), not raised.
 */
@Composable
fun WaveformButton(
    waveform: Int,
    currentWaveform: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Primary,
) {
    val names = listOf("Saw", "Sqr", "Tri", "Sin")
    val isSelected = waveform == currentWaveform

    // Selected = recessed (darker SurfaceContainerLow); unselected = neutral surface.
    val bgColor = if (isSelected) SurfaceContainerLow else SurfaceContainer
    val borderColor = if (isSelected) accentColor else OutlineVariant
    val fgColor = if (isSelected) accentColor else OnSurfaceVariant

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(RadiusSm))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WaveformIcon(
                waveform = waveform,
                color = fgColor,
                size = 16.dp,
            )
            Text(
                text = names.getOrElse(waveform) { "?" },
                color = fgColor,
                style = CaptionSmall,
                maxLines = 1,
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
    size: Dp = 20.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        val sw = 1.5f

        val path =
            when (waveform) {
                0 -> {
                    Path().apply {
                        // Saw: diagonal up, sharp drop
                        moveTo(0f, h)
                        lineTo(w, 0f)
                        lineTo(w, h)
                    }
                }

                1 -> {
                    Path().apply {
                        // Square: low-high-low
                        moveTo(0f, h * 0.5f)
                        lineTo(0f, 0f)
                        lineTo(w * 0.5f, 0f)
                        lineTo(w * 0.5f, h)
                        lineTo(w, h)
                        lineTo(w, h * 0.5f)
                    }
                }

                2 -> {
                    Path().apply {
                        // Triangle: up then down
                        moveTo(0f, h)
                        lineTo(w * 0.5f, 0f)
                        lineTo(w, h)
                    }
                }

                3 -> {
                    Path().apply {
                        // Sine: smooth sine wave curve (scaled to fit bounds)
                        moveTo(0f, h * 0.5f)
                        cubicTo(
                            w * 0.2f,
                            h * 0.05f,
                            w * 0.25f,
                            h * 0.05f,
                            w * 0.5f,
                            h * 0.5f,
                        )
                        cubicTo(
                            w * 0.75f,
                            h * 0.95f,
                            w * 0.8f,
                            h * 0.95f,
                            w,
                            h * 0.5f,
                        )
                    }
                }

                else -> {
                    Path()
                }
            }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
