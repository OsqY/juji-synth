package com.jujidaw.model

import kotlinx.serialization.Serializable

/** A single cell in the step-sequencer grid. */
@Serializable
data class Step(
    val note: Int = -1, // MIDI note 0..127, -1 = rest
    val velocity: Float = 0.75f, // 0.0..1.0
    val gate: Float = 0.8f, // 0.0..1.0, proportion of step held
    val probability: Float = 1.0f, // 0.0..1.0, chance of triggering
) {
    init {
        require(note in -1..127) { "Step note must be between -1 and 127" }
        require(velocity in 0.0f..1.0f) { "Step velocity must be between 0 and 1" }
        require(gate in 0.0f..1.0f) { "Step gate must be between 0 and 1" }
        require(probability in 0.0f..1.0f) { "Step probability must be between 0 and 1" }
    }
}

/** A note inside a pattern, stored as absolute ticks from pattern start. */
@Serializable
data class NoteEvent(
    val note: Int, // MIDI note 0..127
    val velocity: Float, // 0.0..1.0
    val startTick: Long, // tick offset from start of pattern
    val durationTicks: Long, // must be > 0
    val trackIndex: Int = 0, // mixer channel target, denormalized for convenience
    val padIndex: Int = -1, // pad to trigger (-1 = legacy/unknown, use noteOn path)
) {
    init {
        require(note in 0..127) { "Note must be between 0 and 127" }
        require(velocity in 0.0f..1.0f) { "Note velocity must be between 0 and 1" }
        require(startTick >= 0) { "Note start tick must be non-negative" }
        require(durationTicks > 0) { "Note duration must be positive" }
        require(trackIndex in 0..15) { "Track index must be between 0 and 15" }
        require(padIndex in -1..15) { "Pad index must be between -1 and 15" }
    }
}

/** A musical pattern. Patterns are referenced by timeline clips and by the
 *  pattern launcher. Length is authoritative in ticks; `lengthSteps` is a UI
 *  hint for the step-grid view.
 */
@Serializable
data class Pattern(
    // 0..15 = user patterns; 1000..1015 = cached pad-trigger patterns
    // (see TimelineViewModel.PAD_PATTERN_ID_BASE). Resolved by id via
    // List.find, never used as a fixed-size array index.
    val id: Int,
    val name: String = "Pattern ${id + 1}",
    val trackIndex: Int = 0, // default mixer channel
    val lengthSteps: Int = 16, // 1..64, step-grid length
    val lengthTicks: Long = lengthSteps * TICKS_PER_STEP.toLong(),
    val notes: List<NoteEvent> = emptyList(), // canonical representation
    val steps: List<Step?> = emptyList(), // step-grid mirror (optional)
) {
    init {
        require(id >= 0) { "Pattern id must be non-negative" }
        require(trackIndex in 0..15) { "Track index must be between 0 and 15" }
        require(lengthSteps in 1..64) { "Pattern length must be between 1 and 64 steps" }
        require(lengthTicks > 0) { "Pattern length in ticks must be positive" }
    }

    /** Convenience: build a 16-step pattern from the legacy `SequencerStep` list. */
    companion object {
        fun fromSequencerSteps(
            id: Int,
            steps: List<com.jujidaw.model.SequencerStep>,
            trackIndex: Int = 0,
        ): Pattern {
            val noteEvents =
                steps.mapIndexedNotNull { index, step ->
                    if (step.note < 0) {
                        null
                    } else {
                        NoteEvent(
                            note = step.note.coerceIn(0, 127),
                            velocity = (step.velocity / 127f).coerceIn(0f, 1f),
                            startTick = index * TICKS_PER_STEP.toLong(),
                            durationTicks = (TICKS_PER_STEP * step.gate).toLong().coerceAtLeast(1L),
                        )
                    }
                }
            return Pattern(
                id = id,
                trackIndex = trackIndex,
                lengthSteps = steps.size.coerceIn(1, 64),
                notes = noteEvents,
                steps = steps.map { Step(it.note, it.velocity / 127f, it.gate) },
            )
        }
    }
}
