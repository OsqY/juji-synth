package com.jujidaw.project

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PadPerformanceEventBusTest {
    @Test
    fun emitNoteOn_publishesGlobalPadVelocityAndSampleTime() = runBlocking {
        val event = async(start = CoroutineStart.UNDISPATCHED) { PadPerformanceEventBus.events.first() }

        PadPerformanceEventBus.emitNoteOn(padIndex = 21, velocity = 96, sampleTime = 4_410L)

        assertEquals(PadPerformanceEvent.On(21, 96, 4_410L), event.await())
    }

    @Test
    fun emitNoteOff_publishesGlobalPadAndSampleTime() = runBlocking {
        val event = async(start = CoroutineStart.UNDISPATCHED) { PadPerformanceEventBus.events.first() }

        PadPerformanceEventBus.emitNoteOff(padIndex = 4, sampleTime = 8_820L)

        assertEquals(PadPerformanceEvent.Off(4, 8_820L), event.await())
    }
}
