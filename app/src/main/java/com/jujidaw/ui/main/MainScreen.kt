package com.jujidaw.ui.main

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.JujiDawApp
import com.jujidaw.R
import com.jujidaw.model.TICKS_PER_STEP
import com.jujidaw.ui.keyboard.KeyboardScreen
import com.jujidaw.ui.mixer.MixerScreen
import com.jujidaw.ui.pads.PadsScreen
import com.jujidaw.ui.project.ProjectScreen
import com.jujidaw.ui.sequencer.SequencerScreen
import com.jujidaw.ui.synth.SynthScreen
import com.jujidaw.ui.theme.*
import com.jujidaw.ui.timeline.TimelineScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val MIN_BPM = 30f
private const val MAX_BPM = 300f

private enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    TIMELINE("Timeline", Icons.AutoMirrored.Filled.ViewList),
    MIXER("Mixer", Icons.Filled.Equalizer),
    SYNTH("Synth", Icons.Filled.Tune),
    PADS("Pads", Icons.Filled.Dashboard),
    KEYBOARD("Keys", Icons.Filled.MusicNote),
    SEQUENCER("Seq", Icons.Filled.ViewModule),
    PROJECT("Project", Icons.Filled.Folder),
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = MainTab.entries.toTypedArray()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        modifier = modifier.background(BgGunmetal),
        containerColor = BgGunmetal,
        bottomBar = {
            if (!isLandscape) {
                Column {
                    PersistentTransportBar(modifier = Modifier.fillMaxWidth())
                    NavigationBar(
                        containerColor = BgPanel,
                        contentColor = TextPrimary,
                        tonalElevation = 0.dp,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                    )
                                },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.Black,
                                        selectedTextColor = KnobAmber,
                                        indicatorColor = KnobAmber,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextMuted,
                                    ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(BgGunmetal),
        ) {
            if (isLandscape) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .background(BgPanel),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .width(80.dp)
                                .background(BgPanel),
                    ) {
                        // Transport bar — fixed at top, not scrolled
                        PersistentTransportBar(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                        )

                        // Navigation rail — scrollable with visible indicator
                        Box(modifier = Modifier.weight(1f)) {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier.verticalScroll(scrollState),
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    NavigationRailItem(
                                        icon = {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.label,
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = tab.label,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                            )
                                        },
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        colors =
                                            NavigationRailItemDefaults.colors(
                                                selectedIconColor = Color.Black,
                                                selectedTextColor = KnobAmber,
                                                indicatorColor = KnobAmber,
                                                unselectedIconColor = TextSecondary,
                                                unselectedTextColor = TextMuted,
                                            ),
                                    )
                                }
                            }

                            // Visible scrollbar indicator on right edge
                            if (scrollState.maxValue > 0) {
                                // Simple proportional scrollbar: thumb height ~ visible content ratio
                                val scrollFraction = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .width(3.dp)
                                            .height(24.dp)
                                            .offset(y = (scrollFraction * 100).dp)
                                            .background(
                                                Color.White.copy(alpha = 0.4f),
                                                RoundedCornerShape(1.5f),
                                            ),
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (tabs[selectedTab]) {
                    MainTab.TIMELINE -> TimelineScreen()
                    MainTab.MIXER -> MixerScreen()
                    MainTab.SYNTH -> SynthScreen()
                    MainTab.PADS -> PadsScreen()
                    MainTab.KEYBOARD -> KeyboardScreen()
                    MainTab.SEQUENCER -> SequencerScreen()
                    MainTab.PROJECT -> ProjectScreen()
                }
            }
        }
    }
}

