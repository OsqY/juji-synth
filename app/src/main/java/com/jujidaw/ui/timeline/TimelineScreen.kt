package com.jujidaw.ui.timeline

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jujidaw.audio.SynthEngine
import com.jujidaw.model.AudioClip
import com.jujidaw.model.Clip
import com.jujidaw.model.PPQ
import com.jujidaw.model.Pattern
import com.jujidaw.model.PatternClip
import com.jujidaw.model.PadClip
import com.jujidaw.model.TICKS_PER_STEP
import com.jujidaw.model.TransportPosition
import com.jujidaw.model.TransportState
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
fun TimelineScreen(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = viewModel { TimelineViewModel() },
    showTransportControls: Boolean = true,
) {
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
    val deletedClipCount = viewModel.deletedClips.collectAsState().value.size
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var followPlayhead by remember { mutableStateOf(true) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showPatterns by rememberSaveable { mutableStateOf(true) }
    var showPads by rememberSaveable { mutableStateOf(true) }
    var showAutomation by rememberSaveable { mutableStateOf(false) }

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
    val scrubHeight = 16.dp
    val timelineContentHeight = rulerHeight + scrubHeight + 56.dp * 16

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
    var draggedClipOffset by remember { mutableStateOf(Offset.Zero) }

    Column(modifier = modifier.fillMaxSize().background(Bg0).statusBarsPadding()) {
        SectionVisibilityBar(
            showControls = showControls && showTransportControls,
            allowControls = showTransportControls,
            showPatterns = showPatterns,
            showPads = showPads,
            showAutomation = showAutomation,
            onToggleControls = { showControls = !showControls },
            onTogglePatterns = { showPatterns = !showPatterns },
            onTogglePads = { showPads = !showPads },
            onToggleAutomation = { showAutomation = !showAutomation },
        )

        if (showTransportControls && showControls) TransportStrip(
            transport = transport,
            snap = snap,
            zoom = zoom,
            compact = isLandscape,
            deletedClipCount = deletedClipCount,
            onToggleLoop = { viewModel.toggleLoop() },
            onTogglePunch = { viewModel.togglePunch() },
            onLoopStart = { viewModel.setLoopStartToPlayhead() },
            onLoopEnd = { viewModel.setLoopEndToPlayhead() },
            onResetLoop = { viewModel.resetLoop() },
            onPunchIn = { viewModel.setPunchInToPlayhead() },
            onPunchOut = { viewModel.setPunchOutToPlayhead() },
            onBpmChange = { viewModel.setTempo(it) },
            onNudge = { viewModel.nudgePlayhead(it) },
            onSnapChange = { viewModel.setSnap(it) },
            onZoomChange = { viewModel.setZoom(it) },
            onRestoreDeleted = { viewModel.restoreLastDeletedClip() },
        )

        if (showTransportControls && showControls) Spacer(Modifier.height(Spacing.xs))

        // Pattern selector (1-16). Buttons scroll if they don't fit the width.
        if (showPatterns) Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(SurfaceContainer)
                    .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                "Sequencer Pattern",
                color = OnSurfaceVariant,
                style = LabelSmall,
                modifier = Modifier.padding(end = Spacing.sm),
            )
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                for (i in 0 until 16) {
                    val selected = selectedPatternId == i
                    Box(
                        modifier =
                            Modifier
                                .size(TouchTargetMin)
                                .clickable { viewModel.selectPattern(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(RadiusSm))
                                    .background(if (selected) Primary.copy(alpha = 0.12f) else SurfaceContainerLow)
                                    .border(
                                        1.dp,
                                        if (selected) Primary else OutlineVariant,
                                        RoundedCornerShape(RadiusSm),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${i + 1}",
                                color = if (selected) Primary else OnSurface,
                                style = LabelSmall,
                            )
                        }
                    }
                }
            }
        }

        // Pad strip — own row: drop a pad-trigger clip on the selected track at the playhead.
        if (showPads) Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(SurfaceContainer)
                    .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                "One-shot Pad",
                color = Secondary,
                style = LabelSmall,
                modifier = Modifier.padding(end = Spacing.sm),
            )
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                for (i in 0 until 32) {
                    Box(
                        modifier =
                            Modifier
                                .size(TouchTargetMin)
                                .clickable {
                                    val playheadTick = transport.position.toTicks()
                                    viewModel.addPadClip(selectedTrack, playheadTick, i)
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(RadiusSm))
                                    .background(SurfaceContainerLow)
                                    .border(1.dp, Secondary.copy(alpha = 0.4f), RoundedCornerShape(RadiusSm)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (i < 16) "A${i + 1}" else "B${i - 15}",
                                color = OnSurface,
                                style = LabelSmall,
                            )
                        }
                    }
                }
            }
        }

        if (showPatterns || showPads) Spacer(Modifier.height(Spacing.xs))

        // Main area: track headers + timeline
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        ) {
            // Track headers
            Column(
                modifier =
                    Modifier
                        .width(headerWidth)
                        .height(timelineContentHeight)
                        .background(SurfaceContainer),
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(rulerHeight).background(SurfaceContainerLow))
                Box(modifier = Modifier.fillMaxWidth().height(scrubHeight).background(Primary.copy(alpha = 0.08f)))
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
                        .height(timelineContentHeight)
                        .clip(RoundedCornerShape(RadiusLg))
                        .background(Bg1)
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
                                    color = OutlineVariant.copy(alpha = 0.25f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1f,
                                )
                                for (beat in 1 until 4) {
                                    val bx = x + beat * barWidthPx / 4f
                                    drawLine(
                                        color = OutlineVariant.copy(alpha = 0.1f),
                                        start = Offset(bx, 0f),
                                        end = Offset(bx, size.height),
                                        strokeWidth = 0.5f,
                                    )
                                }
                            }
                            val rulerPx = with(density) { rulerHeight.toPx() }
                            val scrubPx = with(density) { scrubHeight.toPx() }
                            for (t in 0..16) {
                                val y = t * trackHeightPx + rulerPx + scrubPx
                                drawLine(
                                    color = OutlineVariant,
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

                        // Dedicated, low-opacity scrub strip. It sits between
                        // the ruler and clips so seeking never steals clip or
                        // horizontal-scroll gestures.
                        val rulerPx = with(density) { rulerHeight.toPx() }
                        val scrubPx = with(density) { scrubHeight.toPx() }
                        fun seekFromTimelineX(x: Float) {
                            viewModel.seekToTick(viewModel.snapTick((x / tickWidthPx).toLong()))
                            followPlayhead = false
                        }
                        Box(
                            modifier =
                                Modifier
                                    .offset { IntOffset(0, rulerPx.toInt()) }
                                    .width(with(density) { totalWidthPx.toDp() })
                                    .height(scrubHeight)
                                    .background(Primary.copy(alpha = 0.08f))
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = { seekFromTimelineX(it.x) })
                                    }.pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { seekFromTimelineX(it.x) },
                                            onHorizontalDrag = { change, _ ->
                                                seekFromTimelineX(change.position.x)
                                                change.consume()
                                            },
                                        )
                                    },
                        )

                        // Invisible tap targets for empty track lanes
                        for (trackIdx in 0 until 16) {
                            key(trackIdx) {
                                val top = rulerPx + scrubPx + trackIdx * trackHeightPx
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
                                                            (offset.x / tickWidthPx).toLong(),
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
                                val top = rulerPx + scrubPx + clip.trackIndex * trackHeightPx
                                val left = clip.startTick * tickWidthPx
                                val width = clip.durationTicks * tickWidthPx
                                // A pad clip is one musical hit. Give it a
                                // stable hit target rather than rendering its
                                // one-step duration as an almost invisible line.
                                val renderedWidth =
                                    if (clip is PadClip) {
                                        maxOf(width, with(density) { 44.dp.toPx() })
                                    } else {
                                        width
                                    }
                                val isDragging = draggedClipId == clip.id
                                ClipItem(
                                        clip = clip,
                                        pattern =
                                            (clip as? PatternClip)?.let { pc ->
                                                patterns.find { it.id == pc.patternId }
                                            },
                                        tickWidthPx = tickWidthPx,
                                        modifier =
                                            Modifier
                                                .offset {
                                                    IntOffset(
                                                        (left + if (isDragging) draggedClipOffset.x else 0f).toInt(),
                                                        (top + if (isDragging) draggedClipOffset.y else 0f).toInt(),
                                                    )
                                                }
                                                .width(with(density) { renderedWidth.toDp() })
                                                .height(with(density) { trackHeightPx.toDp() })
                                                .padding(Spacing.xs),
                                        onTap = { viewModel.toggleMuteClip(clip.id) },
                                        isDragging = isDragging,
                                        onDragStart = {
                                            draggedClipId = clip.id
                                            draggedClipOffset = Offset.Zero
                                        },
                                        onDrag = { delta ->
                                            if (draggedClipId == clip.id) draggedClipOffset += delta
                                        },
                                        onDragEnd = {
                                            if (draggedClipId == clip.id) {
                                                val newTick = viewModel.snapTick(
                                                    (clip.startTick + (draggedClipOffset.x / tickWidthPx).toLong()).coerceAtLeast(0),
                                                )
                                                val newTrack = (clip.trackIndex + (draggedClipOffset.y / trackHeightPx).toInt()).coerceIn(0, 15)
                                                viewModel.moveClip(clip.id, newTick, newTrack)
                                            }
                                            draggedClipId = null
                                            draggedClipOffset = Offset.Zero
                                        },
                                        onDelete = { viewModel.deleteClip(clip.id) },
                                        onTrim = { newDuration ->
                                            viewModel.trimClip(clip.id, newDuration)
                                        },
                                    )
                            }
                        }

                        // Playhead line
                        Box(
                            modifier =
                                Modifier
                                    .offset {
                                        IntOffset(
                                            playheadPx.toInt(),
                                            with(density) { (rulerHeight + scrubHeight).toPx().toInt() },
                                        )
                                    }.width(2.dp)
                                    .fillMaxHeight()
                                    .background(Primary),
                        )
                    }
                }
            }
        }

        // Automation lane
        val param = selectedParam
        if (showAutomation && param != null) {
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
        if (showAutomation && selectedParam == null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(SurfaceContainer)
                        .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    "AUTO",
                    color = OnSurfaceVariant,
                    style = LabelSmall,
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
                                .height(32.dp)
                                .clip(RoundedCornerShape(RadiusSm))
                                .background(SurfaceContainerLow)
                                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                                .clickable { viewModel.selectAutomationParam(id) }
                                .padding(horizontal = Spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = OnSurface, style = CaptionSmall)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Clip color mapping — track hue from the ClipColors grid (saturated representative,
// index 1 per Color.kt row semantics). PatternClip -> warm half (Red..Green),
// AudioClip -> cool half (Teal..Blue) per plan §Color System.
// ---------------------------------------------------------------------------

private fun clipBaseHue(clip: Clip): Color =
    when (clip) {
        is PatternClip -> {
            ClipColors[clip.trackIndex % 6][1]
        }

        is PadClip -> {
            ClipColors[clip.trackIndex % 6][3]
        }

        is AudioClip -> {
            val cool = listOf(6, 7, 8, 9)
            ClipColors[cool[clip.trackIndex % cool.size]][1]
        }

        else -> {
            OnSurfaceVariant
        }
    }

// ---------------------------------------------------------------------------
// Transport Strip
// ---------------------------------------------------------------------------

@Composable
private fun SectionVisibilityBar(
    showControls: Boolean,
    allowControls: Boolean,
    showPatterns: Boolean,
    showPads: Boolean,
    showAutomation: Boolean,
    onToggleControls: () -> Unit,
    onTogglePatterns: () -> Unit,
    onTogglePads: () -> Unit,
    onToggleAutomation: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(SurfaceContainerLow)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("SHOW", color = OnSurfaceVariant, style = CaptionSmall)
        if (allowControls) SectionToggle("Controls", showControls, onToggleControls)
        SectionToggle("Patterns", showPatterns, onTogglePatterns)
        SectionToggle("Pads", showPads, onTogglePads)
        SectionToggle("Automation", showAutomation, onToggleAutomation)
    }
}

@Composable
private fun SectionToggle(
    label: String,
    visible: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(RadiusXs))
                .background(if (visible) Primary.copy(alpha = 0.12f) else SurfaceContainer)
                .border(1.dp, if (visible) Primary else OutlineVariant, RoundedCornerShape(RadiusXs))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (visible) Primary else OnSurfaceVariant, style = CaptionSmall)
    }
}

