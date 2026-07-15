package com.jujidaw.ui.mixer

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jujidaw.JujiDawApp
import com.jujidaw.audio.SynthEngine
import com.jujidaw.model.MidiTarget
import com.jujidaw.project.AutomationPoint
import com.jujidaw.ui.DraggableValueController
import com.jujidaw.ui.SynthKnob
import com.jujidaw.ui.sequencer.AutomationLaneOverlay
import com.jujidaw.ui.theme.*

/**
 * Mixer / FX screen.
 *
 * - 16 horizontally-scrollable channel strips + master strip.
 * - Per-channel: fader, pan, mute/solo/arm, sends A/B, level meter, insert FX.
 * - Bottom perform FX grid (8 pads).
 * - Bottom sheets for insert FX management and automation placeholder.
 *
 * Visual style: Ableton-inspired dark, compact DAW. Flat 4dp `Outline` fader track with
 * a 24dp `SurfaceContainerHighest` cap (`OnSurface` 1dp border). Level meter is a
 * `SurfaceContainerLow` bar with `StateActive` + `StateRecording` (clip) segments.
 * M/S/R badges are text badges colored by state (`StateSolo`/`StateRecording`/neutral mute).
 * Pan + send knobs use the `Secondary` (audio signal-flow) accent. Sheets sit on
 * `SurfaceContainerHigh`. No audio/MIDI/scheduling logic is touched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerScreen(
    modifier: Modifier = Modifier,
    viewModel: MixerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Wire the app-wide MIDI router into the ViewModel
    val app = remember { context.applicationContext as JujiDawApp }
    val router = remember { app.midiRouter }
    LaunchedEffect(router) {
        viewModel.midiRouter = router
        // When a MIDI learn capture happens, show a toast and exit learn mode
        router.onLearnCaptured = { ccNumber, target, _ ->
            viewModel.stopLearn()
            Toast
                .makeText(
                    context,
                    "MIDI learned: CC $ccNumber → ${target.displayLabel}",
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Bg1)
                .padding(Spacing.sm),
    ) {
        MixerToolbar(
            masterState = state.master,
            onMasterFaderChange = viewModel::setMasterFader,
            onShowPerformFx = { /* perform FX already visible below */ },
            onShowAutomation = viewModel::showAutomationSheet,
            midiLearnTarget = state.midiLearnTarget,
            onStartLearn = { /* user must long-press a specific control */ },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.sm))

        // Horizontally-scrollable strips
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                state.channels.forEachIndexed { index, ch ->
                    ChannelStrip(
                        trackIndex = index,
                        channel = ch,
                        isSelected = state.selectedChannel == index,
                        onSelect = { viewModel.selectChannel(index) },
                        onFaderChange = { viewModel.setChannelFader(index, it) },
                        onPanChange = { viewModel.setChannelPan(index, it) },
                        onMuteToggle = { viewModel.toggleMute(index) },
                        onSoloToggle = { viewModel.toggleSolo(index) },
                        onArmToggle = { viewModel.toggleArm(index) },
                        onSendAChange = { viewModel.setSendLevel(index, 0, it) },
                        onSendBChange = { viewModel.setSendLevel(index, 1, it) },
                        onShowInsertSheet = viewModel::showInsertSheet,
                        onMidiLearnFader = {
                            viewModel.startLearn(MidiTarget.ChannelFader(index))
                        },
                        onMidiLearnPan = {
                            viewModel.startLearn(MidiTarget.ChannelPan(index))
                        },
                        onMidiLearnMute = {
                            viewModel.startLearn(MidiTarget.ChannelMute(index))
                        },
                        onMidiLearnSolo = {
                            viewModel.startLearn(MidiTarget.ChannelSolo(index))
                        },
                        onMidiLearnArm = {
                            viewModel.startLearn(MidiTarget.ChannelArm(index))
                        },
                        onMidiLearnSendA = {
                            viewModel.startLearn(MidiTarget.SendLevel(index, 0))
                        },
                        onMidiLearnSendB = {
                            viewModel.startLearn(MidiTarget.SendLevel(index, 1))
                        },
                        modifier = Modifier.heightIn(min = 200.dp),
                    )
                }
                MasterStrip(
                    master = state.master,
                    onFaderChange = viewModel::setMasterFader,
                    onMidiLearn = {
                        viewModel.startLearn(MidiTarget.MasterFader)
                    },
                    modifier = Modifier.heightIn(min = 200.dp),
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        PerformFxGrid(
            activeFx = state.activePerformFx,
            onToggle = viewModel::togglePerformFx,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Insert FX bottom sheet — raised popover surface
    if (state.showInsertSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissInsertSheet,
            containerColor = SurfaceContainerHigh,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg),
        ) {
            InsertFxSheet(
                trackIndex = state.selectedChannel,
                channel = state.channels[state.selectedChannel],
                onAddEffect = viewModel::addInsertEffect,
                onRemoveEffect = viewModel::removeInsertEffect,
                onToggleBypass = viewModel::toggleInsertBypass,
                onReorder = viewModel::reorderInsert,
            )
        }
    }

    // Automation placeholder sheet — raised popover surface
    if (state.showAutomationSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissAutomationSheet,
            containerColor = SurfaceContainerHigh,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg),
        ) {
            AutomationMixerContent(
                selectedParam = state.selectedAutomationParam,
                automationPoints = state.automationPoints,
                onParamSelect = viewModel::selectAutomationParam,
                onPointsUpdate = viewModel::updateAutomationPoints,
                onDismiss = viewModel::dismissAutomationSheet,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TOOLBAR
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MixerToolbar(
    masterState: MasterState,
    onMasterFaderChange: (Float) -> Unit,
    onShowPerformFx: () -> Unit,
    onShowAutomation: () -> Unit,
    midiLearnTarget: MidiTarget?,
    onStartLearn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(RadiusLg))
                .background(SurfaceContainer)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusLg))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "MIXER",
            color = Primary,
            style = DisplaySmall,
        )

        // Mini master fader + value
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "MST",
                color = TextSecondary,
                style = LabelSmall,
            )
            VerticalFader(
                value = masterState.faderDb,
                onValueChange = onMasterFaderChange,
                modifier =
                    Modifier
                        .width(44.dp) // hit width bumped 36 -> 44 (cap stays centered)
                        .height(80.dp),
            )
            Text(
                text = "%.1f".format(masterState.faderDb),
                color = TextPrimary,
                style = CaptionSmall,
                modifier = Modifier.width(32.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // MIDI learn indicator
            val learnActive = midiLearnTarget != null
            Box(
                modifier =
                    Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(if (learnActive) MidiLearn.copy(alpha = 0.3f) else SurfaceContainerLow)
                        .border(
                            1.dp,
                            if (learnActive) MidiLearn else OutlineVariant,
                            RoundedCornerShape(RadiusMd),
                        ).clickable {
                            // A long-press on a specific control starts learn;
                            // the toolbar button toggles display of learnable hints.
                        }.padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (learnActive) "LEARN ${midiLearnTarget?.displayLabel.orEmpty()}" else "MIDI",
                    color = if (learnActive) MidiLearn else OnSurface,
                    style = CaptionSmall,
                    maxLines = 1,
                )
            }
            ToolbarActionButton("Auto", onClick = onShowAutomation)
        }
    }
}

