package com.jujidaw.ui.timeline

import com.jujidaw.model.PPQ
import com.jujidaw.model.PadClip
import com.jujidaw.model.TICKS_PER_STEP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineViewportPerformanceTest {
    @Test
    fun longTimelineKeepsRenderedClipWorkBoundedToViewport() {
        val transform = TimelineTransform(
            viewportWidthPx = 720f,
            horizontalScrollPx = 0f,
            pixelsPerBeat = 24f * 3f,
            zoom = 5f,
            density = 3f,
        )
        val clips = (0 until 4_000).map { index ->
            PadClip(
                id = "clip-$index",
                trackIndex = index % 16,
                startTick = index * TICKS_PER_STEP.toLong(),
                padIndex = index % 32,
            )
        }
        val visibleTicks = transform.visibleTickRange()
        val visible = timelineVisibleClips(
            clips = clips,
            firstVisibleTick = visibleTicks.first,
            lastVisibleTickExclusive = visibleTicks.last + 1L,
        )

        assertEquals("only two bars of clips intersect this 720 px viewport", 8, visible.size)
        assertTrue("viewport should not compose the full timeline", visible.size < clips.size / 100)
        assertTrue(visible.all { clip ->
            clip.startTick < visibleTicks.last + 1L &&
                timelineClipEndTick(clip) > visibleTicks.first
        })
    }

    @Test
    fun activeGestureClipRemainsComposedOutsideVisibleRange() {
        val farClip = PadClip(
            id = "far",
            trackIndex = 0,
            startTick = PPQ.toLong() * 100,
            padIndex = 0,
        )
        val visible = timelineVisibleClips(
            clips = listOf(farClip),
            firstVisibleTick = 0L,
            lastVisibleTickExclusive = PPQ.toLong(),
            pinnedClipId = farClip.id,
        )

        assertTrue(visible.single().id == farClip.id)
    }
}
