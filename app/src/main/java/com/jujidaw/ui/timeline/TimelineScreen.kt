package com.jujidaw.ui.timeline

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import com.jujidaw.project.PadSelectionStore
import com.jujidaw.project.PatternSelectionStore
import com.jujidaw.ui.theme.*
import kotlin.math.sqrt

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
    val persistedZoom = viewModel.zoom.collectAsState().value
    val snap = viewModel.snap.collectAsState().value
    val selectedTrack = viewModel.selectedTrack.collectAsState().value
    val selectedParam = viewModel.selectedAutomationParam.collectAsState().value
    val automationPoints = viewModel.automationPoints.collectAsState().value
    val trackStates = viewModel.trackStates.collectAsState().value
    val selectedPatternId = PatternSelectionStore.selectedPattern.collectAsState().value
    val selectedPad = PadSelectionStore.selectedPad.collectAsState().value
    val tool = viewModel.tool.collectAsState().value
    val selectedClipIds = viewModel.selectedClipIds.collectAsState().value
    val deletedClipCount = viewModel.deletedClips.collectAsState().value.size
    val canUndo = viewModel.canUndo.collectAsState().value
    val canRedo = viewModel.canRedo.collectAsState().value
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var followPlayhead by remember { mutableStateOf(true) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showAutomation by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(persistedZoom) }
    var pinchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(persistedZoom) {
        if (!pinchActive) zoom = persistedZoom
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Timeline mode disables the internal step sequencer
    DisposableEffect(Unit) {
        SynthEngine.setSequencerEnabled(false)
        onDispose { SynthEngine.setSequencerEnabled(true) }
    }

    val density = LocalDensity.current
    val baseBarWidthPx = with(density) { 96.dp.toPx() }
    val totalBars = 200
    val totalDurationTicks = (totalBars * PPQ * 4).toLong()
    val trackHeightPx = with(density) { 56.dp.toPx() }
    val headerWidth = 72.dp
    val rulerHeight = 24.dp
    val scrubHeight = 16.dp
    val rulerPx = with(density) { rulerHeight.toPx() }
    val scrubPx = with(density) { scrubHeight.toPx() }
    val timelineContentHeight = rulerHeight + scrubHeight + 56.dp * 16

    var scrollX by remember { mutableStateOf(0f) }
    var measuredViewportWidthPx by remember { mutableFloatStateOf(0f) }
    val viewportWidthPx = measuredViewportWidthPx.coerceAtLeast(1f)
    val timelineTransform =
        TimelineTransform(
            viewportWidthPx = viewportWidthPx,
            horizontalScrollPx = scrollX,
            pixelsPerBeat = baseBarWidthPx / 4f,
            zoom = zoom,
            density = density.density,
        )
    val barWidthPx = timelineTransform.barWidthPx
    val maxScrollX = timelineTransform.maxScroll(totalDurationTicks)
    val currentZoom by rememberUpdatedState(zoom)
    val currentScrollX by rememberUpdatedState(scrollX)
    val currentMaxScrollX by rememberUpdatedState(maxScrollX)
    val currentTimelineTransform by rememberUpdatedState(timelineTransform)
    val currentSnap by rememberUpdatedState(snap)
    val currentClips by rememberUpdatedState(arrangement.clips)

    val setZoomAtAnchor: (Float, Float) -> Unit = { requestedZoom, anchorViewportX ->
        val nextZoom = sanitizeTimelineZoom(requestedZoom, fallback = zoom)
        scrollX = timelineTransform
            .zoomAroundAnchor(anchorViewportX, zoom, nextZoom, totalDurationTicks)
            .horizontalScrollPx
        zoom = nextZoom
        viewModel.setZoom(nextZoom)
        followPlayhead = false
    }

    val playheadContentPx = timelineTransform.tickToContentPx(transport.position.toTicks())
    val playheadPx = timelineTransform.tickToViewportPx(transport.position.toTicks())
    LaunchedEffect(playheadPx, transport.playing, followPlayhead) {
        if (transport.playing && followPlayhead) {
            val target = (playheadContentPx - viewportWidthPx * 0.3f).coerceIn(0f, maxScrollX)
            scrollX = target
        }
    }

    LaunchedEffect(zoom, viewportWidthPx) {
        scrollX = scrollX.coerceIn(0f, maxScrollX)
    }

    var draggedClipId by remember { mutableStateOf<String?>(null) }
    var draggedClipIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var draggedClipOffset by remember { mutableStateOf(Offset.Zero) }
    var resizePreview by remember { mutableStateOf<ClipResizePreview?>(null) }
    var pendingDeleteClipIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val gestureStateHolder = remember { mutableStateOf<TimelineGestureState>(TimelineGestureState.Idle) }
    val currentDraggedClipId by rememberUpdatedState(draggedClipId)
    fun transitionGesture(event: TimelineGestureEvent): TimelineGestureState {
        val nextState = reduceTimelineGestureState(gestureStateHolder.value, event)
        gestureStateHolder.value = nextState
        return nextState
    }
    val viewportMeasured = measuredViewportWidthPx > 0f
    val visibleTicks = timelineTransform.visibleTickRange()
    val ticksPerBar = (PPQ * 4).toLong()
    val firstVisibleBar =
        if (viewportMeasured) (visibleTicks.first / ticksPerBar).toInt().coerceIn(0, totalBars) else 0
    val lastVisibleBar =
        if (viewportMeasured) {
            ((visibleTicks.last / ticksPerBar) + 1L).toInt().coerceIn(firstVisibleBar, totalBars)
        } else {
            totalBars
        }
    val firstVisibleTick = if (viewportMeasured) visibleTicks.first else 0L
    val lastVisibleTick =
        if (viewportMeasured) {
            timelineSaturatingAdd(visibleTicks.last, 1L).coerceAtLeast(firstVisibleTick)
        } else {
            Long.MAX_VALUE
        }
    val visibleClips =
        arrangement.clips.filter { clip ->
            clip.id == draggedClipId || clip.id == resizePreview?.clipId ||
                (clip.startTick < lastVisibleTick && timelineClipEndTick(clip) > firstVisibleTick)
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Bg0)
                .statusBarsPadding()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.Z && event.isShiftPressed -> {
                            viewModel.redo()
                            true
                        }
                        event.key == Key.Z -> {
                            viewModel.undo()
                            true
                        }
                        event.key == Key.Y -> {
                            viewModel.redo()
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
    ) {
        SectionVisibilityBar(
            showControls = showControls && showTransportControls,
            allowControls = showTransportControls,
            showAutomation = showAutomation,
            onToggleControls = { showControls = !showControls },
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
            onSwingChange = viewModel::setSwing,
            onNudge = { viewModel.nudgePlayhead(it) },
            onSnapChange = { viewModel.setSnap(it) },
            onZoomChange = { setZoomAtAnchor(it, viewportWidthPx / 2f) },
            onRestoreDeleted = { viewModel.restoreLastDeletedClip() },
        )

        if (showTransportControls && showControls) Spacer(Modifier.height(Spacing.xs))

        TimelineEditorToolbar(
            tool = tool,
            padIndex = selectedPad,
            patternId = selectedPatternId,
            canUndo = canUndo,
            canRedo = canRedo,
            onToolChange = viewModel::setTool,
            onPadSelect = { viewModel.selectPad(it); viewModel.setTool(TimelineTool.DRAW_PAD) },
            onPatternSelect = { viewModel.selectPattern(it); viewModel.setTool(TimelineTool.DRAW_PATTERN) },
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
        )
        if (selectedClipIds.isNotEmpty()) {
            TimelineSelectionToolbar(
                selectedCount = selectedClipIds.size,
                onCopy = viewModel::copySelectedClips,
                onPaste = { viewModel.pasteClipboard(transport.position.toTicks(), selectedTrack) },
                onDuplicate = viewModel::duplicateSelectedClips,
                onMute = viewModel::toggleMuteSelectedClips,
                onDelete = viewModel::deleteSelectedClips,
                onClear = viewModel::clearClipSelection,
            )
        }
        Spacer(Modifier.height(Spacing.xs))

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
                        .onSizeChanged { measuredViewportWidthPx = it.width.toFloat() }
                        .testTag("timeline-viewport")
                        .clip(RoundedCornerShape(RadiusLg))
                        .background(Bg1)
                        .pointerInput(Unit) {
                            var gestureStartZoom = currentZoom
                            var gestureZoom = currentZoom
                            var anchorTick = 0f
                            detectTwoFingerTransformGestures(
                                onGestureStart = { centroid ->
                                    when (gestureStateHolder.value) {
                                        is TimelineGestureState.MovingClip -> {
                                            draggedClipId = null
                                            draggedClipIds = emptySet()
                                            draggedClipOffset = Offset.Zero
                                        }
                                        is TimelineGestureState.ResizingStart,
                                        is TimelineGestureState.ResizingEnd,
                                        -> resizePreview = null
                                        else -> Unit
                                    }
                                    transitionGesture(TimelineGestureEvent.BeginPinch)
                                    pinchActive = true
                                    gestureStartZoom = currentZoom
                                    gestureZoom = gestureStartZoom
                                    val gestureTransform = currentTimelineTransform.copy(zoom = gestureStartZoom)
                                    anchorTick =
                                        (centroid.x + gestureTransform.horizontalScrollPx) /
                                            gestureTransform.pixelsPerTick
                                    followPlayhead = false
                                },
                                onGesture = { centroid, absoluteScale ->
                                    if (gestureStateHolder.value != TimelineGestureState.Pinching) return@detectTwoFingerTransformGestures
                                    val nextZoom = sanitizeTimelineZoom(
                                        gestureStartZoom * absoluteScale,
                                        fallback = gestureZoom,
                                    )
                                    gestureZoom = nextZoom
                                    val nextTransform = currentTimelineTransform.copy(
                                        zoom = nextZoom,
                                        horizontalScrollPx = currentScrollX,
                                    )
                                    scrollX =
                                        (anchorTick * nextTransform.pixelsPerTick - centroid.x)
                                            .coerceIn(0f, nextTransform.maxScroll(totalDurationTicks))
                                    zoom = nextZoom
                                },
                                onGestureEnd = {
                                    if (gestureStateHolder.value == TimelineGestureState.Pinching) {
                                        transitionGesture(TimelineGestureEvent.Finish)
                                    }
                                    viewModel.setZoom(gestureZoom)
                                    pinchActive = false
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            var gestureScrollX = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    transitionGesture(TimelineGestureEvent.BeginScroll)
                                    gestureScrollX = currentScrollX
                                    followPlayhead = false
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (gestureStateHolder.value == TimelineGestureState.Scrolling && currentDraggedClipId == null) {
                                        gestureScrollX = (gestureScrollX - dragAmount).coerceIn(0f, currentMaxScrollX)
                                        scrollX = gestureScrollX
                                        change.consume()
                                    }
                                },
                                onDragEnd = {
                                    if (gestureStateHolder.value == TimelineGestureState.Scrolling) {
                                        transitionGesture(TimelineGestureEvent.Finish)
                                    }
                                },
                                onDragCancel = {
                                    if (gestureStateHolder.value == TimelineGestureState.Scrolling) {
                                        transitionGesture(TimelineGestureEvent.Cancel)
                                    }
                                },
                            )
                        },
            ) {
                var marqueeStart by remember { mutableStateOf<Offset?>(null) }
                var marqueeEnd by remember { mutableStateOf<Offset?>(null) }
                // Render in viewport coordinates. Keeping this layer viewport-sized
                // avoids asking Compose to measure the entire zoomed timeline.
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier =
                                Modifier
                                    .fillMaxSize()
                                    .testTag("timeline-grid")
                                    .pointerInput(tool) {
                                    if (tool == TimelineTool.SELECT) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                marqueeStart = it
                                                marqueeEnd = it
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                marqueeEnd = change.position
                                            },
                                            onDragEnd = {
                                                val start = marqueeStart
                                                val end = marqueeEnd
                                                if (start != null && end != null) {
                                                    val firstTrack = ((start.y - rulerPx - scrubPx) / trackHeightPx).toInt().coerceIn(0, 15)
                                                    val lastTrack = ((end.y - rulerPx - scrubPx) / trackHeightPx).toInt().coerceIn(0, 15)
                                                    viewModel.selectClipsInRange(
                                                        currentTimelineTransform.viewportPxToTick(start.x),
                                                        currentTimelineTransform.viewportPxToTick(end.x),
                                                        firstTrack,
                                                        lastTrack,
                                                    )
                                                }
                                                marqueeStart = null
                                                marqueeEnd = null
                                            },
                                        )
                                    }
                                },
                    ) {
                        // Grid + track dividers
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (b in firstVisibleBar..lastVisibleBar) {
                                val x = timelineTransform.tickToViewportPx(b * ticksPerBar)
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
                            if (snap != TimelineViewModel.Snap.FREE) {
                                val stepPx = timelineTransform.durationToPx(snap.ticks)
                                if (stepPx >= 5f) {
                                    val firstStep = (scrollX / stepPx).toInt().coerceAtLeast(0)
                                    val lastStep = kotlin.math.ceil(((scrollX + viewportWidthPx) / stepPx).toDouble()).toInt()
                                    for (step in firstStep..lastStep) {
                                        val x = step * stepPx - timelineTransform.horizontalScrollPx
                                        drawLine(
                                            color = Primary.copy(alpha = 0.18f),
                                            start = Offset(x, 0f),
                                            end = Offset(x, size.height),
                                            strokeWidth = 1f,
                                        )
                                    }
                                }
                            }
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

                        val marqueeFrom = marqueeStart
                        val marqueeTo = marqueeEnd
                        if (marqueeFrom != null && marqueeTo != null) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val left = minOf(marqueeFrom.x, marqueeTo.x)
                                val top = minOf(marqueeFrom.y, marqueeTo.y)
                                val width = kotlin.math.abs(marqueeTo.x - marqueeFrom.x)
                                val height = kotlin.math.abs(marqueeTo.y - marqueeFrom.y)
                                drawRect(
                                    color = Primary.copy(alpha = 0.12f),
                                    topLeft = Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(width, height),
                                )
                                drawRect(
                                    color = Primary,
                                    topLeft = Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(width, height),
                                    style = Stroke(width = 1.dp.toPx()),
                                )
                            }
                        }

                        // Bar ruler
                        TimelineRuler(
                            transform = timelineTransform,
                            firstVisibleBar = firstVisibleBar,
                            lastVisibleBar = lastVisibleBar,
                            modifier = Modifier.height(rulerHeight).fillMaxWidth().testTag("timeline-ruler"),
                        )

                        // Clips
                        for (clip in visibleClips) {
                            if (clip.id in pendingDeleteClipIds) continue
                            key(clip.id) {
                                val top = rulerPx + scrubPx + clip.trackIndex * trackHeightPx
                                val preview = resizePreview?.takeIf { it.clipId == clip.id }
                                val previewTicks = preview?.let {
                                    timelineResizeTicks(
                                        baseStartTick = clip.startTick,
                                        baseDurationTicks = clip.durationTicks,
                                        edge = it.edge,
                                        requestedDeltaTicks = timelineTransform.pxDeltaToTicks(it.deltaPx),
                                        snapResolution = snap.ticks,
                                        free = snap == TimelineViewModel.Snap.FREE,
                                    )
                                }
                                val renderedStartTick = previewTicks?.startTick ?: clip.startTick
                                val renderedDurationTicks = previewTicks?.durationTicks ?: clip.durationTicks
                                val left = timelineTransform.tickToViewportPx(renderedStartTick)
                                val width = timelineTransform.durationToPx(renderedDurationTicks)
                                // Keep the visual width musical. Enlarging pad
                                // clips here made adjacent sixteenth hits cover
                                // one another; selected clips get dedicated
                                // edge handles below instead.
                                val renderedWidth = width.coerceAtLeast(1f)
                                val isDragging = clip.id in draggedClipIds
                                ClipItem(
                                        clip = clip,
                                        pattern =
                                            (clip as? PatternClip)?.let { pc ->
                                                patterns.find { it.id == pc.patternId }
                                            },
                                        modifier =
                                            Modifier
                                                .zIndex(if (clip.id in selectedClipIds || preview != null) 1f else 0f)
                                                .offset {
                                                    IntOffset(
                                                        (left + if (isDragging) draggedClipOffset.x else 0f).toInt(),
                                                        (top + if (isDragging) draggedClipOffset.y else 0f).toInt(),
                                                    )
                                                }
                                                .width(with(density) { renderedWidth.toDp() })
                                                .height(with(density) { trackHeightPx.toDp() }),
                                        onTap = { viewModel.selectClip(clip.id) },
                                        isSelected = clip.id in selectedClipIds,
                                        isDragging = isDragging,
                                        onDragStart = {
                                            val moveClipIds = timelineMoveClipIds(selectedClipIds, clip.id)
                                            if (clip.id !in selectedClipIds) viewModel.selectClip(clip.id)
                                            val nextState = transitionGesture(
                                                TimelineGestureEvent.BeginMove(moveClipIds),
                                            )
                                            if (nextState == TimelineGestureState.MovingClip(moveClipIds)) {
                                                draggedClipId = clip.id
                                                draggedClipIds = moveClipIds
                                                draggedClipOffset = Offset.Zero
                                            }
                                        },
                                        onDrag = { delta ->
                                            if (
                                                draggedClipId == clip.id &&
                                                gestureStateHolder.value.ownsMove(draggedClipIds)
                                            ) {
                                                draggedClipOffset += delta
                                            }
                                        },
                                        onDragEnd = {
                                            val ownsMove =
                                                draggedClipId == clip.id &&
                                                    gestureStateHolder.value.ownsMove(draggedClipIds)
                                            if (ownsMove) {
                                                val newTick = viewModel.snapTick(
                                                    timelineSaturatingAdd(
                                                        clip.startTick,
                                                        timelineTransform.pxDeltaToTicks(draggedClipOffset.x),
                                                    ).coerceAtLeast(0),
                                                )
                                                val newTrack = (clip.trackIndex + (draggedClipOffset.y / trackHeightPx).toInt()).coerceIn(0, 15)
                                                viewModel.moveClips(draggedClipIds, clip.id, newTick, newTrack)
                                            }
                                            if (draggedClipId == clip.id) {
                                                if (ownsMove) transitionGesture(TimelineGestureEvent.Finish)
                                                draggedClipId = null
                                                draggedClipIds = emptySet()
                                                draggedClipOffset = Offset.Zero
                                            }
                                        },
                                        onDragCancel = {
                                            if (draggedClipId == clip.id) {
                                                if (gestureStateHolder.value.ownsMove(draggedClipIds)) {
                                                    transitionGesture(TimelineGestureEvent.Cancel)
                                                }
                                                draggedClipId = null
                                                draggedClipIds = emptySet()
                                                draggedClipOffset = Offset.Zero
                                            }
                                        },
                                        onResizeStart = { edge ->
                                            val nextState = transitionGesture(
                                                when (edge) {
                                                    ClipResizeEdge.LEFT -> TimelineGestureEvent.BeginResizeStart(clip.id)
                                                    ClipResizeEdge.RIGHT -> TimelineGestureEvent.BeginResizeEnd(clip.id)
                                                },
                                            )
                                            if (
                                                nextState == when (edge) {
                                                    ClipResizeEdge.LEFT -> TimelineGestureState.ResizingStart(clip.id)
                                                    ClipResizeEdge.RIGHT -> TimelineGestureState.ResizingEnd(clip.id)
                                                }
                                            ) {
                                                resizePreview = ClipResizePreview(clip.id, edge, 0f)
                                            }
                                        },
                                        onResize = { delta ->
                                            resizePreview = resizePreview?.let { current ->
                                                if (
                                                    current.clipId == clip.id &&
                                                    gestureStateHolder.value.ownsResize(clip.id, current.edge)
                                                ) {
                                                    current.copy(deltaPx = current.deltaPx + delta)
                                                } else {
                                                    current
                                                }
                                            }
                                        },
                                        onResizeEnd = {
                                            val finished = resizePreview?.takeIf {
                                                it.clipId == clip.id && gestureStateHolder.value.ownsResize(clip.id, it.edge)
                                            }
                                            if (finished != null) {
                                                val finishedTicks = timelineResizeTicks(
                                                    baseStartTick = clip.startTick,
                                                    baseDurationTicks = clip.durationTicks,
                                                    edge = finished.edge,
                                                    requestedDeltaTicks = currentTimelineTransform.pxDeltaToTicks(finished.deltaPx),
                                                    snapResolution = currentSnap.ticks,
                                                    free = currentSnap == TimelineViewModel.Snap.FREE,
                                                )
                                                when (finished.edge) {
                                                    ClipResizeEdge.LEFT ->
                                                        viewModel.trimClipFromLeft(
                                                            clip.id,
                                                            finishedTicks.startTick,
                                                        )
                                                    ClipResizeEdge.RIGHT ->
                                                        viewModel.trimClip(
                                                            clip.id,
                                                            finishedTicks.durationTicks,
                                                        )
                                                }
                                            }
                                            if (finished != null) {
                                                transitionGesture(TimelineGestureEvent.Finish)
                                            }
                                            resizePreview = null
                                        },
                                        onResizeCancel = {
                                            if (resizePreview?.clipId == clip.id) {
                                                val cancelled = resizePreview
                                                if (
                                                    cancelled != null &&
                                                    gestureStateHolder.value.ownsResize(clip.id, cancelled.edge)
                                                ) {
                                                    transitionGesture(TimelineGestureEvent.Cancel)
                                                }
                                                resizePreview = null
                                            }
                                        },
                                    )
                            }
                        }

                        resizePreview?.let { activeResize ->
                            arrangement.clips.firstOrNull { it.id == activeResize.clipId }?.let { clip ->
                                val previewTicks = timelineResizeTicks(
                                    baseStartTick = clip.startTick,
                                    baseDurationTicks = clip.durationTicks,
                                    edge = activeResize.edge,
                                    requestedDeltaTicks = timelineTransform.pxDeltaToTicks(activeResize.deltaPx),
                                    snapResolution = snap.ticks,
                                    free = snap == TimelineViewModel.Snap.FREE,
                                )
                                val previewEdgeTick =
                                    if (activeResize.edge == ClipResizeEdge.LEFT) previewTicks.startTick else previewTicks.endTick
                                val previewEdgePx = timelineTransform.tickToViewportPx(previewEdgeTick)
                                Box(
                                    modifier =
                                        Modifier
                                            .offset {
                                                IntOffset(
                                                        previewEdgePx.toInt(),
                                                    (rulerPx + scrubPx + clip.trackIndex * trackHeightPx).toInt(),
                                                )
                                            }
                                            .testTag("timeline-clip-resize-preview-${clip.id}")
                                            .width(2.dp)
                                            .height(with(density) { trackHeightPx.toDp() })
                                            .background(Secondary),
                                )
                                val feedbackWidthPx = with(density) { 168.dp.toPx() }
                                val feedbackHeightPx = with(density) { 32.dp.toPx() }
                                val feedbackX =
                                    (previewEdgePx + if (activeResize.edge == ClipResizeEdge.LEFT) 8f else -feedbackWidthPx - 8f)
                                        .coerceIn(4f, (viewportWidthPx - feedbackWidthPx - 4f).coerceAtLeast(4f))
                                Box(
                                    modifier =
                                        Modifier
                                            .offset {
                                                IntOffset(
                                                    feedbackX.toInt(),
                                                    (rulerPx + scrubPx + clip.trackIndex * trackHeightPx - feedbackHeightPx - 4f)
                                                        .toInt()
                                                        .coerceAtLeast(0),
                                                )
                                            }
                                            .width(168.dp)
                                            .height(32.dp)
                                            .background(SurfaceContainerHighest, RoundedCornerShape(RadiusSm))
                                            .padding(horizontal = Spacing.xs, vertical = 2.dp)
                                            .testTag("timeline-clip-resize-feedback-${clip.id}"),
                                ) {
                                    Text(
                                        text =
                                            "Start ${formatTimelinePosition(previewTicks.startTick)} • " +
                                                "End ${formatTimelinePosition(previewTicks.endTick)}\n" +
                                                "Duration ${formatTimelineDuration(previewTicks.durationTicks)}",
                                        color = OnSurface,
                                        style = CaptionSmall,
                                        maxLines = 2,
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
                                            with(density) { (rulerHeight + scrubHeight).toPx().toInt() },
                                        )
                                    }.width(2.dp)
                                    .fillMaxHeight()
                                    .background(Primary)
                                    .testTag("timeline-playhead"),
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .offset { IntOffset(0, rulerPx.toInt()) }
                            .fillMaxWidth()
                            .height(scrubHeight)
                            .background(Primary.copy(alpha = 0.08f)),
                )

                // Ruler scrub and source placement live in viewport space. This
                // keeps their x-coordinate conversion independent of the
                // translated content layer and prevents column drift.
                Box(
                    modifier =
                        Modifier
                            .offset { IntOffset(0, rulerPx.toInt()) }
                            .fillMaxWidth()
                            .height(scrubHeight)
                            .pointerInput(Unit) {
                                    detectTapGestures {
                                        val nextState = transitionGesture(TimelineGestureEvent.BeginScrub)
                                        if (nextState == TimelineGestureState.Scrubbing) {
                                            viewModel.seekToTick(
                                                currentTimelineTransform.viewportPxToSnappedTick(it.x, currentSnap.ticks),
                                            )
                                            followPlayhead = false
                                            transitionGesture(TimelineGestureEvent.Finish)
                                        }
                                    }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        val nextState = transitionGesture(TimelineGestureEvent.BeginScrub)
                                        if (nextState == TimelineGestureState.Scrubbing) {
                                            viewModel.seekToTick(
                                                currentTimelineTransform.viewportPxToSnappedTick(offset.x, currentSnap.ticks),
                                            )
                                            followPlayhead = false
                                        }
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        if (gestureStateHolder.value == TimelineGestureState.Scrubbing) {
                                            viewModel.seekToTick(
                                                currentTimelineTransform.viewportPxToSnappedTick(change.position.x, currentSnap.ticks),
                                            )
                                            change.consume()
                                        }
                                    },
                                    onDragEnd = {
                                        if (gestureStateHolder.value == TimelineGestureState.Scrubbing) {
                                            transitionGesture(TimelineGestureEvent.Finish)
                                        }
                                    },
                                    onDragCancel = {
                                        if (gestureStateHolder.value == TimelineGestureState.Scrubbing) {
                                            transitionGesture(TimelineGestureEvent.Cancel)
                                        }
                                    },
                                )
                            },
                )

                if (tool == TimelineTool.DRAW_PAD || tool == TimelineTool.DRAW_PATTERN) {
                    Box(
                        modifier =
                            Modifier
                                .offset { IntOffset(0, (rulerPx + scrubPx).toInt()) }
                                .fillMaxWidth()
                                .height(with(density) { (trackHeightPx * 16f).toDp() })
                                .pointerInput(tool) {
                                    detectTapGestures { offset ->
                                        val track = (offset.y / trackHeightPx).toInt().coerceIn(0, 15)
                                        val tick = currentTimelineTransform.viewportPxToSnappedTick(offset.x, currentSnap.ticks)
                                        viewModel.placeSelectedSource(track, tick)
                                    }
                                },
                        )
                }

                if (tool == TimelineTool.DELETE) {
                    Box(
                        modifier =
                            Modifier
                                .offset { IntOffset(0, (rulerPx + scrubPx).toInt()) }
                                .fillMaxWidth()
                                .height(with(density) { (trackHeightPx * 16f).toDp() })
                                .pointerInput(tool) {
                                    detectTimelineEraseGestures(
                                        resolveClipIds = { offset ->
                                            val track = (offset.y / trackHeightPx).toInt().coerceIn(0, 15)
                                            val tick = currentTimelineTransform.viewportPxToTick(offset.x)
                                            val hitSlopTicks =
                                                currentTimelineTransform
                                                    .pxDeltaToTicks(with(density) { 12.dp.toPx() })
                                                    .coerceAtLeast(1L)
                                            timelineClipIdsAtPoint(currentClips, track, tick, hitSlopTicks)
                                        },
                                        onPreview = {
                                            transitionGesture(TimelineGestureEvent.BeginDelete(it))
                                            pendingDeleteClipIds = it
                                        },
                                        onCommit = {
                                            viewModel.deleteClips(it)
                                            pendingDeleteClipIds = emptySet()
                                            transitionGesture(TimelineGestureEvent.Finish)
                                        },
                                        onCancel = {
                                            pendingDeleteClipIds = emptySet()
                                            transitionGesture(TimelineGestureEvent.Cancel)
                                        },
                                    )
                                }
                                .testTag("timeline-delete-tool"),
                    )
                }
            }
        }

        // Automation lane
        val param = selectedParam
        if (showAutomation && param != null) {
            AutomationLane(
                paramId = param,
                points = automationPoints,
                transform = timelineTransform,
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

/**
 * Two-finger-only transform detector. A one-finger gesture is deliberately
 * left untouched so the sibling horizontal-scroll detector can handle it.
 */
private suspend fun PointerInputScope.detectTwoFingerTransformGestures(
    onGestureStart: (centroid: Offset) -> Unit,
    onGesture: (centroid: Offset, absoluteScale: Float) -> Unit,
    onGestureEnd: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var startDistance = 0f
        var transforming = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val first = pressed[0].position
                val second = pressed[1].position
                val centroid = (first + second) / 2f
                val dx = first.x - second.x
                val dy = first.y - second.y
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (!transforming || startDistance <= 0f) {
                    transforming = true
                    startDistance = distance.coerceAtLeast(0.001f)
                    onGestureStart(centroid)
                } else {
                    onGesture(centroid, (distance / startDistance).coerceIn(0.05f, 20f))
                }
                event.changes.forEach { it.consume() }
            } else if (transforming) {
                transforming = false
                onGestureEnd()
            }
        } while (event.changes.any { it.pressed })
        if (transforming) onGestureEnd()
    }
}

