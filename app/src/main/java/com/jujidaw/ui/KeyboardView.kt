package com.jujidaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.ui.theme.*

/**
 * 2-octave piano keyboard with multi-touch (per-pointer gesture lifecycle),
 * note labels, 3D key rendering, pitch-bend strip, and octave offset.
 */

private const val OCTAVE_COUNT = 6

@Composable
fun KeyboardView(
    activeNotes: Set<Int> = emptySet(),
    onNoteOn: (Int) -> Unit = {},
    onNoteOff: (Int) -> Unit = {},
    onPitchBend: (Float) -> Unit = {},
    octaveOffset: Int = 3,
    modifier: Modifier = Modifier
) {
    val currentOnNoteOn by rememberUpdatedState(onNoteOn)
    val currentOnNoteOff by rememberUpdatedState(onNoteOff)
    val currentOnPitchBend by rememberUpdatedState(onPitchBend)

    // MIDI note data per octave
    val whiteKeyToNote = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    val blackKeyOffsets = mapOf(1 to 0.6f, 2 to 1.2f, 4 to 2.7f, 5 to 3.3f, 6 to 3.9f)
    val whiteIdxToBlackNote = mapOf(1 to 1, 2 to 3, 4 to 6, 5 to 8, 6 to 10)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(BgPanel)
    ) {
        // ── Pitch bend strip ────────────────────────────────────────────
        PitchBendStrip(onPitchBend = currentOnPitchBend)

        // ── Keyboard keys ───────────────────────────────────────────────
        KeyboardKeys(
            activeNotes = activeNotes,
            onNoteOn = currentOnNoteOn,
            onNoteOff = currentOnNoteOff,
            whiteKeyToNote = whiteKeyToNote,
            blackKeyOffsets = blackKeyOffsets,
            whiteIdxToBlackNote = whiteIdxToBlackNote,
            octaveOffset = octaveOffset
        )
    }
}

// ---------------------------------------------------------------------------
// Pitch-bend strip
// ---------------------------------------------------------------------------

