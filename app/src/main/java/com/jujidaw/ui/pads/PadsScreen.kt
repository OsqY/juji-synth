package com.jujidaw.ui.pads

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jujidaw.ui.SynthKnob
import com.jujidaw.ui.SynthToggle
import com.jujidaw.ui.theme.*

/**
 * Sampler pad performance screen.
 *
 * - 4×4 velocity-sensitive pads with bank A/B toggle.
 * - Import, Chop, Time-stretch, and Edit controls.
 * - Pad edit bottom sheet for per-pad parameters.
 *
 * Phone-first: pads are large, square, and spaced for finger drumming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadsScreen(
    modifier: Modifier = Modifier,
    viewModel: PadsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Surface toast messages from ViewModel
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    val pickAudio =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            viewModel.importSample(context, uri)
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BgGunmetal)
                .padding(4.dp),
    ) {
        // Toolbar: bank toggle + selected pad label + action buttons
        PadsToolbar(
            currentBank = state.currentBank,
            selectedPad = state.selectedPad,
            onBankChange = viewModel::setBank,
            onImportClick = { pickAudio.launch("audio/*") },
            onChopClick = viewModel::chopSelectedPad,
            onTimeStretchClick = viewModel::showTimeStretchDialog,
            onEditClick = viewModel::showEditSheet,
            isTimeStretching = state.isTimeStretching,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 4×4 pad grid fills remaining space
        Box(modifier = Modifier.weight(1f)) {
            PadsGrid(
                activePads = state.activePads,
                selectedPad = state.selectedPad,
                padNames = state.padNames,
                currentBank = state.currentBank,
                onPadDown = viewModel::onPadDown,
                onPadUp = viewModel::releasePad,
                onPadSelect = viewModel::selectPad,
            )
        }
    }

    // Time-stretch BPM dialog
    if (state.showTimeStretchDialog) {
        TimeStretchDialog(
            bpm = state.timeStretchBpm,
            originalBpm = state.timeStretchOriginalBpm,
            onBpmChange = viewModel::setTimeStretchBpm,
            onOriginalBpmChange = viewModel::setTimeStretchOriginalBpm,
            onConfirm = viewModel::applyTimeStretch,
            onDismiss = viewModel::dismissTimeStretchDialog,
        )
    }

    // Pad parameter edit bottom sheet
    if (state.showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissEditSheet,
            containerColor = BgPanel,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            val globalPad = state.currentBank * 16 + state.selectedPad
            PadEditSheet(
                padIndex = state.selectedPad,
                globalPadIndex = globalPad,
                params = state.padParams.getOrElse(globalPad) { PadParams() },
                padName = state.padNames.getOrElse(globalPad) { "Pad" },
                onParamChange = viewModel::setPadParam,
                onSliceStartChange = viewModel::setSliceStart,
                onSliceEndChange = viewModel::setSliceEnd,
                onChokeGroupChange = viewModel::setChokeGroup,
                onLoadPreset = { viewModel.loadPadPreset(state.selectedPad, it) },
            )
        }
    }
}

@Composable
private fun PadsToolbar(
    currentBank: Int,
    selectedPad: Int,
    onBankChange: (Int) -> Unit,
    onImportClick: () -> Unit,
    onChopClick: () -> Unit,
    onTimeStretchClick: () -> Unit,
    onEditClick: () -> Unit,
    isTimeStretching: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BgPanel)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bank A / B toggle
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BankButton(label = "A", selected = currentBank == 0, onClick = { onBankChange(0) })
            BankButton(label = "B", selected = currentBank == 1, onClick = { onBankChange(1) })
        }

        Text(
            text = "Pad ${selectedPad + 1}",
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolbarButton("Import", onImportClick)
            ToolbarButton("Chop", onChopClick)
            ToolbarButton(
                label = if (isTimeStretching) "..." else "Stretch",
                onClick = onTimeStretchClick,
                enabled = !isTimeStretching,
            )
            ToolbarButton("Edit", onEditClick)
        }
    }
}

@Composable
private fun BankButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(width = 36.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) KnobAmber else BgGunmetal)
                .border(
                    1.dp,
                    if (selected) KnobAmber else PanelHighlight.copy(alpha = 0.4f),
                    RoundedCornerShape(6.dp),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (enabled) BgGunmetal else BgPanel)
                .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PadsGrid(
    activePads: Set<Int>,
    selectedPad: Int,
    padNames: List<String>,
    currentBank: Int,
    onPadDown: (Int, Float) -> Unit,
    onPadUp: (Int) -> Unit,
    onPadSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (row in 0 until 4) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (col in 0 until 4) {
                    val padIndex = row * 4 + col
                    val globalIndex = currentBank * 16 + padIndex
                    SamplerPad(
                        label = padNames.getOrElse(globalIndex) { "P${padIndex + 1}" },
                        isActive = padIndex in activePads,
                        isSelected = padIndex == selectedPad,
                        onDown = { normalizedY -> onPadDown(padIndex, normalizedY) },
                        onUp = { onPadUp(padIndex) },
                        onSelect = { onPadSelect(padIndex) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SamplerPad(
    label: String,
    isActive: Boolean,
    isSelected: Boolean,
    onDown: (Float) -> Unit,
    onUp: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor by animateColorAsState(
        targetValue =
            when {
                isActive -> KnobAmber
                isSelected -> PanelHighlight.copy(alpha = 0.6f)
                else -> BgPanel
            },
        label = "padBg",
    )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(activeColor)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) KnobAmber else PanelHighlight.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                ).pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val normalizedY = down.position.y / size.height
                        onSelect() // any touch selects the pad
                        onDown(normalizedY) // trigger with velocity
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.find { it.id == down.id } ?: break
                            if (!change.pressed) {
                                onUp() // release
                                change.consume()
                                break
                            }
                            change.consume()
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color =
                when {
                    isActive -> Color.Black
                    isSelected -> TextPrimary
                    else -> TextSecondary
                },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TimeStretchDialog(
    bpm: String,
    originalBpm: String,
    onBpmChange: (String) -> Unit,
    onOriginalBpmChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgPanel,
        title = { Text("Time Stretch", fontWeight = FontWeight.Bold, color = KnobCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = originalBpm,
                    onValueChange = onOriginalBpmChange,
                    label = { Text("Original BPM") },
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KnobCyan,
                            unfocusedBorderColor = PanelHighlight,
                            cursorColor = TextPrimary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        ),
                )
                OutlinedTextField(
                    value = bpm,
                    onValueChange = onBpmChange,
                    label = { Text("Target BPM") },
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KnobCyan,
                            unfocusedBorderColor = PanelHighlight,
                            cursorColor = TextPrimary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Apply", color = KnobCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
    )
}

@Composable
private fun PadEditSheet(
    padIndex: Int,
    globalPadIndex: Int,
    params: PadParams,
    padName: String,
    onParamChange: (Int, Int, Float) -> Unit,
    onSliceStartChange: (Int, Float) -> Unit,
    onSliceEndChange: (Int, Float) -> Unit,
    onChokeGroupChange: (Int, Int) -> Unit,
    onLoadPreset: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "$padName (Pad ${globalPadIndex + 1})",
            color = KnobCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        // Continuous parameters
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Row 1: Tune, Volume, Pan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SynthKnob(
                    value = (params.pitch + 24f) / 48f,
                    onValueChange = { onParamChange(padIndex, PadParamIds.PITCH, it * 48f - 24f) },
                    label = "Tune",
                    valueDisplay = "%.1f".format(params.pitch),
                    accentColor = KnobAmber,
                    size = 64.dp,
                )
                SynthKnob(
                    value = params.volume.coerceIn(0f, 1f),
                    onValueChange = { onParamChange(padIndex, PadParamIds.VOLUME, it) },
                    label = "Volume",
                    valueDisplay = "%.0f%%".format(params.volume * 100),
                    accentColor = KnobAmber,
                    size = 64.dp,
                )
                SynthKnob(
                    value = (params.pan + 1f) / 2f,
                    onValueChange = { onParamChange(padIndex, PadParamIds.PAN, it * 2f - 1f) },
                    label = "Pan",
                    valueDisplay = "%.0f".format(params.pan * 100),
                    accentColor = KnobAmber,
                    size = 64.dp,
                )
            }

            // Row 2: Attack, Release, Filter Cutoff
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SynthKnob(
                    value = params.attack.coerceIn(0f, 1f),
                    onValueChange = { onParamChange(padIndex, PadParamIds.ATTACK, it) },
                    label = "Attack",
                    valueDisplay = "%.2f".format(params.attack),
                    accentColor = KnobAmber,
                    size = 64.dp,
                )
                SynthKnob(
                    value = params.release.coerceIn(0f, 1f),
                    onValueChange = { onParamChange(padIndex, PadParamIds.RELEASE, it) },
                    label = "Release",
                    valueDisplay = "%.2f".format(params.release),
                    accentColor = KnobAmber,
                    size = 64.dp,
                )
                SynthKnob(
                    value = params.filterCutoff.coerceIn(0f, 1f),
                    onValueChange = { onParamChange(padIndex, PadParamIds.FILTER_CUTOFF, it) },
                    label = "Filter",
                    valueDisplay = "%.0f%%".format(params.filterCutoff * 100),
                    accentColor = KnobAmber,
                    size = 64.dp,
                )
            }

            // Row 3: Filter Resonance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SynthKnob(
                    value = params.filterResonance.coerceIn(0f, 1f),
                    onValueChange = { onParamChange(padIndex, PadParamIds.FILTER_RESONANCE, it) },
                    label = "Resonance",
                    valueDisplay = "%.0f%%".format(params.filterResonance * 100),
                    accentColor = KnobAmber,
                    size = 64.dp,
                )
            }
        }

        // Boolean toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (!params.synthMode) {
                SynthToggle(
                    checked = params.reverse,
                    onCheckedChange = { onParamChange(padIndex, PadParamIds.REVERSE, if (it) 1f else 0f) },
                    label = "Reverse",
                    enabledColor = KnobAmber,
                )
                SynthToggle(
                    checked = params.oneShot,
                    onCheckedChange = { onParamChange(padIndex, PadParamIds.ONE_SHOT, if (it) 1f else 0f) },
                    label = "One-Shot",
                    enabledColor = KnobAmber,
                )
                SynthToggle(
                    checked = params.useFilter,
                    onCheckedChange = { onParamChange(padIndex, PadParamIds.USE_FILTER, if (it) 1f else 0f) },
                    label = "Filter",
                    enabledColor = KnobAmber,
                )
            }
            SynthToggle(
                checked = params.synthMode,
                onCheckedChange = { onParamChange(padIndex, PadParamIds.SYNTH_MODE, if (it) 1f else 0f) },
                label = "Synth",
                enabledColor = KnobAmber,
            )
        }

        // Root note selector (only relevant for synth-pad mode)
        if (params.synthMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Root Note", color = TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(BgGunmetal)
                                .clickable {
                                    val newNote = (params.synthRootNote - 1).coerceAtLeast(0)
                                    onParamChange(padIndex, PadParamIds.SYNTH_ROOT_NOTE, newNote.toFloat())
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("-", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = midiNoteName(params.synthRootNote),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(48.dp),
                        maxLines = 1,
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(BgGunmetal)
                                .clickable {
                                    val newNote = (params.synthRootNote + 1).coerceAtMost(127)
                                    onParamChange(padIndex, PadParamIds.SYNTH_ROOT_NOTE, newNote.toFloat())
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Preset picker (only when synth mode is active)
            Text("Preset", color = TextSecondary, fontSize = 12.sp)
            val presetNames = PadsViewModel.FACTORY_PRESETS
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(presetNames) { presetName ->
                    FilterChip(
                        selected = false,
                        onClick = { onLoadPreset(presetName) },
                        label = { Text(presetName, fontSize = 10.sp) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KnobCyan.copy(alpha = 0.2f),
                                selectedLabelColor = KnobCyan,
                            ),
                        modifier = Modifier.height(28.dp),
                    )
                }
            }
        } else {
            // Slice controls (TODO: engine support)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Slice (TODO: engine)", color = TextMuted, fontSize = 10.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Start", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.width(32.dp))
                    Slider(
                        value = params.sliceStart,
                        onValueChange = { onSliceStartChange(padIndex, it) },
                        modifier = Modifier.weight(1f),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = KnobAmber,
                                activeTrackColor = KnobAmber,
                                inactiveTrackColor = PanelHighlight.copy(alpha = 0.3f),
                            ),
                    )
                    Text(
                        "%.0f%%".format(params.sliceStart * 100),
                        color = TextPrimary,
                        fontSize = 10.sp,
                        modifier = Modifier.width(36.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("End  ", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.width(32.dp))
                    Slider(
                        value = params.sliceEnd.coerceAtLeast(params.sliceStart),
                        onValueChange = { onSliceEndChange(padIndex, it.coerceAtLeast(params.sliceStart)) },
                        modifier = Modifier.weight(1f),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = KnobAmber,
                                activeTrackColor = KnobAmber,
                                inactiveTrackColor = PanelHighlight.copy(alpha = 0.3f),
                            ),
                    )
                    Text(
                        "%.0f%%".format(params.sliceEnd * 100),
                        color = TextPrimary,
                        fontSize = 10.sp,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
        }

        // Choke group (TODO: engine support)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Choke Group (TODO)", color = TextMuted, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BgGunmetal)
                            .clickable {
                                onChokeGroupChange(padIndex, (params.chokeGroup - 1).coerceAtLeast(0))
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("-", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${params.chokeGroup}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp),
                    maxLines = 1,
                )
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BgGunmetal)
                            .clickable {
                                onChokeGroupChange(padIndex, params.chokeGroup + 1)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private fun midiNoteName(note: Int): String {
    val clamped = note.coerceIn(0, 127)
    val octave = (clamped / 12) - 1
    val name = noteNames[clamped % 12]
    return "$name$octave"
}
