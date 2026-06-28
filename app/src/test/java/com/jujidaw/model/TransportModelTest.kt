package com.jujidaw.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TransportModelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    @Test
    fun transportPositionRoundTripsThroughTicks() {
        val ts = TimeSignature(4, 4)
        val pos = TransportPosition(2, 1, 120)
        val ticks = pos.toTicks(ts)
        val restored = TransportPosition.fromTicks(ticks, ts)
        assertEquals(pos, restored)
    }

    @Test
    fun transportStateSerializesAndDeserializes() {
        val state = TransportState(
            playing = true,
            tempoBpm = 140f,
            position = TransportPosition(1, 2, 0),
            loopEnabled = true,
            loopStart = TransportPosition(0, 0, 0),
            loopEnd = TransportPosition(2, 0, 0)
        )
        val encoded = json.encodeToString(state)
        val decoded = json.decodeFromString(TransportState.serializer(), encoded)
        assertEquals(state, decoded)
    }
}
