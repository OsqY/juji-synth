package com.jujidaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Photorealistic hardware knob with metal rim, tick marks, value arc, LED ring, and vertical drag.
 */
@Composable
fun RealKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    valueDisplay: String = "",
    accentColor: Color = KnobCyan,
    ledColor: Color = LedCyan,
    size: Dp = 48.dp,
    learnMode: Boolean = false,
    isSelected: Boolean = false,
    onLearnSelect: (() -> Unit)? = null,
) {
    val sizePx = with(LocalDensity.current) { size.toPx() }
    val radius = sizePx / 2f
    val indicatorAngle = -135f + value * 270f // -135 to +135 degrees
    val indicatorRad = indicatorAngle * PI.toFloat() / 180f

    var showTooltip by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(value) }
    val density = LocalDensity.current
    val touchSlopPx = with(density) { DraggableValueController.touchSlop.toPx() }
    var dragStartY by remember { mutableStateOf(0f) }
    var hasExceededSlop by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { showTooltip = true },
                            onTap = {
                                if (learnMode) onLearnSelect?.invoke()
                            },
                        )
                    }.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                dragStartY = startOffset.y
                                hasExceededSlop = false
                            },
                            onDrag = { change, dragAmount ->
                                if (!hasExceededSlop) {
                                    val dragDistance = kotlin.math.abs(change.position.y - dragStartY)
                                    if (dragDistance < touchSlopPx) {
                                        return@detectDragGestures
                                    }
                                    hasExceededSlop = true
                                }
                                change.consume()
                                val delta = -dragAmount.y / 200f
                                if (delta != 0f) {
                                    val newValue = (dragValue + delta).coerceIn(0f, 1f)
                                    dragValue = newValue
                                    onValueChange(newValue)
                                }
                            },
                        )
                    },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = sizePx / 2f
                val cy = sizePx / 2f
                val knobRadius = radius - 1f

                // 1. Outer shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    radius = knobRadius,
                    center = Offset(cx + 2.5f, cy + 2.5f),
                )

                // 2. Knob body (radial gradient effect via concentric circles)
                drawCircle(
                    brush =
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0xFF4A4A52),
                                    Color(0xFF2D2D35),
                                    Color(0xFF1A1A22),
                                ),
                            center = Offset(cx - radius * 0.25f, cy - radius * 0.30f),
                            radius = knobRadius,
                        ),
                    radius = knobRadius,
                    center = Offset(cx, cy),
                )

                // 3. Metal rim
                drawCircle(
                    color = KnobRim,
                    radius = knobRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f),
                )

                // 4. Tick marks (30 ticks around perimeter)
                val tickRadius = knobRadius - 4f
                val tickLength = 3f
                for (i in 0 until 30) {
                    val angleDeg = -135f + (i / 29f) * 270f
                    val angleRad = angleDeg * PI.toFloat() / 180f
                    val outerX = cx + cos(angleRad) * tickRadius
                    val outerY = cy + sin(angleRad) * tickRadius
                    val innerX = cx + cos(angleRad) * (tickRadius - tickLength)
                    val innerY = cy + sin(angleRad) * (tickRadius - tickLength)

                    val tickFraction = i / 29f
                    val tickColor = if (tickFraction <= value) accentColor else TextMuted
                    drawLine(
                        color = tickColor,
                        start = Offset(innerX, innerY),
                        end = Offset(outerX, outerY),
                        strokeWidth = 1.5f,
                    )
                }

                // 5. Value arc (thick glow stroke along rim)
                val arcStartRad = (-135f) * PI.toFloat() / 180f
                val arcEndRad = (-135f + value * 270f) * PI.toFloat() / 180f
                if (value > 0.01f) {
                    // Glow layer (wider, transparent)
                    drawCircle(
                        color = accentColor.copy(alpha = 0.2f),
                        radius = knobRadius - 1f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 5f),
                    )
                }

                // 6. Pointer needle
                val pointerLen = knobRadius * 0.65f
                val pointerX = cx + cos(indicatorRad) * pointerLen
                val pointerY = cy + sin(indicatorRad) * pointerLen
                drawLine(
                    color = KnobIndicator,
                    start = Offset(cx, cy),
                    end = Offset(pointerX, pointerY),
                    strokeWidth = 2.5f,
                )

                // 7. LED ring (glows brighter with value)
                if (value > 0f) {
                    val ledAlpha = (0.1f + value * 0.5f).coerceIn(0f, 1f)
                    drawCircle(
                        color = ledColor.copy(alpha = ledAlpha),
                        radius = knobRadius + 2f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2f),
                    )
                }

                // 8. MIDI learn selected highlight
                if (isSelected) {
                    drawCircle(
                        color = MidiLearnGlow,
                        radius = knobRadius + 3f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2f),
                    )
                }
            }

            // Tooltip overlay
            if (showTooltip) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { showTooltip = false }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .background(
                                    Color(0xE6000000),
                                    shape =
                                        androidx.compose.foundation.shape
                                            .RoundedCornerShape(4.dp),
                                ).padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "$label: $valueDisplay",
                            color = TextPrimary,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }

        // Label below knob
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                maxLines = 1,
            )
        }

        // Value display
        if (valueDisplay.isNotEmpty()) {
            Text(
                text = valueDisplay,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
