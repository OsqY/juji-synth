package com.jujidaw.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
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
    var showControls by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Bg0)
                .padding(Spacing.sm)
                .testTag("keyboard-root"),
    ) {
        KeyboardTopBar(
            state = state,
            viewModel = viewModel,
            controlsVisible = showControls,
            onToggleControls = { showControls = !showControls },
        )
        if (showControls) {
            Spacer(Modifier.height(Spacing.sm))
            KeyboardControlStrip(state = state, viewModel = viewModel)
        }
        Spacer(Modifier.height(Spacing.sm))
        Box(modifier = Modifier.weight(1f).testTag("keyboard-grid")) {
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
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TouchTargetMin)
                .background(SurfaceContainer)
                .padding(horizontal = Spacing.xs)
                .testTag("keyboard-toolbar"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
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
                modifier = Modifier.widthIn(min = 68.dp).testTag("keyboard-target"),
                contentDescription = "Keyboard target: ${state.target.displayName}",
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

        Spacer(Modifier.weight(1f))

        OctaveShiftButton(
            icon = Icons.Outlined.ChevronLeft,
            contentDescription = "Lower octave",
            onClick = { viewModel.setBaseOctave(state.baseOctave - 1) },
            modifier = Modifier.testTag("keyboard-octave-down"),
        )
        Text(
            text = "C${state.baseOctave}",
            color = Secondary,
            style = MonoLarge,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center,
        )
        OctaveShiftButton(
            icon = Icons.Outlined.ChevronRight,
            contentDescription = "Raise octave",
            onClick = { viewModel.setBaseOctave(state.baseOctave + 1) },
            modifier = Modifier.testTag("keyboard-octave-up"),
        )
        KeyboardIconButton(
            icon = Icons.Filled.Dashboard,
            contentDescription = "Grid layout",
            selected = state.viewMode == KeyboardViewMode.GRID,
            onClick = { viewModel.setViewMode(KeyboardViewMode.GRID) },
            modifier = Modifier.testTag("keyboard-view-grid"),
        )
        KeyboardIconButton(
            icon = Icons.Filled.MusicNote,
            contentDescription = "Piano layout",
            selected = state.viewMode == KeyboardViewMode.PIANO,
            onClick = { viewModel.setViewMode(KeyboardViewMode.PIANO) },
            modifier = Modifier.testTag("keyboard-view-piano"),
        )
        KeyboardIconButton(
            icon = Icons.Outlined.Tune,
            contentDescription = if (controlsVisible) "Hide performance controls" else "Show performance controls",
            selected = controlsVisible,
            onClick = onToggleControls,
            modifier = Modifier.testTag("keyboard-controls-toggle"),
            role = Role.Button,
        )
    }
}

@Composable
private fun KeyboardIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
) {
    Box(
        modifier =
            modifier
                .size(TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(if (selected) Primary.copy(alpha = 0.18f) else SurfaceContainerLow)
                .border(1.dp, if (selected) Primary else OutlineVariant, RoundedCornerShape(RadiusSm))
                .selectable(selected = selected, onClick = onClick, role = role),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) Primary else OnSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Control strip ──────────────────────────────────────────────────────────

@Composable
private fun KeyboardControlStrip(
    state: KeyboardUiState,
    viewModel: KeyboardViewModel,
) {
    LazyRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TouchTargetMin)
                .testTag("keyboard-control-strip"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
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
            ToggleButton(
                "Vel",
                state.velocityFromTouch,
                modifier = Modifier.testTag("keyboard-velocity-toggle"),
            ) { viewModel.toggleVelocityFromTouch() }
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
                                onSemanticsClick = {
                                    currentOnNoteOn(midiNote, cellHeightPx / 2f, cellHeightPx)
                                    currentOnNoteOff(midiNote)
                                },
                                modifier = Modifier.weight(1f).testTag("keyboard-key-$midiNote"),
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
    onSemanticsClick: () -> Unit,
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
                ).semantics {
                    contentDescription = "Note $name$octave"
                    selected = isActive
                    role = Role.Button
                    stateDescription = if (isActive) "Playing" else if (isInScale) "In scale" else "Out of scale"
                    onClick(label = "Play note") {
                        onSemanticsClick()
                        true
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (name == "C") "$name$octave" else name,
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
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
                .size(TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = OnSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ToggleButton(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(TouchTargetMin)
                .widthIn(min = TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(if (active) Primary.copy(alpha = 0.18f) else SurfaceContainerLow)
                .border(
                    1.dp,
                    if (active) Primary else OutlineVariant,
                    RoundedCornerShape(RadiusSm),
                ).selectable(selected = active, onClick = onClick, role = Role.Checkbox)
                .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) Primary else OnSurface,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CycleButton(
    text: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(TouchTargetMin)
                .widthIn(min = TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onClick)
                .then(
                    if (contentDescription == null) {
                        Modifier
                    } else {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    },
                ).padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = OnSurface,
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
