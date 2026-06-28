package com.jujidaw.model

import kotlinx.serialization.Serializable

/** Pulses per quarter note. All musical-time math uses this resolution. */
const val PPQ: Int = 480

/** Ticks per 16th note (one step-grid cell). */
const val TICKS_PER_STEP: Int = PPQ / 4

/** Ticks per beat (quarter note). */
const val TICKS_PER_BEAT: Int = PPQ

/** Musical position in bars / beats / ticks. All fields are 0-based. */
@Serializable
data class TransportPosition(
    val bar: Int = 0,
    val beat: Int = 0,
    val tick: Int = 0
) {
    /** Convert to absolute PPQ ticks assuming the given time signature. */
    fun toTicks(timeSignature: TimeSignature = TimeSignature()): Long {
        val ticksPerBar = PPQ * timeSignature.numerator.toLong()
        return bar * ticksPerBar + beat * PPQ.toLong() + tick
    }

    companion object {
        /** Build a position from absolute ticks. */
        fun fromTicks(ticks: Long, timeSignature: TimeSignature = TimeSignature()): TransportPosition {
            val ticksPerBar = PPQ * timeSignature.numerator.toLong()
            val bar = (ticks / ticksPerBar).toInt()
            val remainder = ticks % ticksPerBar
            val beat = (remainder / PPQ).toInt()
            val tick = (remainder % PPQ).toInt()
            return TransportPosition(bar, beat, tick)
        }
    }
}

@Serializable
data class TimeSignature(
    val numerator: Int = 4,
    val denominator: Int = 4
) {
    init {
        require(numerator > 0) { "Time signature numerator must be positive" }
        require(denominator > 0) { "Time signature denominator must be positive" }
    }
}

@Serializable
data class TransportState(
    val playing: Boolean = false,
    val recording: Boolean = false,
    val tempoBpm: Float = 120.0f,
    val position: TransportPosition = TransportPosition(),
    val timeSignature: TimeSignature = TimeSignature(),
    val swing: Float = 0.0f,
    val loopEnabled: Boolean = false,
    val loopStart: TransportPosition = TransportPosition(),
    val loopEnd: TransportPosition = TransportPosition(1, 0, 0),
    val punchEnabled: Boolean = false,
    val punchIn: TransportPosition = TransportPosition(),
    val punchOut: TransportPosition = TransportPosition()
) {
    init {
        require(tempoBpm in 30.0f..300.0f) { "Tempo must be between 30 and 300 BPM" }
        require(swing in 0.0f..1.0f) { "Swing must be between 0 and 1" }
    }
}