@Composable
private fun ToolbarActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(RadiusMd))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusMd))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = OnSurface,
            style = LabelSmall,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// CHANNEL STRIP
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ChannelStrip(
    trackIndex: Int,
    channel: ChannelState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onFaderChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onArmToggle: () -> Unit,
    onSendAChange: (Float) -> Unit,
    onSendBChange: (Float) -> Unit,
    onShowInsertSheet: () -> Unit,
    onMidiLearnFader: (() -> Unit)? = null,
    onMidiLearnPan: (() -> Unit)? = null,
    onMidiLearnMute: (() -> Unit)? = null,
    onMidiLearnSolo: (() -> Unit)? = null,
    onMidiLearnArm: (() -> Unit)? = null,
    onMidiLearnSendA: (() -> Unit)? = null,
    onMidiLearnSendB: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(76.dp)
                .clip(RoundedCornerShape(RadiusLg))
                .background(if (isSelected) SurfaceContainerHighest else SurfaceContainer)
                .border(
                    width = 1.dp,
                    color = if (isSelected) Primary else OutlineVariant,
                    shape = RoundedCornerShape(RadiusLg),
                ).clickable(onClick = onSelect)
                .padding(horizontal = Spacing.sm, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Track label
        Text(
            text = "T${trackIndex + 1}",
            color = if (isSelected) Primary else OnSurface,
            style = LabelSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(Spacing.sm))

        // Meter + Fader
        Row(
            modifier = Modifier.height(160.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            LevelMeter(
                level = channel.level,
                modifier =
                    Modifier
                        .width(10.dp)
                        .fillMaxHeight(),
            )
            Spacer(Modifier.width(Spacing.xs))
            VerticalFader(
                value = channel.faderDb,
                onValueChange = onFaderChange,
                onMidiLearn = onMidiLearnFader,
                modifier =
                    Modifier
                        .width(44.dp)
                        .fillMaxHeight(),
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        // Fader value readout
        Text(
            text = "%.1f".format(channel.faderDb),
            color = TextSecondary,
            style = CaptionSmall,
        )

        Spacer(Modifier.height(Spacing.sm))

        // Mute / Solo / Arm — text badges colored by state
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // Mute: neutral muted treatment (mute never invents a new color)
            SmallToggle("M", channel.mute, onMuteToggle, OnSurfaceVariant, onMidiLearn = onMidiLearnMute)
            // Solo: warm amber state
            SmallToggle("S", channel.solo, onSoloToggle, StateSolo, onMidiLearn = onMidiLearnSolo)
            // Record arm: red state
            SmallToggle("R", channel.arm, onArmToggle, StateRecording, onMidiLearn = onMidiLearnArm)
        }

        Spacer(Modifier.height(Spacing.sm))

        // Pan knob — audio signal-flow accent
        SynthKnob(
            value = (channel.pan + 1f) / 2f,
            onValueChange = { onPanChange(it * 2f - 1f) },
            label = "Pan",
            valueDisplay = "%.0f".format(channel.pan * 100),
            accentColor = Secondary,
            size = KnobDefaultSize,
        )

        Spacer(Modifier.height(Spacing.sm))

        // Send A / B knobs — audio signal-flow accent
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SynthKnob(
                value = channel.sendA.coerceIn(0f, 1f),
                onValueChange = onSendAChange,
                label = "A",
                valueDisplay = "%.0f".format(channel.sendA * 100),
                accentColor = Secondary,
                size = 40.dp,
            )
            SynthKnob(
                value = channel.sendB.coerceIn(0f, 1f),
                onValueChange = onSendBChange,
                label = "B",
                valueDisplay = "%.0f".format(channel.sendB * 100),
                accentColor = Secondary,
                size = 40.dp,
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        // Insert FX mini indicators
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            channel.inserts.forEachIndexed { slotIdx, slot ->
                val color =
                    when {
                        slot.type == SynthEngine.EffectType.None -> SurfaceContainerLow
                        slot.bypass -> TextDisabled
                        else -> Secondary
                    }
                val borderColor =
                    when {
                        slot.type == SynthEngine.EffectType.None -> OutlineVariant
                        slot.bypass -> TextDisabled
                        else -> Secondary
                    }
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(RadiusXs))
                            .background(color)
                            .border(
                                1.dp,
                                borderColor,
                                RoundedCornerShape(RadiusXs),
                            ).clickable {
                                onSelect()
                                onShowInsertSheet()
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    if (slot.type != SynthEngine.EffectType.None) {
                        Text(
                            text =
                                slot.type.name
                                    .first()
                                    .toString(),
                            color = if (slot.bypass) OnSurface else OnSecondary,
                            style = CaptionSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// MASTER STRIP
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MasterStrip(
    master: MasterState,
    onFaderChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onMidiLearn: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .width(88.dp)
                .clip(RoundedCornerShape(RadiusLg))
                .background(SurfaceContainer)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusLg))
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "MST",
            color = OnSurface,
            style = LabelSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.height(160.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            LevelMeter(
                level = master.level,
                modifier =
                    Modifier
                        .width(12.dp)
                        .fillMaxHeight(),
            )
            Spacer(Modifier.width(Spacing.sm))
            VerticalFader(
                value = master.faderDb,
                onValueChange = onFaderChange,
                onMidiLearn = onMidiLearn,
                modifier =
                    Modifier
                        .width(48.dp)
                        .fillMaxHeight(),
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = "%.1f".format(master.faderDb),
            color = TextPrimary,
            style = CaptionSmall,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// VERTICAL FADER — flat line track + 24dp cap
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun VerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = -60f..12f,
    onMidiLearn: (() -> Unit)? = null,
) {
    val min = valueRange.start
    val max = valueRange.endInclusive
    val range = max - min

    val density = LocalDensity.current
    val touchSlopPx = with(density) { DraggableValueController.touchSlop.toPx() }

    Box(
        modifier =
            modifier
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downY = down.position.y
                            var dragging = false
                            var longPressTriggered = false
                            var event: PointerEvent

                            do {
                                event = awaitPointerEvent()
                                val change = event.changes.first()
                                if (!dragging) {
                                    val distance =
                                        (change.position - down.position).getDistance()
                                    if (distance > touchSlopPx) {
                                        dragging = true
                                    } else if (
                                        distance > touchSlopPx * 2 &&
                                        !longPressTriggered
                                    ) {
                                        longPressTriggered = true
                                        onMidiLearn?.invoke()
                                    }
                                }
                                if (dragging) {
                                    val fractionDelta = -change.position.y / size.height
                                    val newValue =
                                        DraggableValueController.quantizeDb(
                                            (value + fractionDelta * range)
                                                .coerceIn(min, max),
                                        )
                                    onValueChange(newValue)
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })

                            if (!dragging && !longPressTriggered) {
                                val fraction =
                                    1f - (downY / size.height).coerceIn(0f, 1f)
                                onValueChange(
                                    DraggableValueController.quantizeDb(
                                        min + fraction * range,
                                    ),
                                )
                            }
                        }
                    }
                },
    ) {
        val thumbHeight = 24.dp
        val fraction = ((value - min) / range).coerceIn(0f, 1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val trackW = FaderTrackWidth.toPx() // 4dp flat Outline track
            val thumbH = thumbHeight.toPx()
            val halfThumb = thumbH / 2f
            val topY = halfThumb
            val bottomY = size.height - halfThumb

            // Flat track — single Outline line, value shown by cap position
            drawLine(
                color = Outline,
                start = Offset(cx, topY),
                end = Offset(cx, bottomY),
                strokeWidth = trackW,
                cap = StrokeCap.Round,
            )

            val thumbY = bottomY - (bottomY - topY) * fraction
            val thumbW = FaderCapWidth.toPx() // 24dp cap
            val thumbLeft = cx - thumbW / 2f
            val thumbTop = thumbY - halfThumb

            // Cap fill — SurfaceContainerHighest
            drawRoundRect(
                color = SurfaceContainerHighest,
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(RadiusSm.toPx(), RadiusSm.toPx()),
            )
            // Cap border — OnSurface 1dp
            drawRoundRect(
                color = OnSurface,
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(RadiusSm.toPx(), RadiusSm.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )

            // Cap grip line
            drawLine(
                color = OnSurfaceVariant,
                start = Offset(cx - 6.dp.toPx(), thumbY),
                end = Offset(cx + 6.dp.toPx(), thumbY),
                strokeWidth = 1.5f,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// LEVEL METER — SurfaceContainerLow bar + StateActive/StateRecording segments
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LevelMeter(
    level: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(RadiusSm))) {
        val w = size.width
        val h = size.height

        // Background bar
        drawRect(color = SurfaceContainerLow)

        val levelCoerced = level.coerceIn(0f, 1f)
        // Top ~12% is the clip zone
        val clipThreshold = 0.88f

        // Normal level segment — StateActive
        val activeH = h * levelCoerced.coerceAtMost(clipThreshold)
        if (activeH > 0f) {
            drawRect(
                color = StateActive,
                topLeft = Offset(0f, h - activeH),
                size = Size(w, activeH),
            )
        }

        // Clip segment — StateRecording (only when level reaches the clip zone)
        if (levelCoerced > clipThreshold) {
            val clipH = h * (levelCoerced - clipThreshold)
            drawRect(
                color = StateRecording,
                topLeft = Offset(0f, h * (1f - levelCoerced)),
                size = Size(w, clipH),
            )
        }

        // LED segment dividers (subtle)
        val segCount = 10
        val segH = h / segCount
        for (i in 1 until segCount) {
            val y = i * segH
            drawLine(
                color = OutlineVariant,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// SMALL TOGGLE (M / S / R) — text badges colored by state
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SmallToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onMidiLearn: (() -> Unit)? = null,
) {
    val bg = if (checked) activeColor.copy(alpha = 0.25f) else Color.Transparent
    val bd = if (checked) activeColor else OutlineVariant
    val fg = if (checked) activeColor else OnSurface
    Box(
        modifier =
            modifier
                .size(width = 32.dp, height = 28.dp)
                .clip(RoundedCornerShape(RadiusSm))
                .background(bg)
                .border(1.dp, bd, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            style = LabelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// PERFORM FX GRID
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PerformFxGrid(
    activeFx: Set<PerformFxType>,
    onToggle: (PerformFxType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(RadiusLg))
                .background(SurfaceContainer)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusLg))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val rows = PerformFxType.entries.chunked(4)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { fx ->
                    val active = fx in activeFx
                    PerformFxButton(
                        label = fx.label,
                        active = active,
                        onClick = { onToggle(fx) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PerformFxButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(horizontal = Spacing.xs)
                .height(36.dp)
                .clip(RoundedCornerShape(RadiusMd))
                .background(if (active) Primary.copy(alpha = 0.15f) else SurfaceContainerLow)
                .border(
                    1.dp,
                    if (active) Primary else OutlineVariant,
                    RoundedCornerShape(RadiusMd),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) Primary else TextSecondary,
            style = CaptionSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// INSERT FX BOTTOM SHEET
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun InsertFxSheet(
    trackIndex: Int,
    channel: ChannelState,
    onAddEffect: (Int, Int, SynthEngine.EffectType) -> Unit,
    onRemoveEffect: (Int, Int) -> Unit,
    onToggleBypass: (Int, Int) -> Unit,
    onReorder: (Int, Int, Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = "Inserts – Track ${trackIndex + 1}",
            color = OnSurface,
            style = TitleLarge,
        )

        channel.inserts.forEachIndexed { slot, insert ->
            InsertSlotRow(
                slot = slot,
                insert = insert,
                onAdd = { type -> onAddEffect(trackIndex, slot, type) },
                onRemove = { onRemoveEffect(trackIndex, slot) },
                onBypass = { onToggleBypass(trackIndex, slot) },
                onMoveUp =
                    if (slot > 0) {
                        { onReorder(trackIndex, slot, slot - 1) }
                    } else {
                        null
                    },
                onMoveDown =
                    if (slot < 3) {
                        { onReorder(trackIndex, slot, slot + 1) }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun InsertSlotRow(
    slot: Int,
    insert: InsertSlot,
    onAdd: (SynthEngine.EffectType) -> Unit,
    onRemove: () -> Unit,
    onBypass: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "${slot + 1}",
            color = TextSecondary,
            style = CaptionSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp),
        )

        if (insert.type == SynthEngine.EffectType.None) {
            // Empty slot – add button
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(RadiusSm))
                        .background(SurfaceContainerLow)
                        .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                        .clickable { expanded = true }
                        .padding(horizontal = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add FX",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Add FX", color = OnSurfaceVariant, style = CaptionSmall)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = SurfaceContainerHigh,
            ) {
                SynthEngine.EffectType.entries
                    .filter { it != SynthEngine.EffectType.None }
                    .forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name, color = OnSurface, style = BodyMedium) },
                            onClick = {
                                onAdd(type)
                                expanded = false
                            },
                        )
                    }
            }
        } else {
            // Occupied slot
            Text(
                text = insert.type.name,
                color = if (insert.bypass) TextDisabled else OnSurface,
                style = BodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )

            // Bypass toggle — muted/neutral state
            SmallToggle(
                label = "Byp",
                checked = insert.bypass,
                onCheckedChange = onBypass,
                activeColor = OnSurfaceVariant,
            )

            // Remove
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(RadiusXs))
                        .background(SurfaceContainerLow)
                        .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusXs))
                        .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Reorder arrows
            if (onMoveUp != null) {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(RadiusXs))
                            .background(SurfaceContainerLow)
                            .clickable(onClick = onMoveUp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (onMoveDown != null) {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(RadiusXs))
                            .background(SurfaceContainerLow)
                            .clickable(onClick = onMoveDown),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// AUTOMATION PLACEHOLDER SHEET
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AutomationMixerContent(
    selectedParam: String?,
    automationPoints: List<AutomationPoint>,
    onParamSelect: (String?) -> Unit,
    onPointsUpdate: (List<AutomationPoint>) -> Unit,
    onDismiss: () -> Unit,
) {
    val paramList =
        listOf(
            "fader_0" to "Track 1 Fader",
            "pan_0" to "Track 1 Pan",
            "cutoff_0" to "Cutoff",
            "res_0" to "Resonance",
            "master" to "Master Vol",
        )

    var chosen by remember { mutableStateOf(selectedParam) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Automation",
            color = OnSurface,
            style = TitleLarge,
        )

        // Param chips — selected = Primary (selection convention)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            paramList.forEach { (id, label) ->
                val isSelected = chosen == id
                Box(
                    modifier =
                        Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(RadiusMd))
                            .background(if (isSelected) Primary.copy(alpha = 0.12f) else SurfaceContainerLow)
                            .border(
                                1.dp,
                                if (isSelected) Primary else OutlineVariant,
                                RoundedCornerShape(RadiusMd),
                            ).clickable {
                                chosen = id
                                onParamSelect(id)
                            }.padding(horizontal = Spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Primary else TextSecondary,
                        style = CaptionSmall,
                        maxLines = 1,
                    )
                }
            }
        }

        if (chosen != null) {
            AutomationLaneOverlay(
                points = automationPoints,
                onPointsChange = onPointsUpdate,
                label = chosen!!,
                numSteps = 64,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Spacing.md))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(RadiusLg))
                    .background(SurfaceContainerLow)
                    .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusLg))
                    .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("Close", color = OnSurface, style = LabelSmall)
        }
    }
}
