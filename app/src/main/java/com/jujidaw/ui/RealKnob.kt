package com.jujidaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jujidaw.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Flat Ableton-style dial: "a dial is just a curved slider."
 *
 * Renders a flat track ring (SurfaceContainerHigh, 3dp) + value arc (Accent or Secondary)
 * + a single OnSurface 2dp line indicator. No metal rim, no 30 ticks, no radial gradient,
 * no LED glow. An optional Accent low-alpha (0.25) active ring conveys learn mode; a
 * Primary ring conveys selection. All parameters, signatures, and drag logic are unchanged.
 *
 * See `docs/ui-design-overhaul-plan.md` Phase 2 step 1 and Component Inventory #4.
 */
@Composable
fun RealKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    valueDisplay: String = "",
    accentColor: Color = Secondary,
    ledColor: Color = Accent,
    size: Dp = 48.dp,
    learnMode: Boolean = false,
    isSelected: Boolean = false,
    onLearnSelect: (() -> Unit)? = null,
) {
    val sizePx = with(LocalDensity.current) { size.toPx() }
    val radius = sizePx / 2f

    var showTooltip by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(value) }
    val density = LocalDensity.current
    val touchSlopPx = with(density) { DraggableValueController.touchSlop.toPx() }
    val trackStrokePx = with(density) { 3.dp.toPx() } // 3dp track ring + value arc
    val indicatorStrokePx = with(density) { 2.dp.toPx() } // 2dp line indicator
    val ringOffsetPx = with(density) { 4.dp.toPx() } // active/selection ring offset
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
                val knobRadius = radius - trackStrokePx / 2f
                val arcDiameter = knobRadius * 2f
                val arcTopLeft = Offset(cx - knobRadius, cy - knobRadius)
                val arcStart = 135f // gap at bottom; 270-degree sweep (-135deg -> +135deg preserved)
                val arcSweep = 270f
                val valueSweep = value.coerceIn(0f, 1f) * arcSweep

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

                // 2. Value arc (Accent or Secondary)
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
                val pointerRad = pointerAngleDeg * PI.toFloat() / 180f
                val pointerLen = knobRadius
                drawLine(
                    color = OnSurface,
                    start = Offset(cx, cy),
                    end =
                        Offset(
                            cx + cos(pointerRad) * pointerLen,
                            cy + sin(pointerRad) * pointerLen,
                        ),
                    strokeWidth = indicatorStrokePx,
                    cap = StrokeCap.Round,
                )

                // 4. Optional Accent low-alpha active ring (learn) / Primary selection ring
                if (isSelected) {
                    drawCircle(
                        color = Primary,
                        radius = knobRadius + ringOffsetPx,
                        center = Offset(cx, cy),
                        style = Stroke(width = indicatorStrokePx),
                    )
                } else if (learnMode) {
                    drawCircle(
                        color = ledColor.copy(alpha = 0.25f),
                        radius = knobRadius + ringOffsetPx,
                        center = Offset(cx, cy),
                        style = Stroke(width = indicatorStrokePx),
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
                                    SurfaceContainerHigh,
                                    shape = RoundedCornerShape(RadiusSm),
                                ).border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    ) {
                        Text(
                            text = "$label: $valueDisplay",
                            color = OnSurface,
                            style = MonoMedium,
                            maxLines = 1,
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
                style = LabelSmall,
                maxLines = 1,
            )
        }

        // Value display
        if (valueDisplay.isNotEmpty()) {
            Text(
                text = valueDisplay,
                color = accentColor,
                style = MonoMedium,
                maxLines = 1,
            )
        }
    }
}
