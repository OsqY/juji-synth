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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jujidaw.ui.SynthKnob
import com.jujidaw.ui.SynthToggle
import com.jujidaw.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Sampler pad performance screen.
 *
 * - 4x4 velocity-sensitive pads with bank A/B toggle.
 * - Import, Chop, Time-stretch, and Edit controls.
 * - Pad edit bottom sheet for per-pad parameters.
 *
 * Phone-first: pads are large, square, and spaced for finger drumming.
 *
 * Visuals: see `docs/ui-design-overhaul-plan.md` Phase 4.3 / component #6 (pad grid).
 * - 4x4 pads on `Bg1`; pad fill `SurfaceContainerLow` + bank-hue 25% tint.
 * - Stranger pad = `PadBankB` (Indigo).
 * - Struck pad = bank hue at value-alpha + `PadActiveRing` (`Primary`) 2dp ring (120ms).
 * - Selected pad = `Primary` 1dp outline.
 * - Toolbar icons: file_upload / content_cut / timer / tune (Material Outlined).
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
                .background(Bg0)
                .padding(Spacing.sm),
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

        Spacer(modifier = Modifier.height(Spacing.sm))

        // 4x4 pad grid fills remaining space
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(RadiusLg))
                    .background(Bg1),
        ) {
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
            containerColor = SurfaceContainerHigh,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg),
        ) {
            val globalPad = state.currentBank * 16 + state.selectedPad
            PadEditSheet(
                padIndex = state.selectedPad,
                globalPadIndex = globalPad,
                params = state.padParams.getOrElse(globalPad) { PadParams() },
                padName = state.padNames.getOrElse(globalPad) { "Pad" },
                selectedPresetName = com.jujidaw.project.PadSessionStore.snapshot().getOrNull(globalPad)?.synthPresetName.orEmpty(),
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
                .height(44.dp)
                .clip(RoundedCornerShape(RadiusLg))
                .background(SurfaceContainer)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusLg))
                .padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bank A / B toggle (text "A"/"B" badges)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            BankButton(label = "A", selected = currentBank == 0, onClick = { onBankChange(0) })
            BankButton(label = "B", selected = currentBank == 1, onClick = { onBankChange(1) })
        }

        Text(
            text = "Pad ${selectedPad + 1}",
            color = OnSurface,
            style = LabelSmall,
        )

        // Action buttons: Import / Chop / Stretch / Edit (Material Outlined icons)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ToolbarButton(
                icon = Icons.Outlined.FileUpload,
                contentDescription = "Import",
                onClick = onImportClick,
            )
            ToolbarButton(
                icon = Icons.Outlined.ContentCut,
                contentDescription = "Chop",
                onClick = onChopClick,
            )
            ToolbarButton(
                icon = Icons.Outlined.Timer,
                contentDescription = "Time Stretch",
                onClick = onTimeStretchClick,
                enabled = !isTimeStretching,
            )
            ToolbarButton(
                icon = Icons.Outlined.Tune,
                contentDescription = "Edit",
                onClick = onEditClick,
            )
        }
    }
}

