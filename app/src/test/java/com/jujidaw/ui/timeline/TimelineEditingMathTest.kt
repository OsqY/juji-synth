package com.jujidaw.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineEditingMathTest {
    @Test
    fun sixteenStepGridSnapsAdjacentPlacements() {
        val step = 480L / 4L
        assertEquals(0L, snapTimelineTick(0L, step))
        assertEquals(step, snapTimelineTick(step - 1L, step))
        assertEquals(step * 2L, snapTimelineTick(step * 2L + 1L, step))
    }

    @Test
    fun padDurationStartsAtOneCellAndRoundsToGrid() {
        val step = 120L
        assertEquals(step, normalizeTimelineDuration(1L, step, false))
        assertEquals(step * 2L, normalizeTimelineDuration(step + step / 2L + 1L, step, false))
    }

    @Test
    fun freeModeKeepsExactDurationButStillHasOneTickMinimum() {
        assertEquals(37L, normalizeTimelineDuration(37L, 120L, true))
        assertEquals(1L, normalizeTimelineDuration(0L, 120L, true))
    }

    @Test
    fun viewportCoordinatesIncludeScrollExactlyOnce() {
        val tickWidth = 10f
        // The sixth cell remains the sixth cell after the content is scrolled.
        assertEquals(6L, timelineTickAtViewportX(50f, 10f, tickWidth, 1L))
        assertEquals(12f, timelineContentX(7f, 5f), 0.001f)
    }

    @Test
    fun zoomScrollKeepsAnchorTickStable() {
        val next = timelineZoomScroll(
            anchorViewportX = 100f,
            scrollX = 200f,
            oldTickWidthPx = 10f,
            newTickWidthPx = 20f,
            panX = 0f,
            maxScrollX = 10_000f,
        )
        assertEquals(500f, next, 0.001f)
    }

    @Test
    fun maxScrollUsesMeasuredViewport() {
        assertEquals(700f, timelineMaxScroll(1000f, 300f), 0.001f)
        assertEquals(0f, timelineMaxScroll(1000f, 1200f), 0.001f)
    }
}
