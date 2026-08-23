@file:OptIn(ExperimentalMaterial3Api::class)

package com.jujidaw.ui.synth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jujidaw.JujiDawApp
import com.jujidaw.data.MidiMappingStore
import com.jujidaw.midi.MidiController
import com.jujidaw.model.MidiLearnMode
import com.jujidaw.ui.*
import com.jujidaw.ui.theme.*

/**
 * Synth parameter screen with track selection, oscillator, filter, envelope,
 * LFO, effects, and modulation panels.
 *
 * Reuses the existing parameter panels in a flat, phone-first scrolling device
 * chain. Track and pad targeting stays available in a collapsed chooser.
 *
 * ### Usage
 * ```kotlin
 * SynthScreen(viewModel = viewModel(), modifier = Modifier.fillMaxSize())
 * ```
 *
 */
@Composable
fun SynthScreen(
    viewModel: SynthViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTargetPicker by rememberSaveable { mutableStateOf(false) }

    // Toast messages from ViewModel
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // Inject MIDI mapping store (needs Context)
    LaunchedEffect(Unit) {
        viewModel.setMidiMappingStore(MidiMappingStore(context))
    }

    // MIDI device scanning
    val app = context.applicationContext as JujiDawApp
    val midiRouter = remember { app.midiRouter }
    val midiController = remember { MidiController(context, midiRouter) }
    DisposableEffect(Unit) {
        midiController.startScanning()
        onDispose { midiController.stopScanning() }
    }

    // MIDI learn capture callback
    val currentLearnState by rememberUpdatedState(uiState.midiLearnState)
    DisposableEffect(Unit) {
        MidiController.onCcLearnCallback = { ccNumber, value ->
            val learnState = currentLearnState
            if (learnState.mode == MidiLearnMode.CONTROL_SELECTED && learnState.selectedParamId != null) {
                viewModel.confirmMidiLearn(ccNumber, value)
            }
        }
        onDispose {
            MidiController.onCcLearnCallback = null
        }
    }

    val presetDao = app.database.presetDao()

    val learnMode = uiState.midiLearnState.mode != MidiLearnMode.IDLE
    val selectedParamId = uiState.midiLearnState.selectedParamId

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Bg1)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.sm)
                .testTag("synth-root"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SynthToolbar(
            selectedTrack = uiState.selectedTrack,
            selectedPadIndex = uiState.selectedPadIndex,
            learnMode = learnMode,
            targetsVisible = showTargetPicker,
            onToggleTargets = { showTargetPicker = !showTargetPicker },
            onToggleMidiLearn = viewModel::toggleMidiLearn,
            onShowPresets = { viewModel.showPresetBrowser(true) },
            onShowSave = { viewModel.showSaveDialog(true) },
            onPanic = {
                com.jujidaw.audio.SynthEngine.panic()
                Toast.makeText(context, "Panic! All sound stopped", Toast.LENGTH_SHORT).show()
            },
        )

        if (showTargetPicker) {
            SynthTargetPicker(
                selectedTrack = uiState.selectedTrack,
                selectedPadIndex = uiState.selectedPadIndex,
                onSelectTrack = viewModel::selectTrack,
                onSelectGlobal = { viewModel.selectTrack(uiState.selectedTrack) },
                onSelectPad = viewModel::selectPad,
            )
        }

        OscillatorPanel(
            state = uiState.synthState,
            onParamChange = viewModel::updateSynthState,
            modifier = Modifier.fillMaxWidth().testTag("synth-panel-oscillators"),
            learnMode = learnMode,
            selectedParamId = selectedParamId,
            onLearnSelect = viewModel::selectParamForLearn,
        )
        FilterPanel(
            state = uiState.synthState,
            onParamChange = viewModel::updateSynthState,
            modifier = Modifier.fillMaxWidth().testTag("synth-panel-filter"),
            learnMode = learnMode,
            selectedParamId = selectedParamId,
            onLearnSelect = viewModel::selectParamForLearn,
        )
        EnvelopePanel(
            state = uiState.synthState,
            onParamChange = viewModel::updateSynthState,
            modifier = Modifier.fillMaxWidth().testTag("synth-panel-envelopes"),
            learnMode = learnMode,
            selectedParamId = selectedParamId,
            onLearnSelect = viewModel::selectParamForLearn,
        )
        LfoPanel(
            state = uiState.synthState,
            onParamChange = viewModel::updateSynthState,
            modifier = Modifier.fillMaxWidth().testTag("synth-panel-lfo"),
            learnMode = learnMode,
            selectedParamId = selectedParamId,
            onLearnSelect = viewModel::selectParamForLearn,
        )
        EffectsPanel(
            state = uiState.synthState,
            onParamChange = viewModel::updateSynthState,
            modifier = Modifier.fillMaxWidth().testTag("synth-panel-effects"),
            learnMode = learnMode,
            selectedParamId = selectedParamId,
            onLearnSelect = viewModel::selectParamForLearn,
        )
        SynthPanel(
            title = "MODULATION",
            accentColor = MidiLearn,
            modifier = Modifier.fillMaxWidth().testTag("synth-panel-modulation"),
        ) {
            PatchBayView(
                routes = uiState.synthState.modulationRoutes,
                onRouteChange = { idx, route -> viewModel.updateModulationRoute(idx, route) },
                learnMode = learnMode,
                selectedParamId = selectedParamId,
                onLearnSelect = viewModel::selectParamForLearn,
            )
        }
    }

    // ── PRESET BROWSER BOTTOM SHEET ──
    if (uiState.showPresetBrowser) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showPresetBrowser(false) },
            containerColor = SurfaceContainerHigh,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            PresetBrowser(
                onDismiss = { viewModel.showPresetBrowser(false) },
                onSelectPreset = { name ->
                    viewModel.loadPreset(name)
                    viewModel.showPresetBrowser(false)
                },
                presetDao = presetDao,
                selectedCategory = uiState.presetCategory,
                onCategoryChange = { viewModel.setPresetCategory(it) }
            )
        }
    }

    // ── SAVE PRESET DIALOG ──
    if (uiState.showSaveDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showSaveDialog(false) },
            containerColor = SurfaceContainerHigh,
            title = {
                Text(
                    "Save Preset",
                    fontWeight = FontWeight.Bold,
                    color = Secondary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.savePresetName,
                        onValueChange = viewModel::setSavePresetName,
                        label = { Text("Preset Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Secondary,
                            unfocusedBorderColor = OutlineVariant,
                            cursorColor = TextPrimary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.savePreset(uiState.savePresetName) },
                    enabled = uiState.savePresetName.isNotBlank()
                ) {
                    Text("Save", color = Secondary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showSaveDialog(false) }) {
                    Text("Cancel", color = TextDisabled)
                }
            }
        )
    }
}

