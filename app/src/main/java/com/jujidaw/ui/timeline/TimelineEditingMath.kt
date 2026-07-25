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
    val pixelsPerTick: Float
        get() = (pixelsPerBeat * zoom / PPQ).coerceAtLeast(Float.MIN_VALUE)

    val barWidthPx: Float
        get() = pixelsPerBeat * zoom * 4f

    fun tickToContentPx(tick: Long): Float = tick.coerceAtLeast(0L) * pixelsPerTick

    fun tickToViewportPx(tick: Long): Float = tickToContentPx(tick) - horizontalScrollPx

    fun viewportPxToContentPx(x: Float): Float = (x + horizontalScrollPx).coerceAtLeast(0f)

    fun contentPxToTick(x: Float): Long =
        floor(x.coerceAtLeast(0f) / pixelsPerTick)
            .toLong()
            .coerceAtLeast(0L)

    fun viewportPxToTick(x: Float): Long =
        floor(viewportPxToContentPx(x) / pixelsPerTick)
            .toLong()
            .coerceAtLeast(0L)

    fun viewportPxToSnappedTick(x: Float, resolution: Long): Long =
        snapTimelineTick(viewportPxToTick(x), resolution)

    fun durationToPx(durationTicks: Long): Float = durationTicks.coerceAtLeast(0L) * pixelsPerTick

    fun pxToDuration(px: Float): Long =
        floor(px.coerceAtLeast(0f) / pixelsPerTick)
            .toLong()
            .coerceAtLeast(0L)

    fun pxDeltaToTicks(px: Float): Long = (px / pixelsPerTick).toLong()

    fun visibleTickRange(): LongRange {
        val first = viewportPxToTick(0f)
        val lastExclusive =
            ceil(((horizontalScrollPx + viewportWidthPx).coerceAtLeast(horizontalScrollPx)) / pixelsPerTick)
                .toLong()
                .coerceAtLeast(first + 1L)
        return first..(lastExclusive - 1L)
    }

    fun maxScroll(totalDurationTicks: Long): Float =
        (durationToPx(totalDurationTicks) - viewportWidthPx).coerceAtLeast(0f)

    fun zoomAroundAnchor(
        anchorViewportPx: Float,
        previousZoom: Float,
        newZoom: Float,
        totalDurationTicks: Long,
    ): TimelineTransform {
        val safePreviousZoom = previousZoom.coerceAtLeast(0.01f)
        val anchorTick =
            ((anchorViewportPx + horizontalScrollPx).coerceAtLeast(0f) /
                (pixelsPerBeat * safePreviousZoom / PPQ).coerceAtLeast(Float.MIN_VALUE))
        val next = copy(zoom = newZoom.coerceIn(0.2f, 5f))
        return next.copy(
            horizontalScrollPx =
                (anchorTick * next.pixelsPerTick - anchorViewportPx)
                    .coerceIn(0f, next.maxScroll(totalDurationTicks)),
        )
    }

    fun zoomAroundAnchor(
        anchorViewportPx: Float,
        previousZoom: Float,
        newZoom: Float,
        maxScrollPx: Float,
    ): TimelineTransform {
        val safePreviousZoom = previousZoom.coerceAtLeast(0.01f)
        val anchorTick = viewportPxToContentPx(anchorViewportPx) /
            (pixelsPerBeat * safePreviousZoom / PPQ).coerceAtLeast(Float.MIN_VALUE)
        val next = copy(zoom = newZoom.coerceIn(0.2f, 5f))
        return next.copy(
            horizontalScrollPx =
                (anchorTick * next.pixelsPerTick - anchorViewportPx)
                    .coerceIn(0f, maxScrollPx),
        )
    }
}

/** Pure timeline math kept separate so gesture behaviour can be tested without Compose. */
internal fun snapTimelineTick(tick: Long, resolution: Long): Long {
    val safeResolution = resolution.coerceAtLeast(1L)
    return ((tick.coerceAtLeast(0L) + safeResolution / 2L) / safeResolution) * safeResolution
}

internal fun normalizeTimelineDuration(
    duration: Long,
    resolution: Long,
    free: Boolean,
): Long {
    val minimum = if (free) 1L else resolution.coerceAtLeast(1L)
    val safeDuration = duration.coerceAtLeast(minimum)
    if (free) return safeDuration
    return ((safeDuration + minimum / 2L) / minimum) * minimum
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
    if (tickWidthPx <= 0f) return 0L
    return TimelineTransform(Float.POSITIVE_INFINITY, scrollX, tickWidthPx * PPQ, 1f, 1f)
        .viewportPxToSnappedTick(viewportX, resolution)
}

internal fun timelineRawTickAtViewportX(
    viewportX: Float,
    scrollX: Float,
    tickWidthPx: Float,
): Long {
    if (tickWidthPx <= 0f) return 0L
    return TimelineTransform(Float.POSITIVE_INFINITY, scrollX, tickWidthPx * PPQ, 1f, 1f)
        .viewportPxToTick(viewportX)
}

internal fun timelineClipIdsAtPoint(
    clips: List<Clip>,
    trackIndex: Int,
    tick: Long,
): Set<String> =
    clips
        .asSequence()
        .filter { it.trackIndex == trackIndex && tick >= it.startTick && tick < it.startTick + it.durationTicks }
        .mapTo(linkedSetOf()) { it.id }

internal enum class ClipResizeEdge {
    LEFT,
    RIGHT,
}

internal data class TimelineResizeGeometry(
    val leftPx: Float,
    val widthPx: Float,
    val appliedDeltaPx: Float,
)

internal fun timelineResizeGeometry(
    baseLeftPx: Float,
    baseWidthPx: Float,
    minimumWidthPx: Float,
    edge: ClipResizeEdge?,
    requestedDeltaPx: Float,
): TimelineResizeGeometry {
    val safeLeft = baseLeftPx.coerceAtLeast(0f)
    val safeMinimum = minimumWidthPx.coerceAtLeast(0f)
    val safeWidth = baseWidthPx.coerceAtLeast(safeMinimum)
    val delta =
        when (edge) {
            ClipResizeEdge.LEFT -> requestedDeltaPx.coerceIn(-safeLeft, (safeWidth - safeMinimum).coerceAtLeast(0f))
            ClipResizeEdge.RIGHT -> requestedDeltaPx.coerceAtLeast(safeMinimum - safeWidth)
            null -> 0f
        }
    return when (edge) {
        ClipResizeEdge.LEFT -> TimelineResizeGeometry(safeLeft + delta, safeWidth - delta, delta)
        ClipResizeEdge.RIGHT -> TimelineResizeGeometry(safeLeft, safeWidth + delta, delta)
        null -> TimelineResizeGeometry(safeLeft, safeWidth, 0f)
    }
}

internal fun timelineMaxScroll(
    totalWidthPx: Float,
    viewportWidthPx: Float,
): Float = (totalWidthPx - viewportWidthPx).coerceAtLeast(0f)

internal fun timelineZoomScroll(
    anchorViewportX: Float,
    scrollX: Float,
    oldTickWidthPx: Float,
    newTickWidthPx: Float,
    panX: Float,
    maxScrollX: Float,
): Float {
    if (oldTickWidthPx <= 0f || newTickWidthPx <= 0f) return scrollX.coerceIn(0f, maxScrollX)
    val transform = TimelineTransform(
        viewportWidthPx = Float.POSITIVE_INFINITY,
        horizontalScrollPx = scrollX,
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
                maxScrollPx = maxScrollX,
            )
            .horizontalScrollPx - panX
    ).coerceIn(0f, maxScrollX)
}
