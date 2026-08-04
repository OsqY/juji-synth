package com.jujidaw.ui.timeline

import com.jujidaw.model.Clip
import com.jujidaw.model.PPQ
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Single source of truth for musical-time to viewport-coordinate conversion.
 *
 * Clips and transport state stay in ticks. Pixel values are derived only when
 * rendering or resolving a pointer position inside the viewport.
 */
internal data class TimelineTransform(
    val viewportWidthPx: Float,
    val horizontalScrollPx: Float,
    /** Base pixels per beat before [zoom] is applied. */
    val pixelsPerBeat: Float,
    val zoom: Float,
    val density: Float,
) {
    private val safeViewportWidthPx: Float
        get() = timelineFiniteNonNegative(viewportWidthPx)

    private val safeHorizontalScrollPx: Float
        get() = timelineFiniteNonNegative(horizontalScrollPx)

    private val safePixelsPerBeat: Float
        get() = timelineFinitePositive(pixelsPerBeat, fallback = 1f)

    val pixelsPerTick: Float
        get() = timelineFinitePositive(
            timelineFiniteFloat(safePixelsPerBeat.toDouble() * sanitizeTimelineZoom(zoom) / PPQ),
            fallback = Float.MIN_VALUE,
        )

    val barWidthPx: Float
        get() = timelineFiniteNonNegative(
            timelineFiniteFloat(safePixelsPerBeat.toDouble() * sanitizeTimelineZoom(zoom) * 4.0),
        )

    fun tickToContentPx(tick: Long): Float =
        timelineFiniteFloat(tick.coerceAtLeast(0L).toDouble() * pixelsPerTick)

    fun tickToViewportPx(tick: Long): Float =
        timelineFiniteFloat(tickToContentPx(tick).toDouble() - safeHorizontalScrollPx)

    fun viewportPxToContentPx(x: Float): Float =
        timelineFiniteNonNegative(
            timelineFiniteFloat(timelineFiniteOrZero(x).toDouble() + safeHorizontalScrollPx),
        )

    fun contentPxToTick(x: Float): Long =
        timelineDoubleToLong(floor(timelineFiniteNonNegative(x).toDouble() / pixelsPerTick))

    fun viewportPxToTick(x: Float): Long =
        floor(viewportPxToContentPx(x) / pixelsPerTick)
            .toLong()
            .coerceAtLeast(0L)

    fun viewportPxToSnappedTick(x: Float, resolution: Long): Long =
        snapTimelineTick(viewportPxToTick(x), resolution)

    fun durationToPx(durationTicks: Long): Float =
        timelineFiniteFloat(durationTicks.coerceAtLeast(0L).toDouble() * pixelsPerTick)

    fun pxToDuration(px: Float): Long =
        timelineDoubleToLong(floor(timelineFiniteNonNegative(px).toDouble() / pixelsPerTick))

    fun pxDeltaToTicks(px: Float): Long =
        timelineDoubleToLong(timelineFiniteOrZero(px).toDouble() / pixelsPerTick, allowNegative = true)

    fun visibleTickRange(): LongRange {
        val first = viewportPxToTick(0f)
        if (first == Long.MAX_VALUE) return Long.MAX_VALUE..Long.MAX_VALUE
        val lastExclusive =
            timelineDoubleToLong(
                ceil(viewportPxToContentPx(safeViewportWidthPx).toDouble() / pixelsPerTick),
            )
                .coerceAtLeast(first + 1L)
        return first..(lastExclusive - 1L)
    }

    fun maxScroll(totalDurationTicks: Long): Float =
        timelineFiniteNonNegative(
            timelineFiniteFloat(durationToPx(totalDurationTicks).toDouble() - safeViewportWidthPx),
        )

    fun zoomAroundAnchor(
        anchorViewportPx: Float,
        previousZoom: Float,
        newZoom: Float,
        totalDurationTicks: Long,
    ): TimelineTransform {
        val safeAnchorViewportPx = timelineFiniteOrZero(anchorViewportPx)
        val safePreviousZoom = timelineFinitePositive(previousZoom, fallback = sanitizeTimelineZoom(zoom)).coerceAtLeast(0.01f)
        val previousPixelsPerTick =
            timelineFinitePositive(
                timelineFiniteFloat(safePixelsPerBeat.toDouble() * safePreviousZoom / PPQ),
                Float.MIN_VALUE,
            )
        val anchorTick = viewportPxToContentPx(safeAnchorViewportPx) / previousPixelsPerTick
        val next = copy(zoom = sanitizeTimelineZoom(newZoom, fallback = sanitizeTimelineZoom(zoom)))
        return next.copy(
            horizontalScrollPx =
                timelineFiniteFloat((anchorTick * next.pixelsPerTick - safeAnchorViewportPx).toDouble())
                    .coerceIn(0f, next.maxScroll(totalDurationTicks)),
        )
    }

    fun zoomAroundAnchor(
        anchorViewportPx: Float,
        previousZoom: Float,
        newZoom: Float,
        maxScrollPx: Float,
    ): TimelineTransform {
        val safeAnchorViewportPx = timelineFiniteOrZero(anchorViewportPx)
        val safePreviousZoom = timelineFinitePositive(previousZoom, fallback = sanitizeTimelineZoom(zoom)).coerceAtLeast(0.01f)
        val previousPixelsPerTick =
            timelineFinitePositive(
                timelineFiniteFloat(safePixelsPerBeat.toDouble() * safePreviousZoom / PPQ),
                Float.MIN_VALUE,
            )
        val anchorTick = viewportPxToContentPx(safeAnchorViewportPx) / previousPixelsPerTick
        val next = copy(zoom = sanitizeTimelineZoom(newZoom, fallback = sanitizeTimelineZoom(zoom)))
        val safeMaxScrollPx = timelineFiniteNonNegative(maxScrollPx, positiveInfinityFallback = Float.MAX_VALUE)
        return next.copy(
            horizontalScrollPx =
                timelineFiniteFloat((anchorTick * next.pixelsPerTick - safeAnchorViewportPx).toDouble())
                    .coerceIn(0f, safeMaxScrollPx),
        )
    }
}

