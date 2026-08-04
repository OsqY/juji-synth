package com.jujidaw.ui.timeline

/** Exclusive gesture states used to arbitrate competing Timeline surfaces. */
internal sealed interface TimelineGestureState {
    data object Idle : TimelineGestureState
    data object Scrolling : TimelineGestureState
    data object Pinching : TimelineGestureState
    data class MovingClip(val clipIds: Set<String>) : TimelineGestureState
    data class ResizingStart(val clipId: String) : TimelineGestureState
    data class ResizingEnd(val clipId: String) : TimelineGestureState
    data class Deleting(val deletedIds: Set<String>) : TimelineGestureState
    data object Scrubbing : TimelineGestureState
}

internal sealed interface TimelineGestureEvent {
    data object BeginPinch : TimelineGestureEvent
    data object BeginScroll : TimelineGestureEvent
    data class BeginMove(val clipIds: Set<String>) : TimelineGestureEvent
    data class BeginResizeStart(val clipId: String) : TimelineGestureEvent
    data class BeginResizeEnd(val clipId: String) : TimelineGestureEvent
    data class BeginDelete(val clipIds: Set<String>) : TimelineGestureEvent
    data object UpdateDelete : TimelineGestureEvent
    data object BeginScrub : TimelineGestureEvent
    data object Finish : TimelineGestureEvent
    data object Cancel : TimelineGestureEvent
}

/**
 * Pure state reducer. Pinch has priority over a one-finger scroll that may have
 * started just before the second pointer arrived; all other gestures are
 * exclusive once claimed by their surface.
 */
internal fun reduceTimelineGestureState(
    state: TimelineGestureState,
    event: TimelineGestureEvent,
): TimelineGestureState =
    when (event) {
        TimelineGestureEvent.BeginPinch -> TimelineGestureState.Pinching
        TimelineGestureEvent.BeginScroll ->
            if (state == TimelineGestureState.Idle || state == TimelineGestureState.Scrolling) {
                TimelineGestureState.Scrolling
            } else {
                state
            }
        is TimelineGestureEvent.BeginMove ->
            if (state == TimelineGestureState.Idle) {
                TimelineGestureState.MovingClip(event.clipIds)
            } else {
                state
            }
        is TimelineGestureEvent.BeginResizeStart ->
            if (state == TimelineGestureState.Idle) {
                TimelineGestureState.ResizingStart(event.clipId)
            } else {
                state
            }
        is TimelineGestureEvent.BeginResizeEnd ->
            if (state == TimelineGestureState.Idle) {
                TimelineGestureState.ResizingEnd(event.clipId)
            } else {
                state
            }
        is TimelineGestureEvent.BeginDelete ->
            when (state) {
                TimelineGestureState.Idle -> TimelineGestureState.Deleting(event.clipIds)
                is TimelineGestureState.Deleting -> TimelineGestureState.Deleting(state.deletedIds + event.clipIds)
                else -> state
            }
        TimelineGestureEvent.UpdateDelete -> state
        TimelineGestureEvent.BeginScrub ->
            if (state == TimelineGestureState.Idle) TimelineGestureState.Scrubbing else state
        TimelineGestureEvent.Finish,
        TimelineGestureEvent.Cancel,
        -> TimelineGestureState.Idle
    }

/** Keep an existing multi-selection when one of its clips starts a move gesture. */
internal fun timelineMoveClipIds(
    selectedClipIds: Set<String>,
    anchorClipId: String,
): Set<String> =
    if (anchorClipId in selectedClipIds) selectedClipIds else setOf(anchorClipId)

internal fun TimelineGestureState.ownsMove(clipIds: Set<String>): Boolean =
    this is TimelineGestureState.MovingClip && this.clipIds == clipIds

internal fun TimelineGestureState.ownsResize(
    clipId: String,
    edge: ClipResizeEdge,
): Boolean =
    when (edge) {
        ClipResizeEdge.LEFT -> this == TimelineGestureState.ResizingStart(clipId)
        ClipResizeEdge.RIGHT -> this == TimelineGestureState.ResizingEnd(clipId)
    }