@Composable
private fun PersistentTransportBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val transportController =
        remember {
            (context.applicationContext as JujiDawApp).transportController
        }

    var transportState by remember {
        mutableStateOf(transportController.transportState)
    }
    var showBpmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            transportState = transportController.transportState
            delay(50)
        }
    }

    if (showBpmDialog) {
        BpmEditDialog(
            currentBpm = transportState.tempoBpm,
            onDismiss = { showBpmDialog = false },
            onConfirm = { bpm ->
                transportController.setTempo(bpm)
                showBpmDialog = false
            },
        )
    }

    Row(
        modifier =
            modifier
                .height(48.dp)
                .background(BgPanel)
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Transport group: [Play][Stop][Record][Reset]
        TransportMiniButton(
            label = "\u25B6",
            active = transportState.playing,
            activeColor = TransportGreen,
            onClick = { if (!transportState.playing) transportController.play() },
            modifier = Modifier.size(36.dp),
        )
        TransportMiniButton(
            label = "\u25A0",
            active = false,
            activeColor = TransportRed,
            onClick = { transportController.stop() },
            modifier = Modifier.size(36.dp),
        )
        RecordButton(
            recording = transportState.recording,
            onClick = { transportController.setRecording(!transportState.recording) },
        )
        TransportMiniButton(
            label = "\u21BA",
            active = false,
            activeColor = TransportRed,
            onClick = {
                transportController.stop()
                transportController.seek(com.jujidaw.model.TransportPosition())
            },
            modifier = Modifier.size(32.dp),
        )

        GroupDivider()

        // Position: bar|beat|step
        val step = transportState.position.tick / TICKS_PER_STEP
        Text(
            text = "${transportState.position.bar + 1}|${transportState.position.beat + 1}|${step + 1}",
            color = KnobAmber,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )

        GroupDivider()

        // BPM group — tap chip to edit (dialog, 30..300); +/- nudge by 1 BPM.
        BpmChip(
            bpm = transportState.tempoBpm,
            onTap = { showBpmDialog = true },
            onNudge = { delta ->
                transportController.setTempo(
                    (transportState.tempoBpm + delta).coerceIn(MIN_BPM, MAX_BPM),
                )
            },
        )
    }
}

/**
 * Tempo edit dialog: numeric field + slider, clamped to [MIN_BPM]..[MAX_BPM].
 */
@Composable
private fun BpmEditDialog(
    currentBpm: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var text by remember { mutableStateOf("%.1f".format(currentBpm)) }
    val parsed = text.toFloatOrNull()
    val clamped = parsed?.coerceIn(MIN_BPM, MAX_BPM) ?: currentBpm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tempo (BPM)") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        text = value.filter { it.isDigit() || it == '.' }
                    },
                    singleLine = true,
                    label = { Text("$MIN_BPM..$MAX_BPM") },
                    isError = parsed == null || parsed < MIN_BPM || parsed > MAX_BPM,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = clamped,
                    onValueChange = { text = "%.1f".format(it) },
                    valueRange = MIN_BPM..MAX_BPM,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(clamped) },
                enabled = parsed != null && parsed >= MIN_BPM && parsed <= MAX_BPM,
            ) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TransportMiniButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (active) activeColor else BgGunmetal)
                .border(
                    1.dp,
                    if (active) activeColor else PanelHighlight.copy(alpha = 0.4f),
                    RoundedCornerShape(6.dp),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) Color.White else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RecordButton(
    recording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (recording) TransportRed.copy(alpha = 0.25f) else BgGunmetal)
                .border(
                    1.dp,
                    if (recording) TransportRed else TransportRed.copy(alpha = 0.6f),
                    RoundedCornerShape(8.dp),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Unambiguous red filled circle — the Record arm control.
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(TransportRed),
        )
    }
}

@Composable
private fun GroupDivider() {
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(24.dp)
                .background(PanelHighlight.copy(alpha = 0.4f)),
    )
}

@Composable
private fun BpmChip(
    bpm: Float,
    onTap: () -> Unit,
    onNudge: (Float) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(BgGunmetal)
                .border(1.dp, KnobGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .clickable(onClick = onTap)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "BPM:",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "%d".format(bpm.toInt()),
            color = KnobGreen,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        TransportMiniButton(
            label = "\u2212",
            active = false,
            activeColor = KnobGreen,
            onClick = { onNudge(-1f) },
            modifier = Modifier.size(26.dp),
        )
        TransportMiniButton(
            label = "+",
            active = false,
            activeColor = KnobGreen,
            onClick = { onNudge(1f) },
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(BgGunmetal)
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = TextMuted,
                fontSize = 14.sp,
            )
        }
    }
}