/** One-finger eraser stroke; a second finger cancels it so pinch zoom remains available. */
private suspend fun PointerInputScope.detectTimelineEraseGestures(
    resolveClipIds: (Offset) -> Set<String>,
    onPreview: (Set<String>) -> Unit,
    onCommit: (Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    try {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val erased = TimelineDeleteSession()
            onPreview(erased.add(resolveClipIds(down.position)))
            down.consume()
            var cancelled = false
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    if (!cancelled) onCancel()
                    cancelled = true
                } else if (!cancelled && pressed.size == 1) {
                    onPreview(erased.add(resolveClipIds(pressed.first().position)))
                    pressed.first().consume()
                }
            } while (event.changes.any { it.pressed })
            if (!cancelled) onCommit(erased.ids)
        }
    } finally {
        onCancel()
    }
}

// ---------------------------------------------------------------------------
// Transport Strip
// ---------------------------------------------------------------------------

@Composable
private fun SectionVisibilityBar(
    showControls: Boolean,
    allowControls: Boolean,
    showAutomation: Boolean,
    onToggleControls: () -> Unit,
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

/** The draw source is selected on Pads/Seq; this toolbar only chooses how the lane tap behaves. */
@Composable
private fun TimelineEditorToolbar(
    tool: TimelineTool,
    padIndex: Int,
    patternId: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onToolChange: (TimelineTool) -> Unit,
    onPadSelect: (Int) -> Unit,
    onPatternSelect: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    var sourceMode by rememberSaveable {
        mutableStateOf(if (tool == TimelineTool.DRAW_PATTERN) TimelineTool.DRAW_PATTERN else TimelineTool.DRAW_PAD)
    }
    val padListState = rememberLazyListState()
    val patternListState = rememberLazyListState()
    val sourceListState = if (sourceMode == TimelineTool.DRAW_PAD) padListState else patternListState
    val selectedSourceIndex = if (sourceMode == TimelineTool.DRAW_PAD) padIndex else patternId

    LaunchedEffect(tool) {
        if (tool == TimelineTool.DRAW_PAD || tool == TimelineTool.DRAW_PATTERN) sourceMode = tool
    }
    LaunchedEffect(sourceMode, selectedSourceIndex) {
        sourceListState.animateScrollToItem((selectedSourceIndex - 2).coerceAtLeast(0))
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SurfaceContainer)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            TimelineToolButton("Select", tool == TimelineTool.SELECT, Primary) { onToolChange(TimelineTool.SELECT) }
            TimelineToolButton("Delete", tool == TimelineTool.DELETE, StateRecording) { onToolChange(TimelineTool.DELETE) }
            TimelineToolButton("Pads", sourceMode == TimelineTool.DRAW_PAD && tool == TimelineTool.DRAW_PAD, Secondary) {
                sourceMode = TimelineTool.DRAW_PAD
                onToolChange(TimelineTool.DRAW_PAD)
            }
            TimelineToolButton("Patterns", sourceMode == TimelineTool.DRAW_PATTERN && tool == TimelineTool.DRAW_PATTERN, Primary) {
                sourceMode = TimelineTool.DRAW_PATTERN
                onToolChange(TimelineTool.DRAW_PATTERN)
            }
            Spacer(Modifier.weight(1f))
            HistoryButton(Icons.AutoMirrored.Outlined.Undo, "Undo (Ctrl+Z)", canUndo, onUndo)
            HistoryButton(Icons.AutoMirrored.Outlined.Redo, "Redo (Ctrl+Y)", canRedo, onRedo)
        }

        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(SurfaceContainerLow)
                    .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                    .testTag(if (sourceMode == TimelineTool.DRAW_PAD) "timeline-pad-selector" else "timeline-pattern-selector")
                    .padding(horizontal = Spacing.xs, vertical = 2.dp),
        ) {
            val itemWidth = (maxWidth - Spacing.xs * 4) / 5
            val sourceIndexes = if (sourceMode == TimelineTool.DRAW_PAD) (0 until 32).toList() else (0 until 16).toList()
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                state = sourceListState,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(sourceIndexes, key = { it }) { index ->
                    val isPad = sourceMode == TimelineTool.DRAW_PAD
                    val chipLabel = if (isPad) if (index < 16) "A${index + 1}" else "B${index - 15}" else "P${index + 1}"
                    SourceChip(
                        label = chipLabel,
                        selected =
                            if (isPad) {
                                padIndex == index && tool == TimelineTool.DRAW_PAD
                            } else {
                                patternId == index && tool == TimelineTool.DRAW_PATTERN
                            },
                        accent = if (isPad) Secondary else Primary,
                        modifier = Modifier
                            .width(itemWidth)
                            .testTag("timeline-source-chip-$chipLabel"),
                    ) {
                        if (isPad) onPadSelect(index) else onPatternSelect(index)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineToolButton(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(RadiusSm))
                .background(if (selected) accent.copy(alpha = 0.18f) else SurfaceContainerLow)
                .border(1.dp, if (selected) accent else OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) accent else OnSurface, style = LabelSmall)
    }
}

