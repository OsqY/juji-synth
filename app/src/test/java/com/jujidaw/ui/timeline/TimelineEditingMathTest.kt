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
    fun deleteHitSlopFindsShortClipWithoutDeletingAdjacentClip() {
        val clips =
            listOf(
                PadClip(id = "short", trackIndex = 2, startTick = 120L, durationTicks = 1L, padIndex = 0),
                PadClip(id = "adjacent", trackIndex = 2, startTick = 121L, durationTicks = 120L, padIndex = 1),
            )

        assertEquals(
            setOf("short"),
            timelineClipIdsAtPoint(clips, trackIndex = 2, tick = 118L, hitSlopTicks = 3L),
        )
        // An exact hit always wins over the fallback hit slop.
        assertEquals(
            setOf("adjacent"),
            timelineClipIdsAtPoint(clips, trackIndex = 2, tick = 121L, hitSlopTicks = 120L),
        )
    }

    @Test
    fun deleteHitTestingClampsInvalidNegativeTicksWithoutOverflow() {
        val clip = PadClip(
            id = "last",
            trackIndex = 0,
            startTick = Long.MAX_VALUE - 2L,
            durationTicks = 2L,
            padIndex = 0,
        )

        assertEquals(
            setOf("last"),
            timelineClipIdsAtPoint(
                listOf(clip),
                trackIndex = 0,
                tick = Long.MAX_VALUE,
                hitSlopTicks = Long.MAX_VALUE,
            ),
        )
        assertEquals(emptySet<String>(), timelineClipIdsAtPoint(listOf(clip), 0, -1L, 1L))
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

    @Test
    fun maximumDurationRoundsWithoutOverflow() {
        val normalized = normalizeTimelineDuration(Long.MAX_VALUE, 120L, free = false)

        assertTrue(normalized > 0L)
        assertTrue(normalized <= Long.MAX_VALUE)
        assertEquals(0L, normalized % 120L)
    }

    @Test
    fun clipEndsAndTickDeltasSaturateAtLongBounds() {
        val clip = PadClip(
            id = "extreme",
            trackIndex = 0,
            startTick = Long.MAX_VALUE - 10L,
            durationTicks = 120L,
            padIndex = 0,
        )

        assertEquals(Long.MAX_VALUE, timelineClipEndTick(clip))
        assertEquals(Long.MAX_VALUE, timelineSaturatingAdd(Long.MAX_VALUE - 1L, 10L))
        assertEquals(Long.MIN_VALUE, timelineSaturatingAdd(Long.MIN_VALUE + 1L, -10L))
    }

    @Test
    fun patternContentOffsetUsesModularArithmeticAtLongBounds() {
        val length = 1_920L
        val expected =
            Math.floorMod(
                Math.floorMod(Long.MAX_VALUE - 3L, length) +
                    Math.floorMod(Long.MAX_VALUE, length) -
                    Math.floorMod(Long.MIN_VALUE, length),
                length,
            )

        val offset =
            timelinePatternContentOffset(
                contentOffsetTicks = Long.MAX_VALUE - 3L,
                newStartTick = Long.MAX_VALUE,
                previousStartTick = Long.MIN_VALUE,
                patternLengthTicks = length,
            )

        assertEquals(expected, offset)
        assertTrue(offset in 0L until length)
    }

    @Test
    fun nonFiniteResizeAndZoomInputsProduceFiniteGeometry() {
        val geometry = timelineResizeGeometry(
            baseLeftPx = Float.NaN,
            baseWidthPx = Float.POSITIVE_INFINITY,
            minimumWidthPx = Float.NaN,
            edge = ClipResizeEdge.RIGHT,
            requestedDeltaPx = Float.NaN,
        )
        assertTrue(geometry.leftPx.isFinite())
        assertTrue(geometry.widthPx.isFinite())
        assertTrue(geometry.appliedDeltaPx.isFinite())

        val scroll = timelineZoomScroll(
            anchorViewportX = Float.NaN,
            scrollX = Float.NaN,
            oldTickWidthPx = Float.POSITIVE_INFINITY,
            newTickWidthPx = Float.NaN,
            panX = Float.NaN,
            maxScrollX = Float.POSITIVE_INFINITY,
        )
        assertTrue(scroll.isFinite())
        assertTrue(scroll >= 0f)
    }

    @Test
    fun edgeAutoScrollVelocityIsDirectionalAndZeroInTheMiddle() {
        assertEquals(0f, timelineEdgeAutoScrollVelocity(400f, 800f, 100f, 600f), 0.001f)
        assertEquals(-600f, timelineEdgeAutoScrollVelocity(0f, 800f, 100f, 600f), 0.001f)
        assertEquals(600f, timelineEdgeAutoScrollVelocity(800f, 800f, 100f, 600f), 0.001f)
        assertTrue(timelineEdgeAutoScrollVelocity(50f, 800f, 100f, 600f) < 0f)
        assertTrue(timelineEdgeAutoScrollVelocity(750f, 800f, 100f, 600f) > 0f)
    }

    @Test
    fun autoScrollDeltaClampsAtBothViewportBounds() {
        assertEquals(10f, timelineAutoScrollDelta(0f, 600f, 1f / 60f, 100f), 0.001f)
        assertEquals(-10f, timelineAutoScrollDelta(100f, -600f, 1f / 60f, 100f), 0.001f)
        assertEquals(10f, timelineAutoScrollDelta(90f, 600f, 1f / 60f, 100f), 0.001f)
        assertEquals(0f, timelineAutoScrollDelta(0f, -600f, 1f / 60f, 100f), 0.001f)
        assertEquals(0f, timelineAutoScrollDelta(50f, Float.NaN, 1f, 100f), 0.001f)
    }
}
