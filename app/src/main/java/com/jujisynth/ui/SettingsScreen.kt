package com.jujisynth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.jujisynth.data.AppSettings
import com.jujisynth.data.SettingsDataStore
import com.jujisynth.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Hardware-style audio settings panel displayed as an overlay.
 * Provides radio-button selection for sample rate, buffer size, and output mode.
 * Persists settings via [SettingsDataStore] when provided.
 */
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    settingsDataStore: SettingsDataStore? = null,
    onSettingsApplied: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // --- local mutable copies ---
    var selectedSampleRate by remember { mutableStateOf(44100) }
    var selectedBufferSize by remember { mutableStateOf(256) }
    var selectedOutputMode by remember { mutableStateOf("stereo") }

    val scope = rememberCoroutineScope()

    // Load persisted settings on first composition (one-shot to avoid coroutine leak)
    LaunchedEffect(settingsDataStore) {
        val settings = settingsDataStore?.getSettings()
        if (settings != null) {
            selectedSampleRate = settings.sampleRate
            selectedBufferSize = settings.bufferSize
            selectedOutputMode = settings.outputMode
        }
    }

    // Helper: persist whenever a value changes
    fun persist() {
        scope.launch {
            settingsDataStore?.saveSettings(
                sampleRate = selectedSampleRate,
                bufferSize = selectedBufferSize,
                outputMode = selectedOutputMode
            )
            onSettingsApplied()
        }
    }

    // --- UI ---
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.55f)
            .clip(RoundedCornerShape(12.dp))
            .background(BgPanel)
            .border(1.dp, BgPanel.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AUDIO SETTINGS",
                    color = KnobCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                TextButton(onClick = onDismiss) {
                    Text("✕", color = TextSecondary, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Sample Rate ---
            SettingsSection(title = "Sample Rate") {
                SettingsRadioOption(
                    label = "44100 Hz",
                    selected = selectedSampleRate == 44100,
                    onClick = { selectedSampleRate = 44100; persist() }
                )
                SettingsRadioOption(
                    label = "48000 Hz",
                    selected = selectedSampleRate == 48000,
                    onClick = { selectedSampleRate = 48000; persist() }
                )
            }

            Spacer(Modifier.height(10.dp))

            // --- Buffer Size ---
            SettingsSection(title = "Buffer Size") {
                SettingsRadioOption(
                    label = "128 samples",
                    selected = selectedBufferSize == 128,
                    onClick = { selectedBufferSize = 128; persist() }
                )
                SettingsRadioOption(
                    label = "256 samples",
                    selected = selectedBufferSize == 256,
                    onClick = { selectedBufferSize = 256; persist() }
                )
                SettingsRadioOption(
                    label = "512 samples",
                    selected = selectedBufferSize == 512,
                    onClick = { selectedBufferSize = 512; persist() }
                )
            }

            Spacer(Modifier.height(10.dp))

            // --- Output Mode ---
            SettingsSection(title = "Output Mode") {
                SettingsRadioOption(
                    label = "Mono",
                    selected = selectedOutputMode == "mono",
                    onClick = { selectedOutputMode = "mono"; persist() }
                )
                SettingsRadioOption(
                    label = "Stereo",
                    selected = selectedOutputMode == "stereo",
                    onClick = { selectedOutputMode = "stereo"; persist() }
                )
            }
        }
    }
}

/**
 * A labeled section wrapper used inside [SettingsScreen].
 * Draws an engraved-style section title followed by [content].
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = KnobCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(BgGunmetal)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Column { content() }
        }
    }
}

/**
 * A single radio-button row used inside [SettingsScreen].
 */
@Composable
private fun SettingsRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = KnobCyan,
                unselectedColor = PanelHighlight.copy(alpha = 0.6f)
            ),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = if (selected) KnobCyan else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