@Composable
private fun TransportStrip(
    transport: TransportState,
    snap: TimelineViewModel.Snap,
    zoom: Float,
    compact: Boolean,
    deletedClipCount: Int,
    onToggleLoop: () -> Unit,
    onTogglePunch: () -> Unit,
    onLoopStart: () -> Unit,
    onLoopEnd: () -> Unit,
    onResetLoop: () -> Unit,
    onPunchIn: () -> Unit,
    onPunchOut: () -> Unit,
    onBpmChange: (Float) -> Unit,
    onNudge: (Long) -> Unit,
    onSnapChange: (TimelineViewModel.Snap) -> Unit,
    onZoomChange: (Float) -> Unit,
    onRestoreDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RadiusLg))
                .background(SurfaceContainer)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TransportButton(
                    label = if (transport.loopEnabled) "Loop: On" else "Loop: Off",
                    active = transport.loopEnabled,
                    activeColor = StateSolo,
                    onClick = onToggleLoop,
                    modifier = Modifier.size(width = 64.dp, height = 36.dp),
                )
                LabeledTinyButton("Start", onLoopStart, width = 46.dp)
                LabeledTinyButton("End", onLoopEnd, width = 42.dp)
                LabeledTinyButton("Reset", onResetLoop, width = 48.dp)
                TransportButton(
                    label = if (transport.punchEnabled) "Punch: On" else "Punch: Off",
                    active = transport.punchEnabled,
                    activeColor = Secondary,
                    onClick = onTogglePunch,
                    modifier = Modifier.size(width = 68.dp, height = 36.dp),
                )
                LabeledTinyButton("In", onPunchIn, width = 34.dp)
                LabeledTinyButton("Out", onPunchOut, width = 38.dp)
                NudgeArrow(Icons.Outlined.ChevronLeft) { onNudge(-snap.ticks) }
                NudgeArrow(Icons.Outlined.ChevronRight) { onNudge(snap.ticks) }
                SnapButton(snap, onSnapChange)
                ZoomStepButton(Icons.Outlined.ZoomOut) { onZoomChange(zoom - 0.2f) }
                Text("${(zoom * 100).toInt()}%", color = OnSurfaceVariant, style = CaptionSmall)
                ZoomStepButton(Icons.Outlined.ZoomIn) { onZoomChange(zoom + 0.2f) }
                RestoreTrashButton(deletedClipCount, onRestoreDeleted)
            }
            return@Column
        }

        // Row 1 — Loop & Punch groups. Horizontally scrollable so the clear
        // text labels never clip on narrow screens.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            TransportButton(
                label = if (transport.loopEnabled) "Loop: On" else "Loop: Off",
                active = transport.loopEnabled,
                activeColor = StateSolo,
                onClick = onToggleLoop,
                modifier = Modifier.size(width = 64.dp, height = 36.dp),
            )
            LabeledTinyButton("Loop Start", onLoopStart, width = 64.dp)
            LabeledTinyButton("Loop End", onLoopEnd, width = 58.dp)
            LabeledTinyButton("Reset Loop", onResetLoop, width = 66.dp)

            Spacer(Modifier.width(Spacing.md))

            TransportButton(
                label = if (transport.punchEnabled) "Punch: On" else "Punch: Off",
                active = transport.punchEnabled,
                activeColor = Secondary,
                onClick = onTogglePunch,
                modifier = Modifier.size(width = 68.dp, height = 36.dp),
            )
            LabeledTinyButton("Punch In", onPunchIn, width = 64.dp)
            LabeledTinyButton("Punch Out", onPunchOut, width = 70.dp)
        }

        // Row 2 — Playhead nudge + grid controls. Position/BPM are shown in
        // the always-visible persistent transport bar, so they are not duplicated here.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Nudge playhead by one step. These call onNudge only;
            // they never switch the active tab/screen.
            NudgeArrow(Icons.Outlined.ChevronLeft) { onNudge(-snap.ticks) }
            NudgeArrow(Icons.Outlined.ChevronRight) { onNudge(snap.ticks) }

            Spacer(Modifier.weight(1f))

            SnapButton(snap, onSnapChange)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text("Zoom", color = OnSurfaceVariant, style = CaptionSmall)
                ZoomStepButton(Icons.Outlined.ZoomOut) { onZoomChange(zoom - 0.2f) }
                ZoomStepButton(Icons.Outlined.ZoomIn) { onZoomChange(zoom + 0.2f) }
            }
            RestoreTrashButton(deletedClipCount, onRestoreDeleted)
        }
    }
}

