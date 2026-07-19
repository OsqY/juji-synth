package com.jujidaw.project

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A live pad gesture captured at the transport position where it occurred.
 * Pad indexes are global: Bank A is 0..15 and Bank B is 16..31.
 */
sealed interface PadPerformanceEvent {
    val padIndex: Int
    val sampleTime: Long

    data class On(
        override val padIndex: Int,
        val velocity: Int,
        override val sampleTime: Long,
    ) : PadPerformanceEvent

    data class Off(
        override val padIndex: Int,
        override val sampleTime: Long,
    ) : PadPerformanceEvent
}

/**
 * App-scoped stream of live pad performance events for recording consumers.
 * Events are intentionally not replayed: a recorder only receives gestures
 * played while it is actively collecting.
 */
object PadPerformanceEventBus {
    private val _events = MutableSharedFlow<PadPerformanceEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<PadPerformanceEvent> = _events.asSharedFlow()

    fun emitNoteOn(
        padIndex: Int,
        velocity: Int,
        sampleTime: Long,
    ) {
        _events.tryEmit(PadPerformanceEvent.On(padIndex, velocity, sampleTime))
    }

    fun emitNoteOff(
        padIndex: Int,
        sampleTime: Long,
    ) {
        _events.tryEmit(PadPerformanceEvent.Off(padIndex, sampleTime))
    }
}
