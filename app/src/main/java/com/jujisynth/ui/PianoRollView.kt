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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujisynth.audio.SynthEngine
import com.jujisynth.model.PianoRollNote
import com.jujisynth.ui.theme.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * DAW-style piano roll with variable-length notes, velocity editing,
 * and pattern playback synced with the sequencer transport.
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

    // Playhead: trigger noteOn/noteOff as playhead advances
    LaunchedEffect(isPlaying, currentStep) {
        if (!isPlaying) {
            // Stop all sounding notes when transport stops
            soundingNotes.forEach { SynthEngine.noteOff(it) }
            soundingNotes = emptySet()
            return@LaunchedEffect
        }

        // Note-off for notes that ended on previous step
        val endedNotes = soundingNotes.filter { note ->
            notes.none { n ->
                n.note == note &&
                currentStep >= n.startStep &&
                currentStep < n.startStep + n.duration &&
                !n.muted
            }
        }
        endedNotes.forEach { SynthEngine.noteOff(it) }

        // Note-on for notes starting on this step
        val startingNotes = notes.filter { n ->
            !n.muted &&
            currentStep >= n.startStep &&
            currentStep < n.startStep + n.duration &&
            !soundingNotes.contains(n.note)
        }
        startingNotes.forEach { SynthEngine.noteOn(it.note, it.velocity) }

        soundingNotes = (soundingNotes - endedNotes.toSet()) + startingNotes.map { it.note }.toSet()
    }

    // Clean up sounding notes when composable leaves
    DisposableEffect(Unit) {
        onDispose {
            soundingNotes.forEach { SynthEngine.noteOff(it) }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
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
            val densityPx = density.density
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(notes) {
                            detectTapGestures { offset ->
                                val col = (offset.x / (cellWidthDp.toPx())).toInt().coerceIn(0, numSteps - 1)
                                val row = (offset.y / (cellHeightDp.toPx())).toInt()
                                val note = endNote - row

                                // Check if tapping on existing note
                                val existing = notes.find { n ->
                                    n.note == note &&
                                    col >= n.startStep &&
                                    col < n.startStep + n.duration
                                }
                                if (existing != null) {
                                    // Remove the note
                                    onNotesChange(notes - existing)
                                } else {
                                    // Create new note (do NOT play immediately)
                                    val newNote = PianoRollNote(
                                        note = note,
                                        startStep = col.toFloat(),
                                        duration = 1f,
                                        velocity = 100
                                    )
                                    onNotesChange(notes + newNote)
                                }
                            }
                        }
                        .pointerInput(notes) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    // ... drag start handled per gesture state
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                },
                                onDragEnd = { }
                            )
                        }
                ) {
                    val cellW = cellWidthDp.toPx()
                    val cellH = cellHeightDp.toPx()
                    val totalH = totalNotes * cellH
                    val totalW = numSteps * cellW

                    // Draw grid lines
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

                    // Draw existing notes
                    for (noteData in notes) {
                        val row = endNote - noteData.note
                        val x = noteData.startStep * cellW
                        val y = row * cellH
                        val w = noteData.duration * cellW
                        val isSounding = isPlaying && soundingNotes.contains(noteData.note)

                        // Note rectangle
                        drawRect(
                            color = if (noteData.muted)
                                TextMuted.copy(alpha = 0.4f)
                            else if (isSounding)
                                KnobGreen
                            else
                                KnobCyan.copy(alpha = 0.7f),
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
                    }

                    // Draw playhead
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
}