@Composable
private fun HistoryButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainerLow)
                .border(1.dp, if (enabled) OutlineVariant else OutlineVariant.copy(alpha = 0.45f), RoundedCornerShape(RadiusSm))
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) OnSurface else OnSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TimelineSelectionToolbar(
    selectedCount: Int,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDuplicate: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(SurfaceContainerHigh)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("$selectedCount selected", color = Primary, style = CaptionSmall)
        SelectionAction("Copy", onCopy)
        SelectionAction("Paste", onPaste)
        SelectionAction("Duplicate", onDuplicate)
        SelectionAction("Mute", onMute)
        SelectionAction("Delete", onDelete, StateRecording)
        SelectionAction("Clear", onClear)
    }
}

@Composable
private fun SelectionAction(label: String, onClick: () -> Unit, color: Color = OnSurface) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp)) {
        Text(label, color = color, style = CaptionSmall)
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
    onSwingChange: (Float) -> Unit,
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
                SwingButton(transport.swing, onSwingChange)
                ZoomStepButton(Icons.Outlined.ZoomOut) { onZoomChange(zoom - 0.2f) }
                Text(
                    "${(zoom * 100).toInt()}%",
                    color = OnSurfaceVariant,
                    style = CaptionSmall,
                    modifier = Modifier.testTag("timeline-zoom-indicator"),
                )
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
            SwingButton(transport.swing, onSwingChange)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text("Zoom", color = OnSurfaceVariant, style = CaptionSmall, modifier = Modifier.testTag("timeline-zoom-indicator"))
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
        onSwingChange = viewModel::setSwing,
        onNudge = viewModel::nudgePlayhead,
        onSnapChange = viewModel::setSnap,
        onZoomChange = viewModel::setZoom,
        onRestoreDeleted = viewModel::restoreLastDeletedClip,
        modifier = modifier,
    )
}