/** Pure timeline math kept separate so gesture behaviour can be tested without Compose. */
internal fun snapTimelineTick(tick: Long, resolution: Long): Long {
    val safeResolution = resolution.coerceAtLeast(1L)
    val safeTick = tick.coerceAtLeast(0L)
    val quotient = safeTick / safeResolution
    val remainder = safeTick % safeResolution
    val half = safeResolution / 2L
    val roundUp = remainder > half || (safeResolution % 2L == 0L && remainder == half)
    val roundedQuotient =
        if (roundUp && quotient < Long.MAX_VALUE / safeResolution) quotient + 1L else quotient
    return roundedQuotient * safeResolution
}

internal fun normalizeTimelineDuration(
    duration: Long,
    resolution: Long,
    free: Boolean,
): Long {
    val minimum = if (free) 1L else resolution.coerceAtLeast(1L)
    val safeDuration = duration.coerceAtLeast(minimum)
    if (free) return safeDuration
    val quotient = safeDuration / minimum
    val remainder = safeDuration % minimum
    val half = minimum / 2L
    val roundUp = remainder > half || (minimum % 2L == 0L && remainder == half)
    val maxQuotient = Long.MAX_VALUE / minimum
    val roundedQuotient = if (roundUp && quotient < maxQuotient) quotient + 1L else quotient
    return (roundedQuotient * minimum).coerceAtLeast(minimum)
}

/** Convert a viewport x-coordinate into the translated timeline content space. */
internal fun timelineContentX(
    viewportX: Float,
    scrollX: Float,
): Float = TimelineTransform(Float.POSITIVE_INFINITY, scrollX, 1f, 1f, 1f).viewportPxToContentPx(viewportX)

/** Resolve a viewport x-coordinate to one snapped musical tick. */
internal fun timelineTickAtViewportX(
    viewportX: Float,
    scrollX: Float,
    tickWidthPx: Float,
    resolution: Long,
): Long {
    if (!tickWidthPx.isFinite() || tickWidthPx <= 0f) return 0L
    return TimelineTransform(Float.POSITIVE_INFINITY, scrollX, tickWidthPx * PPQ, 1f, 1f)
        .viewportPxToSnappedTick(viewportX, resolution)
}

