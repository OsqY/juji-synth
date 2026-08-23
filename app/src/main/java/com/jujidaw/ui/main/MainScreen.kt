package com.jujidaw.ui.main

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jujidaw.JujiDawApp
import com.jujidaw.model.TICKS_PER_STEP
import com.jujidaw.ui.keyboard.KeyboardScreen
import com.jujidaw.ui.help.HelpScreen
import com.jujidaw.ui.mixer.MixerScreen
import com.jujidaw.ui.pads.PadsScreen
import com.jujidaw.ui.project.ProjectScreen
import com.jujidaw.ui.sequencer.SequencerScreen
import com.jujidaw.ui.synth.SynthScreen
import com.jujidaw.ui.theme.*
import com.jujidaw.ui.timeline.TimelineScreen
import com.jujidaw.ui.timeline.TimelineViewModel
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
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val tabs = MainTab.entries.toTypedArray()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val timelineViewModel: TimelineViewModel = viewModel { TimelineViewModel() }

    Scaffold(
        modifier = modifier.background(Bg0),
        containerColor = Bg0,
        bottomBar = {
            if (!isLandscape) {
                Column {
                    PersistentTransportBar(
                        modifier = Modifier.fillMaxWidth(),
                        onHelp = { showHelp = true },
                    )
                    DestinationDock(
                        tabs = tabs,
                        selectedTab = selectedTab,
                        onSelect = { selectedTab = it },
                    )
                }
            }
        },
    ) { innerPadding ->
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Bg0),
        ) {
            if (isLandscape) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .background(SurfaceContainer),
                ) {
                    // The global transport must retain a full-width touch
                    // target in landscape. Keeping it inside the 80dp rail
                    // made Play/Record/Reset effectively invisible.
                    PersistentTransportBar(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        onHelp = { showHelp = true },
                    )

                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        DestinationRail(
                            tabs = tabs,
                            selectedTab = selectedTab,
                            onSelect = { selectedTab = it },
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            when (tabs[selectedTab]) {
                                MainTab.TIMELINE -> TimelineScreen(
                                    viewModel = timelineViewModel,
                                )
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
            } else {
                Box(modifier = Modifier.weight(1f)) {
                when (tabs[selectedTab]) {
                    MainTab.TIMELINE -> TimelineScreen(viewModel = timelineViewModel)
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
        if (showHelp) {
            HelpScreen(onClose = { showHelp = false })
        }
    }
}

@Composable
private fun DestinationDock(
    tabs: Array<MainTab>,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(SurfaceContainer)
                .border(width = 1.dp, color = OutlineVariant),
    ) {
        tabs.forEachIndexed { index, tab ->
            DestinationButton(
                tab = tab,
                selected = selectedTab == index,
                vertical = false,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun DestinationRail(
    tabs: Array<MainTab>,
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(56.dp)
                .background(SurfaceContainer)
                .verticalScroll(rememberScrollState()),
    ) {
        tabs.forEachIndexed { index, tab ->
            DestinationButton(
                tab = tab,
                selected = selectedTab == index,
                vertical = true,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun DestinationButton(
    tab: MainTab,
    selected: Boolean,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .testTag("main-destination-${tab.name.lowercase()}")
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .align(if (vertical) Alignment.CenterStart else Alignment.TopCenter)
                        .then(if (vertical) Modifier.width(2.dp).fillMaxHeight() else Modifier.height(2.dp).fillMaxWidth())
                        .background(Primary),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = if (selected) null else tab.label,
                tint = if (selected) Primary else OnSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            if (selected) {
                Text(
                    text = tab.label,
                    color = Primary,
                    style = CaptionSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PersistentTransportBar(
    modifier: Modifier = Modifier,
    onHelp: () -> Unit,
) {
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
                .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
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

        GroupDivider()

        val step = transportState.position.tick / TICKS_PER_STEP
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(SurfaceContainerLow)
                    .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                    .clickable {
                        transportController.stop()
                        transportController.seek(com.jujidaw.model.TransportPosition())
                    }
                    .semantics {
                        contentDescription =
                            "Return to start. Position ${transportState.position.bar + 1}.${transportState.position.beat + 1}.${step + 1}"
                    }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            Text(
                text = "${transportState.position.bar + 1}.${transportState.position.beat + 1}.${step + 1}",
                color = Primary,
                style = MonoLarge,
            )
        }

        GroupDivider()

        BpmChip(
            bpm = transportState.tempoBpm,
            onTap = { showBpmDialog = true },
        )

        TransportMiniButton(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = "Open workflow help",
            active = false,
            activeColor = Primary,
            onClick = onHelp,
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
                ).clickable(onClick = onClick)
                .semantics {
                    contentDescription = if (recording) "Stop recording" else "Record"
                    stateDescription = if (recording) "Recording" else "Not recording"
                },
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
 * BPM chip — compact numeric readout. Tap opens the precise editor.
 */
@Composable
private fun BpmChip(
    bpm: Float,
    onTap: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onTap)
                .semantics { contentDescription = "Tempo ${bpm.toInt()} BPM. Edit tempo" }
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "%d BPM".format(bpm.toInt()),
            color = OnSurface,
            style = MonoLarge,
        )
    }
}
