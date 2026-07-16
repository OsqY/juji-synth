@file:OptIn(ExperimentalMaterial3Api::class)

package com.jujidaw.ui.synth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Reuses the existing panel composables from [MainSynthScreen] arranged in a
 * phone-first vertically-scrolling column.
 *
 * ### Usage
 * ```kotlin
 * SynthScreen(viewModel = viewModel(), modifier = Modifier.fillMaxSize())
 * ```
 *
 * ### TODO for integrator
 * - Wire into the bottom-navigation graph (Group 14).
 * - Per-track synth state isolation is stubbed; currently all tracks share one engine.
 * - Landscape / tablet layout can restore the 3-column hardware chassis from [MainSynthScreen].
 */
@Composable
fun SynthScreen(
    viewModel: SynthViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
        modifier = modifier
            .fillMaxSize()
            .background(BgGunmetal)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── TRACK SELECTOR ──
        TrackSelectorRow(
            selectedTrack = uiState.selectedTrack,
            onSelectTrack = viewModel::selectTrack
        )

        PadSynthSelectorRow(
            selectedPadIndex = uiState.selectedPadIndex,
            onSelectGlobal = { viewModel.selectTrack(uiState.selectedTrack) },
            onSelectPad = viewModel::selectPad,
        )

        // ── SYNTH PANELS ──
        HardwareChassis(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SynthPanel(title = "OSC", accentColor = KnobAmber) {
                    OscillatorPanel(
                        state = uiState.synthState,
                        onParamChange = viewModel::updateSynthState,
                        learnMode = learnMode,
                        selectedParamId = selectedParamId,
                        onLearnSelect = viewModel::selectParamForLearn
                    )
                }

                SynthPanel(title = "FILTER", accentColor = KnobCyan) {
                    FilterPanel(
                        state = uiState.synthState,
                        onParamChange = viewModel::updateSynthState,
                        learnMode = learnMode,
                        selectedParamId = selectedParamId,
                        onLearnSelect = viewModel::selectParamForLearn
                    )
                }

                SynthPanel(title = "ENV", accentColor = KnobGreen) {
                    EnvelopePanel(
                        state = uiState.synthState,
                        onParamChange = viewModel::updateSynthState,
                        learnMode = learnMode,
                        selectedParamId = selectedParamId,
                        onLearnSelect = viewModel::selectParamForLearn
                    )
                }

                SynthPanel(title = "LFO", accentColor = KnobPink) {
                    LfoPanel(
                        state = uiState.synthState,
                        onParamChange = viewModel::updateSynthState,
                        learnMode = learnMode,
                        selectedParamId = selectedParamId,
                        onLearnSelect = viewModel::selectParamForLearn
                    )
                }

                SynthPanel(title = "FX", accentColor = KnobRed) {
                    EffectsPanel(
                        state = uiState.synthState,
                        onParamChange = viewModel::updateSynthState,
                        learnMode = learnMode,
                        selectedParamId = selectedParamId,
                        onLearnSelect = viewModel::selectParamForLearn
                    )
                }

                SynthPanel(title = "MOD", accentColor = KnobPink) {
                    PatchBayView(
                        routes = uiState.synthState.modulationRoutes,
                        onRouteChange = { idx, route -> viewModel.updateModulationRoute(idx, route) },
                        learnMode = learnMode,
                        selectedParamId = selectedParamId,
                        onLearnSelect = viewModel::selectParamForLearn
                    )
                }
            }
        }

        // ── ACTION BAR ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // MIDI Learn button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            uiState.midiLearnState.mode == MidiLearnMode.CONTROL_SELECTED -> KnobGreen.copy(alpha = 0.5f)
                            uiState.midiLearnState.mode == MidiLearnMode.LEARN_ACTIVE -> KnobAmber.copy(alpha = 0.5f)
                            else -> BgPanel
                        }
                    )
                    .border(1.dp, PanelHighlight.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable { viewModel.toggleMidiLearn() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "MIDI",
                    color = KnobCyan,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // PANIC button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(KnobRed.copy(alpha = 0.5f))
                    .border(1.dp, KnobRed, RoundedCornerShape(6.dp))
                    .clickable {
                        com.jujidaw.audio.SynthEngine.panic()
                        Toast.makeText(context, "Panic! All sound stopped", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "PANIC",
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            listOf(
                "PRESET" to { viewModel.showPresetBrowser(true) },
                "SAVE" to { viewModel.showSaveDialog(true) }
            ).forEach { (label, onClick) ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgPanel)
                        .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = TextSecondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // ── PRESET BROWSER BOTTOM SHEET ──
    if (uiState.showPresetBrowser) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.showPresetBrowser(false) },
            containerColor = BgPanel,
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
            containerColor = BgPanel,
            title = {
                Text(
                    "Save Preset",
                    fontWeight = FontWeight.Bold,
                    color = KnobCyan
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
                            focusedBorderColor = KnobCyan,
                            unfocusedBorderColor = PanelHighlight,
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
                    Text("Save", color = KnobCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showSaveDialog(false) }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

/**
 * Horizontally-scrollable row of track buttons (1..16).
 * Selected track is highlighted with the amber accent.
 */
@Composable
private fun TrackSelectorRow(
    selectedTrack: Int,
    onSelectTrack: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "TRACK",
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp)
        )
        repeat(16) { track ->
            val isSelected = track == selectedTrack
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) KnobAmber.copy(alpha = 0.3f) else BgPanel
                    )
                    .border(
                        1.dp,
                        if (isSelected) KnobAmber else PanelHighlight.copy(alpha = 0.4f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelectTrack(track) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${track + 1}",
                    color = if (isSelected) KnobAmber else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Select the sound source being edited. The global track synth remains
 * available, while Pads A and B each expose their 16 independent synths.
 */
@Composable
private fun PadSynthSelectorRow(
    selectedPadIndex: Int,
    onSelectGlobal: () -> Unit,
    onSelectPad: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "SOURCE",
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp),
        )
        SourceSelectorButton(
            label = "GLOBAL",
            selected = selectedPadIndex < 0,
            onClick = onSelectGlobal,
        )
        repeat(32) { padIndex ->
            if (padIndex == 0) {
                Text(
                    "A",
                    color = KnobCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (padIndex == 16) {
                Text(
                    "B",
                    color = KnobCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            SourceSelectorButton(
                label = "P${padIndex % 16 + 1}",
                selected = selectedPadIndex == padIndex,
                onClick = { onSelectPad(padIndex) },
            )
        }
    }
}

@Composable
private fun SourceSelectorButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) KnobAmber.copy(alpha = 0.35f) else BgPanel)
                .border(1.dp, if (selected) KnobAmber else PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) KnobAmber else TextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
