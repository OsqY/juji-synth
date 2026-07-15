package com.jujidaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier,
) {
    val currentOnNoteOn by rememberUpdatedState(onNoteOn)
    val currentOnNoteOff by rememberUpdatedState(onNoteOff)
    val currentOnPitchBend by rememberUpdatedState(onPitchBend)

    // MIDI note data per octave
    val whiteKeyToNote = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    val blackKeyOffsets = mapOf(1 to 0.6f, 2 to 1.2f, 4 to 2.7f, 5 to 3.3f, 6 to 3.9f)
    val whiteIdxToBlackNote = mapOf(1 to 1, 2 to 3, 4 to 6, 5 to 8, 6 to 10)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainer),
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
            octaveOffset = octaveOffset,
        )
    }
}

// ---------------------------------------------------------------------------
// Pitch-bend strip
// ---------------------------------------------------------------------------

@Composable
private fun PitchBendStrip(onPitchBend: (Float) -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val totalWidth = size.width.toFloat()

                        fun Float.toBend(): Float = ((this / totalWidth) * 2f - 1f).coerceIn(-1f, 1f)

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
                },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush =
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                SurfaceContainer.copy(alpha = 0.6f),
                                SurfaceContainer.copy(alpha = 0.3f),
                                SurfaceContainer.copy(alpha = 0.6f),
                            ),
                    ),
                size = size,
            )
            val centreX = w / 2f
            drawLine(
                color = Secondary,
                start = Offset(centreX, 0f),
                end = Offset(centreX, h),
                strokeWidth = 2f,
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
    octaveOffset: Int,
) {
    val textMeasurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp),
    ) {
        val keyboardWidth = maxWidth * (OCTAVE_COUNT / 2f)

        Box(
            modifier =
                Modifier
                    .width(keyboardWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(SurfaceContainer)
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
                                                val note =
                                                    noteAtPosition(
                                                        x = change.position.x,
                                                        y = change.position.y,
                                                        viewWidth = size.width.toFloat(),
                                                        viewHeight = size.height.toFloat(),
                                                        whiteKeyToNote = whiteKeyToNote,
                                                        blackKeyOffsets = blackKeyOffsets,
                                                        whiteIdxToBlackNote = whiteIdxToBlackNote,
                                                        octaveOffset = octaveOffset,
                                                    )
                                                if (note >= 0) {
                                                    activePointers[ptrId] = note
                                                    onNoteOn(note)
                                                }
                                                change.consume()
                                            }

                                            else -> {
                                                val oldNote = activePointers[ptrId]
                                                val newNote =
                                                    noteAtPosition(
                                                        x = change.position.x,
                                                        y = change.position.y,
                                                        viewWidth = size.width.toFloat(),
                                                        viewHeight = size.height.toFloat(),
                                                        whiteKeyToNote = whiteKeyToNote,
                                                        blackKeyOffsets = blackKeyOffsets,
                                                        whiteIdxToBlackNote = whiteIdxToBlackNote,
                                                        octaveOffset = octaveOffset,
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
                    },
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
                    textMeasurer = textMeasurer,
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
    octaveOffset: Int,
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
// Flat key rendering with note labels
// ---------------------------------------------------------------------------

/**
 * Draws white and black keys in the flat Ableton-inspired style with note-name
 * labels on each key.
 *
 * - **White keys**: flat [KeyWhite] fill + flat top highlight; [Primary] when pressed
 * - **Black keys**: flat [KeyBlack] fill + thin [Outline]; [Primary] when pressed
 * - **Note labels**: [CaptionSmall] — name + octave near the bottom of each white
 *   key, plus a lighter label near the top of each black key
 */
private fun DrawScope.drawKeyboard(
    viewWidth: Float,
    viewHeight: Float,
    activeNotes: Set<Int>,
    whiteKeyToNote: IntArray,
    blackKeyOffsets: Map<Int, Float>,
    whiteIdxToBlackNote: Map<Int, Int>,
    octaveOffset: Int,
    textMeasurer: TextMeasurer,
) {
    val keyWidth = viewWidth / (OCTAVE_COUNT * 7).toFloat()
    val blackKeyWidth = keyWidth * 0.55f
    val blackKeyHeight = viewHeight * 0.65f
    val baseOctaveMidi = 48 + (octaveOffset - 3) * 12

    // ── White keys ──────────────────────────────────────────────────────
    for (octave in 0 until OCTAVE_COUNT) {
        for (whiteIdx in 0 until 7) {
            val semitone = baseOctaveMidi + octave * 12 + whiteKeyToNote[whiteIdx]
            val isPressed = semitone in activeNotes
            val x = (octave * 7 + whiteIdx) * keyWidth

            // Flat surface — KeyWhite, or Primary when pressed.
            drawRect(
                color = if (isPressed) Primary else KeyWhite,
                topLeft = Offset(x, 0f),
                size = Size(keyWidth, viewHeight),
            )

            // Flat top highlight (lighter band) — resting keys only.
            if (!isPressed) {
                drawRect(
                    color = OnSurface.copy(alpha = 0.18f),
                    topLeft = Offset(x, 0f),
                    size = Size(keyWidth, Spacing.xs.toPx()),
                )
            }

            // Quiet divider between adjacent white keys.
            drawRect(
                color = OutlineVariant,
                topLeft = Offset(x + keyWidth - 1f, 0f),
                size = Size(1f, viewHeight),
            )
        }
    }

    // ── Black keys ──────────────────────────────────────────────────────
    for (octave in 0 until OCTAVE_COUNT) {
        for ((whiteIdx, offsetRat) in blackKeyOffsets) {
            val semitone = baseOctaveMidi + octave * 12 + whiteIdxToBlackNote[whiteIdx]!!
            val isPressed = semitone in activeNotes
            val baseX = octave * 7 * keyWidth
            val x = baseX + offsetRat * keyWidth - blackKeyWidth / 2f

            // Flat surface — KeyBlack, or Primary when pressed.
            drawRect(
                color = if (isPressed) Primary else KeyBlack,
                topLeft = Offset(x, 0f),
                size = Size(blackKeyWidth, blackKeyHeight),
            )

            // Thin outline (quiet key boundary).
            drawRect(
                color = Outline,
                topLeft = Offset(x, 0f),
                size = Size(blackKeyWidth, blackKeyHeight),
                style = Stroke(width = 1f),
            )
        }
    }

    // ── Note labels on white keys — CaptionSmall near the bottom ────────
    val noteNames = arrayOf("C", "D", "E", "F", "G", "A", "B")
    val whiteLabelStyle = CaptionSmall.copy(color = TextSecondary)

    for (octave in 0 until OCTAVE_COUNT) {
        for (whiteIdx in 0 until 7) {
            val x = (octave * 7 + whiteIdx) * keyWidth
            val midiNote = baseOctaveMidi + octave * 12 + whiteKeyToNote[whiteIdx]
            val displayOctave = (midiNote / 12) - 1
            val label = "${noteNames[whiteIdx]}$displayOctave"
            val textLayout =
                textMeasurer.measure(
                    text = label,
                    style = whiteLabelStyle,
                )
            drawText(
                textLayoutResult = textLayout,
                topLeft =
                    Offset(
                        x = x + (keyWidth - textLayout.size.width) / 2f,
                        y = viewHeight - textLayout.size.height - Spacing.sm.toPx(),
                    ),
            )
        }
    }

    // ── Note labels on black keys — CaptionSmall, lighter, near the top ─
    val blackNoteNames = mapOf(1 to "C#", 2 to "D#", 4 to "F#", 5 to "G#", 6 to "A#")
    val blackLabelStyle = CaptionSmall.copy(color = OnSurface)

    for (octave in 0 until OCTAVE_COUNT) {
        for ((whiteIdx, offsetRat) in blackKeyOffsets) {
            val semitone = baseOctaveMidi + octave * 12 + whiteIdxToBlackNote[whiteIdx]!!
            if (semitone in activeNotes) continue // skip label when pressed

            val baseX = octave * 7 * keyWidth
            val x = baseX + offsetRat * keyWidth - blackKeyWidth / 2f
            val displayOctave = (semitone / 12) - 1
            val label = "${blackNoteNames[whiteIdx]}$displayOctave"
            val textLayout = textMeasurer.measure(text = label, style = blackLabelStyle)
            drawText(
                textLayoutResult = textLayout,
                topLeft =
                    Offset(
                        x = x + (blackKeyWidth - textLayout.size.width) / 2f,
                        y = Spacing.xs.toPx(),
                    ),
            )
        }
    }
}
