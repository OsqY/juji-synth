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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Phone-first: strips are fixed-width and scroll horizontally; faders are tall
 * enough for finger control.
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
                .background(BgGunmetal)
                .padding(4.dp),
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

        Spacer(Modifier.height(4.dp))

        // Horizontally-scrollable strips
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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

        Spacer(Modifier.height(4.dp))

        PerformFxGrid(
            activeFx = state.activePerformFx,
            onToggle = viewModel::togglePerformFx,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Insert FX bottom sheet
    if (state.showInsertSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissInsertSheet,
            containerColor = BgPanel,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
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

    // Automation placeholder sheet
    if (state.showAutomationSheet) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissAutomationSheet,
            containerColor = BgPanel,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
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
                .clip(RoundedCornerShape(8.dp))
                .background(BgPanel)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "MIXER",
            color = KnobAmber,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        // Mini master fader + value
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "MST",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            VerticalFader(
                value = masterState.faderDb,
                onValueChange = onMasterFaderChange,
                modifier =
                    Modifier
                        .width(36.dp)
                        .height(80.dp),
            )
            Text(
                text = "%.1f".format(masterState.faderDb),
                color = TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // MIDI learn indicator
            val learnActive = midiLearnTarget != null
            Box(
                modifier =
                    Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (learnActive) KnobAmber.copy(alpha = 0.3f) else BgGunmetal)
                        .border(
                            1.5.dp,
                            if (learnActive) KnobAmber else PanelHighlight.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp),
                        ).clickable {
                            // A long-press on a specific control starts learn;
                            // the toolbar button toggles display of learnable hints.
                        }.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (learnActive) "LEARN ${midiLearnTarget?.displayLabel.orEmpty()}" else "MIDI",
                    color = if (learnActive) KnobAmber else TextPrimary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
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
                .clip(RoundedCornerShape(6.dp))
                .background(BgGunmetal)
                .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
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
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) BgPanel else BgGunmetal)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) KnobAmber else PanelHighlight.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                ).clickable(onClick = onSelect)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Track label
        Text(
            text = "T${trackIndex + 1}",
            color = if (isSelected) KnobAmber else TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(4.dp))

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
            Spacer(Modifier.width(4.dp))
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

        Spacer(Modifier.height(4.dp))

        // Fader value readout
        Text(
            text = "%.1f".format(channel.faderDb),
            color = TextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(4.dp))

        // Mute / Solo / Arm
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SmallToggle("M", channel.mute, onMuteToggle, KnobAmber, onMidiLearn = onMidiLearnMute)
            SmallToggle("S", channel.solo, onSoloToggle, KnobGreen, onMidiLearn = onMidiLearnSolo)
            SmallToggle("R", channel.arm, onArmToggle, KnobRed, onMidiLearn = onMidiLearnArm)
        }

        Spacer(Modifier.height(4.dp))

        // Pan knob
        SynthKnob(
            value = (channel.pan + 1f) / 2f,
            onValueChange = { onPanChange(it * 2f - 1f) },
            label = "Pan",
            valueDisplay = "%.0f".format(channel.pan * 100),
            accentColor = KnobCyan,
            size = 48.dp,
        )

        Spacer(Modifier.height(4.dp))

        // Send A / B knobs
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SynthKnob(
                value = channel.sendA.coerceIn(0f, 1f),
                onValueChange = onSendAChange,
                label = "A",
                valueDisplay = "%.0f".format(channel.sendA * 100),
                accentColor = KnobOrange,
                size = 40.dp,
            )
            SynthKnob(
                value = channel.sendB.coerceIn(0f, 1f),
                onValueChange = onSendBChange,
                label = "B",
                valueDisplay = "%.0f".format(channel.sendB * 100),
                accentColor = KnobOrange,
                size = 40.dp,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Insert FX mini indicators
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            channel.inserts.forEachIndexed { slotIdx, slot ->
                val color =
                    when {
                        slot.type == SynthEngine.EffectType.None -> BgGunmetal
                        slot.bypass -> TextMuted
                        else -> KnobCyan
                    }
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                            .border(
                                1.dp,
                                if (slot.type != SynthEngine.EffectType.None) {
                                    if (slot.bypass) TextMuted else KnobCyan
                                } else {
                                    PanelHighlight.copy(alpha = 0.3f)
                                },
                                RoundedCornerShape(3.dp),
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
                            color = Color.Black,
                            fontSize = 7.sp,
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
                .clip(RoundedCornerShape(8.dp))
                .background(BgPanel)
                .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "MST",
            color = KnobAmber,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(4.dp))

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
            Spacer(Modifier.width(6.dp))
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

        Spacer(Modifier.height(4.dp))

        Text(
            text = "%.1f".format(master.faderDb),
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// VERTICAL FADER
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
        val thumbHeight = 28.dp
        val fraction = ((value - min) / range).coerceIn(0f, 1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val trackW = 6.dp.toPx()
            val thumbH = thumbHeight.toPx()
            val halfThumb = thumbH / 2f
            val topY = halfThumb
            val bottomY = size.height - halfThumb

            // Track background
            drawLine(
                color = PanelHighlight.copy(alpha = 0.3f),
                start = Offset(cx, topY),
                end = Offset(cx, bottomY),
                strokeWidth = trackW,
                cap = StrokeCap.Round,
            )

            // Active track
            val thumbY = bottomY - (bottomY - topY) * fraction
            drawLine(
                color = KnobAmber,
                start = Offset(cx, thumbY),
                end = Offset(cx, bottomY),
                strokeWidth = trackW,
                cap = StrokeCap.Round,
            )

            // Thumb body
            val thumbW = 32.dp.toPx()
            val thumbLeft = cx - thumbW / 2f
            val thumbTop = thumbY - halfThumb
            drawRoundRect(
                color = BgPanel,
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            drawRoundRect(
                color = KnobAmber,
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style =
                    androidx.compose.ui.graphics.drawscope
                        .Stroke(width = 2.dp.toPx()),
            )

            // Thumb center line
            drawLine(
                color = TextSecondary,
                start = Offset(cx - 6.dp.toPx(), thumbY),
                end = Offset(cx + 6.dp.toPx(), thumbY),
                strokeWidth = 1.5f,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// LEVEL METER
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LevelMeter(
    level: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        val w = size.width
        val h = size.height

        // Background
        drawRect(color = BgGunmetal)

        // Bar
        val barH = h * level.coerceIn(0f, 1f)
        val gradient =
            Brush.verticalGradient(
                colors = listOf(KnobGreen, KnobAmber, KnobRed),
                startY = h,
                endY = 0f,
            )
        drawRect(
            brush = gradient,
            topLeft = Offset(0f, h - barH),
            size = Size(w, barH),
        )

        // LED segment dividers
        val segCount = 10
        val segH = h / segCount
        for (i in 1 until segCount) {
            val y = i * segH
            drawLine(
                color = BgPanel,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.5f,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// SMALL TOGGLE (M / S / R)
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
    Box(
        modifier =
            modifier
                .size(width = 32.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) activeColor else BgPanel)
                .border(
                    1.dp,
                    if (checked) activeColor else PanelHighlight.copy(alpha = 0.4f),
                    RoundedCornerShape(6.dp),
                ).clickable(onClick = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (checked) Color.Black else TextSecondary,
            fontSize = 10.sp,
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
                .clip(RoundedCornerShape(8.dp))
                .background(BgPanel)
                .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                .padding(horizontal = 2.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (active) KnobAmber.copy(alpha = 0.25f) else BgGunmetal)
                .border(
                    1.5.dp,
                    if (active) KnobAmber else PanelHighlight.copy(alpha = 0.4f),
                    RoundedCornerShape(6.dp),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) KnobAmber else TextSecondary,
            fontSize = 9.sp,
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
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Inserts – Track ${trackIndex + 1}",
            color = KnobCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${slot + 1}",
            color = TextSecondary,
            fontSize = 12.sp,
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
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgGunmetal)
                        .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("+ Add FX", color = TextMuted, fontSize = 10.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = BgPanel,
            ) {
                SynthEngine.EffectType.entries
                    .filter { it != SynthEngine.EffectType.None }
                    .forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name, color = TextPrimary, fontSize = 12.sp) },
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
                color = if (insert.bypass) TextMuted else TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )

            // Bypass toggle
            SmallToggle(
                label = "Byp",
                checked = insert.bypass,
                onCheckedChange = onBypass,
                activeColor = KnobOrange,
            )

            // Remove
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BgGunmetal)
                        .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text("X", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            // Reorder arrows
            if (onMoveUp != null) {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BgGunmetal)
                            .clickable(onClick = onMoveUp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↑", color = TextSecondary, fontSize = 10.sp)
                }
            }
            if (onMoveDown != null) {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BgGunmetal)
                            .clickable(onClick = onMoveDown),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↓", color = TextSecondary, fontSize = 10.sp)
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
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Automation",
            color = KnobCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        // Param chips
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            paramList.forEach { (id, label) ->
                val isSelected = chosen == id
                Box(
                    modifier =
                        Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) KnobCyan.copy(alpha = 0.25f) else BgGunmetal)
                            .border(
                                1.dp,
                                if (isSelected) KnobCyan else PanelHighlight.copy(alpha = 0.4f),
                                RoundedCornerShape(6.dp),
                            ).clickable {
                                chosen = id
                                onParamSelect(id)
                            }.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) KnobCyan else TextSecondary,
                        fontSize = 9.sp,
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

        Spacer(Modifier.height(8.dp))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgGunmetal)
                    .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("Close", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
