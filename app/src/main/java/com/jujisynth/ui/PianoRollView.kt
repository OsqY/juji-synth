package com.jujisynth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujisynth.audio.SynthEngine
import com.jujisynth.model.PianoRollNote
import com.jujisynth.ui.theme.*
import kotlin.math.roundToInt

/**
 * DAW-style piano roll with variable-length notes, velocity editing,
 * dragging, resizing, and pattern playback synced with the sequencer transport.
 */
@Composable
fun PianoRollView(
    notes: List<PianoRollNote>,
    onNotesChange: (List<PianoRollNote>) -> Unit,
    numSteps: Int = 16,
    currentStep: Int = -1,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val startNote = 36  // C2
    val endNote = 84    // C7
    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val totalNotes = endNote - startNote
    val cellWidthDp = 28.dp
    val cellHeightDp = 14.dp
    val density = LocalDensity.current

    // Track which notes are currently sounding (for playback visualization)
    var soundingNotes by remember { mutableStateOf(setOf<Int>()) }

    // Drag interaction state (Task 9.5 & 9.6)
    var dragOp by remember { mutableStateOf<DragOp?>(null) }

    // Velocity editing state (Task 9.7)
    var velEditNote by remember { mutableStateOf<PianoRollNote?>(null) }

    // Stable refs for gesture handlers so they survive recomposition
    val currentNotes by rememberUpdatedState(notes)
    val currentOnNotesChange by rememberUpdatedState(onNotesChange)

    // Playhead: trigger noteOn/noteOff as playhead advances
    LaunchedEffect(isPlaying, currentStep) {
        if (!isPlaying) {
            soundingNotes.forEach { SynthEngine.noteOff(it) }
            soundingNotes = emptySet()
            return@LaunchedEffect
        }

        val endedNotes = soundingNotes.filter { note ->
            notes.none { n ->
                n.note == note &&
                currentStep >= n.startStep &&
                currentStep < n.startStep + n.duration &&
                !n.muted
            }
        }
        endedNotes.forEach { SynthEngine.noteOff(it) }

        val startingNotes = notes.filter { n ->
            !n.muted &&
            currentStep >= n.startStep &&
            currentStep < n.startStep + n.duration &&
            !soundingNotes.contains(n.note)
        }
        startingNotes.forEach { SynthEngine.noteOn(it.note, it.velocity) }

        soundingNotes = (soundingNotes - endedNotes.toSet()) + startingNotes.map { it.note }.toSet()
    }

    DisposableEffect(Unit) {
        onDispose {
            soundingNotes.forEach { SynthEngine.noteOff(it) }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // === MAIN GRID LAYOUT ===
        Column {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                for (col in 0 until numSteps) {
                    val isCurrent = col == currentStep && isPlaying
                    Box(
                        modifier = Modifier
                            .width(cellWidthDp)
                            .height(16.dp)
                            .background(if (isCurrent) KnobCyan.copy(alpha = 0.3f) else BgPanel.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$col", color = if (isCurrent) KnobCyan else TextMuted, fontSize = 7.sp)
                    }
                }
            }

            // Grid body
            Row(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                // Note labels
                Column(
                    modifier = Modifier
                        .width(40.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (note in endNote downTo startNote) {
                        val noteName = noteNames[note % 12]
                        val octave = (note / 12) - 1
                        val isC = noteName == "C"
                        Box(
                            modifier = Modifier
                                .height(cellHeightDp)
                                .width(40.dp)
                                .background(if (isC) BgPanel.copy(alpha = 0.3f) else BgGunmetal),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                if (isC) "$noteName$octave" else "",
                                color = TextSecondary,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }
                    }
                }

                // Grid with gesture handling
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            // ----- TAP & LONG-PRESS -----
                            .pointerInput(notes, numSteps) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val cellWpx = cellWidthDp.toPx()
                                        val cellHpx = cellHeightDp.toPx()
                                        val col = (offset.x / cellWpx).toInt().coerceIn(0, numSteps - 1)
                                        val row = (offset.y / cellHpx).toInt()
                                        val note = endNote - row

                                        // Dismiss velocity editor
                                        velEditNote = null

                                        val existing = currentNotes.find { n ->
                                            n.note == note &&
                                            col >= n.startStep &&
                                            col < n.startStep + n.duration
                                        }
                                        if (existing != null) {
                                            // Delete note
                                            currentOnNotesChange(currentNotes - existing)
                                        } else {
                                            // Create note (not played until transport)
                                            val newNote = PianoRollNote(
                                                note = note,
                                                startStep = col.toFloat(),
                                                duration = 1f,
                                                velocity = 100
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
                                        val hit = currentNotes.find { n ->
                                            n.note == note &&
                                            col >= n.startStep &&
                                            col < n.startStep + n.duration
                                        }
                                        if (hit != null) {
                                            velEditNote = hit
                                        }
                                    }
                                )
                            }
                            // ----- DRAG: MOVE & RESIZE -----
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val cellWpx = cellWidthDp.toPx()
                                        val cellHpx = cellHeightDp.toPx()
                                        val col = (offset.x / cellWpx).toFloat()
                                        val row = (offset.y / cellHpx).toInt()
                                        val note = endNote - row
                                        val hitIdx = currentNotes.indexOfFirst { n ->
                                            n.note == note &&
                                            col >= n.startStep &&
                                            col < n.startStep + n.duration
                                        }
                                        if (hitIdx >= 0) {
                                            val hitNote = currentNotes[hitIdx]
                                            // Right 25% → resize, otherwise → move
                                            val isRightEdge = col > hitNote.startStep + hitNote.duration * 0.75f
                                            dragOp = DragOp(
                                                idx = hitIdx,
                                                mode = if (isRightEdge) DragMode.RESIZE else DragMode.MOVE,
                                                startStep = hitNote.startStep,
                                                startNote = hitNote.note,
                                                origDuration = hitNote.duration
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
                                        val stepSnap = 0.5f  // snap to half-step grid

                                        when (op.mode) {
                                            DragMode.MOVE -> {
                                                val deltaSteps = dragAmount.x / cellWpx
                                                val rawStep = op.startStep + deltaSteps
                                                val snappedStep = (rawStep / stepSnap).roundToInt() * stepSnap
                                                val clampedStep = snappedStep.coerceIn(0f, (numSteps - stepSnap).coerceAtLeast(0f))

                                                val deltaRows = -(dragAmount.y / cellHeightDp.toPx()).roundToInt()
                                                val newNoteVal = (op.startNote + deltaRows).coerceIn(startNote, endNote)

                                                val updated = note.copy(startStep = clampedStep, note = newNoteVal)
                                                currentOnNotesChange(curNotes.toMutableList().apply { set(op.idx, updated) })
                                            }
                                            DragMode.RESIZE -> {
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
                                    onDragCancel = { dragOp = null }
                                )
                            }
                    ) {
                        val cellW = cellWidthDp.toPx()
                        val cellH = cellHeightDp.toPx()
                        val totalH = totalNotes * cellH
                        val totalW = numSteps * cellW

                        // Grid lines
                        for (col in 0..numSteps) {
                            val x = col * cellW
                            val isBeat = col % 4 == 0
                            drawLine(
                                color = PanelHighlight.copy(alpha = if (isBeat) 0.2f else 0.08f),
                                start = Offset(x, 0f),
                                end = Offset(x, totalH),
                                strokeWidth = if (isBeat) 1.5f else 0.5f
                            )
                        }
                        for (row in 0..totalNotes) {
                            val y = row * cellH
                            val note = endNote - row
                            val isC = note % 12 == 0
                            drawLine(
                                color = PanelHighlight.copy(alpha = if (isC) 0.15f else 0.05f),
                                start = Offset(0f, y),
                                end = Offset(totalW, y),
                                strokeWidth = if (isC) 1f else 0.5f
                            )
                        }

                        // Draw notes
                        for (noteData in notes) {
                            val row = endNote - noteData.note
                            val x = noteData.startStep * cellW
                            val y = row * cellH
                            val w = noteData.duration * cellW
                            val isSounding = isPlaying && soundingNotes.contains(noteData.note)
                            val isSelected = velEditNote == noteData

                            // Note body
                            drawRect(
                                color = when {
                                    noteData.muted -> TextMuted.copy(alpha = 0.4f)
                                    isSounding -> KnobGreen
                                    isSelected -> KnobCyan.copy(alpha = 1f)  // highlight selected
                                    else -> KnobCyan.copy(alpha = 0.7f)
                                },
                                topLeft = Offset(x, y + 1f),
                                size = Size(w, cellH - 2f)
                            )
                            // Velocity brightness overlay
                            val velAlpha = noteData.velocity / 255f
                            drawRect(
                                color = Color.White.copy(alpha = velAlpha * 0.3f),
                                topLeft = Offset(x, y + 1f),
                                size = Size(w, cellH - 2f)
                            )

                            // Resize handle indicator (subtle vertical line at right edge)
                            if (w > cellW * 1.5f) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.3f),
                                    start = Offset(x + w - 2f, y + 3f),
                                    end = Offset(x + w - 2f, y + cellH - 3f),
                                    strokeWidth = 1.5f
                                )
                            }
                        }

                        // Playhead
                        if (isPlaying && currentStep >= 0 && currentStep < numSteps) {
                            val playheadX = currentStep * cellW
                            drawLine(
                                color = KnobGreen,
                                start = Offset(playheadX, 0f),
                                end = Offset(playheadX, totalH),
                                strokeWidth = 2f
                            )
                        }
                    }
                }
            }
        }

        // === VELOCITY EDITOR OVERLAY (Task 9.7) ===
        if (velEditNote != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(BgPanel.copy(alpha = 0.95f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        "Vel: ${velEditNote!!.velocity}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(56.dp)
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
                        colors = SliderDefaults.colors(
                            thumbColor = KnobCyan,
                            activeTrackColor = KnobCyan,
                            inactiveTrackColor = BgGunmetal
                        )
                    )
                    TextButton(onClick = { velEditNote = null }) {
                        Text("Done", color = KnobCyan, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// --- Drag interaction state types (Tasks 9.5 & 9.6) ---
private data class DragOp(
    val idx: Int,
    val mode: DragMode,
    val startStep: Float,
    val startNote: Int,
    val origDuration: Float
)

private enum class DragMode { MOVE, RESIZE }
