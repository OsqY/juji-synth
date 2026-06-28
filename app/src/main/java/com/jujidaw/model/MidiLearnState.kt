package com.jujidaw.model

/** Possible states of the MIDI Learn workflow. */
enum class MidiLearnMode {
    /** No MIDI learn operation in progress. */
    IDLE,

    /** Waiting for the next MIDI CC message to arrive. */
    LEARN_ACTIVE,

    /** A control has been selected; awaiting assignment. */
    CONTROL_SELECTED
}

/**
 * UI-level state holder for the MIDI Learn feature.
 *
 * Supports both legacy synth-param learn ([selectedParamId]) and
 * mixer/effect target learn ([selectedTarget]).
 */
data class MidiLearnState(
    val mode: MidiLearnMode = MidiLearnMode.IDLE,
    val selectedParamId: Int? = null,
    val selectedTarget: MidiTarget? = null,
    val pendingCcNumber: Int? = null
)
