package com.jujidaw.ui.timeline

import com.jujidaw.model.PadClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun rawTickAndDeleteHitTestingUseMusicalBounds() {
        val clips =
            listOf(
                PadClip(id = "first", trackIndex = 2, startTick = 120L, durationTicks = 120L, padIndex = 0),
                PadClip(id = "second", trackIndex = 2, startTick = 240L, durationTicks = 120L, padIndex = 1),
            )

        assertEquals(240L, timelineRawTickAtViewportX(20f, 220f, 1f))
        assertEquals(setOf("second"), timelineClipIdsAtPoint(clips, trackIndex = 2, tick = 240L))
        assertEquals(emptySet<String>(), timelineClipIdsAtPoint(clips, trackIndex = 1, tick = 240L))
    }

    @Test
    fun resizeGeometryPreviewsEdgesAndHonorsMinimumWidth() {
        val left = timelineResizeGeometry(100f, 80f, 20f, ClipResizeEdge.LEFT, 25f)
        assertEquals(125f, left.leftPx, 0.001f)
        assertEquals(55f, left.widthPx, 0.001f)

        val leftClamped = timelineResizeGeometry(10f, 80f, 20f, ClipResizeEdge.LEFT, -50f)
        assertEquals(0f, leftClamped.leftPx, 0.001f)
        assertEquals(90f, leftClamped.widthPx, 0.001f)

        val rightClamped = timelineResizeGeometry(100f, 80f, 20f, ClipResizeEdge.RIGHT, -200f)
        assertEquals(20f, rightClamped.widthPx, 0.001f)
    }

    @Test
    fun resizePreviewUsesMusicalTicksAndKeepsOppositeEdgeStable() {
        val left = timelineResizeTicks(
            baseStartTick = 240L,
            baseDurationTicks = 480L,
            edge = ClipResizeEdge.LEFT,
            requestedDeltaTicks = -70L,
            snapResolution = 120L,
            free = false,
        )
        assertEquals(120L, left.startTick)
        assertEquals(600L, left.durationTicks)
        assertEquals(720L, left.endTick)

        val right = timelineResizeTicks(
            baseStartTick = 240L,
            baseDurationTicks = 480L,
            edge = ClipResizeEdge.RIGHT,
            requestedDeltaTicks = 70L,
            snapResolution = 120L,
            free = false,
        )
        assertEquals(240L, right.startTick)
        assertEquals(600L, right.durationTicks)
        assertEquals(840L, right.endTick)
    }

    @Test
    fun resizePreviewHonorsMinimumDurationAndFreeMode() {
        val clamped = timelineResizeTicks(
            baseStartTick = 240L,
            baseDurationTicks = 120L,
            edge = ClipResizeEdge.RIGHT,
            requestedDeltaTicks = -400L,
            snapResolution = 120L,
            free = false,
        )
        assertEquals(240L, clamped.startTick)
        assertEquals(120L, clamped.durationTicks)

        val free = timelineResizeTicks(
            baseStartTick = 240L,
            baseDurationTicks = 120L,
            edge = ClipResizeEdge.RIGHT,
            requestedDeltaTicks = 37L,
            snapResolution = 120L,
            free = true,
        )
        assertEquals(240L, free.startTick)
        assertEquals(157L, free.durationTicks)
    }

    @Test
    fun snappingLargeTicksDoesNotOverflow() {
        val snapped = snapTimelineTick(Long.MAX_VALUE, 120L)
        assertTrue(snapped in 0L..Long.MAX_VALUE)
        assertTrue(snapped <= Long.MAX_VALUE)
    }
}
