package com.jujidaw.ui.timeline

import com.jujidaw.model.Clip

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
): Float = (viewportX + scrollX).coerceAtLeast(0f)

/** Resolve a viewport x-coordinate to one snapped musical tick. */
internal fun timelineTickAtViewportX(
    viewportX: Float,
    scrollX: Float,
    tickWidthPx: Float,
    resolution: Long,
): Long {
    if (tickWidthPx <= 0f) return 0L
    return snapTimelineTick((timelineContentX(viewportX, scrollX) / tickWidthPx).toLong(), resolution)
}

internal fun timelineRawTickAtViewportX(
    viewportX: Float,
    scrollX: Float,
    tickWidthPx: Float,
): Long {
    if (tickWidthPx <= 0f) return 0L
    return (timelineContentX(viewportX, scrollX) / tickWidthPx).toLong().coerceAtLeast(0L)
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
    val anchorTick = timelineContentX(anchorViewportX, scrollX) / oldTickWidthPx
    return (anchorTick * newTickWidthPx - anchorViewportX - panX).coerceIn(0f, maxScrollX)
}
