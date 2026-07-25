package com.jujidaw.ui.timeline

import com.jujidaw.model.PPQ
import com.jujidaw.model.TICKS_PER_STEP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTransformTest {
    private val totalBars = 200
    private val totalDurationTicks = (totalBars * PPQ * 4).toLong()
    private val viewportWidths = listOf(240f, 720f, 1440f)
    private val densities = listOf(1f, 2f, 3f, 4f)
    private val zooms = listOf(0.25f, 0.5f, 1f, 2f, 3f, 5f)

    @Test
    fun tickAndViewportConversionsRoundTripAcrossZoomDensityAndScroll() {
        for (density in densities) {
            for (zoom in zooms) {
                for (viewportWidth in viewportWidths) {
                    val basePixelsPerBeat = 24f * density
                    val unscrolled = TimelineTransform(viewportWidth, 0f, basePixelsPerBeat, zoom, density)
                    val maxScroll = unscrolled.maxScroll(totalDurationTicks)
                    val scrollPositions = listOf(0f, maxScroll / 2f, maxScroll)
                    for (scroll in scrollPositions) {
                        val transform = unscrolled.copy(horizontalScrollPx = scroll)
                        val ticks = listOf(
                            0L,
                            TICKS_PER_STEP.toLong(),
                            4_800L,
                            (totalDurationTicks / 2L).coerceAtLeast(0L),
                            totalDurationTicks - TICKS_PER_STEP,
                        )
                        for (tick in ticks) {
                            val viewportX = transform.tickToViewportPx(tick)
                            val roundTrip = transform.viewportPxToTick(viewportX)
                            assertTrue(
                                "density=$density zoom=$zoom scroll=$scroll tick=$tick roundTrip=$roundTrip",
                                kotlin.math.abs(roundTrip - tick) <= 1L,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun zoomAroundAnchorKeepsTheSameMusicalTickUnderTheGesture() {
        for (density in densities) {
            val before = TimelineTransform(720f, 0f, 24f * density, 1f, density)
            val anchorTick = 10_000L
            val anchorViewportX = before.tickToViewportPx(anchorTick)

            for (newZoom in zooms.filter { it >= before.zoom }) {
                val after = before.zoomAroundAnchor(
                    anchorViewportPx = anchorViewportX,
                    previousZoom = before.zoom,
                    newZoom = newZoom,
                    totalDurationTicks = totalDurationTicks,
                )
                assertEquals(
                    "density=$density zoom=$newZoom",
                    anchorTick,
                    after.viewportPxToTick(anchorViewportX),
                )
            }

            val zoomedOut = before.zoomAroundAnchor(
                anchorViewportPx = 0f,
                previousZoom = before.zoom,
                newZoom = 0.25f,
                totalDurationTicks = totalDurationTicks,
            )
            assertEquals(0L, zoomedOut.viewportPxToTick(0f))
        }
    }

    @Test
    fun snapUsesTheSameCellAfterZoomAndScroll() {
        val cellTicks = TICKS_PER_STEP.toLong()
        for (zoom in zooms) {
            val transform = TimelineTransform(480f, 1_000f, 24f, zoom, 1f)
            val cellStart = 16L * cellTicks
            val tapX = transform.tickToViewportPx(cellStart)
            assertEquals(cellStart, transform.viewportPxToSnappedTick(tapX, cellTicks))
        }
    }

    @Test
    fun playheadAndClipAtTheSameTickShareTheSameViewportPosition() {
        for (density in densities) {
            for (zoom in zooms) {
                val transform = TimelineTransform(720f, 3_000f, 24f * density, zoom, density)
                val tick = 12_345L
                val clipLeft = transform.tickToContentPx(tick) - transform.horizontalScrollPx
                val playhead = transform.tickToViewportPx(tick)
                assertEquals(clipLeft, playhead, 0.0001f)
            }
        }
    }

    @Test
    fun visibleRangeIsOrderedAndNonNegativeAtScrollBounds() {
        for (density in densities) {
            for (zoom in zooms) {
                val base = TimelineTransform(320f, 0f, 24f * density, zoom, density)
                val maxScroll = base.maxScroll(totalDurationTicks)
                for (scroll in listOf(0f, maxScroll / 2f, maxScroll)) {
                    val range = base.copy(horizontalScrollPx = scroll).visibleTickRange()
                    assertTrue(range.first >= 0L)
                    assertTrue(range.last >= range.first)
                    assertTrue(range.last <= totalDurationTicks + PPQ * 4L)
                }
            }
        }
    }

    @Test
    fun extremeTimelineValuesRemainFiniteWithoutLayoutSizedPixelStorage() {
        val transform = TimelineTransform(240f, 0f, 24f * 4f, 5f, 4f)
        val longDuration = 100_000_000L
        val width = transform.durationToPx(longDuration)
        assertTrue(width.isFinite())
        assertTrue(width > 0f)
        assertTrue(transform.tickToViewportPx(longDuration).isFinite())
        assertTrue(transform.pxToDuration(width) > 0L)
    }
}
