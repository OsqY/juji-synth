package com.jujidaw.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jujidaw.audio.SynthEngine
import com.jujidaw.model.ParamIds
import com.jujidaw.ui.KeyboardView
import com.jujidaw.ui.theme.*

// ── Public API ─────────────────────────────────────────────────────────────

/**
 * Full-screen keyboard performance page.
 *
 * Features:
 * - Chromatic grid (2 octaves visible, C2–C6 range) and classic piano view
 * - Scale/key selector with scale-lock toggle
 * - Velocity from touch Y-position and optional aftertouch drag
 * - Note repeat with rate selector and hold toggle
 * - Arpeggiator (up / down / up-down / random) with rate and 1–4 octave range
 * - Target routing: Synth, Sampler Bank A/B, or Track 1–16
 *
 * Phone-first: pads are sized to fill available space with a 48 dp minimum
 * implied by typical phone widths (6 cols × ~60 dp).
 */
@Composable
fun KeyboardScreen(
    modifier: Modifier = Modifier,
    viewModel: KeyboardViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BgGunmetal)
                .padding(4.dp),
    ) {
        KeyboardTopBar(state = state, viewModel = viewModel)
        Spacer(Modifier.height(4.dp))
        KeyboardControlStrip(state = state, viewModel = viewModel)
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.weight(1f)) {
            when (state.viewMode) {
                KeyboardViewMode.GRID -> {
                    ChromaticGrid(
                        state = state,
                        onNoteOn = { note, y, h -> viewModel.noteOn(note, touchY = y, keyHeight = h) },
                        onNoteOff = { note -> viewModel.noteOff(note) },
                        onNoteDrag = { note, y, h -> viewModel.onNoteDrag(note, y, h) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                KeyboardViewMode.PIANO -> {
                    KeyboardView(
                        activeNotes = state.activeNotes,
                        onNoteOn = { note -> viewModel.noteOn(note) },
                        onNoteOff = { note -> viewModel.noteOff(note) },
                        onPitchBend = { pitch ->
                            SynthEngine.setParam(ParamIds.PITCH_BEND, pitch)
                        },
                        octaveOffset = state.baseOctave,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ── Top bar ────────────────────────────────────────────────────────────────

@Composable
private fun KeyboardTopBar(
    state: KeyboardUiState,
    viewModel: KeyboardViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var targetExpanded by remember { mutableStateOf(false) }
        val allTargets =
            remember {
                listOf(
                    KeyboardTarget.Synth,
                    KeyboardTarget.SamplerA,
                    KeyboardTarget.SamplerB,
                ) + (0..15).map { KeyboardTarget.Track(it) } +
                    (0..31).map { KeyboardTarget.SelectedPad(it) }
            }

        Box {
            CycleButton(
                text = state.target.displayName,
                onClick = { targetExpanded = true },
            )
            DropdownMenu(
                expanded = targetExpanded,
                onDismissRequest = { targetExpanded = false },
            ) {
                allTargets.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.displayName) },
                        onClick = {
                            viewModel.setTarget(target)
                            targetExpanded = false
                        },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OctaveShiftButton(Icons.Outlined.ChevronLeft, "Lower octave") {
                viewModel.setBaseOctave(state.baseOctave - 1)
            }
            Text(
                text = "C${state.baseOctave}",
                color = Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            OctaveShiftButton(Icons.Outlined.ChevronRight, "Raise octave") {
                viewModel.setBaseOctave(state.baseOctave + 1)
            }
        }

        Row {
            ToggleButton(
                text = "Grid",
                active = state.viewMode == KeyboardViewMode.GRID,
                onClick = { viewModel.setViewMode(KeyboardViewMode.GRID) },
            )
            Spacer(Modifier.width(4.dp))
            ToggleButton(
                text = "Piano",
                active = state.viewMode == KeyboardViewMode.PIANO,
                onClick = { viewModel.setViewMode(KeyboardViewMode.PIANO) },
            )
        }
    }
}

// ── Control strip ──────────────────────────────────────────────────────────

@Composable
private fun KeyboardControlStrip(
    state: KeyboardUiState,
    viewModel: KeyboardViewModel,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            CycleButton(state.scaleType.displayName) { viewModel.cycleScaleType() }
        }
        item {
            CycleButton(noteName(state.rootNote)) {
                viewModel.setRootNote((state.rootNote + 1) % 12)
            }
        }
        item {
            ToggleButton("Lock", state.scaleLock) { viewModel.toggleScaleLock() }
        }
        item {
            ToggleButton("Vel", state.velocityFromTouch) { viewModel.toggleVelocityFromTouch() }
        }
        item {
            ToggleButton("AT", state.aftertouchEnabled) { viewModel.toggleAftertouch() }
        }

        item {
            ToggleButton("Rpt", state.noteRepeatEnabled) { viewModel.toggleNoteRepeat() }
        }
        item {
            CycleButton(state.noteRepeatRate.displayName) { viewModel.cycleNoteRepeatRate() }
        }

        item {
            ToggleButton("Arp", state.arpEnabled) { viewModel.toggleArp() }
        }
        item {
            CycleButton(state.arpMode.displayName) { viewModel.cycleArpMode() }
        }
        item {
            CycleButton(state.arpRate.displayName) { viewModel.cycleArpRate() }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniButton("-") { viewModel.setArpOctaveRange(state.arpOctaveRange - 1) }
                Text(
                    text = "${state.arpOctaveRange}",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                MiniButton("+") { viewModel.setArpOctaveRange(state.arpOctaveRange + 1) }
            }
        }
    }
}

// ── Chromatic grid ─────────────────────────────────────────────────────────

@Composable
private fun ChromaticGrid(
    state: KeyboardUiState,
    onNoteOn: (Int, Float, Float) -> Unit,
    onNoteOff: (Int) -> Unit,
    onNoteDrag: (Int, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = 4
    val cols = 6
    val baseNote = (state.baseOctave + 1) * 12 // C2=36, C3=48, C4=60, C5=72

    val currentOnNoteOn by rememberUpdatedState(onNoteOn)
    val currentOnNoteOff by rememberUpdatedState(onNoteOff)
    val currentOnNoteDrag by rememberUpdatedState(onNoteDrag)

    BoxWithConstraints(
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
    ) {
        val density = LocalDensity.current
        val cellWidthPx = with(density) { (maxWidth / cols).toPx() }
        val cellHeightPx = with(density) { (maxHeight / rows).toPx() }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Bg1)
                    .pointerInput(state.baseOctave) {
                        val activePointers = mutableMapOf<Long, Int>()
                        try {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    for (change in event.changes) {
                                        val ptrId = change.id.value
                                        val col =
                                            (change.position.x / cellWidthPx)
                                                .toInt()
                                                .coerceIn(0, cols - 1)
                                        val rowFromTop =
                                            (change.position.y / cellHeightPx)
                                                .toInt()
                                                .coerceIn(0, rows - 1)
                                        val rowFromBottom = (rows - 1) - rowFromTop
                                        val noteOffset = rowFromBottom * cols + col
                                        val midiNote = baseNote + noteOffset

                                        when {
                                            !change.pressed -> {
                                                val oldNote = activePointers.remove(ptrId)
                                                if (oldNote != null) {
                                                    currentOnNoteOff(oldNote)
                                                }
                                                change.consume()
                                            }

                                            ptrId !in activePointers -> {
                                                activePointers[ptrId] = midiNote
                                                currentOnNoteOn(
                                                    midiNote,
                                                    change.position.y,
                                                    cellHeightPx,
                                                )
                                                change.consume()
                                            }

                                            else -> {
                                                val oldNote = activePointers[ptrId]
                                                if (oldNote != midiNote) {
                                                    if (oldNote != null) {
                                                        currentOnNoteOff(oldNote)
                                                    }
                                                    activePointers[ptrId] = midiNote
                                                    currentOnNoteOn(
                                                        midiNote,
                                                        change.position.y,
                                                        cellHeightPx,
                                                    )
                                                } else {
                                                    oldNote?.let {
                                                        currentOnNoteDrag(
                                                            it,
                                                            change.position.y,
                                                            cellHeightPx,
                                                        )
                                                    }
                                                }
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        } finally {
                            activePointers.values.toSet().forEach { currentOnNoteOff(it) }
                            activePointers.clear()
                        }
                    },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in (rows - 1) downTo 0) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (col in 0 until cols) {
                            val noteOffset = row * cols + col
                            val midiNote = baseNote + noteOffset
                            val isActive = midiNote in state.activeNotes
                            val inScale =
                                !state.scaleLock ||
                                    isNoteInScale(
                                        midiNote,
                                        state.rootNote,
                                        state.scaleType,
                                    )
                            KeyPad(
                                note = midiNote,
                                isActive = isActive,
                                isInScale = inScale,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyPad(
    note: Int,
    isActive: Boolean,
    isInScale: Boolean,
    modifier: Modifier = Modifier,
) {
    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val name = noteNames[note % 12]
    val octave = (note / 12) - 1

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        isActive -> Primary
                        !isInScale -> Bg0
                        else -> SurfaceContainer
                    },
                ).border(
                    width = 1.dp,
                    color =
                        if (isInScale) {
                            Outline
                        } else {
                            OutlineVariant
                        },
                    shape = RoundedCornerShape(6.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$name$octave",
            color =
                when {
                    isActive -> Color.Black
                    !isInScale -> TextDisabled
                    else -> TextSecondary
                },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Small reusable controls ────────────────────────────────────────────────

@Composable
private fun OctaveShiftButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = OnSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MiniButton(
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
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ToggleButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (active) KnobAmber.copy(alpha = 0.25f) else BgPanel)
                .border(
                    1.dp,
                    if (active) KnobAmber else PanelHighlight.copy(alpha = 0.4f),
                    RoundedCornerShape(4.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) KnobAmber else TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CycleButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BgPanel)
                .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun noteName(note: Int): String {
    val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return names[(note % 12 + 12) % 12]
}

private fun isNoteInScale(
    note: Int,
    root: Int,
    scale: ScaleType,
): Boolean {
    val semitone = ((note - root) % 12).let { if (it < 0) it + 12 else it }
    return semitone in scale.intervals
}
