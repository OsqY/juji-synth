package com.jujisynth.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujisynth.data.PresetDao
import com.jujisynth.data.PresetEntity
import com.jujisynth.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.jujisynth.model.SynthState

/**
 * Preset browser dialog showing categorized presets loaded from Room database.
 * Falls back to hardcoded demo presets when no DAO is provided.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetBrowser(
    onDismiss: () -> Unit,
    onSelectPreset: (String) -> Unit,
    presetDao: PresetDao? = null,
    selectedCategory: String = "All",
    onCategoryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dbPresets by remember { mutableStateOf<List<PresetEntity>?>(null) }

    LaunchedEffect(presetDao) {
        if (presetDao != null) {
            presetDao.getAllPresets().collect { list -> dbPresets = list }
        }
    }

    val allPresets = dbPresets ?: fallbackPresets

    val categories = remember(allPresets) {
        listOf("All") + allPresets.map { it.category }.distinct().sorted()
    }

    val scope = rememberCoroutineScope()

    val displayPresets = remember(selectedCategory, allPresets) {
        if (selectedCategory == "All") allPresets
        else allPresets.filter { it.category == selectedCategory }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgPanel)
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PRESETS", color = KnobCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextButton(onClick = onDismiss) {
                    Text("✕", color = TextSecondary, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Category tabs
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                categories.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) KnobCyan else BgPanel.copy(alpha = 0.3f))
                            .clickable { onCategoryChange(cat) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(cat, color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Preset list
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(displayPresets, key = { it.name }) { preset ->
                    val isUserPreset = !preset.isFactory && presetDao != null
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isUserPreset) BgPanel.copy(alpha = 0.7f) else BgPanel)
                            .then(
                                if (isUserPreset) {
                                    Modifier.combinedClickable(
                                        onClick = { onSelectPreset(preset.name) },
                                        onLongClick = {
                                            scope.launch { presetDao.deletePreset(preset) }
                                        }
                                    )
                                } else {
                                    Modifier.clickable { onSelectPreset(preset.name) }
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(preset.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                if (preset.isFactory) {
                                    Text("FACTORY", color = KnobCyan.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Light)
                                } else {
                                    Text("USER", color = KnobGreen.copy(alpha = 0.6f), fontSize = 7.sp, fontWeight = FontWeight.Light)
                                }
                            }
                            Text(preset.description, color = TextMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

private val fbJson = Json { encodeDefaults = true }
private fun fjson(s: SynthState): String = fbJson.encodeToString(SynthState.serializer(), s)

val fallbackPresets: List<PresetEntity> = listOf(
    // ── Leads (5) ──────────────────────────────────────────────
    PresetEntity(name = "Saw Lead Thick", category = "Leads",
        description = "Thick detuned saws with high cutoff, dry",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 0, osc2Waveform = 0, oscDetune = 0.3f,
            filterCutoff = 0.9f, filterResonance = 0.1f,
            ampAttack = 0.01f, ampDecay = 0.3f, ampSustain = 0.7f, ampRelease = 0.15f,
            masterVolume = 0.75f
        ))),
    PresetEntity(name = "Saw Lead Bright", category = "Leads",
        description = "Bright single saw with resonance peak, dry",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 0, osc2Level = 0f,
            filterCutoff = 1.0f, filterResonance = 0.3f,
            ampAttack = 0.02f, ampDecay = 0.2f, ampSustain = 0.6f, ampRelease = 0.2f,
            masterVolume = 0.7f
        ))),
    PresetEntity(name = "Saw Lead Stab", category = "Leads",
        description = "Staccato saw stab with zero sustain, dry",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 0, oscDetune = 0.15f,
            filterCutoff = 0.85f, filterResonance = 0.2f,
            ampAttack = 0.01f, ampDecay = 0.1f, ampSustain = 0f, ampRelease = 0.05f,
            masterVolume = 0.8f
        ))),
    PresetEntity(name = "Saw Lead Wide", category = "Leads",
        description = "Wide detuned saws, dry and punchy",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 0, osc2Waveform = 0, oscDetune = 0.4f,
            filterCutoff = 0.8f,
            ampAttack = 0.03f, ampDecay = 0.25f, ampSustain = 0.8f, ampRelease = 0.1f,
            masterVolume = 0.7f
        ))),
    PresetEntity(name = "Saw Lead Fury", category = "Leads",
        description = "Aggressive max-detune saws, filter wide open",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 0, osc2Waveform = 0, oscDetune = 0.5f,
            filterCutoff = 0.95f, filterResonance = 0.15f,
            ampAttack = 0.01f, ampDecay = 0.4f, ampSustain = 0.9f, ampRelease = 0.15f,
            masterVolume = 0.75f
        ))),

    // ── Pads (5) ───────────────────────────────────────────────
    PresetEntity(name = "Triangle Dream", category = "Pads",
        description = "Warm triangle pad with slow attack and reverb",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 2, osc2Waveform = 3, oscMix = 0.5f,
            filterCutoff = 0.5f, filterResonance = 0.3f,
            ampAttack = 0.4f, ampDecay = 0.4f, ampSustain = 0.8f, ampRelease = 0.5f,
            reverbMix = 0.4f
        ))),
    PresetEntity(name = "Sine Swell", category = "Pads",
        description = "Slow swelling sine pad with soft reverb",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 3, osc2Waveform = 3, oscDetune = 0.1f,
            filterCutoff = 0.45f, filterResonance = 0.25f,
            ampAttack = 0.5f, ampDecay = 0.3f, ampSustain = 0.85f, ampRelease = 0.6f,
            reverbMix = 0.35f
        ))),
    PresetEntity(name = "Moving Pad", category = "Pads",
        description = "Triangle pad with LFO filter movement and reverb",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 2, osc2Waveform = 2, oscDetune = 0.25f,
            filterCutoff = 0.55f, filterResonance = 0.2f,
            ampAttack = 0.35f, ampDecay = 0.5f, ampSustain = 0.9f, ampRelease = 0.45f,
            reverbMix = 0.5f, lfo1Rate = 0.3f, lfo1Depth = 0.4f
        ))),
    PresetEntity(name = "Soft Triangle", category = "Pads",
        description = "Single triangle pad, slow and smooth",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 2, osc2Level = 0f,
            filterCutoff = 0.6f, filterResonance = 0.2f,
            ampAttack = 0.45f, ampDecay = 0.35f, ampSustain = 0.7f, ampRelease = 0.55f,
            reverbMix = 0.3f
        ))),
    PresetEntity(name = "Evolving Pad", category = "Pads",
        description = "Lush evolving triangle/sine pad with LFO",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 2, osc2Waveform = 3, oscMix = 0.6f,
            filterCutoff = 0.4f, filterResonance = 0.35f,
            ampAttack = 0.4f, ampDecay = 0.4f, ampSustain = 0.75f, ampRelease = 0.7f,
            reverbMix = 0.45f, lfo1Rate = 0.2f, lfo1Depth = 0.5f
        ))),

    // ── Bass (4) ───────────────────────────────────────────────
    PresetEntity(name = "Square Bass", category = "Bass",
        description = "Punchy square bass with low cutoff, loud",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 1, osc2Level = 0f,
            filterCutoff = 0.15f, filterResonance = 0.4f,
            ampAttack = 0.01f, ampDecay = 0.15f, ampSustain = 0.9f, ampRelease = 0.08f,
            masterVolume = 0.9f
        ))),
    PresetEntity(name = "Deep Sub", category = "Bass",
        description = "Massive sub-bass with sub oscillator, deep rumble",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 3, osc2Level = 0f, subOscLevel = 0.7f,
            filterCutoff = 0.08f, filterResonance = 0.3f,
            ampAttack = 0.01f, ampDecay = 0.1f, ampSustain = 0.85f, ampRelease = 0.05f,
            masterVolume = 0.95f
        ))),
    PresetEntity(name = "Pulse Bass", category = "Bass",
        description = "Dual square pulse bass with slight detune",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 1, osc2Waveform = 1, oscDetune = 0.1f, oscMix = 0.5f,
            filterCutoff = 0.2f, filterResonance = 0.25f,
            ampAttack = 0.02f, ampDecay = 0.2f, ampSustain = 0.8f, ampRelease = 0.1f,
            masterVolume = 0.85f
        ))),
    PresetEntity(name = "Sine Sub Bass", category = "Bass",
        description = "Pure sine bass with full sub oscillator, huge",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 3, osc1Level = 0.5f, osc2Level = 0f, subOscLevel = 1f,
            filterCutoff = 0.12f, filterResonance = 0.5f,
            ampAttack = 0.02f, ampDecay = 0.15f, ampSustain = 0.9f, ampRelease = 0.12f,
            masterVolume = 0.9f
        ))),

    // ── FX (3) ─────────────────────────────────────────────────
    PresetEntity(name = "Percussive Chop", category = "FX",
        description = "Noise-heavy percussive chop with delay",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 0, osc1Level = 0.3f, osc2Level = 0f, noiseLevel = 0.7f,
            filterCutoff = 0.9f, filterResonance = 0.6f,
            ampAttack = 0.01f, ampDecay = 0.05f, ampSustain = 0f, ampRelease = 0.03f,
            delayMix = 0.4f, delayTime = 0.25f, delayFeedback = 0.5f
        ))),
    PresetEntity(name = "Noise Burst", category = "FX",
        description = "Pure noise burst, extremely short, percussive",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Level = 0f, osc2Level = 0f, noiseLevel = 1f,
            filterCutoff = 0.5f, filterResonance = 0.8f,
            ampAttack = 0.01f, ampDecay = 0.02f, ampSustain = 0f, ampRelease = 0.02f,
            masterVolume = 0.6f
        ))),
    PresetEntity(name = "Filter Blast", category = "FX",
        description = "Extreme filter sweep with delay and high resonance",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 0, osc2Waveform = 1, oscMix = 0.3f,
            filterCutoff = 0.1f, filterResonance = 0.9f, filterEnvAmount = 0.8f,
            ampAttack = 0.01f, ampDecay = 0.1f, ampSustain = 0f, ampRelease = 0.05f,
            delayMix = 0.5f, delayFeedback = 0.6f
        ))),

    // ── Ambient (3) ────────────────────────────────────────────
    PresetEntity(name = "Washy Pad", category = "Ambient",
        description = "Washy triangle/sine pad with reverb, responsive attack",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 2, osc2Waveform = 3, oscMix = 0.5f,
            filterCutoff = 0.3f,
            ampAttack = 0.3f, ampDecay = 0.4f, ampSustain = 0.6f, ampRelease = 0.8f,
            reverbMix = 0.6f, reverbDecay = 0.7f,
            masterVolume = 0.4f
        ))),
    PresetEntity(name = "Deep Space", category = "Ambient",
        description = "Deep space sine drone with heavy reverb, very quiet",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 3, osc2Waveform = 3, oscDetune = 0.05f,
            filterCutoff = 0.25f,
            ampAttack = 0.35f, ampDecay = 0.5f, ampSustain = 0.5f, ampRelease = 0.9f,
            reverbMix = 0.7f, reverbDecay = 0.8f,
            masterVolume = 0.35f
        ))),
    PresetEntity(name = "Ethereal Haze", category = "Ambient",
        description = "Hazy evolving texture with LFO, noise, and reverb",
        isFactory = true, parametersJson = fjson(SynthState(
            osc1Waveform = 2, osc2Level = 0f, noiseLevel = 0.2f,
            filterCutoff = 0.35f, filterResonance = 0.1f,
            ampAttack = 0.25f, ampDecay = 0.6f, ampSustain = 0.4f, ampRelease = 0.7f,
            reverbMix = 0.55f, reverbDecay = 0.75f,
            lfo1Rate = 0.1f, lfo1Depth = 0.6f,
            masterVolume = 0.3f
        ))),
)
