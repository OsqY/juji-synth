package com.jujisynth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.jujisynth.JujiSynthApp
import com.jujisynth.audio.SynthEngine
import com.jujisynth.data.PresetEntity
import com.jujisynth.data.SettingsDataStore
import com.jujisynth.model.ModulationRoute
import com.jujisynth.model.SynthState
import com.jujisynth.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val presetJson = Json { encodeDefaults = true }

@Composable
fun MainSynthScreen(modifier: Modifier = Modifier) {
    // Single source of truth
    var synthState by remember { mutableStateOf(SynthState()) }

    // Separate UI-only state
    var selectedTab by remember { mutableStateOf(SynthTab.OSC) }
    var activeNotes by remember { mutableStateOf(setOf<Int>()) }

    var octaveOffset by remember { mutableStateOf(3) }
    var showPresets by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var savePresetName by remember { mutableStateOf("") }
    var savePresetCategory by remember { mutableStateOf("Leads") }
    var categoryExpanded by remember { mutableStateOf(false) }
    val saveScope = rememberCoroutineScope()

    // Database
    val context = LocalContext.current
    val app = context.applicationContext as JujiSynthApp
    val presetDao = app.database.presetDao()
    val settingsDataStore = remember { SettingsDataStore(context) }

    // Preset collection for loading
    var dbPresets by remember { mutableStateOf<List<PresetEntity>?>(null) }
    LaunchedEffect(presetDao) {
        presetDao.getAllPresets().collect { list -> dbPresets = list }
    }

    Column(modifier = modifier.fillMaxSize().background(BgPrimary).statusBarsPadding()) {
        // Tab Bar
        TabBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        // Content area wrapped in HardwareChassis
        HardwareChassis(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            // Single panel shown based on selected tab
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                SynthTab.OSC -> OscillatorPanel(
                    state = synthState,
                    onParamChange = { newState -> synthState = newState }
                )
                SynthTab.FILTER -> FilterPanel(
                    state = synthState,
                    onParamChange = { newState -> synthState = newState }
                )
                SynthTab.ENV -> EnvelopePanel(
                    state = synthState,
                    onParamChange = { newState -> synthState = newState }
                )
                SynthTab.LFO -> LfoPanel(
                    state = synthState,
                    onParamChange = { newState -> synthState = newState }
                )
                SynthTab.FX -> EffectsPanel(
                    state = synthState,
                    onParamChange = { newState -> synthState = newState }
                )
                SynthTab.MOD -> ModulationMatrixPanel(
                    routes = synthState.modulationRoutes,
                    onRouteChange = { idx, route ->
                        val newRoutes = synthState.modulationRoutes.toMutableList().apply { set(idx, route) }
                        synthState = synthState.copy(modulationRoutes = newRoutes)
                    }
                )
                SynthTab.SEQ -> SequencerView(
                    steps = synthState.sequencerSteps,
                    onStepChange = { idx, step ->
                        val newSteps = synthState.sequencerSteps.toMutableList().apply { set(idx, step) }
                        synthState = synthState.copy(sequencerSteps = newSteps)
                    },
                    currentStep = 0,
                    playing = synthState.sequencerPlaying,
                    onPlayingChange = { synthState = synthState.copy(sequencerPlaying = it) },
                    tempo = synthState.sequencerTempo,
                    onTempoChange = { synthState = synthState.copy(sequencerTempo = it) }
                )
            }
            }
        }

        // Bottom: Transport + Action buttons + Keyboard
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
            // Action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini transport (always visible)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (synthState.sequencerPlaying) KnobGreen else PurpleMid)
                        .clickable { synthState = synthState.copy(sequencerPlaying = !synthState.sequencerPlaying) },
                        contentAlignment = Alignment.Center) {
                        Text(if (synthState.sequencerPlaying) "\u25A0" else "\u25B6", color = Color.White, fontSize = 10.sp)
                    }
                    Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                        .background(PurpleMid).clickable { synthState = synthState.copy(sequencerPlaying = false) },
                        contentAlignment = Alignment.Center) {
                        Text("\u21BA", color = Color.White, fontSize = 10.sp)
                    }
                    Text("BPM: %.0f".format(synthState.sequencerTempo), color = TextSecondary, fontSize = 9.sp)
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "PRESET" to { showPresets = true },
                        "SAVE" to { showSaveDialog = true },
                        "HELP" to { showManual = true },
                        "\u2699" to { showSettings = true }
                    ).forEach { (label, onClick) ->
                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                            .background(PurpleMid.copy(alpha = 0.3f))
                            .border(1.dp, PurpleMid.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable(onClick = onClick),
                            contentAlignment = Alignment.Center) {
                            Text(label, color = PurpleLight, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // Keyboard with octave controls and pitch bend strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Octave controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PurpleMid)
                            .clickable { octaveOffset = (octaveOffset - 1).coerceAtLeast(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "C$octaveOffset",
                        color = PurpleLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PurpleMid)
                            .clickable { octaveOffset = (octaveOffset + 1).coerceAtMost(7) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.width(8.dp))

                KeyboardView(
                    activeNotes = activeNotes,
                    onNoteOn = { note ->
                        activeNotes = activeNotes + note
                        SynthEngine.noteOn(note, 100)
                    },
                    onNoteOff = { note ->
                        activeNotes = activeNotes - note
                        SynthEngine.noteOff(note)
                    },
                    onPitchBend = { pitch -> SynthEngine.setParam(51, pitch) },
                    octaveOffset = octaveOffset,
                    modifier = Modifier.weight(1f)
                )

                // Pitch bend is now handled inside KeyboardView
            }
        }
    }

    // Dialogs
    if (showPresets) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            PresetBrowser(
                onDismiss = { showPresets = false },
                onSelectPreset = { name ->
                    val allPresets = dbPresets ?: fallbackPresets
                    val preset = allPresets.find { it.name == name }
                    if (preset != null && preset.parametersJson != "{}") {
                        try {
                            val loadedState = presetJson.decodeFromString<SynthState>(preset.parametersJson)
                            synthState = loadedState
                            // Apply all params to the audio engine
                            applySynthStateToEngine(loadedState)
                        } catch (_: Exception) {
                            // Fallback presets have "{}" – silently ignore parse errors
                        }
                    }
                    showPresets = false
                },
                presetDao = presetDao
            )
        }
    }
    if (showManual) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            ManualScreen(onDismiss = { showManual = false })
        }
    }
    if (showSettings) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            SettingsScreen(onDismiss = { showSettings = false }, settingsDataStore = settingsDataStore, modifier = Modifier.padding(16.dp))
        }
    }
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = BgPanel,
            title = { Text("Save Preset", fontWeight = FontWeight.Bold, color = PurpleLight) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = savePresetName, onValueChange = { savePresetName = it },
                        label = { Text("Preset Name") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PurplePrimary,
                            unfocusedBorderColor = PurpleMid, cursorColor = PurpleLight,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (savePresetName.isNotBlank()) {
                        saveScope.launch {
                            val jsonStr = presetJson.encodeToString(SynthState.serializer(), synthState)
                            presetDao.insertPreset(PresetEntity(name = savePresetName.trim(),
                                category = savePresetCategory, description = "User preset",
                                isFactory = false, parametersJson = jsonStr))
                        }
                        showSaveDialog = false; savePresetName = ""
                    }
                }, enabled = savePresetName.isNotBlank()) { Text("Save", color = PurpleLight) }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

