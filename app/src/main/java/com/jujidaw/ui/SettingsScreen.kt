package com.jujidaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jujidaw.data.SettingsDataStore
import com.jujidaw.ui.theme.*
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

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(RadiusLg))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusLg))
                .padding(Spacing.md)
                .testTag("settings-root"),
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "AUDIO",
                    color = Primary,
                    style = TitleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier =
                        Modifier
                            .size(TouchTargetMin)
                            .clip(RoundedCornerShape(RadiusSm))
                            .background(SurfaceContainer)
                            .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                            .clickable(onClick = onDismiss)
                            .testTag("settings-close"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close audio settings", tint = OnSurface)
                }
            }

            Spacer(Modifier.height(Spacing.md))

            SettingsSection(title = "Sample Rate") {
                SettingsRadioOption(
                    label = "44100 Hz",
                    selected = selectedSampleRate == 44100,
                    tag = "settings-rate-44100",
                    onClick = { selectedSampleRate = 44100; persist() }
                )
                SettingsRadioOption(
                    label = "48000 Hz",
                    selected = selectedSampleRate == 48000,
                    tag = "settings-rate-48000",
                    onClick = { selectedSampleRate = 48000; persist() }
                )
            }

            Spacer(Modifier.height(Spacing.md))

            SettingsSection(title = "Buffer Size") {
                SettingsRadioOption(
                    label = "128 samples",
                    selected = selectedBufferSize == 128,
                    tag = "settings-buffer-128",
                    onClick = { selectedBufferSize = 128; persist() }
                )
                SettingsRadioOption(
                    label = "256 samples",
                    selected = selectedBufferSize == 256,
                    tag = "settings-buffer-256",
                    onClick = { selectedBufferSize = 256; persist() }
                )
                SettingsRadioOption(
                    label = "512 samples",
                    selected = selectedBufferSize == 512,
                    tag = "settings-buffer-512",
                    onClick = { selectedBufferSize = 512; persist() }
                )
            }

            Spacer(Modifier.height(Spacing.md))

            SettingsSection(title = "Output Mode") {
                SettingsRadioOption(
                    label = "Mono",
                    selected = selectedOutputMode == "mono",
                    tag = "settings-output-mono",
                    onClick = { selectedOutputMode = "mono"; persist() }
                )
                SettingsRadioOption(
                    label = "Stereo",
                    selected = selectedOutputMode == "stereo",
                    tag = "settings-output-stereo",
                    onClick = { selectedOutputMode = "stereo"; persist() }
                )
            }
        }
    }
}

/**
 * A labeled section wrapper used inside [SettingsScreen].
 * Groups one audio choice without adding another decorative container.
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = OnSurfaceVariant,
            style = LabelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), content = content)
    }
}

/**
 * A single radio-button row used inside [SettingsScreen].
 */
@Composable
private fun SettingsRadioOption(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(if (selected) Primary.copy(alpha = 0.12f) else SurfaceContainer)
                .border(1.dp, if (selected) Primary else OutlineVariant, RoundedCornerShape(RadiusSm))
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .padding(horizontal = Spacing.sm)
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = Primary,
                unselectedColor = OnSurfaceVariant,
            ),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            color = if (selected) Primary else OnSurface,
            style = BodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
