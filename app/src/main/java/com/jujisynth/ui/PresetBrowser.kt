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

    var selectedCategory by remember { mutableStateOf("All") }

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
                Text("PRESETS", color = PurpleLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                            .background(if (isSelected) PurplePrimary else PurpleMid.copy(alpha = 0.3f))
                            .clickable { selectedCategory = cat }
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
                            .background(if (isUserPreset) SurfaceCard.copy(alpha = 0.7f) else SurfaceCard)
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
                                    Text("FACTORY", color = PurpleLight.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Light)
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

val fallbackPresets: List<PresetEntity> = listOf(
    PresetEntity(name = "Deep Sub Bass", category = "Bass", description = "Heavy sub-bass with filter closed", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Acid Lead", category = "Leads", description = "Classic TB-303 style squelching lead", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Soft Pad", category = "Pads", description = "Warm evolving pad with slow attack", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Bright Arp", category = "FX", description = "Plucky arpeggiated synth with delay", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Atmospheric Swell", category = "Ambient", description = "Evolving soundscape with slow LFO movement", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Resonant Pluck", category = "Leads", description = "Filtered pluck with resonance peak", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Warm Bass", category = "Bass", description = "Round bass tone with subtle movement", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Analog Brass", category = "Leads", description = "Classic analog-style brass patch", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Dreamscape", category = "Ambient", description = "Layered ambient texture with reverb", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Tech House Stab", category = "FX", description = "Short stabby chord with filter modulation", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Saw Lead", category = "Leads", description = "Aggressive saw wave lead", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Motion Pad", category = "Pads", description = "Pad with LFO-modulated filter sweep", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "808 Kick", category = "Bass", description = "Deep kick drum simulation", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Space Echo", category = "FX", description = "Effects patch with heavy delay/reverb", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Ambient Noise", category = "Ambient", description = "Washy noise-based texture", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Square Lead", category = "Leads", description = "Classic square wave lead", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Filter Sweep", category = "FX", description = "Manual filter sweep effect", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Soft Strings", category = "Pads", description = "Sustained string-like pad", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Sub Osc Test", category = "Bass", description = "Testing sub oscillator level", isFactory = true, parametersJson = "{}"),
    PresetEntity(name = "Pulse Bass", category = "Bass", description = "Pulse wave bass with slight detune", isFactory = true, parametersJson = "{}"),
)