@Composable
private fun SourceChip(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(RadiusSm))
            .background(if (selected) accent.copy(alpha = 0.2f) else SurfaceContainerLow)
            .border(1.dp, if (selected) accent else OutlineVariant, RoundedCornerShape(RadiusSm))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) accent else OnSurface, style = LabelSmall)
    }
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
                .testTag("timeline-snap-indicator")
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
private fun SwingButton(
    swing: Float,
    onSwingChange: (Float) -> Unit,
) {
    val next = when {
        swing < 0.125f -> 0.25f
        swing < 0.375f -> 0.5f
        swing < 0.625f -> 0.75f
        else -> 0f
    }
    Box(
        modifier =
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(RadiusSm))
                .background(if (swing > 0f) Secondary.copy(alpha = 0.14f) else SurfaceContainerLow)
                .border(1.dp, if (swing > 0f) Secondary else OutlineVariant, RoundedCornerShape(RadiusSm))
                .clickable { onSwingChange(next) }
                .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text("Swing ${(swing * 100).toInt()}%", color = if (swing > 0f) Secondary else OnSurfaceVariant, style = CaptionSmall)
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
    transform: TimelineTransform,
    firstVisibleBar: Int,
    lastVisibleBar: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Box(modifier = modifier) {
        for (b in firstVisibleBar until lastVisibleBar) {
            Text(
                text = "${b + 1}",
                color = OnSurfaceVariant,
                style = CaptionSmall,
                modifier =
                    Modifier
                        .offset(x = with(density) { transform.tickToViewportPx(b * (PPQ * 4L)).toDp() })
                        .padding(start = Spacing.xs, top = Spacing.sm),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Clip Item
// ---------------------------------------------------------------------------

private enum class ClipDragMode {
    MOVE,
    RESIZE_LEFT,
    RESIZE_RIGHT,
}

private data class ClipResizePreview(
    val clipId: String,
    val edge: ClipResizeEdge,
    val deltaPx: Float,
)

private fun formatTimelinePosition(tick: Long): String {
    val ticksPerBar = PPQ * 4L
    val bar = tick.coerceAtLeast(0L) / ticksPerBar + 1L
    val beat = (tick.coerceAtLeast(0L) % ticksPerBar) / PPQ + 1L
    return "$bar:$beat"
}

private fun formatTimelineDuration(durationTicks: Long): String {
    val duration = durationTicks.coerceAtLeast(1L)
    val ticksPerBar = PPQ * 4L
    return when {
        duration % ticksPerBar == 0L -> {
            val bars = duration / ticksPerBar
            "$bars ${if (bars == 1L) "bar" else "bars"}"
        }
        duration % PPQ == 0L -> {
            val beats = duration / PPQ
            "$beats ${if (beats == 1L) "beat" else "beats"}"
        }
        duration == TICKS_PER_STEP.toLong() -> "1/16"
        else -> "$duration ticks"
    }
}

@Composable
private fun ClipItem(
    clip: Clip,
    pattern: Pattern?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    isSelected: Boolean,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onResizeStart: (ClipResizeEdge) -> Unit,
    onResize: (deltaPx: Float) -> Unit,
    onResizeEnd: () -> Unit,
    onResizeCancel: () -> Unit,
) {
    val density = LocalDensity.current
    val edgeTouchPx = with(density) { 20.dp.toPx() }
    val handleBandPx = with(density) { 24.dp.toPx() }
    val currentSelected by rememberUpdatedState(isSelected)
    val hue = clipBaseHue(clip)
    val bg = if (clip.mute) hue.copy(alpha = 0.5f) else hue.copy(alpha = if (isDragging) 0.9f else 0.6f)
    val edge =
        when {
            isDragging -> Primary
            isSelected -> Primary
            clip.mute -> OnSurfaceVariant
            clip is AudioClip -> Secondary
            else -> hue
        }
    var dragMode by remember(clip.id) { mutableStateOf(ClipDragMode.MOVE) }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(RadiusSm))
                .background(bg)
                .border(1.dp, edge, RoundedCornerShape(RadiusSm))
                .testTag("timeline-clip-${clip.id}")
                .pointerInput(clip.id) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val inHandleBand = offset.y <= handleBandPx
                                val nearLeft = offset.x <= edgeTouchPx
                                val nearRight = offset.x >= size.width - edgeTouchPx
                                dragMode = when {
                                    !currentSelected || clip.mute -> ClipDragMode.MOVE
                                    inHandleBand && nearLeft && nearRight && offset.x <= size.width / 2f -> ClipDragMode.RESIZE_LEFT
                                    inHandleBand && nearLeft && nearRight -> ClipDragMode.RESIZE_RIGHT
                                    inHandleBand && nearLeft -> ClipDragMode.RESIZE_LEFT
                                    inHandleBand && nearRight -> ClipDragMode.RESIZE_RIGHT
                                    else -> ClipDragMode.MOVE
                                }
                                when (dragMode) {
                                    ClipDragMode.MOVE -> onDragStart()
                                    ClipDragMode.RESIZE_LEFT -> onResizeStart(ClipResizeEdge.LEFT)
                                    ClipDragMode.RESIZE_RIGHT -> onResizeStart(ClipResizeEdge.RIGHT)
                                }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                when (dragMode) {
                                    ClipDragMode.MOVE -> onDrag(amount)
                                    ClipDragMode.RESIZE_LEFT,
                                    ClipDragMode.RESIZE_RIGHT,
                                    -> onResize(amount.x)
                                }
                            },
                            onDragEnd = {
                                when (dragMode) {
                                    ClipDragMode.MOVE -> onDragEnd()
                                    ClipDragMode.RESIZE_LEFT,
                                    ClipDragMode.RESIZE_RIGHT,
                                    -> onResizeEnd()
                                }
                                dragMode = ClipDragMode.MOVE
                            },
                            onDragCancel = {
                                val cancelledMode = dragMode
                                dragMode = ClipDragMode.MOVE
                                if (cancelledMode == ClipDragMode.MOVE) onDragCancel() else onResizeCancel()
                            },
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

            if (!clip.mute && isSelected) {
                TimelineResizeHandle(
                    edge = ClipResizeEdge.LEFT,
                    clipId = clip.id,
                    color = edge,
                )
                TimelineResizeHandle(
                    edge = ClipResizeEdge.RIGHT,
                    clipId = clip.id,
                    color = edge,
                )
            }
    }
}

@Composable
private fun BoxScope.TimelineResizeHandle(
    edge: ClipResizeEdge,
    clipId: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .align(if (edge == ClipResizeEdge.LEFT) Alignment.TopStart else Alignment.TopEnd)
                .size(width = 12.dp, height = 24.dp)
                .testTag(
                    if (edge == ClipResizeEdge.LEFT) {
                        "timeline-clip-start-handle-$clipId"
                    } else {
                        "timeline-clip-end-handle-$clipId"
                    },
                )
                .background(color.copy(alpha = 0.85f), RoundedCornerShape(RadiusSm)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            drawLine(
                color = OnSurface,
                start = Offset(centerX, 5.dp.toPx()),
                end = Offset(centerX, size.height - 5.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )
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
    transform: TimelineTransform,
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
            val ticksPerBar = (PPQ * 4).toLong()
            val visibleTicks = transform.visibleTickRange()
            val firstVisibleBar = (visibleTicks.first / ticksPerBar).toInt().coerceIn(0, totalBars)
            val lastVisibleBar = ((visibleTicks.last / ticksPerBar) + 1L).toInt().coerceIn(firstVisibleBar, totalBars)

            Canvas(modifier = Modifier.fillMaxSize()) {
                for (b in firstVisibleBar..lastVisibleBar) {
                    val x = transform.tickToViewportPx(b * ticksPerBar)
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
                        val x1 = transform.tickToViewportPx(p1.point.tick)
                        val y1 = (1f - p1.point.value) * size.height
                        val x2 = transform.tickToViewportPx(p2.point.tick)
                        val y2 = (1f - p2.point.value) * size.height
                        drawLine(Primary, Offset(x1, y1), Offset(x2, y2), strokeWidth = 2f)
                    }
                }
                for (p in sorted) {
                    val x = transform.tickToViewportPx(p.point.tick)
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
                                val tick = transform.viewportPxToTick(offset.x)
                                val value = 1f - (offset.y / hPx).coerceIn(0f, 1f)
                                onAddPoint(tick, value.coerceIn(0f, 1f))
                            }
                        },
            )

            // Draggable point overlays
            for (p in points) {
                val xDp = with(density) { transform.tickToViewportPx(p.point.tick).toDp() } - 12.dp
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
                                            transform.viewportPxToTick(
                                                transform.tickToViewportPx(p.point.tick) + dragOffset.x,
                                            )
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