internal fun timelineRawTickAtViewportX(
    viewportX: Float,
    scrollX: Float,
    tickWidthPx: Float,
): Long {
    if (!tickWidthPx.isFinite() || tickWidthPx <= 0f) return 0L
    return TimelineTransform(Float.POSITIVE_INFINITY, scrollX, tickWidthPx * PPQ, 1f, 1f)
        .viewportPxToTick(viewportX)
}

internal fun timelineClipIdsAtPoint(
    clips: List<Clip>,
    trackIndex: Int,
    tick: Long,
    hitSlopTicks: Long = 0L,
): Set<String> =
    clips
        .asSequence()
        .filter { it.trackIndex == trackIndex }
        .let { candidates ->
            val safeTick = tick.coerceAtLeast(0L)
            val exact =
                candidates
                    .filter { safeTick >= it.startTick && safeTick < timelineSaturatingAdd(it.startTick, it.durationTicks) }
                    .mapTo(linkedSetOf()) { it.id }
            if (exact.isNotEmpty() || hitSlopTicks <= 0L) {
                exact
            } else {
                // A very short clip can be smaller than a finger without
                // making adjacent clips overlap. If there is no exact hit,
                // choose only the nearest clip inside the musical hit slop.
                candidates
                    .mapNotNull { clip ->
                        val endExclusive = timelineSaturatingAdd(clip.startTick, clip.durationTicks)
                        val distance =
                            when {
                                safeTick < clip.startTick -> clip.startTick - safeTick
                                safeTick >= endExclusive -> timelineSaturatingAdd(safeTick - endExclusive, 1L)
                                else -> 0L
                            }
                        if (distance <= hitSlopTicks) clip to distance else null
                    }
                    .minWithOrNull(compareBy<Pair<Clip, Long>> { it.second }.thenBy { it.first.startTick })
                    ?.let { setOf(it.first.id) }
                    ?: emptySet()
            }
        }

/**
 * Keep composition bounded to the viewport while retaining clips involved in
 * an active gesture even when their preview moves outside the visible range.
 */
internal fun timelineVisibleClips(
    clips: List<Clip>,
    firstVisibleTick: Long,
    lastVisibleTickExclusive: Long,
    pinnedClipIds: Set<String> = emptySet(),
): List<Clip> {
    val safeFirstTick = firstVisibleTick.coerceAtLeast(0L)
    val safeLastTick = lastVisibleTickExclusive.coerceAtLeast(safeFirstTick)
    return clips.filter { clip ->
        clip.id in pinnedClipIds ||
            (clip.startTick < safeLastTick && timelineClipEndTick(clip) > safeFirstTick)
    }
}

internal enum class ClipResizeEdge {
    LEFT,
    RIGHT,
}

internal data class TimelineResizeGeometry(
    val leftPx: Float,
    val widthPx: Float,
    val appliedDeltaPx: Float,
)

/** Musical preview for a resize gesture. Pixel deltas are converted before this is calculated. */
internal data class TimelineResizeTicks(
    val startTick: Long,
    val durationTicks: Long,
) {
    val endTick: Long
        get() = timelineSaturatingAdd(startTick, durationTicks)
}

internal fun timelineResizeTicks(
    baseStartTick: Long,
    baseDurationTicks: Long,
    edge: ClipResizeEdge,
    requestedDeltaTicks: Long,
    snapResolution: Long,
    free: Boolean,
): TimelineResizeTicks {
    val minimumDuration = if (free) 1L else snapResolution.coerceAtLeast(1L)
    val safeStart = baseStartTick.coerceAtLeast(0L)
    val safeDuration = baseDurationTicks.coerceAtLeast(minimumDuration)
    val safeEnd = timelineSaturatingAdd(safeStart, safeDuration)
    val maximumStart = (safeEnd - minimumDuration).coerceAtLeast(0L)

    return when (edge) {
        ClipResizeEdge.LEFT -> {
            val requestedStart = timelineSaturatingAdd(safeStart, requestedDeltaTicks)
            val snappedStart =
                if (free || requestedDeltaTicks == 0L) requestedStart else snapTimelineTick(requestedStart, snapResolution)
            val start = snappedStart.coerceIn(0L, maximumStart)
            TimelineResizeTicks(startTick = start, durationTicks = (safeEnd - start).coerceAtLeast(minimumDuration))
        }
        ClipResizeEdge.RIGHT -> {
            val requestedEnd = timelineSaturatingAdd(safeEnd, requestedDeltaTicks)
            val snappedEnd =
                if (free || requestedDeltaTicks == 0L) requestedEnd else snapTimelineTick(requestedEnd, snapResolution)
            val end = snappedEnd.coerceAtLeast(timelineSaturatingAdd(safeStart, minimumDuration))
            TimelineResizeTicks(startTick = safeStart, durationTicks = (end - safeStart).coerceAtLeast(minimumDuration))
        }
    }
}

