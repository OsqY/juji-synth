package com.jujisynth.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.jujisynth.midi.MidiController
import com.jujisynth.audio.SynthEngine
import com.jujisynth.data.MidiMapping
import com.jujisynth.data.MidiMappingStore
import com.jujisynth.data.PresetEntity
import com.jujisynth.data.SettingsDataStore
import com.jujisynth.model.MidiLearnMode
import com.jujisynth.model.MidiLearnState
import com.jujisynth.model.ModulationRoute
import com.jujisynth.model.ParamIds
import com.jujisynth.model.SynthState
import com.jujisynth.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import androidx.compose.material3.ExperimentalMaterial3Api

private val presetJson = Json { encodeDefaults = true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSynthScreen(modifier: Modifier = Modifier) {
    // Single source of truth
    var synthState by remember { mutableStateOf(SynthState()) }

    // Separate UI-only state
    var activeNotes by remember { mutableStateOf(setOf<Int>()) }
    var keyboardView by remember { mutableStateOf("piano") } // "piano" or "roll"

    var octaveOffset by remember { mutableStateOf(3) }
    var showPresets by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var savePresetName by remember { mutableStateOf("") }
    var savePresetCategory by remember { mutableStateOf("Leads") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var presetCategory by remember { mutableStateOf("All") }
    val saveScope = rememberCoroutineScope()

    // Database
    val context = LocalContext.current

    // MIDI Learn
    var midiLearnMode by remember { mutableStateOf(MidiLearnMode.IDLE) }
    var selectedParamId by remember { mutableStateOf<Int?>(null) }
    var midiMappings by remember { mutableStateOf<List<MidiMapping>>(emptyList()) }
    val midiMappingStore = remember { MidiMappingStore(context) }
    val learnScope = rememberCoroutineScope()
    val app = context.applicationContext as JujiSynthApp
    val presetDao = app.database.presetDao()
    val settingsDataStore = remember { SettingsDataStore(context) }

    // MIDI Controller
    val midiController = remember { MidiController(context) }
    DisposableEffect(Unit) {
        midiController.startScanning()
        onDispose { midiController.stopScanning() }
    }

    // Load persisted MIDI mappings on startup
    LaunchedEffect(Unit) {
        midiMappingStore.mappingsFlow.collect { mappings ->
            midiMappings = mappings
        }
    }

    // Wire MIDI Learn capture callback into MidiController
    val currentLearnMode by rememberUpdatedState(midiLearnMode)
    val currentSelectedParamId by rememberUpdatedState(selectedParamId)
    val currentMidiMappingStore by rememberUpdatedState(midiMappingStore)
    val currentMidiMappings by rememberUpdatedState(midiMappings)
    DisposableEffect(Unit) {
        MidiController.onCcLearnCallback = { ccNumber, value ->
            if (currentLearnMode == MidiLearnMode.CONTROL_SELECTED && currentSelectedParamId != null) {
                val paramId = currentSelectedParamId!!
                // Create and persist the mapping
                val mapping = MidiMapping(ccNumber = ccNumber, paramId = paramId)
                learnScope.launch {
                    currentMidiMappingStore.addMapping(mapping)
                }
                // Apply the incoming value to the engine immediately
                SynthEngine.setParam(paramId, value)
                // Reset learn mode back to LEARN_ACTIVE (control assigned)
                selectedParamId = null
                midiLearnMode = MidiLearnMode.LEARN_ACTIVE
            }
        }
        onDispose {
            MidiController.onCcLearnCallback = null
        }
    }

    // Preset collection for loading
    var dbPresets by remember { mutableStateOf<List<PresetEntity>?>(null) }
    LaunchedEffect(presetDao) {
        presetDao.getAllPresets().collect { list -> dbPresets = list }
    }

    // Poll sequencer current step when playing
    LaunchedEffect(synthState.sequencerPlaying) {
        while (synthState.sequencerPlaying) {
            val step = SynthEngine.getSequencerStep()
            if (step != synthState.sequencerCurrentStep) {
                synthState = synthState.copy(sequencerCurrentStep = step)
            }
            kotlinx.coroutines.delay(33) // ~30fps
        }
    }

    Column(modifier = modifier.fillMaxSize().background(BgGunmetal).statusBarsPadding()) {
        // 3-column persistent layout (no tab bar — all sections visible at once)
        HardwareChassis(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 3.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // ── LEFT COLUMN: OSC + MOD ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // OSC panel (full oscillator section)
                    SynthPanel(title = "OSC", accentColor = KnobAmber) {
                        OscillatorPanel(
                            state = synthState,
                            onParamChange = { newState -> synthState = newState },
                            learnMode = midiLearnMode != MidiLearnMode.IDLE,
                            selectedParamId = selectedParamId,
                            onLearnSelect = { paramId ->
                                selectedParamId = paramId
                                midiLearnMode = MidiLearnMode.CONTROL_SELECTED
                            }
                        )
                    }
                    // MOD panel (patch bay)
                    SynthPanel(title = "MOD", accentColor = KnobPink) {
                        PatchBayView(
                            routes = synthState.modulationRoutes,
                            onRouteChange = { idx, route ->
                                val newRoutes = synthState.modulationRoutes.toMutableList().apply { set(idx, route) }
                                synthState = synthState.copy(modulationRoutes = newRoutes)
                                SynthEngine.setModulationRoute(idx, route.source, route.destination, route.amount, route.active)
                            },
                            learnMode = midiLearnMode != MidiLearnMode.IDLE,
                            selectedParamId = selectedParamId,
                            onLearnSelect = { paramId ->
                                selectedParamId = paramId
                                midiLearnMode = MidiLearnMode.CONTROL_SELECTED
                            }
                        )
                    }
                }

                // ── CENTER COLUMN: FILTER + ENV + LFO ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    SynthPanel(title = "FILTER", accentColor = KnobCyan) {
                        FilterPanel(
                            state = synthState,
                            onParamChange = { newState -> synthState = newState },
                            learnMode = midiLearnMode != MidiLearnMode.IDLE,
                            selectedParamId = selectedParamId,
                            onLearnSelect = { paramId ->
                                selectedParamId = paramId
                                midiLearnMode = MidiLearnMode.CONTROL_SELECTED
                            }
                        )
                    }
                    SynthPanel(title = "ENV", accentColor = KnobGreen) {
                        EnvelopePanel(
                            state = synthState,
                            onParamChange = { newState -> synthState = newState },
                            learnMode = midiLearnMode != MidiLearnMode.IDLE,
                            selectedParamId = selectedParamId,
                            onLearnSelect = { paramId ->
                                selectedParamId = paramId
                                midiLearnMode = MidiLearnMode.CONTROL_SELECTED
                            }
                        )
                    }
                    SynthPanel(title = "LFO", accentColor = KnobPink) {
                        LfoPanel(
                            state = synthState,
                            onParamChange = { newState -> synthState = newState },
                            learnMode = midiLearnMode != MidiLearnMode.IDLE,
                            selectedParamId = selectedParamId,
                            onLearnSelect = { paramId ->
                                selectedParamId = paramId
                                midiLearnMode = MidiLearnMode.CONTROL_SELECTED
                            }
                        )
                    }
                }

                // ── RIGHT COLUMN: FX + SEQ + SCOPE ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    SynthPanel(title = "FX", accentColor = KnobRed) {
                        EffectsPanel(
                            state = synthState,
                            onParamChange = { newState -> synthState = newState },
                            learnMode = midiLearnMode != MidiLearnMode.IDLE,
                            selectedParamId = selectedParamId,
                            onLearnSelect = { paramId ->
                                selectedParamId = paramId
                                midiLearnMode = MidiLearnMode.CONTROL_SELECTED
                            }
                        )
                    }
                    SynthPanel(title = "SEQ", accentColor = KnobOrange) {
                        SequencerView(
                            steps = synthState.sequencerSteps,
                            onStepChange = { idx, step ->
                                val newSteps = synthState.sequencerSteps.toMutableList().apply { set(idx, step) }
                                synthState = synthState.copy(sequencerSteps = newSteps)
                                // Sync to engine via JNI
                                val notes = IntArray(16) { i -> newSteps[i].note }
                                val velocities = IntArray(16) { i -> newSteps[i].velocity }
                                val gates = FloatArray(16) { i -> newSteps[i].gate }
                                val auto = FloatArray(16) { i -> newSteps[i].automation }
                                SynthEngine.setSequencerSteps(notes, velocities, gates, auto)
                            },
                            currentStep = synthState.sequencerCurrentStep,
                            playing = synthState.sequencerPlaying,
                            onPlayingChange = { synthState = synthState.copy(sequencerPlaying = it) },
                            tempo = synthState.sequencerTempo,
                            onTempoChange = {
                                synthState = synthState.copy(sequencerTempo = it)
                                SynthEngine.setParam(60, it)
                            },
                            learnMode = midiLearnMode != MidiLearnMode.IDLE,
                            selectedParamId = selectedParamId,
                            onLearnSelect = { paramId ->
                                selectedParamId = paramId
                                midiLearnMode = MidiLearnMode.CONTROL_SELECTED
                            }
                        )
                    }
                    // Oscilloscope
                    OscilloscopeView(modifier = Modifier.fillMaxWidth())
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
                    // Record arm
                    Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (synthState.sequencerRecording) KnobRed else BgPanel)
                        .border(1.dp, if (synthState.sequencerRecording) KnobRed else PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable {
                            synthState = synthState.copy(sequencerRecording = !synthState.sequencerRecording)
                        },
                        contentAlignment = Alignment.Center) {
                        Text("\u25CF", color = Color.White, fontSize = 8.sp)
                    }
                    // Play/Pause
                    Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (synthState.sequencerPlaying) TransportGreen else BgPanel)
                        .border(1.dp, if (synthState.sequencerPlaying) TransportGreen.copy(alpha = 0.6f) else PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable {
                            val newPlaying = !synthState.sequencerPlaying
                            synthState = synthState.copy(sequencerPlaying = newPlaying)
                            SynthEngine.setParam(61, if (newPlaying) 1f else 0f)
                            // Reset playhead on play
                            if (newPlaying) {
                                synthState = synthState.copy(sequencerCurrentStep = 0)
                            }
                        },
                        contentAlignment = Alignment.Center) {
                        Text(if (synthState.sequencerPlaying) "\u25A0" else "\u25B6", color = Color.White, fontSize = 9.sp)
                    }
                    // Reset
                    Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                        .background(BgPanel)
                        .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable {
                            synthState = synthState.copy(sequencerPlaying = false, sequencerCurrentStep = 0)
                            SynthEngine.setParam(61, 0f)
                        },
                        contentAlignment = Alignment.Center) {
                        Text("\u21BA", color = Color.White, fontSize = 9.sp)
                    }
                    // Loop toggle
                    Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (synthState.sequencerLooping) KnobGreen.copy(alpha = 0.5f) else BgPanel)
                        .border(1.dp, if (synthState.sequencerLooping) KnobGreen else PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable {
                            val newLooping = !synthState.sequencerLooping
                            synthState = synthState.copy(sequencerLooping = newLooping)
                            SynthEngine.setParam(ParamIds.SEQ_LOOPING, if (newLooping) 1f else 0f)
                        },
                        contentAlignment = Alignment.Center) {
                        Text("\u21BB", color = Color.White, fontSize = 9.sp)
                    }
                    // BPM LCD display
                    LcdDisplay(
                        value = "%.0f".format(synthState.sequencerTempo) + " BPM",
                        label = "BPM",
                        color = KnobGreen,
                        fontSize = 8.sp
                    )
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // MIDI Learn button
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    midiLearnMode == MidiLearnMode.CONTROL_SELECTED -> KnobGreen.copy(alpha = 0.5f)
                                    midiLearnMode == MidiLearnMode.LEARN_ACTIVE -> KnobAmber.copy(alpha = 0.5f)
                                    else -> BgPanel
                                }
                            )
                            .border(1.dp, PanelHighlight.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable {
                                if (midiLearnMode != MidiLearnMode.IDLE) {
                                    midiLearnMode = MidiLearnMode.IDLE
                                    selectedParamId = null
                                } else {
                                    midiLearnMode = MidiLearnMode.LEARN_ACTIVE
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("MIDI", color = KnobCyan, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                    // PANIC button
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                        .background(KnobRed.copy(alpha = 0.5f))
                        .border(1.dp, KnobRed, RoundedCornerShape(6.dp))
                        .clickable {
                            SynthEngine.panic()
                            activeNotes = emptySet()
                            Toast.makeText(context, "Panic! All sound stopped", Toast.LENGTH_SHORT).show()
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("PANIC", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                    listOf(
                        "PRESET" to { showPresets = true },
                        "SAVE" to { showSaveDialog = true },
                        "HELP" to { showManual = true },
                        "\u2699" to { showSettings = true }
                    ).forEach { (label, onClick) ->
                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                            .background(BgPanel)
                            .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable(onClick = onClick),
                            contentAlignment = Alignment.Center) {
                            Text(label, color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
                            .background(BgPanel)
                            .clickable { octaveOffset = (octaveOffset - 1).coerceAtLeast(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "C$octaveOffset",
                        color = KnobCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BgPanel)
                            .clickable { octaveOffset = (octaveOffset + 1).coerceAtMost(7) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // View toggle (piano / roll)
                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (keyboardView == "piano") KnobCyan else BgPanel)
                        .clickable { keyboardView = if (keyboardView == "piano") "roll" else "piano" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (keyboardView == "piano") "R" else "P",
                        color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Recording capture: when armed and playing, capture keyboard input
                val captureNote = { note: Int ->
                    if (synthState.sequencerRecording && synthState.sequencerPlaying) {
                        val gridStep = synthState.sequencerCurrentStep.toFloat()
                        // Check if a note exists at this position already
                        val existingAtPos = synthState.pianoRollNotes.find { n ->
                            n.note == note &&
                            kotlin.math.abs(n.startStep - gridStep) < 0.5f
                        }
                        if (existingAtPos == null) {
                            val newNote = com.jujisynth.model.PianoRollNote(
                                note = note,
                                startStep = gridStep,
                                duration = 1f,
                                velocity = 100
                            )
                            synthState = synthState.copy(
                                pianoRollNotes = synthState.pianoRollNotes + newNote
                            )
                        }
                    }
                }

                if (keyboardView == "piano") {
                    KeyboardView(
                        activeNotes = activeNotes,
                        onNoteOn = { note ->
                            activeNotes = activeNotes + note
                            SynthEngine.noteOn(note, 100)
                            captureNote(note)
                        },
                        onNoteOff = { note ->
                            activeNotes = activeNotes - note
                            SynthEngine.noteOff(note)
                        },
                        onPitchBend = { pitch -> SynthEngine.setParam(51, pitch) },
                        octaveOffset = octaveOffset,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    PianoRollView(
                        notes = synthState.pianoRollNotes,
                        onNotesChange = { newNotes ->
                            synthState = synthState.copy(pianoRollNotes = newNotes)
                        },
                        numSteps = synthState.pianoRollLength,
                        currentStep = if (synthState.sequencerPlaying) synthState.sequencerCurrentStep else -1,
                        isPlaying = synthState.sequencerPlaying,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Dialogs
    if (showPresets) {
        ModalBottomSheet(
            onDismissRequest = { showPresets = false },
            containerColor = BgPanel,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            PresetBrowser(
                onDismiss = { showPresets = false },
                onSelectPreset = { name ->
                    val allPresets = dbPresets ?: fallbackPresets
                    val preset = allPresets.find { it.name == name }
                    if (preset != null && preset.parametersJson != "{}") {
                        try {
                            val loadedState = presetJson.decodeFromString<SynthState>(preset.parametersJson)
                            synthState = loadedState
                            applySynthStateToEngine(loadedState)
                            Toast.makeText(context, "Loaded: ${preset.name}", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) { }
                    }
                    showPresets = false
                },
                presetDao = presetDao,
                selectedCategory = presetCategory,
                onCategoryChange = { presetCategory = it }
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
            SettingsScreen(
                onDismiss = { showSettings = false },
                settingsDataStore = settingsDataStore,
                onSettingsApplied = {
                    SynthEngine.stop()
                    SynthEngine.start()
                },
                modifier = Modifier.padding(16.dp)
            )
        }
    }
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = BgPanel,
            title = { Text("Save Preset", fontWeight = FontWeight.Bold, color = KnobCyan) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = savePresetName, onValueChange = { savePresetName = it },
                        label = { Text("Preset Name") }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KnobCyan,
                            unfocusedBorderColor = PanelHighlight, cursorColor = TextPrimary,
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
                }, enabled = savePresetName.isNotBlank()) { Text("Save", color = KnobCyan) }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel", color = TextMuted) } }
        )
    }
}

private fun applySynthStateToEngine(state: SynthState) {
    android.util.Log.i("JujiSynth", "applySynthStateToEngine called")
    // Reset effects first so old delay/reverb buffer content doesn't bleed into new preset
    SynthEngine.resetEffects()

    // Apply all parameters atomically via a single float array.
    // Order must match AudioEngine::SYNTH_PARAM_COUNT layout in JniBridge.cpp.
    val params = floatArrayOf(
        // Oscillators (9)
        state.osc1Level, state.osc2Level, state.osc1Waveform.toFloat(), state.osc2Waveform.toFloat(),
        state.oscDetune, state.subOscLevel, state.noiseLevel, state.oscMix,
        if (state.oscSync) 1f else 0f,
        // Filter (4)
        state.filterCutoff, state.filterResonance, state.filterMode.toFloat(), state.filterEnvAmount,
        // Amp Envelope (4)
        state.ampAttack, state.ampDecay, state.ampSustain, state.ampRelease,
        // Filter Envelope (4)
        state.filterAttack, state.filterDecay, state.filterSustain, state.filterRelease,
        // LFO1 (3) + LFO2 (3)
        state.lfo1Rate, state.lfo1Depth, state.lfo1Waveform.toFloat(),
        state.lfo2Rate, state.lfo2Depth, state.lfo2Waveform.toFloat(),
        // Effects: Reverb (2), Delay (3), Distortion (2), Bypass (1), Chorus (3)
        state.reverbMix, state.reverbDecay,
        state.delayMix, state.delayTime, state.delayFeedback,
        state.distortionDrive, state.distortionMix,
        if (state.effectsBypass) 1f else 0f,
        state.chorusRate, state.chorusDepth, state.chorusMix,
        // Master (1)
        state.masterVolume
    )
    SynthEngine.applySynthState(params)

    // NOTE: Sequencer params NOT applied here — presets should not control
    // sequencer state to avoid unexpected playback or stutter

    // Modulation routes
    state.modulationRoutes.forEachIndexed { idx, route ->
        SynthEngine.setModulationRoute(idx, route.source, route.destination, route.amount, route.active)
    }
}