/** Compact timeline controls hosted in MainScreen's landscape transport row. */
@Composable
fun TimelineLandscapeControls(
    viewModel: TimelineViewModel,
    modifier: Modifier = Modifier,
) {
    val transport = viewModel.transportState.collectAsState().value
    val snap = viewModel.snap.collectAsState().value
    val zoom = viewModel.zoom.collectAsState().value
    val deletedClipCount = viewModel.deletedClips.collectAsState().value.size
    TransportStrip(
        transport = transport,
        snap = snap,
        zoom = zoom,
        compact = true,
        deletedClipCount = deletedClipCount,
        onToggleLoop = viewModel::toggleLoop,
        onTogglePunch = viewModel::togglePunch,
        onLoopStart = viewModel::setLoopStartToPlayhead,
        onLoopEnd = viewModel::setLoopEndToPlayhead,
        onResetLoop = viewModel::resetLoop,
        onPunchIn = viewModel::setPunchInToPlayhead,
        onPunchOut = viewModel::setPunchOutToPlayhead,
        onBpmChange = viewModel::setTempo,
        onNudge = viewModel::nudgePlayhead,
        onSnapChange = viewModel::setSnap,
        onZoomChange = viewModel::setZoom,
        onRestoreDeleted = viewModel::restoreLastDeletedClip,
        modifier = modifier,
    )
}

