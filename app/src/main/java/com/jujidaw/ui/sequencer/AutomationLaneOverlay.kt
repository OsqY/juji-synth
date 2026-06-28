package com.jujidaw.ui.sequencer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.project.AutomationPoint
import com.jujidaw.ui.theme.BgGunmetal
import com.jujidaw.ui.theme.BgPanel
import com.jujidaw.ui.theme.KnobAmber
import com.jujidaw.ui.theme.TextMuted
import com.jujidaw.ui.theme.TextSecondary

/** Height of one automated lane for a single parameter. */
private const val LANE_HEIGHT_DP = 48

/**
 * An automation lane overlay showing points and connecting lines for a set
 * of AutomationPoints. Supports:
 * - Tap on empty space → create point at tapped position and value
 * - Long-press existing point → delete it
 * - Drag existing point → move it
 */
@Composable
fun AutomationLaneOverlay(
    points: List<AutomationPoint>,
    onPointsChange: (List<AutomationPoint>) -> Unit,
    label: String = "",
    numSteps: Int = 64,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val pointRadius = 6.dp
    val lineColor = KnobAmber.copy(alpha = 0.7f)
    val pointColor = KnobAmber
    val gridColor = BgPanel.copy(alpha = 0.4f)
    val bgColor = BgGunmetal

    // Working copy for gesture edit
    val currentPoints = remember { mutableStateListOf<AutomationPoint>() }
    LaunchedEffect(points) {
        currentPoints.clear()
        currentPoints.addAll(points)
    }

    Column(modifier = modifier.fillMaxWidth().height((LANE_HEIGHT_DP + 8).dp)) {
        // Lane header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 44.dp, bottom = 2.dp)
        ) {
            Text(
                label,
                color = TextSecondary,
                fontSize = 7.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LANE_HEIGHT_DP.dp)
                .background(bgColor)
                .padding(start = 44.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val stepWidth = size.width.toFloat() / numSteps
                                val col = (offset.x / stepWidth).toInt()
                                    .coerceIn(0, numSteps - 1)
                                val value =
                                    (LANE_HEIGHT_DP.dp.toPx() - offset.y) / LANE_HEIGHT_DP.dp
                                        .toPx()
                                val clampedValue = value.coerceIn(0f, 1f)

                                val clickedPoint = currentPoints.find { p ->
                                    val px = (p.position.toFloat() / numSteps) * size.width
                                    val py =
                                        (1f - p.value) * LANE_HEIGHT_DP.dp.toPx()
                                    kotlin.math.abs(offset.x - px) < 12.dp.toPx() &&
                                        kotlin.math.abs(offset.y - py) < 12.dp.toPx()
                                }

                                if (clickedPoint != null) {
                                    // Delete on tap on existing point
                                    currentPoints.remove(clickedPoint)
                                    onPointsChange(currentPoints.toList())
                                } else {
                                    // Add new point
                                    val newPoint = AutomationPoint(
                                        position = col.toLong(),
                                        value = clampedValue
                                    )
                                    val existingIdx = currentPoints.indexOfFirst {
                                        it.position.toFloat() / numSteps * size.width >= offset.x &&
                                            (it.position.toFloat() / numSteps * size.width) > offset.x - stepWidth
                                    }
                                    val insertionIdx = currentPoints.indexOfFirst { p ->
                                        p.position > newPoint.position
                                    }.let { if (it < 0) currentPoints.size else it }
                                    currentPoints.add(insertionIdx, newPoint)
                                    onPointsChange(currentPoints.toList())
                                }
                            },
                            onLongPress = { offset ->
                                // Delete on long-press
                                val removed = currentPoints.firstOrNull { p ->
                                    val px = (p.position.toFloat() / numSteps) * size.width
                                    val py =
                                        (1f - p.value) * LANE_HEIGHT_DP.dp.toPx()
                                    kotlin.math.abs(offset.x - px) < 12.dp.toPx() &&
                                        kotlin.math.abs(offset.y - py) < 12.dp.toPx()
                                }
                                if (removed != null) {
                                    currentPoints.remove(removed)
                                    onPointsChange(currentPoints.toList())
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                val draggedPoint = currentPoints.firstOrNull { p ->
                                    val px = (p.position.toFloat() / numSteps) * size.width
                                    val py =
                                        (1f - p.value) * LANE_HEIGHT_DP.dp.toPx()
                                    kotlin.math.abs(change.position.x - px) < 20.dp.toPx() &&
                                        kotlin.math.abs(change.position.y - py) < 20.dp.toPx()
                                }
                                if (draggedPoint != null) {
                                    val stepWidth =
                                        size.width.toFloat() / numSteps
                                    val newCol =
                                        (change.position.x / stepWidth).toInt()
                                            .coerceIn(0, numSteps - 1)
                                    val newValue =
                                        (LANE_HEIGHT_DP.dp.toPx() - change.position.y) / LANE_HEIGHT_DP
                                            .dp.toPx()
                                    val idx = currentPoints.indexOf(draggedPoint)
                                    if (idx >= 0) {
                                        currentPoints[idx] = currentPoints[idx].copy(
                                            position = newCol.toLong(),
                                            value = newValue.coerceIn(0f, 1f)
                                        )
                                        onPointsChange(currentPoints.toList())
                                    }
                                }
                            }
                        )
                    }
            ) {
                val stepWidth = size.width / numSteps
                val laneHeight = size.height

                // Horizontal guide lines at 0%, 25%, 50%, 75%, 100%
                for (pct in listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)) {
                    val y = laneHeight * (1f - pct)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }

                // Draw connection lines between points
                val sortedPoints = currentPoints.sortedBy { it.position }
                for (i in 0 until sortedPoints.size - 1) {
                    val p1 = sortedPoints[i]
                    val p2 = sortedPoints[i + 1]
                    val x1 = p1.position.toFloat() / numSteps * size.width
                    val y1 = (1f - p1.value) * laneHeight
                    val x2 = p2.position.toFloat() / numSteps * size.width
                    val y2 = (1f - p2.value) * laneHeight
                    drawLine(
                        color = lineColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 2f
                    )
                }

                // Draw automation points
                for (p in sortedPoints) {
                    val x = p.position.toFloat() / numSteps * size.width
                    val y = (1f - p.value) * laneHeight
                    drawCircle(
                        color = pointColor,
                        radius = pointRadius.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = (pointRadius / 2).toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}