@Composable
private fun BankButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) Primary.copy(alpha = 0.15f) else SurfaceContainerLow
    val bd = if (selected) Primary else OutlineVariant
    val fg = if (selected) Primary else OnSurface

    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(RadiusSm))
                .background(bg)
                .border(1.dp, bd, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            style = LabelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val bgColor =
        if (enabled) SurfaceContainerLow else DisabledFill

    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(RadiusSm))
                .background(bgColor)
                .border(
                    1.dp,
                    if (enabled) OutlineVariant else OutlineVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(RadiusSm),
                ).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) OnSurface else DisabledText,
            modifier = Modifier.size(20.dp),
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
    val bankHue = if (currentBank == 0) PadBankA else PadBankB

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        for (row in 0 until 4) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                for (col in 0 until 4) {
                    val padIndex = row * 4 + col
                    val globalIndex = currentBank * 16 + padIndex
                    SamplerPad(
                        label = padNames.getOrElse(globalIndex) { "P${padIndex + 1}" },
                        isActive = padIndex in activePads,
                        isSelected = padIndex == selectedPad,
                        bankHue = bankHue,
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
    bankHue: Color,
    onDown: (Float) -> Unit,
    onUp: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Last strike velocity (visual-only local state; does not affect audio logic).
    var lastVelocity by remember { mutableStateOf(1f) }

    // Struck-pad transient ring: shown for 120ms on each strike.
    var struckRing by remember { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (isActive) {
            struckRing = true
            delay(120)
            struckRing = false
        } else {
            struckRing = false
        }
    }

    val shape = RoundedCornerShape(RadiusSm)

    // Bank hue at value-alpha while struck; 25% resting tint otherwise.
    val tintAlpha = if (isActive) 0.45f + lastVelocity.coerceIn(0f, 1f) * 0.35f else 0.25f
    val tint by animateColorAsState(
        targetValue = bankHue.copy(alpha = tintAlpha),
        label = "padTint",
    )

    // Border: struck ring (2dp Primary) > selected outline (1dp Primary) > rest (1dp OutlineVariant).
    val (borderW, borderC) =
        when {
            struckRing -> 2.dp to PadActiveRing
            isSelected -> 1.dp to Primary
            else -> 1.dp to OutlineVariant
        }

    val textColor =
        when {
            isActive -> OnSurface
            isSelected -> OnSurface
            else -> OnSurfaceVariant
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clip(shape)
                .background(SurfaceContainerLow, shape)
                .background(tint, shape)
                .border(borderW, borderC, shape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val normalizedY = down.position.y / size.height
                        lastVelocity = normalizedY
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
            color = textColor,
            style = CaptionSmall,
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
        containerColor = SurfaceContainerHigh,
        shape = RoundedCornerShape(RadiusLg),
        title = {
            Text(
                "Time Stretch",
                color = Secondary,
                style = TitleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = originalBpm,
                    onValueChange = onOriginalBpmChange,
                    label = { Text("Original BPM") },
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Secondary,
                            unfocusedBorderColor = Outline,
                            cursorColor = OnSurface,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface,
                            focusedLabelColor = Secondary,
                            unfocusedLabelColor = OnSurfaceVariant,
                        ),
                )
                OutlinedTextField(
                    value = bpm,
                    onValueChange = onBpmChange,
                    label = { Text("Target BPM") },
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Secondary,
                            unfocusedBorderColor = Outline,
                            cursorColor = OnSurface,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface,
                            focusedLabelColor = Secondary,
                            unfocusedLabelColor = OnSurfaceVariant,
                        ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Apply", color = Primary, style = LabelSmall)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, style = LabelSmall)
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
    selectedPresetName: String,
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
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = "$padName (Pad ${globalPadIndex + 1})",
            color = OnSurface,
            style = TitleLarge,
        )

        // Continuous parameters
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                    accentColor = Primary,
                    size = 64.dp,
                )
                SynthKnob(
                    value = params.volume.coerceIn(0f, 1f),
                    onValueChange = { onParamChange(padIndex, PadParamIds.VOLUME, it) },
                    label = "Volume",
                    valueDisplay = "%.0f%%".format(params.volume * 100),
                    accentColor = Primary,
                    size = 64.dp,
                )
                SynthKnob(
                    value = (params.pan + 1f) / 2f,
                    onValueChange = { onParamChange(padIndex, PadParamIds.PAN, it * 2f - 1f) },
                    label = "Pan",
                    valueDisplay = "%.0f".format(params.pan * 100),
                    accentColor = Primary,
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
                    accentColor = Primary,
                    size = 64.dp,
                )
                SynthKnob(
                    value = params.release.coerceIn(0f, 1f),
                    onValueChange = { onParamChange(padIndex, PadParamIds.RELEASE, it) },
                    label = "Release",
                    valueDisplay = "%.2f".format(params.release),
                    accentColor = Primary,
                    size = 64.dp,
                )
                SynthKnob(
                    value = params.filterCutoff.coerceIn(0f, 1f),
                    onValueChange = { onParamChange(padIndex, PadParamIds.FILTER_CUTOFF, it) },
                    label = "Filter",
                    valueDisplay = "%.0f%%".format(params.filterCutoff * 100),
                    accentColor = Secondary,
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
                    accentColor = Secondary,
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
                    enabledColor = Primary,
                )
                SynthToggle(
                    checked = params.oneShot,
                    onCheckedChange = { onParamChange(padIndex, PadParamIds.ONE_SHOT, if (it) 1f else 0f) },
                    label = "One-Shot",
                    enabledColor = Primary,
                )
                SynthToggle(
                    checked = params.useFilter,
                    onCheckedChange = { onParamChange(padIndex, PadParamIds.USE_FILTER, if (it) 1f else 0f) },
                    label = "Filter",
                    enabledColor = Primary,
                )
            }
            SynthToggle(
                checked = params.synthMode,
                onCheckedChange = { onParamChange(padIndex, PadParamIds.SYNTH_MODE, if (it) 1f else 0f) },
                label = "Synth",
                enabledColor = Primary,
            )
        }

        // Root note selector (only relevant for synth-pad mode)
        if (params.synthMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Root Note", color = TextSecondary, style = LabelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    StepperBox(label = "-") {
                        val newNote = (params.synthRootNote - 1).coerceAtLeast(0)
                        onParamChange(padIndex, PadParamIds.SYNTH_ROOT_NOTE, newNote.toFloat())
                    }
                    Text(
                        text = midiNoteName(params.synthRootNote),
                        color = OnSurface,
                        style = MonoMedium,
                        modifier = Modifier.width(48.dp),
                        maxLines = 1,
                    )
                    StepperBox(label = "+") {
                        val newNote = (params.synthRootNote + 1).coerceAtMost(127)
                        onParamChange(padIndex, PadParamIds.SYNTH_ROOT_NOTE, newNote.toFloat())
                    }
                }
            }

            // Preset picker (only when synth mode is active)
            Text("Preset", color = TextSecondary, style = LabelSmall)
            val presetNames = PadsViewModel.FACTORY_PRESETS
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(presetNames) { presetName ->
                    FilterChip(
                        selected = presetName == selectedPresetName,
                        onClick = { onLoadPreset(presetName) },
                        label = { Text(presetName, style = CaptionSmall) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceContainerLow,
                                labelColor = OnSurface,
                                selectedContainerColor = Primary.copy(alpha = 0.12f),
                                selectedLabelColor = Primary,
                            ),
                        border =
                            FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = presetName == selectedPresetName,
                                borderColor = OutlineVariant,
                            ),
                        modifier = Modifier.height(32.dp),
                    )
                }
            }
        } else {
            // Slice controls (TODO: engine support)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Slice (TODO: engine)", color = TextDisabled, style = CaptionSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Start", color = TextSecondary, style = CaptionSmall, modifier = Modifier.width(32.dp))
                    Slider(
                        value = params.sliceStart,
                        onValueChange = { onSliceStartChange(padIndex, it) },
                        modifier = Modifier.weight(1f),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = Primary,
                                activeTrackColor = Primary,
                                inactiveTrackColor = OutlineVariant,
                            ),
                    )
                    Text(
                        "%.0f%%".format(params.sliceStart * 100),
                        color = OnSurface,
                        style = MonoMedium,
                        modifier = Modifier.width(36.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("End  ", color = TextSecondary, style = CaptionSmall, modifier = Modifier.width(32.dp))
                    Slider(
                        value = params.sliceEnd.coerceAtLeast(params.sliceStart),
                        onValueChange = { onSliceEndChange(padIndex, it.coerceAtLeast(params.sliceStart)) },
                        modifier = Modifier.weight(1f),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = Primary,
                                activeTrackColor = Primary,
                                inactiveTrackColor = OutlineVariant,
                            ),
                    )
                    Text(
                        "%.0f%%".format(params.sliceEnd * 100),
                        color = OnSurface,
                        style = MonoMedium,
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
            Text("Choke Group (TODO)", color = TextDisabled, style = CaptionSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                StepperBox(label = "-") {
                    onChokeGroupChange(padIndex, (params.chokeGroup - 1).coerceAtLeast(0))
                }
                Text(
                    text = "${params.chokeGroup}",
                    color = OnSurface,
                    style = MonoMedium,
                    modifier = Modifier.width(32.dp),
                    maxLines = 1,
                )
                StepperBox(label = "+") {
                    onChokeGroupChange(padIndex, params.chokeGroup + 1)
                }
            }
        }
    }
}

/** Small square stepper button used for +/- value nudges (root note, choke group). */
@Composable
private fun StepperBox(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = OnSurface,
            style = BodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private fun midiNoteName(note: Int): String {
    val clamped = note.coerceIn(0, 127)
    val octave = (clamped / 12) - 1
    val name = noteNames[clamped % 12]
    return "$name$octave"
}
