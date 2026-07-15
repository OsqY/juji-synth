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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Stop
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
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
                .height(TransportHeight)
                .background(SurfaceContainer)
                .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Transport group: [Play][Stop][Record][Reset] — flat Material Symbols Outlined icons
        // on SurfaceContainer. Active states use Primary/StateActive/StateRecording per plan §1.
        TransportMiniButton(
            icon = Icons.Outlined.PlayArrow,
            contentDescription = "Play",
            active = transportState.playing,
            activeColor = StateActive,
            onClick = { if (!transportState.playing) transportController.play() },
        )
        TransportMiniButton(
            icon = Icons.Outlined.Stop,
            contentDescription = "Stop",
            active = false,
            activeColor = Primary,
            onClick = { transportController.stop() },
        )
        RecordButton(
            recording = transportState.recording,
            onClick = { transportController.setRecording(!transportState.recording) },
        )
        TransportMiniButton(
            icon = Icons.Outlined.RestartAlt,
            contentDescription = "Return to start",
            active = false,
            activeColor = Primary,
            onClick = {
                transportController.stop()
                transportController.seek(com.jujidaw.model.TransportPosition())
            },
        )

        GroupDivider()

        // Position chip — bar|beat|step readout, monoLarge in Primary per plan §1.
        val step = transportState.position.tick / TICKS_PER_STEP
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(SurfaceContainerLow)
                    .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            Text(
                text = "${transportState.position.bar + 1}|${transportState.position.beat + 1}|${step + 1}",
                color = Primary,
                style = MonoLarge,
            )
        }

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
 * Tempo edit dialog — SurfaceContainerHigh container, RadiusLg corner, xl padding,
 * Space Grotesk title (TitleLarge). Numeric field + slider, clamped to [MIN_BPM]..[MAX_BPM].
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
    val canConfirm = parsed != null && parsed >= MIN_BPM && parsed <= MAX_BPM

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = SurfaceContainerHigh,
            shape = RoundedCornerShape(RadiusLg),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(Spacing.xl)
                        .width(IntrinsicSize.Max),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "Tempo (BPM)",
                    color = OnSurface,
                    style = TitleLarge,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        text = value.filter { it.isDigit() || it == '.' }
                    },
                    singleLine = true,
                    label = { Text("$MIN_BPM..$MAX_BPM") },
                    isError = !canConfirm,
                )
                Slider(
                    value = clamped,
                    onValueChange = { text = "%.1f".format(it) },
                    valueRange = MIN_BPM..MAX_BPM,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = OnSurface)
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    TextButton(
                        onClick = { onConfirm(clamped) },
                        enabled = canConfirm,
                    ) {
                        Text("Set", color = Primary)
                    }
                }
            }
        }
    }
}

/**
 * Flat transport icon chip on the 48dp transport strip.
 *
 * 44dp touch rect (TouchTargetMin) with a 36dp visual inner stock so the icon stays
 * compact per plan §1 while satisfying the 44dp minimum interactive target. Inactive
 * = transparent + OutlineVariant border + OnSurface icon; active = activeColor 15%
 * tinted fill + activeColor border + activeColor icon. Replaces the legacy unicode-glyph
 * transport buttons (play / stop / restart / minus / plus) with Material Symbols Outlined icons.
 */
@Composable
private fun TransportMiniButton(
    icon: ImageVector,
    contentDescription: String?,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(TouchTargetMin)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(if (active) activeColor.copy(alpha = 0.15f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) activeColor else OutlineVariant,
                        RoundedCornerShape(RadiusSm),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) activeColor else OnSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Record-arm control. 44dp touch rect (TouchTargetMin) on the 48dp transport strip.
 *
 * Per plan §1: "recording = StateRecording filled circle". At rest the control shows
 * `radio_button_checked` (Material Symbols Outlined) tinted StateRecording; when armed
 * (recording=true) the visual switches to a StateRecording-filled inner circle on a
 * StateRecording 15% tinted chip so the record state reads from across the room.
 */
@Composable
private fun RecordButton(
    recording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(if (recording) StateRecording.copy(alpha = 0.15f) else Color.Transparent)
                .border(
                    1.dp,
                    if (recording) StateRecording else OutlineVariant,
                    RoundedCornerShape(RadiusSm),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            // Record = filled circle (StateRecording). Pulsing animation deferred to Phase 5.
            Box(
                modifier =
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(StateRecording),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.RadioButtonChecked,
                contentDescription = "Record",
                tint = StateRecording,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun GroupDivider() {
    // 1dp × 20dp OutlineVariant per plan §1: subtle structural separator between
    // transport groups (Play/Stop/Record/Reset | Position | BPM).
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(20.dp)
                .background(OutlineVariant),
    )
}

/**
 * BPM chip — monoLarge numeric readout + LabelSmall "BPM" tag on SurfaceContainerLow,
 * tap to open BpmEditDialog and nudge with Material Symbols Outlined `add`/`remove`
 * icons (replaces legacy unicode `−`/`+` per plan §3 step 1). Nudge buttons reuse the
 * 44dp-touch TransportMiniButton shape.
 */
@Composable
private fun BpmChip(
    bpm: Float,
    onTap: () -> Unit,
    onNudge: (Float) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onTap)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "BPM",
            color = TextSecondary,
            style = LabelSmall,
        )
        Text(
            text = "%d".format(bpm.toInt()),
            color = OnSurface,
            style = MonoLarge,
        )
        TransportMiniButton(
            icon = Icons.Outlined.Remove,
            contentDescription = "Decrease BPM",
            active = false,
            activeColor = Primary,
            onClick = { onNudge(-1f) },
        )
        TransportMiniButton(
            icon = Icons.Outlined.Add,
            contentDescription = "Increase BPM",
            active = false,
            activeColor = Primary,
            onClick = { onNudge(1f) },
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