@Composable
private fun SnapButton(
    snap: TimelineViewModel.Snap,
    onSnapChange: (TimelineViewModel.Snap) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(RadiusSm))
                .background(Primary.copy(alpha = 0.08f))
                .border(1.dp, Primary.copy(alpha = 0.55f), RoundedCornerShape(RadiusSm))
                .clickable {
                    val values = TimelineViewModel.Snap.values()
                    onSnapChange(values[(snap.ordinal + 1) % values.size])
                }.padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text("Snap ${snap.label}", color = Primary, style = LabelSmall)
    }
}

@Composable
private fun RestoreTrashButton(
    deletedClipCount: Int,
    onRestoreDeleted: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable(enabled = deletedClipCount > 0, onClick = onRestoreDeleted)
                .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(Icons.Outlined.Delete, contentDescription = "Restore last deleted clip", modifier = Modifier.size(16.dp))
            Text("Trash $deletedClipCount", color = if (deletedClipCount > 0) OnSurface else OnSurfaceVariant, style = CaptionSmall)
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
                .clip(RoundedCornerShape(RadiusMd))
                .background(if (active) activeColor.copy(alpha = 0.25f) else SurfaceContainerLow)
                .border(
                    1.dp,
                    if (active) activeColor else OutlineVariant,
                    RoundedCornerShape(RadiusMd),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) activeColor else OnSurface,
            style = LabelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun LabeledTinyButton(
    label: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = 44.dp,
) {
    Box(
        modifier =
            Modifier
                .size(width = width, height = 28.dp)
                .clip(RoundedCornerShape(RadiusXs))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusXs))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = OnSurfaceVariant,
            style = CaptionSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun NudgeArrow(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Nudge playhead",
            tint = OnSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ZoomStepButton(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainerLow)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Zoom",
            tint = OnSurface,
            modifier = Modifier.size(18.dp),
        )
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
                .background(if (isSelected) Primary.copy(alpha = 0.12f) else Color.Transparent)
                .clickable { onSelect() }
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = "${index + 1}",
            color = if (isSelected) Primary else OnSurfaceVariant,
            style = LabelSmall,
            modifier = Modifier.width(16.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.weight(1f),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TrackButton("M", state.mute, OnSurfaceVariant, OnPrimary, onMute, Modifier.size(22.dp))
                TrackButton("S", state.solo, StateSolo, OnStateSolo, onSolo, Modifier.size(22.dp))
                TrackButton("R", state.arm, StateRecording, OnStateRecording, onArm, Modifier.size(22.dp))
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(RadiusXs))
                        .background(SurfaceContainerLow),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(state.level)
                            .background(if (state.level > 0.9f) StateRecording else StateActive),
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
    onColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(RadiusXs))
                .background(if (active) activeColor else SurfaceContainerLow)
                .border(
                    1.dp,
                    if (active) activeColor else OutlineVariant,
                    RoundedCornerShape(RadiusXs),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) onColor else OnSurfaceVariant,
            style = LabelSmall,
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
                color = OnSurfaceVariant,
                style = CaptionSmall,
                modifier =
                    Modifier
                        .offset(x = with(density) { (b * barWidthPx).toDp() })
                        .padding(start = Spacing.xs, top = Spacing.sm),
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
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTrim: (newDurationTicks: Long) -> Unit,
    onDelete: () -> Unit = {},
) {
    val hue = clipBaseHue(clip)
    val bg = if (clip.mute) hue.copy(alpha = 0.5f) else hue.copy(alpha = if (isDragging) 0.9f else 0.6f)
    val edge =
        when {
            isDragging -> Primary
            clip.mute -> OnSurfaceVariant
            clip is AudioClip -> Secondary
            else -> hue
        }
    Box {
        Box(
            modifier =
                modifier
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(bg)
                    .border(1.dp, edge, RoundedCornerShape(RadiusSm))
                    .pointerInput(clip.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount)
                            },
                            onDragEnd = onDragEnd,
                        )
                    }.clickable(onClick = onTap),
        ) {
            if (clip is PadClip) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val markerX = 8.dp.toPx()
                    drawLine(
                        color = edge,
                        start = Offset(markerX, 0f),
                        end = Offset(markerX, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawCircle(
                        color = edge,
                        radius = 6.dp.toPx(),
                        center = Offset(markerX, size.height / 2f),
                    )
                }
            }
            Text(
                text =
                    when (clip) {
                        is PatternClip -> pattern?.name ?: "P${clip.patternId + 1}"
                        is PadClip -> "P${clip.padIndex + 1}"
                        is AudioClip -> clip.audioFilePath.substringAfterLast('/').take(12)
                    },
                color = if (clip.mute) OnSurfaceVariant else OnSurface,
                style = CaptionSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(Spacing.xs).align(Alignment.TopStart),
            )

            if (clip is AudioClip) {
                val hash = remember(clip.id) { clip.id.hashCode() }
                Canvas(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp, horizontal = Spacing.xs)) {
                    val bars = (size.width / 6).toInt().coerceAtLeast(4)
                    val w = size.width / bars
                    for (i in 0 until bars) {
                        val h = kotlin.math.abs((hash + i * 71) % 100) / 100f * size.height * 0.7f
                        drawRect(
                            color = OnSurface.copy(alpha = 0.4f),
                            topLeft = Offset(i * w + 1f, (size.height - h) / 2),
                            size =
                                androidx.compose.ui.geometry
                                    .Size(w - 2f, h),
                        )
                    }
                }
            }

            if (!clip.mute && clip !is PadClip) {
                var trimDelta by remember { mutableStateOf(0f) }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(20.dp)
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
                                .background(OnSurface.copy(alpha = 0.5f)),
                    )
                }
            }

            // Direct delete avoids a popup being positioned outside the
            // horizontally translated timeline. Deleted clips remain
            // recoverable from the timeline trash control.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Move clip to trash",
                    tint = OnSurface,
                    modifier = Modifier.size(16.dp),
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
    val hue = clipBaseHue(clip)
    val bg =
        if (clip.mute) {
            hue.copy(alpha = 0.5f)
        } else {
            hue.copy(alpha = if (isGhost) 0.9f else 0.6f)
        }
    val edge =
        when {
            isGhost -> Primary
            clip.mute -> OnSurfaceVariant
            clip is AudioClip -> Secondary
            else -> hue
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(RadiusSm))
                .background(bg)
                .border(1.dp, edge, RoundedCornerShape(RadiusSm)),
    ) {
        Text(
            text =
                when (clip) {
                    is PatternClip -> pattern?.name ?: "P${clip.patternId + 1}"
                    is PadClip -> "Pad ${clip.padIndex + 1}"
                    is AudioClip -> clip.audioFilePath.substringAfterLast('/').take(12)
                },
            color = if (clip.mute) OnSurfaceVariant else OnSurface,
            style = CaptionSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(Spacing.xs),
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
    Column(modifier = modifier.background(SurfaceContainerLow).padding(Spacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "AUTO: $paramId",
                color = Primary,
                style = LabelSmall,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    points.maxByOrNull { it.point.tick }?.let { onDeletePoint(it.id) }
                },
            ) {
                Text("DEL LAST", color = StateRecording, style = CaptionSmall)
            }
            TextButton(onClick = onClose) {
                Text("CLOSE", color = OnSurfaceVariant, style = CaptionSmall)
            }
        }
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(Bg0),
        ) {
            val density = LocalDensity.current
            val hPx = with(density) { maxHeight.toPx() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                for (b in 0..totalBars) {
                    val x = b * barWidthPx
                    drawLine(
                        OutlineVariant.copy(alpha = 0.15f),
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
                        drawLine(Primary, Offset(x1, y1), Offset(x2, y2), strokeWidth = 2f)
                    }
                }
                for (p in sorted) {
                    val x = p.point.tick * tickWidthPx
                    val y = (1f - p.point.value) * size.height
                    drawCircle(Primary, radius = 5f, center = Offset(x, y))
                    drawCircle(OnSurface, radius = 2.5f, center = Offset(x, y))
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