internal fun timelineResizeGeometry(
    baseLeftPx: Float,
    baseWidthPx: Float,
    minimumWidthPx: Float,
    edge: ClipResizeEdge?,
    requestedDeltaPx: Float,
): TimelineResizeGeometry {
    val safeLeft = timelineFiniteNonNegative(baseLeftPx)
    val safeMinimum = timelineFiniteNonNegative(minimumWidthPx)
    val safeWidth = timelineFiniteNonNegative(baseWidthPx).coerceAtLeast(safeMinimum)
    val safeRequestedDelta = timelineFiniteOrZero(requestedDeltaPx)
    val delta =
        when (edge) {
            ClipResizeEdge.LEFT -> safeRequestedDelta.coerceIn(-safeLeft, (safeWidth - safeMinimum).coerceAtLeast(0f))
            ClipResizeEdge.RIGHT -> safeRequestedDelta.coerceAtLeast(safeMinimum - safeWidth)
            null -> 0f
        }
    return when (edge) {
        ClipResizeEdge.LEFT -> TimelineResizeGeometry(safeLeft + delta, safeWidth - delta, delta)
        ClipResizeEdge.RIGHT -> TimelineResizeGeometry(safeLeft, safeWidth + delta, delta)
        null -> TimelineResizeGeometry(safeLeft, safeWidth, 0f)
    }
}

internal fun timelineSaturatingAdd(left: Long, right: Long): Long =
    when {
        right > 0L && left > Long.MAX_VALUE - right -> Long.MAX_VALUE
        right < 0L && (right == Long.MIN_VALUE || left < Long.MIN_VALUE - right) -> Long.MIN_VALUE
        else -> left + right
    }

internal fun timelinePatternContentOffset(
    contentOffsetTicks: Long,
    newStartTick: Long,
    previousStartTick: Long,
    patternLengthTicks: Long,
): Long {
    val length = patternLengthTicks.coerceAtLeast(1L)
    val base = Math.floorMod(contentOffsetTicks, length)
    val newStart = Math.floorMod(newStartTick, length)
    val previousStart = Math.floorMod(previousStartTick, length)
    val delta = Math.floorMod(newStart - previousStart, length)
    return if (base >= length - delta) {
        base - (length - delta)
    } else {
        base + delta
    }
}

internal fun timelineEdgeAutoScrollVelocity(
    pointerViewportX: Float,
    viewportWidthPx: Float,
    edgeWidthPx: Float,
    maxVelocityPxPerSecond: Float,
): Float {
    if (!pointerViewportX.isFinite() || !viewportWidthPx.isFinite() || viewportWidthPx <= 0f) return 0f
    val edge = edgeWidthPx.takeIf { it.isFinite() && it > 0f } ?: return 0f
    val maxVelocity = maxVelocityPxPerSecond.takeIf { it.isFinite() && it > 0f } ?: return 0f
    val zone = edge.coerceAtMost(viewportWidthPx / 2f)
    return when {
        pointerViewportX < zone -> -maxVelocity * ((zone - pointerViewportX) / zone).coerceIn(0f, 1f)
        pointerViewportX > viewportWidthPx - zone -> maxVelocity * ((pointerViewportX - (viewportWidthPx - zone)) / zone).coerceIn(0f, 1f)
        else -> 0f
    }
}

