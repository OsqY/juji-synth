package com.jujidaw.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineGestureStateTest {
    @Test
    fun pinchTakesPriorityOverScrollAndFinishesToIdle() {
        var state: TimelineGestureState = TimelineGestureState.Idle
        state = reduceTimelineGestureState(state, TimelineGestureEvent.BeginScroll)
        assertEquals(TimelineGestureState.Scrolling, state)
        state = reduceTimelineGestureState(state, TimelineGestureEvent.BeginPinch)
        assertEquals(TimelineGestureState.Pinching, state)
        state = reduceTimelineGestureState(state, TimelineGestureEvent.Finish)
        assertEquals(TimelineGestureState.Idle, state)
    }

    @Test
    fun moveResizeDeleteAndScrubAreExclusive() {
        val moving = reduceTimelineGestureState(
            TimelineGestureState.Idle,
            TimelineGestureEvent.BeginMove(setOf("a", "b")),
        )
        assertEquals(TimelineGestureState.MovingClip(setOf("a", "b")), moving)
        assertEquals(
            moving,
            reduceTimelineGestureState(moving, TimelineGestureEvent.BeginResizeStart("a")),
        )

        val resizing = reduceTimelineGestureState(
            TimelineGestureState.Idle,
            TimelineGestureEvent.BeginResizeEnd("a"),
        )
        assertEquals(TimelineGestureState.ResizingEnd("a"), resizing)
        assertEquals(
            resizing,
            reduceTimelineGestureState(resizing, TimelineGestureEvent.BeginScrub),
        )

        val deleting = reduceTimelineGestureState(
            TimelineGestureState.Idle,
            TimelineGestureEvent.BeginDelete(setOf("a")),
        )
        assertEquals(
            TimelineGestureState.Deleting(setOf("a", "b")),
            reduceTimelineGestureState(deleting, TimelineGestureEvent.BeginDelete(setOf("b"))),
        )
    }

    @Test
    fun cancelAlwaysReturnsToIdle() {
        val states = listOf(
            TimelineGestureState.Scrolling,
            TimelineGestureState.Pinching,
            TimelineGestureState.MovingClip(setOf("a")),
            TimelineGestureState.ResizingStart("a"),
            TimelineGestureState.ResizingEnd("a"),
            TimelineGestureState.Deleting(setOf("a")),
            TimelineGestureState.Scrubbing,
        )
        states.forEach { state ->
            assertEquals(
                TimelineGestureState.Idle,
                reduceTimelineGestureState(state, TimelineGestureEvent.Cancel),
            )
        }
    }

    @Test
    fun moveGestureKeepsAnExistingMultiSelection() {
        val selected = linkedSetOf("kick", "snare", "hat")

        assertEquals(selected, timelineMoveClipIds(selected, "snare"))
        assertEquals(setOf("tom"), timelineMoveClipIds(selected, "tom"))
        assertEquals(setOf("kick"), timelineMoveClipIds(emptySet(), "kick"))
    }
}