@Composable
private fun SynthToolbar(
    selectedTrack: Int,
    selectedPadIndex: Int,
    learnMode: Boolean,
    targetsVisible: Boolean,
    onToggleTargets: () -> Unit,
    onToggleMidiLearn: () -> Unit,
    onShowPresets: () -> Unit,
    onShowSave: () -> Unit,
    onPanic: () -> Unit,
) {
    val source =
        if (selectedPadIndex < 0) {
            "GLOBAL"
        } else {
            "${if (selectedPadIndex < 16) "A" else "B"}${selectedPadIndex % 16 + 1}"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SurfaceContainer)
                .padding(horizontal = Spacing.xs)
                .testTag("synth-toolbar"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(TouchTargetMin)
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(if (targetsVisible) Primary.copy(alpha = 0.18f) else SurfaceContainerLow)
                    .border(1.dp, if (targetsVisible) Primary else OutlineVariant, RoundedCornerShape(RadiusSm))
                    .clickable(onClick = onToggleTargets)
                    .semantics {
                        contentDescription = "Change synth target"
                        stateDescription = if (targetsVisible) "Expanded" else "Collapsed"
                    }.testTag("synth-target-toggle"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "T${(selectedTrack + 1).toString().padStart(2, '0')} · $source",
                color = if (targetsVisible) Primary else OnSurface,
                style = LabelSmall,
                maxLines = 1,
            )
        }
        SynthToolbarAction("MIDI", "MIDI learn", onToggleMidiLearn, "synth-midi", active = learnMode)
        SynthToolbarAction("LIB", "Open preset library", onShowPresets, "synth-presets")
        SynthToolbarAction("SAVE", "Save preset", onShowSave, "synth-save")
        SynthToolbarAction("!", "Panic: stop all sound", onPanic, "synth-panic", danger = true)
    }
}

@Composable
private fun SynthToolbarAction(
    label: String,
    description: String,
    onClick: () -> Unit,
    tag: String,
    active: Boolean = false,
    danger: Boolean = false,
) {
    val accent = if (danger) StateRecording else Primary
    Box(
        modifier =
            Modifier
                .height(TouchTargetMin)
                .widthIn(min = TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(if (active || danger) accent.copy(alpha = 0.18f) else SurfaceContainerLow)
                .border(1.dp, if (active || danger) accent else OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = description
                    if (description == "MIDI learn") stateDescription = if (active) "On" else "Off"
                }.testTag(tag)
                .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active || danger) accent else OnSurface, style = LabelSmall)
    }
}

@Composable
private fun SynthTargetPicker(
    selectedTrack: Int,
    selectedPadIndex: Int,
    onSelectTrack: (Int) -> Unit,
    onSelectGlobal: () -> Unit,
    onSelectPad: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .padding(Spacing.sm)
                .testTag("synth-target-picker"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TrackSelectorRow(selectedTrack = selectedTrack, onSelectTrack = onSelectTrack)
        PadSynthSelectorRow(
            selectedPadIndex = selectedPadIndex,
            onSelectGlobal = onSelectGlobal,
            onSelectPad = onSelectPad,
        )
    }
}

@Composable
private fun TrackSelectorRow(
    selectedTrack: Int,
    onSelectTrack: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("TRACK", color = TextSecondary, style = CaptionSmall, modifier = Modifier.width(52.dp))
        repeat(16) { track ->
            SelectorButton(
                label = "${track + 1}",
                selected = track == selectedTrack,
                onClick = { onSelectTrack(track) },
                modifier = Modifier.testTag("synth-track-$track"),
            )
        }
    }
}

@Composable
private fun PadSynthSelectorRow(
    selectedPadIndex: Int,
    onSelectGlobal: () -> Unit,
    onSelectPad: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SOURCE", color = TextSecondary, style = CaptionSmall, modifier = Modifier.width(52.dp))
        SelectorButton(
            label = "G",
            selected = selectedPadIndex < 0,
            onClick = onSelectGlobal,
            modifier = Modifier.testTag("synth-source-global"),
        )
        repeat(32) { padIndex ->
            SelectorButton(
                label = "${if (padIndex < 16) "A" else "B"}${padIndex % 16 + 1}",
                selected = selectedPadIndex == padIndex,
                onClick = { onSelectPad(padIndex) },
                modifier = Modifier.testTag("synth-source-pad-$padIndex"),
            )
        }
    }
}

@Composable
private fun SelectorButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(TouchTargetMin)
                .widthIn(min = TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(if (selected) Primary.copy(alpha = 0.18f) else SurfaceContainer)
                .border(1.dp, if (selected) Primary else OutlineVariant, RoundedCornerShape(RadiusSm))
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Primary else OnSurface, style = LabelSmall)
    }
}
