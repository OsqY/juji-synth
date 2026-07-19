package com.jujidaw.ui.timeline

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