internal fun timelineAutoScrollDelta(
    currentScrollPx: Float,
    velocityPxPerSecond: Float,
    elapsedSeconds: Float,
    maxScrollPx: Float,
): Float {
    val safeCurrent = currentScrollPx.takeIf { it.isFinite() } ?: 0f
    val safeVelocity = velocityPxPerSecond.takeIf { it.isFinite() } ?: 0f
    val safeElapsed = elapsedSeconds.takeIf { it.isFinite() && it > 0f } ?: 0f
    val safeMax = maxScrollPx.takeIf { it.isFinite() && it > 0f } ?: 0f
    val next = (safeCurrent + safeVelocity * safeElapsed).coerceIn(0f, safeMax)
    return next - safeCurrent.coerceIn(0f, safeMax)
}

internal fun timelineMaxScroll(
    totalWidthPx: Float,
    viewportWidthPx: Float,
): Float =
    timelineFiniteNonNegative(
        timelineFiniteFloat(
            timelineFiniteNonNegative(totalWidthPx, positiveInfinityFallback = Float.MAX_VALUE).toDouble() -
                timelineFiniteNonNegative(viewportWidthPx),
        ),
    )

internal fun timelineZoomScroll(
    anchorViewportX: Float,
    scrollX: Float,
    oldTickWidthPx: Float,
    newTickWidthPx: Float,
    panX: Float,
    maxScrollX: Float,
): Float {
    val safeMaxScrollX = timelineFiniteNonNegative(maxScrollX, positiveInfinityFallback = Float.MAX_VALUE)
    val safeScrollX = timelineFiniteNonNegative(scrollX).coerceIn(0f, safeMaxScrollX)
    if (
        !oldTickWidthPx.isFinite() || oldTickWidthPx <= 0f ||
        !newTickWidthPx.isFinite() || newTickWidthPx <= 0f
    ) {
        return safeScrollX
    }
    val transform = TimelineTransform(
        viewportWidthPx = Float.POSITIVE_INFINITY,
        horizontalScrollPx = safeScrollX,
        pixelsPerBeat = oldTickWidthPx * PPQ,
        zoom = 1f,
        density = 1f,
    )
    return (
        transform
            .zoomAroundAnchor(
                anchorViewportPx = anchorViewportX,
                previousZoom = 1f,
                newZoom = newTickWidthPx / oldTickWidthPx,
                maxScrollPx = safeMaxScrollX,
            )
            .horizontalScrollPx - timelineFiniteOrZero(panX)
    ).coerceIn(0f, safeMaxScrollX)
}

internal fun sanitizeTimelineZoom(
    value: Float,
    fallback: Float = 1f,
): Float {
    val safeFallback = if (fallback.isFinite()) fallback.coerceIn(0.2f, 5f) else 1f
    return if (value.isFinite()) value.coerceIn(0.2f, 5f) else safeFallback
}

internal fun timelineClipEndTick(clip: Clip): Long =
    timelineSaturatingAdd(clip.startTick, clip.durationTicks)

private fun timelineFiniteOrZero(value: Float): Float =
    if (value.isFinite()) value else 0f

private fun timelineFinitePositive(
    value: Float,
    fallback: Float,
): Float =
    if (value.isFinite() && value > 0f) value else fallback.coerceAtLeast(Float.MIN_VALUE)

private fun timelineFiniteNonNegative(
    value: Float,
    positiveInfinityFallback: Float = 0f,
): Float =
    when {
        value == Float.POSITIVE_INFINITY -> positiveInfinityFallback.coerceAtLeast(0f)
        !value.isFinite() -> 0f
        else -> value.coerceAtLeast(0f)
    }

private fun timelineFiniteFloat(value: Double): Float =
    when {
        value.isNaN() -> 0f
        value >= Float.MAX_VALUE -> Float.MAX_VALUE
        value <= -Float.MAX_VALUE -> -Float.MAX_VALUE
        else -> value.toFloat()
    }

private fun timelineDoubleToLong(
    value: Double,
    allowNegative: Boolean = false,
): Long =
    when {
        value.isNaN() -> 0L
        value >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
        allowNegative && value <= Long.MIN_VALUE.toDouble() -> Long.MIN_VALUE
        value <= 0.0 -> if (allowNegative) value.toLong() else 0L
        else -> value.toLong()
    }
