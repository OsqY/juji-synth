package com.jujidaw.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes the destination of a MIDI CC mapping or note routing.
 *
 * New mappings should use these sealed subtypes. Legacy mappings that only
 * stored a raw [paramId] are interpreted as [SynthParam] during migration.
 */
@Serializable
sealed class MidiTarget {

    /** A synth engine parameter (legacy default). */
    @Serializable
    @SerialName("synthParam")
    data class SynthParam(val paramId: Int) : MidiTarget()

    /** Mixer channel fader level (dB). */
    @Serializable
    @SerialName("chFader")
    data class ChannelFader(val track: Int) : MidiTarget()

    /** Mixer channel pan (-1 … 1). */
    @Serializable
    @SerialName("chPan")
    data class ChannelPan(val track: Int) : MidiTarget()

    /** Mixer channel mute toggle. */
    @Serializable
    @SerialName("chMute")
    data class ChannelMute(val track: Int) : MidiTarget()

    /** Mixer channel solo toggle. */
    @Serializable
    @SerialName("chSolo")
    data class ChannelSolo(val track: Int) : MidiTarget()

    /** Mixer channel record-arm toggle. */
    @Serializable
    @SerialName("chArm")
    data class ChannelArm(val track: Int) : MidiTarget()

    /** Send level to a bus on a given track. */
    @Serializable
    @SerialName("sendLevel")
    data class SendLevel(val track: Int, val bus: Int) : MidiTarget()

    /** Bus (return) fader level. */
    @Serializable
    @SerialName("busFader")
    data class BusFader(val bus: Int) : MidiTarget()

    /** Master channel fader. */
    @Serializable
    @SerialName("masterFader")
    data object MasterFader : MidiTarget()

    /** A parameter of an insert effect. */
    @Serializable
    @SerialName("insertParam")
    data class InsertParam(val track: Int, val slot: Int, val paramId: Int) : MidiTarget()

    /** A live performance FX pad (stutter, gate, …). */
    @Serializable
    @SerialName("performFx")
    data class PerformFx(val type: String) : MidiTarget()

    // ── Helpers ────────────────────────────────────────────────────────

    /** Human-readable label for UI display. */
    val displayLabel: String
        get() = when (this) {
            is SynthParam -> "Synth Param $paramId"
            is ChannelFader -> "Track ${track + 1} Fader"
            is ChannelPan -> "Track ${track + 1} Pan"
            is ChannelMute -> "Track ${track + 1} Mute"
            is ChannelSolo -> "Track ${track + 1} Solo"
            is ChannelArm -> "Track ${track + 1} Arm"
            is SendLevel -> "Track ${track + 1} Send ${'A' + bus}"
            is BusFader -> "Bus ${bus + 1} Fader"
            is MasterFader -> "Master Fader"
            is InsertParam -> "Track ${track + 1} FX$slot P$paramId"
            is PerformFx -> "FX: $type"
        }
}