private fun applySynthStateToEngine(state: SynthState) {
    SynthEngine.setParam(0, state.osc1Level)
    SynthEngine.setParam(1, state.osc2Level)
    SynthEngine.setParam(2, state.osc1Waveform.toFloat())
    SynthEngine.setParam(3, state.osc2Waveform.toFloat())
    SynthEngine.setParam(4, state.oscDetune)
    SynthEngine.setParam(5, state.subOscLevel)
    SynthEngine.setParam(6, state.noiseLevel)
    SynthEngine.setParam(7, state.oscMix)
    SynthEngine.setParam(10, state.filterCutoff)
    SynthEngine.setParam(11, state.filterResonance)
    SynthEngine.setParam(12, state.filterMode.toFloat())
    SynthEngine.setParam(13, state.filterEnvAmount)
    SynthEngine.setParam(20, state.ampAttack)
    SynthEngine.setParam(21, state.ampDecay)
    SynthEngine.setParam(22, state.ampSustain)
    SynthEngine.setParam(23, state.ampRelease)
    SynthEngine.setParam(25, state.filterAttack)
    SynthEngine.setParam(26, state.filterDecay)
    SynthEngine.setParam(27, state.filterSustain)
    SynthEngine.setParam(28, state.filterRelease)
    SynthEngine.setParam(30, state.lfo1Rate)
    SynthEngine.setParam(31, state.lfo1Depth)
    SynthEngine.setParam(32, state.lfo1Waveform.toFloat())
    SynthEngine.setParam(35, state.lfo2Rate)
    SynthEngine.setParam(36, state.lfo2Depth)
    SynthEngine.setParam(37, state.lfo2Waveform.toFloat())
    SynthEngine.setParam(40, state.reverbMix)
    SynthEngine.setParam(41, state.reverbDecay)
    SynthEngine.setParam(42, state.delayMix)
    SynthEngine.setParam(43, state.delayTime)
    SynthEngine.setParam(44, state.delayFeedback)
    SynthEngine.setParam(45, state.distortionDrive)
    SynthEngine.setParam(46, state.distortionMix)
    SynthEngine.setParam(47, if (state.effectsBypass) 1f else 0f)
    SynthEngine.setParam(50, state.masterVolume)
}
