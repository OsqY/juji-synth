package com.jujidaw.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jujidaw.audio.SynthEngine
import com.jujidaw.model.AudioClip
import com.jujidaw.model.AutomationCurve
import com.jujidaw.model.Clip
import com.jujidaw.model.PPQ
import com.jujidaw.model.Pattern
import com.jujidaw.model.PatternClip
import com.jujidaw.model.TICKS_PER_STEP
import com.jujidaw.model.TransportPosition
import com.jujidaw.model.TransportState
import com.jujidaw.ui.LcdDisplay
import com.jujidaw.ui.theme.*

/**
 * Arrangement Timeline screen.
 *
 * Displays 16 track lanes with pattern/audio clips, a transport strip,
 * horizontal zoom/scroll, snap-to-grid editing, and an automation lane.
 *
 * TODO(integrator):
 *  - Wire track mute/solo/arm to C++ mixer when JNI API is added.
 *  - Replace audio-clip waveform placeholder with real decoded PCM overview.
 *  - Add a dedicated delete gesture for automation points (currently uses
 *    "DEL LAST" button in lane header).
 *  - Provide a shared [TransportController] instance if navigation switches
 *    between screens.
 */
@Composable
fun TimelineScreen(modifier: Modifier = Modifier) {
    val viewModel: TimelineViewModel = viewModel { TimelineViewModel() }
    val transport = viewModel.transportState.collectAsState().value
    val arrangement = viewModel.arrangement.collectAsState().value
    val patterns = viewModel.patterns.collectAsState().value
    val zoom = viewModel.zoom.collectAsState().value
    val snap = viewModel.snap.collectAsState().value
    val selectedTrack = viewModel.selectedTrack.collectAsState().value
    val selectedParam = viewModel.selectedAutomationParam.collectAsState().value
    val automationPoints = viewModel.automationPoints.collectAsState().value
    val trackStates = viewModel.trackStates.collectAsState().value
    val selectedPatternId = viewModel.selectedPatternId.collectAsState().value

    var followPlayhead by remember { mutableStateOf(true) }

    // Timeline mode disables the internal step sequencer
    DisposableEffect(Unit) {
        SynthEngine.setSequencerEnabled(false)
        onDispose { SynthEngine.setSequencerEnabled(true) }
    }

    val density = LocalDensity.current
    val barWidthPx = with(density) { (96.dp * zoom).toPx() }
    val tickWidthPx = barWidthPx / (PPQ * 4f)
    val totalBars = 200
    val totalWidthPx = totalBars * barWidthPx
    val trackHeightPx = with(density) { 56.dp.toPx() }
    val headerWidth = 72.dp
    val rulerHeight = 24.dp

    var scrollX by remember { mutableStateOf(0f) }
    val viewportWidthPx = with(density) { 300.dp.toPx() }
    val maxScrollX = (totalWidthPx - viewportWidthPx).coerceAtLeast(0f)

    val playheadPx = transport.position.toTicks() * tickWidthPx
    LaunchedEffect(playheadPx, transport.playing, followPlayhead) {
        if (transport.playing && followPlayhead) {
            val target = (playheadPx - viewportWidthPx * 0.3f).coerceIn(0f, maxScrollX)
            scrollX = target
        }
    }

    var draggedClipId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().background(BgGunmetal).statusBarsPadding()) {
        TransportStrip(
            transport = transport,
            snap = snap,
            zoom = zoom,
            onToggleLoop = { viewModel.toggleLoop() },
            onTogglePunch = { viewModel.togglePunch() },
            onLoopStart = { viewModel.setLoopStartToPlayhead() },
            onLoopEnd = { viewModel.setLoopEndToPlayhead() },
            onPunchIn = { viewModel.setPunchInToPlayhead() },
            onPunchOut = { viewModel.setPunchOutToPlayhead() },
            onBpmChange = { viewModel.setTempo(it) },
            onNudge = { viewModel.nudgePlayhead(it) },
            onSnapChange = { viewModel.setSnap(it) },
            onZoomChange = { viewModel.setZoom(it) },
        )

        Spacer(Modifier.height(2.dp))

        // Pattern selector row
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(BgPanel)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "PATTERN",
                color = TextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 4.dp),
            )
            for (i in 0 until 16) {
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (selectedPatternId == i) KnobAmber else BgGunmetal)
                            .border(
                                1.dp,
                                if (selectedPatternId == i) KnobAmber else PanelHighlight.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp),
                            ).clickable { viewModel.selectPattern(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${i + 1}",
                        color = if (selectedPatternId == i) Color.Black else TextPrimary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Pad selector strip: place a pad on the selected track at the playhead
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "PADS",
                color = TextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 4.dp),
            )
            for (i in 0 until 16) {
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BgGunmetal)
                            .border(1.dp, PanelHighlight.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .clickable {
                                val playheadTick = transport.position.toTicks()
                                viewModel.addPadClip(selectedTrack, playheadTick, i)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${i + 1}",
                        color = TextPrimary,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        // Main area: track headers + timeline
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Track headers
            Column(
                modifier =
                    Modifier
                        .width(headerWidth)
                        .fillMaxHeight()
                        .background(BgPanel),
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(rulerHeight).background(BgGunmetal))
                for (i in 0 until 16) {
                    key(i) {
                        TrackHeader(
                            index = i,
                            state = trackStates.getOrElse(i) { TimelineViewModel.TrackUiState() },
                            isSelected = selectedTrack == i,
                            onSelect = { viewModel.selectTrack(i) },
                            onMute = { viewModel.toggleMuteTrack(i) },
                            onSolo = { viewModel.toggleSoloTrack(i) },
                            onArm = { viewModel.toggleArmTrack(i) },
                        )
                    }
                }
            }

            // Timeline content
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgPanel)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { followPlayhead = false },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (draggedClipId == null) {
                                        scrollX = (scrollX - dragAmount).coerceIn(0f, maxScrollX)
                                        change.consume()
                                    }
                                },
                            )
                        },
            ) {
                Box(modifier = Modifier.offset { IntOffset(-scrollX.toInt(), 0) }) {
                    Box(
                        modifier =
                            Modifier
                                .width(with(density) { totalWidthPx.toDp() })
                                .fillMaxHeight(),
                    ) {
                        // Grid + track dividers
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (b in 0..totalBars) {
                                val x = b * barWidthPx
                                drawLine(
                                    color = PanelHighlight.copy(alpha = 0.25f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1f,
                                )
                                for (beat in 1 until 4) {
                                    val bx = x + beat * barWidthPx / 4f
                                    drawLine(
                                        color = PanelHighlight.copy(alpha = 0.1f),
                                        start = Offset(bx, 0f),
                                        end = Offset(bx, size.height),
                                        strokeWidth = 0.5f,
                                    )
                                }
                            }
                            val rulerPx = with(density) { rulerHeight.toPx() }
                            for (t in 0..16) {
                                val y = t * trackHeightPx + rulerPx
                                drawLine(
                                    color = PanelDivider,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f,
                                )
                            }
                        }

                        // Bar ruler
                        TimelineRuler(
                            barWidthPx = barWidthPx,
                            totalBars = totalBars,
                            modifier = Modifier.height(rulerHeight).fillMaxWidth(),
                        )

                        // Invisible tap targets for empty track lanes
                        val rulerPx = with(density) { rulerHeight.toPx() }
                        for (trackIdx in 0 until 16) {
                            key(trackIdx) {
                                val top = rulerPx + trackIdx * trackHeightPx
                                Box(
                                    modifier =
                                        Modifier
                                            .offset { IntOffset(0, top.toInt()) }
                                            .width(with(density) { totalWidthPx.toDp() })
                                            .height(with(density) { trackHeightPx.toDp() })
                                            .pointerInput(trackIdx) {
                                                detectTapGestures { offset ->
                                                    val tapTick =
                                                        viewModel.snapTick(
                                                            ((offset.x + scrollX) / tickWidthPx).toLong(),
                                                        )
                                                    viewModel.addPatternClip(trackIdx, tapTick)
                                                }
                                            },
                                )
                            }
                        }

                        // Clips
                        for (clip in arrangement.clips) {
                            key(clip.id) {
                                val top = rulerPx + clip.trackIndex * trackHeightPx
                                val left = clip.startTick * tickWidthPx
                                val width = clip.durationTicks * tickWidthPx
                                if (draggedClipId != clip.id) {
                                    ClipItem(
                                        clip = clip,
                                        pattern =
                                            (clip as? PatternClip)?.let { pc ->
                                                patterns.find { it.id == pc.patternId }
                                            },
                                        tickWidthPx = tickWidthPx,
                                        modifier =
                                            Modifier
                                                .offset { IntOffset(left.toInt(), top.toInt()) }
                                                .width(with(density) { width.toDp() })
                                                .height(with(density) { trackHeightPx.toDp() })
                                                .padding(2.dp),
                                        onTap = { viewModel.toggleMuteClip(clip.id) },
                                        onLongPress = { draggedClipId = clip.id },
                                        onTrim = { newDuration ->
                                            viewModel.trimClip(clip.id, newDuration)
                                        },
                                    )
                                }
                            }
                        }

                        // Dragged clip ghost overlay
                        if (draggedClipId != null) {
                            val clip = arrangement.clips.find { it.id == draggedClipId }
                            if (clip != null) {
                                val startTop = rulerPx + clip.trackIndex * trackHeightPx
                                val startLeft = clip.startTick * tickWidthPx
                                var currentOffset by remember { mutableStateOf(Offset.Zero) }

                                Box(
                                    modifier =
                                        Modifier
                                            .offset {
                                                IntOffset(
                                                    (startLeft + currentOffset.x).toInt(),
                                                    (startTop + currentOffset.y).toInt(),
                                                )
                                            }.width(with(density) { (clip.durationTicks * tickWidthPx).toDp() })
                                            .height(with(density) { trackHeightPx.toDp() })
                                            .padding(2.dp)
                                            .pointerInput(draggedClipId) {
                                                detectDragGestures(
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        currentOffset += dragAmount
                                                    },
                                                    onDragEnd = {
                                                        val deltaTicks = (currentOffset.x / tickWidthPx).toLong()
                                                        val deltaTracks = (currentOffset.y / trackHeightPx).toInt()
                                                        val newTick =
                                                            viewModel.snapTick(
                                                                (clip.startTick + deltaTicks).coerceAtLeast(0),
                                                            )
                                                        val newTrack = (clip.trackIndex + deltaTracks).coerceIn(0, 15)
                                                        viewModel.moveClip(clip.id, newTick, newTrack)
                                                        draggedClipId = null
                                                    },
                                                )
                                            },
                                ) {
                                    ClipContent(
                                        clip = clip,
                                        pattern =
                                            (clip as? PatternClip)?.let { pc ->
                                                patterns.find { it.id == pc.patternId }
                                            },
                                        isGhost = true,
                                    )
                                }
                            }
                        }

                        // Playhead line
                        Box(
                            modifier =
                                Modifier
                                    .offset {
                                        IntOffset(
                                            playheadPx.toInt(),
                                            with(density) { rulerHeight.toPx().toInt() },
                                        )
                                    }.width(2.dp)
                                    .fillMaxHeight()
                                    .background(KnobAmber),
                        )
                    }
                }
            }
        }

        // Automation lane
        val param = selectedParam
        if (param != null) {
            AutomationLane(
                paramId = param,
                points = automationPoints,
                barWidthPx = barWidthPx,
                tickWidthPx = tickWidthPx,
                totalBars = totalBars,
                onAddPoint = { tick, value ->
                    viewModel.addAutomationPoint(param, tick, value)
                },
                onMovePoint = { id, tick, value ->
                    viewModel.moveAutomationPoint(id, tick, value)
                },
                onDeletePoint = { id -> viewModel.deleteAutomationPoint(id) },
                onClose = { viewModel.selectAutomationParam(null) },
                modifier = Modifier.height(140.dp).fillMaxWidth(),
            )
        }

        // Quick automation param selector (shown when lane is closed)
        if (selectedParam == null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(BgPanel)
                        .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "AUTO",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                val params =
                    listOf(
                        "track.0.synth.filter.cutoff" to "Filter Cutoff",
                        "track.0.synth.amp.level" to "Amp Level",
                        "track.0.synth.lfo1.rate" to "LFO Rate",
                        "track.0.synth.master.volume" to "Master Vol",
                    )
                params.forEach { (id, label) ->
                    Box(
                        modifier =
                            Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(BgGunmetal)
                                .border(1.dp, PanelHighlight.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .clickable { viewModel.selectAutomationParam(id) }
                                .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = TextPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Transport Strip
// ---------------------------------------------------------------------------

@Composable
private fun TransportStrip(
    transport: TransportState,
    snap: TimelineViewModel.Snap,
    zoom: Float,
    onToggleLoop: () -> Unit,
    onTogglePunch: () -> Unit,
    onLoopStart: () -> Unit,
    onLoopEnd: () -> Unit,
    onPunchIn: () -> Unit,
    onPunchOut: () -> Unit,
    onBpmChange: (Float) -> Unit,
    onNudge: (Long) -> Unit,
    onSnapChange: (TimelineViewModel.Snap) -> Unit,
    onZoomChange: (Float) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BgPanel)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TransportButton(
            label = "↻",
            active = transport.loopEnabled,
            activeColor = TransportAmber,
            onClick = onToggleLoop,
            modifier = Modifier.size(36.dp),
        )
        TransportButton(
            label = "PUNCH",
            active = transport.punchEnabled,
            activeColor = KnobCyan,
            onClick = onTogglePunch,
            modifier = Modifier.size(40.dp),
        )

        Spacer(Modifier.width(4.dp))

        val step = transport.position.tick / TICKS_PER_STEP
        LcdDisplay(
            value = "${transport.position.bar + 1}|${transport.position.beat + 1}|${step + 1}",
            label = "TIME",
            color = KnobAmber,
            fontSize = 12.sp,
            modifier = Modifier.width(84.dp),
        )
        LcdDisplay(
            value = "%.1f".format(transport.tempoBpm),
            label = "BPM",
            color = KnobGreen,
            fontSize = 12.sp,
            modifier = Modifier.width(64.dp),
        )

        Spacer(Modifier.width(4.dp))

        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgGunmetal)
                    .clickable { onNudge(-TICKS_PER_STEP.toLong()) },
            contentAlignment = Alignment.Center,
        ) { Text("◀", color = TextPrimary, fontSize = 10.sp) }
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgGunmetal)
                    .clickable { onNudge(TICKS_PER_STEP.toLong()) },
            contentAlignment = Alignment.Center,
        ) { Text("▶", color = TextPrimary, fontSize = 10.sp) }

        Spacer(Modifier.width(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                LabeledTinyButton("◀ Loop", onLoopStart)
                LabeledTinyButton("Loop ▶", onLoopEnd)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                LabeledTinyButton("◀ P.In", onPunchIn)
                LabeledTinyButton("P.Out ▶", onPunchOut)
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier =
                Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgGunmetal)
                    .border(1.dp, PanelHighlight.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .clickable {
                        val values = TimelineViewModel.Snap.values()
                        val next = values[(snap.ordinal + 1) % values.size]
                        onSnapChange(next)
                    }.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(snap.label, color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BgGunmetal)
                        .clickable { onZoomChange(zoom + 0.2f) },
                contentAlignment = Alignment.Center,
            ) { Text("+", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BgGunmetal)
                        .clickable { onZoomChange(zoom - 0.2f) },
                contentAlignment = Alignment.Center,
            ) { Text("−", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TransportButton(
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
                    if (active) activeColor else PanelHighlight.copy(alpha = 0.3f),
                    RoundedCornerShape(6.dp),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else TextPrimary,
            fontSize = if (label.length == 1) 14.sp else 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LabeledTinyButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(width = 44.dp, height = 20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BgGunmetal)
                .border(1.dp, PanelHighlight.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextSecondary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------
// Track Header
// ---------------------------------------------------------------------------

@Composable
private fun TrackHeader(
    index: Int,
    state: TimelineViewModel.TrackUiState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onArm: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(if (isSelected) PanelHighlight.copy(alpha = 0.25f) else Color.Transparent)
                .clickable { onSelect() }
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${index + 1}",
            color = if (isSelected) KnobAmber else TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(16.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TrackButton("M", state.mute, KnobRed, onMute, Modifier.size(22.dp))
                TrackButton("S", state.solo, KnobAmber, onSolo, Modifier.size(22.dp))
                TrackButton("R", state.arm, TransportRed, onArm, Modifier.size(22.dp))
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(state.level)
                            .background(
                                when {
                                    state.level > 0.9f -> KnobRed
                                    state.level > 0.7f -> KnobAmber
                                    else -> KnobGreen
                                },
                            ),
                )
            }
        }
    }
}

@Composable
private fun TrackButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(3.dp))
                .background(if (active) activeColor else BgGunmetal)
                .border(
                    1.dp,
                    if (active) activeColor else PanelHighlight.copy(alpha = 0.3f),
                    RoundedCornerShape(3.dp),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else TextSecondary,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---------------------------------------------------------------------------
// Timeline Ruler
// ---------------------------------------------------------------------------

@Composable
private fun TimelineRuler(
    barWidthPx: Float,
    totalBars: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Box(modifier = modifier) {
        for (b in 0 until totalBars) {
            Text(
                text = "${b + 1}",
                color = TextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .offset(x = with(density) { (b * barWidthPx).toDp() })
                        .padding(start = 2.dp, top = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Clip Item
// ---------------------------------------------------------------------------

@Composable
private fun ClipItem(
    clip: Clip,
    pattern: Pattern?,
    tickWidthPx: Float,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onTrim: (newDurationTicks: Long) -> Unit,
) {
    val bg =
        when {
            clip.mute -> TextMuted.copy(alpha = 0.5f)
            clip is PatternClip -> KnobAmber.copy(alpha = 0.75f)
            clip is AudioClip -> KnobCyan.copy(alpha = 0.75f)
            else -> TextMuted
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(bg)
                .border(
                    1.dp,
                    if (clip.mute) TextMuted else Color.White.copy(alpha = 0.4f),
                    RoundedCornerShape(4.dp),
                ).pointerInput(clip.id) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onLongPress = { onLongPress() },
                    )
                },
    ) {
        Text(
            text =
                when (clip) {
                    is PatternClip -> pattern?.name ?: "P${clip.patternId + 1}"
                    is AudioClip -> clip.audioFilePath.substringAfterLast('/').take(12)
                },
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(4.dp).align(Alignment.TopStart),
        )

        if (clip is AudioClip) {
            val hash = remember(clip.id) { clip.id.hashCode() }
            Canvas(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp, horizontal = 4.dp)) {
                val bars = (size.width / 6).toInt().coerceAtLeast(4)
                val w = size.width / bars
                for (i in 0 until bars) {
                    val h = kotlin.math.abs((hash + i * 71) % 100) / 100f * size.height * 0.7f
                    drawRect(
                        color = Color.Black.copy(alpha = 0.25f),
                        topLeft = Offset(i * w + 1f, (size.height - h) / 2),
                        size =
                            androidx.compose.ui.geometry
                                .Size(w - 2f, h),
                    )
                }
            }
        }

        if (!clip.mute) {
            var trimDelta by remember { mutableStateOf(0f) }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(14.dp)
                        .fillMaxHeight()
                        .pointerInput(clip.id) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    trimDelta += dragAmount
                                },
                                onDragEnd = {
                                    val deltaTicks = (trimDelta / tickWidthPx).toLong()
                                    val newDuration =
                                        (clip.durationTicks + deltaTicks)
                                            .coerceAtLeast(TICKS_PER_STEP.toLong())
                                    onTrim(newDuration)
                                    trimDelta = 0f
                                },
                            )
                        },
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight(0.6f)
                            .align(Alignment.Center)
                            .background(Color.White.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

@Composable
private fun ClipContent(
    clip: Clip,
    pattern: Pattern?,
    isGhost: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg =
        when {
            clip.mute -> TextMuted.copy(alpha = 0.5f)
            clip is PatternClip -> KnobAmber.copy(alpha = if (isGhost) 0.9f else 0.75f)
            clip is AudioClip -> KnobCyan.copy(alpha = if (isGhost) 0.9f else 0.75f)
            else -> TextMuted
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(bg)
                .border(
                    1.dp,
                    if (isGhost) KnobAmber else Color.White.copy(alpha = 0.4f),
                    RoundedCornerShape(4.dp),
                ),
    ) {
        Text(
            text =
                when (clip) {
                    is PatternClip -> pattern?.name ?: "P${clip.patternId + 1}"
                    is AudioClip -> clip.audioFilePath.substringAfterLast('/').take(12)
                },
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Automation Lane
// ---------------------------------------------------------------------------

@Composable
private fun AutomationLane(
    paramId: String,
    points: List<TimelineViewModel.UiAutomationPoint>,
    barWidthPx: Float,
    tickWidthPx: Float,
    totalBars: Int,
    onAddPoint: (Long, Float) -> Unit,
    onMovePoint: (Long, Long, Float) -> Unit,
    onDeletePoint: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(BgPanel).padding(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "AUTO: $paramId",
                color = KnobAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    points.maxByOrNull { it.point.tick }?.let { onDeletePoint(it.id) }
                },
            ) {
                Text("DEL LAST", color = KnobRed, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onClose) {
                Text("CLOSE", color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1A1A1E)),
        ) {
            val density = LocalDensity.current
            val hPx = with(density) { maxHeight.toPx() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                for (b in 0..totalBars) {
                    val x = b * barWidthPx
                    drawLine(
                        PanelHighlight.copy(alpha = 0.15f),
                        Offset(x, 0f),
                        Offset(x, size.height),
                        1f,
                    )
                }
                val sorted = points.sortedBy { it.point.tick }
                if (sorted.size >= 2) {
                    for (i in 0 until sorted.size - 1) {
                        val p1 = sorted[i]
                        val p2 = sorted[i + 1]
                        val x1 = p1.point.tick * tickWidthPx
                        val y1 = (1f - p1.point.value) * size.height
                        val x2 = p2.point.tick * tickWidthPx
                        val y2 = (1f - p2.point.value) * size.height
                        drawLine(KnobAmber, Offset(x1, y1), Offset(x2, y2), strokeWidth = 2f)
                    }
                }
                for (p in sorted) {
                    val x = p.point.tick * tickWidthPx
                    val y = (1f - p.point.value) * size.height
                    drawCircle(KnobAmber, radius = 5f, center = Offset(x, y))
                    drawCircle(Color.White, radius = 2.5f, center = Offset(x, y))
                }
            }

            // Tap empty space to add
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(paramId) {
                            detectTapGestures { offset ->
                                val tick = (offset.x / tickWidthPx).toLong().coerceAtLeast(0)
                                val value = 1f - (offset.y / hPx).coerceIn(0f, 1f)
                                onAddPoint(tick, value.coerceIn(0f, 1f))
                            }
                        },
            )

            // Draggable point overlays
            for (p in points) {
                val xDp = with(density) { (p.point.tick * tickWidthPx).toDp() } - 12.dp
                val yDp = with(density) { ((1f - p.point.value) * hPx).toDp() } - 12.dp
                var dragOffset by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier =
                        Modifier
                            .offset(
                                x = xDp + with(density) { dragOffset.x.toDp() },
                                y = yDp + with(density) { dragOffset.y.toDp() },
                            ).size(24.dp)
                            .pointerInput(p.id) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount
                                    },
                                    onDragEnd = {
                                        val newTick =
                                            ((p.point.tick * tickWidthPx + dragOffset.x) / tickWidthPx)
                                                .toLong()
                                                .coerceAtLeast(0)
                                        val newValue =
                                            1f -
                                                ((p.point.value * hPx + dragOffset.y) / hPx)
                                                    .coerceIn(0f, 1f)
                                        onMovePoint(p.id, newTick, newValue)
                                        dragOffset = Offset.Zero
                                    },
                                )
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    // Invisible touch target; visuals drawn by Canvas above
                }
            }
        }
    }
}