@Composable
private fun PitchBendStrip(onPitchBend: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val totalWidth = size.width.toFloat()

                    fun Float.toBend(): Float =
                        ((this / totalWidth) * 2f - 1f).coerceIn(-1f, 1f)

                    onPitchBend(down.position.x.toBend())

                    var active = true
                    while (active) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (change.id == down.id) {
                                if (change.pressed) {
                                    onPitchBend(change.position.x.toBend())
                                    change.consume()
                                } else {
                                    active = false
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        BgPanel.copy(alpha = 0.6f),
                        BgPanel.copy(alpha = 0.3f),
                        BgPanel.copy(alpha = 0.6f)
                    )
                ),
                size = size
            )
            val centreX = w / 2f
            drawLine(
                color = KnobCyan.copy(alpha = 0.8f),
                start = Offset(centreX, 0f),
                end = Offset(centreX, h),
                strokeWidth = 2f
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Keyboard body – multi-touch with per-pointer gesture lifecycle
// ---------------------------------------------------------------------------

/**
 * The 64 dp keyboard surface.
 *
 * Uses [awaitEachGesture] so each pointer gets its own coroutine lifecycle.
 * A [finally] block ensures note-off fires even on gesture cancellation
 * (e.g. when a second pointer arrives and the OS cancels the first gesture).
 *
 * @param octaveOffset shifts all notes up/down by that many octaves from C3.
 */
@Composable
private fun KeyboardKeys(
    activeNotes: Set<Int>,
    onNoteOn: (Int) -> Unit,
    onNoteOff: (Int) -> Unit,
    whiteKeyToNote: IntArray,
    blackKeyOffsets: Map<Int, Float>,
    whiteIdxToBlackNote: Map<Int, Int>,
    octaveOffset: Int
) {
    val textMeasurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        val keyboardWidth = maxWidth * (OCTAVE_COUNT / 2f)

        Box(
            modifier = Modifier
                .width(keyboardWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(BgPanel)
                .horizontalScroll(scrollState)
                // key = octaveOffset → restarts gesture detector when octave changes
                .pointerInput(octaveOffset) {
                    awaitPointerEventScope {
                        val activePointers = mutableMapOf<Long, Int>()
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                for (change in event.changes) {
                                    val ptrId = change.id.value
                                    when {
                                        !change.pressed -> {
                                            val oldNote = activePointers.remove(ptrId)
                                            if (oldNote != null && oldNote >= 0) onNoteOff(oldNote)
                                            change.consume()
                                        }
                                        ptrId !in activePointers -> {
                                            val note = noteAtPosition(
                                                x = change.position.x, y = change.position.y,
                                                viewWidth = size.width.toFloat(), viewHeight = size.height.toFloat(),
                                                whiteKeyToNote = whiteKeyToNote, blackKeyOffsets = blackKeyOffsets,
                                                whiteIdxToBlackNote = whiteIdxToBlackNote, octaveOffset = octaveOffset
                                            )
                                            if (note >= 0) {
                                                activePointers[ptrId] = note
                                                onNoteOn(note)
                                            }
                                            change.consume()
                                        }
                                        else -> {
                                            val oldNote = activePointers[ptrId]
                                            val newNote = noteAtPosition(
                                                x = change.position.x, y = change.position.y,
                                                viewWidth = size.width.toFloat(), viewHeight = size.height.toFloat(),
                                                whiteKeyToNote = whiteKeyToNote, blackKeyOffsets = blackKeyOffsets,
                                                whiteIdxToBlackNote = whiteIdxToBlackNote, octaveOffset = octaveOffset
                                            )
                                            when {
                                                newNote >= 0 && newNote != oldNote -> {
                                                    if (oldNote != null) onNoteOff(oldNote)
                                                    activePointers[ptrId] = newNote
                                                    onNoteOn(newNote)
                                                }
                                                newNote < 0 && oldNote != null -> {
                                                    onNoteOff(oldNote)
                                                    activePointers.remove(ptrId)
                                                }
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        } finally {
                            // CRITICAL: release all tracked notes when gesture detector is cancelled
                            activePointers.values.forEach { note -> if (note >= 0) onNoteOff(note) }
                            activePointers.clear()
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.width(keyboardWidth).fillMaxHeight()) {
                drawKeyboard(
                    viewWidth = size.width,
                    viewHeight = size.height,
                    activeNotes = activeNotes,
                    whiteKeyToNote = whiteKeyToNote,
                    blackKeyOffsets = blackKeyOffsets,
                    whiteIdxToBlackNote = whiteIdxToBlackNote,
                    octaveOffset = octaveOffset,
                    textMeasurer = textMeasurer
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Note-at-position logic (black-key-aware) with octave offset
// ---------------------------------------------------------------------------

/**
 * Determines the MIDI note under the touch point (x, y).
 *
 * Black keys are checked first when the touch is in the top 65 % of the
 * keyboard.  If no black key is hit (or the touch is too low), the
 * corresponding white-key note is returned.
 *
 * The result is shifted by [octaveOffset]: octaveOffset=3 means the first
 * white key is C3 (MIDI 48), octaveOffset=4 means C4 (MIDI 60), etc.
 *
 * @return MIDI note or -1 if outside the keyboard.
 */
private fun noteAtPosition(
    x: Float,
    y: Float,
    viewWidth: Float,
    viewHeight: Float,
    whiteKeyToNote: IntArray,
    blackKeyOffsets: Map<Int, Float>,
    whiteIdxToBlackNote: Map<Int, Int>,
    octaveOffset: Int
): Int {
    val totalWhiteKeys = OCTAVE_COUNT * 7
    val keyWidth = viewWidth / totalWhiteKeys.toFloat()
    val blackKeyWidth = keyWidth * 0.55f
    val blackKeyHeight = viewHeight * 0.65f
    val baseOctaveMidi = 48 + (octaveOffset - 3) * 12 // MIDI 48 = C3 = offset 3

    // 1) Black-key hit test (only top portion of the keyboard)
    if (y <= blackKeyHeight) {
        for (octave in 0 until OCTAVE_COUNT) {
            for ((whiteIdx, offsetRat) in blackKeyOffsets) {
                val baseWhiteX = octave * 7 * keyWidth
                val centreX = baseWhiteX + offsetRat * keyWidth
                val left = centreX - blackKeyWidth / 2f
                val right = centreX + blackKeyWidth / 2f

                if (x in left..right) {
                    return baseOctaveMidi + octave * 12 + whiteIdxToBlackNote[whiteIdx]!!
                }
            }
        }
    }

    // 2) White key (fall-through)
    val whiteIndex = (x / keyWidth).toInt()
    if (whiteIndex < 0 || whiteIndex >= totalWhiteKeys) return -1
    val octave = whiteIndex / 7
    val whiteNote = whiteIndex % 7
    return baseOctaveMidi + octave * 12 + whiteKeyToNote[whiteNote]
}

// ---------------------------------------------------------------------------
// 3D key rendering with note labels
// ---------------------------------------------------------------------------

/**
 * Draws two octaves of white and black keys with a three-dimensional look
 * and note-name labels on white keys.
 *
 * - **White keys**: subtle vertical gradient + top highlight + shadow
 * - **Black keys**: top/left highlights + outline + shadow
 * - **Pressed keys**: shifted down 1.5 dp, intensified shadow, [KeyPressed] colour
 * - **Note labels**: note name + octave number drawn near the bottom of each white key
 */
private fun DrawScope.drawKeyboard(
    viewWidth: Float,
    viewHeight: Float,
    activeNotes: Set<Int>,
    whiteKeyToNote: IntArray,
    blackKeyOffsets: Map<Int, Float>,
    whiteIdxToBlackNote: Map<Int, Int>,
    octaveOffset: Int,
    textMeasurer: TextMeasurer
) {
    val keyWidth = viewWidth / (OCTAVE_COUNT * 7).toFloat()
    val blackKeyWidth = keyWidth * 0.55f
    val blackKeyHeight = viewHeight * 0.65f
    val pressedOffset = 1.5.dp.toPx()
    val baseOctaveMidi = 48 + (octaveOffset - 3) * 12

    // ── White keys ──────────────────────────────────────────────────────
    for (octave in 0 until OCTAVE_COUNT) {
        for (whiteIdx in 0 until 7) {
            val semitone = baseOctaveMidi + octave * 12 + whiteKeyToNote[whiteIdx]
            val isPressed = semitone in activeNotes
            val x = (octave * 7 + whiteIdx) * keyWidth
            val offY = if (isPressed) pressedOffset else 0f

            // Shadow (offset down-right) — skip for pressed keys
            if (!isPressed) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.15f),
                    topLeft = Offset(x + 1.5f, 1.5f),
                    size = Size(keyWidth - 1f, viewHeight)
                )
            }

            // Main surface
            if (isPressed) {
                drawRect(
                    color = KeyPressed,
                    topLeft = Offset(x, offY),
                    size = Size(keyWidth - 1f, viewHeight - offY)
                )
                // Intensified bottom shadow when pressed
                drawRect(
                    color = Color.Black.copy(alpha = 0.3f),
                    topLeft = Offset(x, viewHeight - 2f),
                    size = Size(keyWidth - 1f, 2f)
                )
            } else {
                // Subtle gradient: top slightly lighter
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White, KeyWhite),
                        startY = 0f,
                        endY = viewHeight * 0.3f
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(keyWidth - 1f, viewHeight)
                )
                // Thin highlight edge at the top
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.7f), Color.Transparent)
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(keyWidth - 1f, 2f)
                )
            }
        }
    }

    // ── Black keys ──────────────────────────────────────────────────────
    for (octave in 0 until OCTAVE_COUNT) {
        for ((whiteIdx, offsetRat) in blackKeyOffsets) {
            val semitone = baseOctaveMidi + octave * 12 + whiteIdxToBlackNote[whiteIdx]!!
            val isPressed = semitone in activeNotes
            val baseX = octave * 7 * keyWidth
            val x = baseX + offsetRat * keyWidth - blackKeyWidth / 2f
            val offY = if (isPressed) pressedOffset else 0f

            // Shadow
            drawRect(
                color = Color.Black.copy(alpha = if (isPressed) 0.4f else 0.2f),
                topLeft = Offset(x + 1f, 1f + offY),
                size = Size(blackKeyWidth, blackKeyHeight)
            )

            // Surface
            drawRect(
                color = if (isPressed) KeyPressed else KeyBlack,
                topLeft = Offset(x, offY),
                size = Size(blackKeyWidth, blackKeyHeight)
            )

            // Top highlight edge (depth)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                ),
                topLeft = Offset(x, offY),
                size = Size(blackKeyWidth, 3f)
            )

            // Left-edge highlight (depth)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                ),
                topLeft = Offset(x, offY),
                size = Size(2f, blackKeyHeight)
            )

            // Thin outline
            drawRect(
                color = BgPanel.copy(alpha = 0.4f),
                topLeft = Offset(x, offY),
                size = Size(blackKeyWidth, blackKeyHeight),
                style = Stroke(width = 0.5f)
            )
        }
    }

    // ── Note labels on white keys ───────────────────────────────────────
    val noteNames = arrayOf("C", "D", "E", "F", "G", "A", "B")
    val labelStyle = TextStyle(
        color = Color(0xFF333333),
        fontSize = 11.sp
    )

    for (octave in 0 until OCTAVE_COUNT) {
        for (whiteIdx in 0 until 7) {
            val x = (octave * 7 + whiteIdx) * keyWidth
            val midiNote = baseOctaveMidi + octave * 12 + whiteKeyToNote[whiteIdx]
            val displayOctave = (midiNote / 12) - 1
            val label = "${noteNames[whiteIdx]}$displayOctave"
            val textLayout = textMeasurer.measure(
                text = label,
                style = labelStyle
            )
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(
                    x = x + (keyWidth - 1f - textLayout.size.width) / 2f,
                    y = viewHeight - textLayout.size.height - 4.dp.toPx()
                )
            )
        }
    }

    // ── Note labels on black keys ────────────────────────────────────────
    val blackNoteNames = mapOf(1 to "C#", 2 to "D#", 4 to "F#", 5 to "G#", 6 to "A#")
    val blackLabelStyle = TextStyle(
        color = Color(0xFF9A9AB0),
        fontSize = 9.sp
    )

    for (octave in 0 until OCTAVE_COUNT) {
        for ((whiteIdx, offsetRat) in blackKeyOffsets) {
            val semitone = baseOctaveMidi + octave * 12 + whiteIdxToBlackNote[whiteIdx]!!
            val isBlackPressed = semitone in activeNotes
            if (isBlackPressed) continue // skip label when pressed

            val baseX = octave * 7 * keyWidth
            val x = baseX + offsetRat * keyWidth - blackKeyWidth / 2f
            val displayOctave = (semitone / 12) - 1
            val label = "${blackNoteNames[whiteIdx]}$displayOctave"
            val textLayout = textMeasurer.measure(text = label, style = blackLabelStyle)
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(
                    x = x + (blackKeyWidth - textLayout.size.width) / 2f,
                    y = 2.dp.toPx()
                )
            )
        }
    }
}
