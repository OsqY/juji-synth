@file:OptIn(ExperimentalFoundationApi::class)

package com.jujidaw.ui.sequencer

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jujidaw.model.PianoRollNote
import com.jujidaw.ui.LcdDisplay
import com.jujidaw.ui.SynthPanel
import com.jujidaw.ui.theme.*
import kotlin.math.roundToInt

/**
 * Full-screen sequencer with pattern selector, step grid, and piano-roll editor.
 *
 * ### Usage
 * ```kotlin
 * SequencerScreen(
 *     viewModel = viewModel { SequencerViewModel(transportController) },
 *     modifier = Modifier.fillMaxSize()
 * )
 * ```
 *
 * ### TODO for integrator
 * - Provide a shared [TransportController] singleton when navigation wiring is added.
 * - Wire [SequencerScreen] into the bottom-navigation graph (Group 14).
 * - Per-track pitch assignment UI is not yet implemented; all tracks default to C4=60.
 * - Live keyboard/pad recording into the active pattern is not yet implemented.
 */
@Composable
fun SequencerScreen(
    viewModel: SequencerViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BgGunmetal)
                .statusBarsPadding()
                .padding(4.dp),
    ) {
        // ── TOP BAR ──
        SequencerTopBar(
            uiState = uiState,
            onSelectPattern = viewModel::selectPattern,
            onCopy = viewModel::copyPattern,
            onPaste = viewModel::pastePattern,
            onClear = viewModel::clearPattern,
            onBpmChange = viewModel::setBpm,
            onToggleViewMode = viewModel::toggleViewMode,
            onToggleAutomation = viewModel::toggleAutomation,
            showAutomation = uiState.showAutomation,
        )

        Spacer(Modifier.height(4.dp))

        // ── MAIN EDITOR ──
        Box(modifier = Modifier.weight(1f)) {
            when (uiState.viewMode) {
                SequencerViewMode.STEP -> {
                    StepSequencerGrid(
                        pattern = uiState.patterns[uiState.selectedPatternId],
                        currentStep = uiState.currentStep,
                        isPlaying = uiState.isPlaying,
                        onStepToggle = viewModel::toggleStep,
                        onStepLongPress = viewModel::showVelocityEditor,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                SequencerViewMode.PIANO_ROLL -> {
                    SequencerPianoRoll(
                        notes = uiState.patterns[uiState.selectedPatternId].pianoRollNotes,
                        onNotesChange = viewModel::updatePianoRollNotes,
                        currentStep = uiState.currentStep,
                        isPlaying = uiState.isPlaying,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Automation lane overlay (piano-roll only, toggled)
        if (uiState.showAutomation && uiState.viewMode == SequencerViewMode.PIANO_ROLL) {
            AutomationLaneOverlay(
                points = uiState.automationPoints,
                onPointsChange = viewModel::updateAutomationPoints,
                label = uiState.selectedAutomationParam.label,
                numSteps = 64,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── BOTTOM STEP RAIL ──
        StepRail(
            numSteps = 16,
            currentStep = uiState.currentStep,
            isPlaying = uiState.isPlaying,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // ── VELOCITY POPUP ──
    if (uiState.showVelocityPopup) {
        val pattern = uiState.patterns[uiState.selectedPatternId]
        val cell =
            pattern.tracks
                .getOrNull(uiState.velocityEditTrack)
                ?.steps
                ?.getOrNull(uiState.velocityEditStep)
        VelocityPopup(
            initialVelocity = cell?.velocity ?: 100,
            onVelocityChange = { vel ->
                viewModel.setStepVelocity(uiState.velocityEditTrack, uiState.velocityEditStep, vel)
            },
            onDismiss = viewModel::dismissVelocityEditor,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// TOP BAR
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SequencerTopBar(
    uiState: SequencerUiState,
    onSelectPattern: (Int) -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onBpmChange: (Float) -> Unit,
    onToggleViewMode: () -> Unit,
    onToggleAutomation: () -> Unit,
    showAutomation: Boolean,
) {
    SynthPanel(title = "SEQUENCER", accentColor = KnobAmber) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: pattern selector + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Pattern selector (1–16)
                PatternSelector(
                    selectedId = uiState.selectedPatternId,
                    onSelect = onSelectPattern,
                    modifier = Modifier.weight(1f),
                )

                // Copy / Paste / Clear
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    SmallActionButton(label = "C", onClick = onCopy)
                    SmallActionButton(label = "P", onClick = onPaste, enabled = uiState.copyBufferPattern != null)
                    SmallActionButton(label = "X", onClick = onClear)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Row 2: view-mode toggle + BPM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // View mode toggle
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "A",
                        color = if (showAutomation) KnobAmber else TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier
                                .background(
                                    if (showAutomation) {
                                        KnobAmber.copy(alpha = 0.4f)
                                    } else {
                                        BgPanel.copy(alpha = 0.3f)
                                    },
                                    RoundedCornerShape(4.dp),
                                ).clickable { onToggleAutomation() }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    ViewModeButton(
                        label = "STEP",
                        selected = uiState.viewMode == SequencerViewMode.STEP,
                        onClick = onToggleViewMode,
                    )
                    ViewModeButton(
                        label = "PIANO",
                        selected = uiState.viewMode == SequencerViewMode.PIANO_ROLL,
                        onClick = onToggleViewMode,
                    )
                }

                // BPM LCD
                LcdDisplay(
                    value = "%.0f".format(uiState.bpm),
                    label = "BPM",
                    color = LcdText,
                    fontSize = 12.sp,
                    modifier = Modifier.width(80.dp),
                )

                // BPM nudge buttons
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    BpmNudgeButton(text = "-") { onBpmChange(uiState.bpm - 1f) }
                    BpmNudgeButton(text = "+") { onBpmChange(uiState.bpm + 1f) }
                }
            }
        }
    }
}

@Composable
private fun PatternSelector(
    selectedId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (id in 0..15) {
            val isSelected = id == selectedId
            Box(
                modifier =
                    Modifier
                        .size(width = 32.dp, height = 32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) KnobAmber else BgPanel)
                        .border(
                            1.dp,
                            if (isSelected) KnobAmber else PanelHighlight.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp),
                        ).clickable { onSelect(id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${id + 1}",
                    color = if (isSelected) Color.Black else TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (enabled) BgPanel else BgPanel.copy(alpha = 0.5f))
                .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) TextPrimary else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ViewModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(28.dp)
                .widthIn(min = 52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) KnobAmber.copy(alpha = 0.25f) else BgPanel)
                .border(
                    1.dp,
                    if (selected) KnobAmber else PanelHighlight.copy(alpha = 0.3f),
                    RoundedCornerShape(6.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) KnobAmber else TextSecondary,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun BpmNudgeButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BgPanel)
                .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════════════
// STEP SEQUENCER GRID
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun StepSequencerGrid(
    pattern: SequencerPattern,
    currentStep: Int,
    isPlaying: Boolean,
    onStepToggle: (Int, Int) -> Unit,
    onStepLongPress: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(
        modifier = modifier.verticalScroll(vScroll),
    ) {
        // Shared horizontally-scrollable container for header + grid
        Box(modifier = Modifier.horizontalScroll(hScroll)) {
            Column {
                // Header row with step numbers
                Row(modifier = Modifier.padding(start = 44.dp)) {
                    for (step in 0 until 16) {
                        val isCurrent = isPlaying && step == (currentStep % 16)
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isCurrent) KnobAmber.copy(alpha = 0.15f) else Color.Transparent,
                                        RoundedCornerShape(4.dp),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$step",
                                color = if (isCurrent) KnobAmber else TextMuted,
                                fontSize = 9.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // Grid body
                Column {
                    for (track in 0 until 16) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Pad label (row R = Pad R)
                            Box(
                                modifier =
                                    Modifier
                                        .width(44.dp)
                                        .height(36.dp)
                                        .padding(end = 4.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Text(
                                    "P${track + 1}",
                                    color = TextSecondary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            // Step cells
                            for (step in 0 until 16) {
                                val cell =
                                    pattern.tracks
                                        .getOrNull(track)
                                        ?.steps
                                        ?.getOrNull(step)
                                val isActive = cell?.active == true
                                val isCurrent = isPlaying && step == (currentStep % 16)

                                StepCellBox(
                                    isActive = isActive,
                                    isCurrent = isCurrent,
                                    velocity = cell?.velocity ?: 0,
                                    onClick = { onStepToggle(track, step) },
                                    onLongPress = { onStepLongPress(track, step) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCellBox(
    isActive: Boolean,
    isCurrent: Boolean,
    velocity: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val baseColor =
        when {
            isActive -> KnobAmber
            else -> SeqStepInactive
        }
    val alpha =
        when {
            isActive -> 0.5f + (velocity / 127f) * 0.5f
            else -> 1f
        }

    Box(
        modifier =
            Modifier
                .padding(1.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(baseColor.copy(alpha = alpha))
                .border(
                    width = if (isCurrent) 2.dp else 1.dp,
                    color =
                        when {
                            isCurrent && isActive -> Color.White
                            isCurrent -> KnobAmber
                            else -> PanelHighlight.copy(alpha = 0.3f)
                        },
                    shape = RoundedCornerShape(4.dp),
                ).combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (isActive) {
            Text(
                text = "\u25CF",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// PIANO ROLL (64 steps, C2–C6)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SequencerPianoRoll(
    notes: List<PianoRollNote>,
    onNotesChange: (List<PianoRollNote>) -> Unit,
    currentStep: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val startNote = 36 // C2
    val endNote = 84 // C6
    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val totalNotes = endNote - startNote
    val numSteps = 64
    val cellWidthDp = 28.dp
    val cellHeightDp = 14.dp
    val density = LocalDensity.current

    var dragOp by remember { mutableStateOf<PianoRollDragOp?>(null) }
    var velEditNote by remember { mutableStateOf<PianoRollNote?>(null) }

    val currentNotes by rememberUpdatedState(notes)
    val currentOnNotesChange by rememberUpdatedState(onNotesChange)

    Box(modifier = modifier.fillMaxWidth()) {
        Column {
            // Header row
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp)
                        .horizontalScroll(rememberScrollState()),
            ) {
                for (col in 0 until numSteps) {
                    val isCurrent = isPlaying && col == (currentStep % numSteps)
                    Box(
                        modifier =
                            Modifier
                                .width(cellWidthDp)
                                .height(18.dp)
                                .background(
                                    if (isCurrent) {
                                        KnobAmber.copy(alpha = 0.25f)
                                    } else {
                                        BgPanel.copy(alpha = 0.3f)
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$col",
                            color = if (isCurrent) KnobAmber else TextMuted,
                            fontSize = 7.sp,
                        )
                    }
                }
            }

            // Grid body
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Note labels
                Column(
                    modifier =
                        Modifier
                            .width(44.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    for (note in endNote downTo startNote) {
                        val name = noteNames[note % 12]
                        val octave = (note / 12) - 1
                        val isC = name == "C"
                        Box(
                            modifier =
                                Modifier
                                    .height(cellHeightDp)
                                    .width(44.dp)
                                    .background(
                                        if (isC) {
                                            BgPanel.copy(alpha = 0.3f)
                                        } else {
                                            BgGunmetal
                                        },
                                    ),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Text(
                                if (isC) "$name$octave" else "",
                                color = TextSecondary,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }
                }

                // Scrollable grid with gestures
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState()),
                ) {
                    Canvas(
                        modifier =
                            Modifier
                                .width(cellWidthDp * numSteps)
                                .height(cellHeightDp * totalNotes)
                                .pointerInput(notes, numSteps) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            val cellWpx = cellWidthDp.toPx()
                                            val cellHpx = cellHeightDp.toPx()
                                            val col = (offset.x / cellWpx).toInt().coerceIn(0, numSteps - 1)
                                            val row = (offset.y / cellHpx).toInt()
                                            val note = endNote - row

                                            velEditNote = null

                                            val existing =
                                                currentNotes.find { n ->
                                                    n.note == note &&
                                                        col >= n.startStep &&
                                                        col < n.startStep + n.duration
                                                }
                                            if (existing != null) {
                                                currentOnNotesChange(currentNotes - existing)
                                            } else {
                                                val newNote =
                                                    PianoRollNote(
                                                        note = note,
                                                        startStep = col.toFloat(),
                                                        duration = 1f,
                                                        velocity = 100,
                                                    )
                                                currentOnNotesChange(currentNotes + newNote)
                                            }
                                        },
                                        onLongPress = { offset ->
                                            val cellWpx = cellWidthDp.toPx()
                                            val cellHpx = cellHeightDp.toPx()
                                            val col = (offset.x / cellWpx).toFloat()
                                            val row = (offset.y / cellHpx).toInt()
                                            val note = endNote - row
                                            val hit =
                                                currentNotes.find { n ->
                                                    n.note == note &&
                                                        col >= n.startStep &&
                                                        col < n.startStep + n.duration
                                                }
                                            if (hit != null) velEditNote = hit
                                        },
                                    )
                                }.pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val cellWpx = cellWidthDp.toPx()
                                            val cellHpx = cellHeightDp.toPx()
                                            val col = (offset.x / cellWpx).toFloat()
                                            val row = (offset.y / cellHpx).toInt()
                                            val note = endNote - row
                                            val hitIdx =
                                                currentNotes.indexOfFirst { n ->
                                                    n.note == note &&
                                                        col >= n.startStep &&
                                                        col < n.startStep + n.duration
                                                }
                                            if (hitIdx >= 0) {
                                                val hitNote = currentNotes[hitIdx]
                                                val isRightEdge = col > hitNote.startStep + hitNote.duration * 0.75f
                                                dragOp =
                                                    PianoRollDragOp(
                                                        idx = hitIdx,
                                                        mode = if (isRightEdge) PianoRollDragMode.RESIZE else PianoRollDragMode.MOVE,
                                                        startStep = hitNote.startStep,
                                                        startNote = hitNote.note,
                                                        origDuration = hitNote.duration,
                                                    )
                                                velEditNote = null
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            val op = dragOp ?: return@detectDragGestures
                                            change.consume()
                                            val cellWpx = cellWidthDp.toPx()
                                            val curNotes = currentNotes
                                            val note = curNotes.getOrNull(op.idx) ?: return@detectDragGestures
                                            val stepSnap = 0.5f

                                            when (op.mode) {
                                                PianoRollDragMode.MOVE -> {
                                                    val deltaSteps = dragAmount.x / cellWpx
                                                    val rawStep = op.startStep + deltaSteps
                                                    val snappedStep = (rawStep / stepSnap).roundToInt() * stepSnap
                                                    val clampedStep =
                                                        snappedStep.coerceIn(
                                                            0f,
                                                            (numSteps - stepSnap).coerceAtLeast(0f),
                                                        )
                                                    val deltaRows = -(dragAmount.y / cellHeightDp.toPx()).roundToInt()
                                                    val newNoteVal = (op.startNote + deltaRows).coerceIn(startNote, endNote)
                                                    val updated = note.copy(startStep = clampedStep, note = newNoteVal)
                                                    currentOnNotesChange(curNotes.toMutableList().apply { set(op.idx, updated) })
                                                }

                                                PianoRollDragMode.RESIZE -> {
                                                    val deltaSteps = dragAmount.x / cellWpx
                                                    val rawDur = op.origDuration + deltaSteps
                                                    val snappedDur = (rawDur / stepSnap).roundToInt() * stepSnap
                                                    val clampedDur = snappedDur.coerceAtLeast(stepSnap)
                                                    val updated = note.copy(duration = clampedDur)
                                                    currentOnNotesChange(curNotes.toMutableList().apply { set(op.idx, updated) })
                                                }
                                            }
                                        },
                                        onDragEnd = { dragOp = null },
                                        onDragCancel = { dragOp = null },
                                    )
                                },
                    ) {
                        val cellW = cellWidthDp.toPx()
                        val cellH = cellHeightDp.toPx()
                        val totalH = totalNotes * cellH
                        val totalW = numSteps * cellW

                        // Vertical grid lines
                        for (col in 0..numSteps) {
                            val x = col * cellW
                            val isBeat = col % 4 == 0
                            drawLine(
                                color = PanelHighlight.copy(alpha = if (isBeat) 0.2f else 0.08f),
                                start = Offset(x, 0f),
                                end = Offset(x, totalH),
                                strokeWidth = if (isBeat) 1.5f else 0.5f,
                            )
                        }
                        // Horizontal grid lines
                        for (row in 0..totalNotes) {
                            val y = row * cellH
                            val note = endNote - row
                            val isC = note % 12 == 0
                            drawLine(
                                color = PanelHighlight.copy(alpha = if (isC) 0.15f else 0.05f),
                                start = Offset(0f, y),
                                end = Offset(totalW, y),
                                strokeWidth = if (isC) 1f else 0.5f,
                            )
                        }

                        // Notes
                        for (noteData in notes) {
                            val row = endNote - noteData.note
                            val x = noteData.startStep * cellW
                            val y = row * cellH
                            val w = noteData.duration * cellW
                            val isSelected = velEditNote == noteData

                            drawRect(
                                color =
                                    when {
                                        noteData.muted -> TextMuted.copy(alpha = 0.4f)
                                        isSelected -> KnobCyan.copy(alpha = 1f)
                                        else -> KnobCyan.copy(alpha = 0.7f)
                                    },
                                topLeft = Offset(x, y + 1f),
                                size = Size(w, cellH - 2f),
                            )
                            // Velocity overlay
                            val velAlpha = noteData.velocity / 255f
                            drawRect(
                                color = Color.White.copy(alpha = velAlpha * 0.3f),
                                topLeft = Offset(x, y + 1f),
                                size = Size(w, cellH - 2f),
                            )
                            // Resize handle
                            if (w > cellW * 1.5f) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.3f),
                                    start = Offset(x + w - 2f, y + 3f),
                                    end = Offset(x + w - 2f, y + cellH - 3f),
                                    strokeWidth = 1.5f,
                                )
                            }
                        }

                        // Playhead
                        if (isPlaying && currentStep >= 0) {
                            val stepInPattern = currentStep % numSteps
                            val playheadX = stepInPattern * cellW
                            drawLine(
                                color = KnobGreen,
                                start = Offset(playheadX, 0f),
                                end = Offset(playheadX, totalH),
                                strokeWidth = 2f,
                            )
                        }
                    }
                }
            }
        }

        // Velocity editor overlay
        if (velEditNote != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(BgPanel.copy(alpha = 0.95f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        "Vel: ${velEditNote!!.velocity}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(56.dp),
                    )
                    Slider(
                        value = velEditNote!!.velocity.toFloat(),
                        onValueChange = { v ->
                            val currentNote = velEditNote ?: return@Slider
                            val newNote = currentNote.copy(velocity = v.roundToInt().coerceIn(0, 127))
                            val newList = currentNotes.map { if (it == currentNote) newNote else it }
                            currentOnNotesChange(newList)
                            velEditNote = newNote
                        },
                        valueRange = 0f..127f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = KnobCyan,
                                activeTrackColor = KnobCyan,
                                inactiveTrackColor = BgGunmetal,
                            ),
                    )
                    Text(
                        "Done",
                        color = KnobCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { velEditNote = null },
                    )
                }
            }
        }
    }
}

private data class PianoRollDragOp(
    val idx: Int,
    val mode: PianoRollDragMode,
    val startStep: Float,
    val startNote: Int,
    val origDuration: Float,
)

private enum class PianoRollDragMode { MOVE, RESIZE }

// ═══════════════════════════════════════════════════════════════════
// BOTTOM STEP RAIL
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun StepRail(
    numSteps: Int,
    currentStep: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (step in 0 until numSteps) {
            val isCurrent = isPlaying && step == (currentStep % numSteps)
            Box(
                modifier =
                    Modifier
                        .size(width = 18.dp, height = 18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                isCurrent -> KnobGreen
                                else -> BgPanel
                            },
                        ).border(
                            1.dp,
                            if (isCurrent) KnobGreen else PanelHighlight.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${step + 1}",
                    color = if (isCurrent) Color.Black else TextMuted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// VELOCITY POPUP
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun VelocityPopup(
    initialVelocity: Int,
    onVelocityChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var velocity by remember(initialVelocity) { mutableIntStateOf(initialVelocity) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgPanel)
                    .border(1.dp, PanelHighlight, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Step Velocity",
                color = KnobAmber,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "$velocity",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = velocity.toFloat(),
                onValueChange = {
                    velocity = it.roundToInt().coerceIn(0, 127)
                    onVelocityChange(velocity)
                },
                valueRange = 0f..127f,
                colors =
                    SliderDefaults.colors(
                        thumbColor = KnobAmber,
                        activeTrackColor = KnobAmber,
                        inactiveTrackColor = BgGunmetal,
                    ),
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(KnobAmber.copy(alpha = 0.2f))
                        .border(1.dp, KnobAmber, RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("Done", color = KnobAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
