package com.jujidaw.project

import com.jujidaw.data.MidiMapping
import com.jujidaw.model.Arrangement
import com.jujidaw.model.Pattern
import com.jujidaw.model.SynthState
import com.jujidaw.model.TimeSignature
import kotlinx.serialization.Serializable

/**
 * A single automation point at a tick position with a normalised float value.
 */
@Serializable
data class AutomationPoint(
    val position: Long = 0L,
    val value: Float = 0.0f,
)

/**
 * An automation clip for one (track, param) pair.
 * Points are expected to be ordered by position.
 */
@Serializable
data class AutomationClip(
    val trackIndex: Int = 0,
    val paramIndex: Int = 0,
    val points: List<AutomationPoint> = emptyList(),
)

/**
 * An insert FX slot on a mixer channel or bus.
 *
 * `effectType` matches [com.jujidaw.audio.SynthEngine.EffectType.value]:
 * 0=None, 1=Reverb, 2=Delay, 3=Distortion, 4=Chorus, 5=Filter,
 * 6=Bitcrusher, 7=Compressor, 8=Eq.
 */
@Serializable
data class InsertFxSlot(
    val slotIndex: Int = 0,
    val effectType: Int = 0,
    val bypass: Boolean = false,
    val params: Map<Int, Float> = emptyMap(),
)

/**
 * Serializable mirror of the cached pad parameters kept in
 * [com.jujidaw.ui.pads.PadParams]. Lives in the project layer so project
 * persistence never depends on the UI package.
 */
@Serializable
data class PadParamValues(
    val pitch: Float = 0f,
    val pan: Float = 0f,
    val volume: Float = 1f,
    val attack: Float = 0f,
    val release: Float = 0f,
    val filterCutoff: Float = 1f,
    val filterResonance: Float = 0f,
    val reverse: Boolean = false,
    val loop: Boolean = false,
    val oneShot: Boolean = true,
    val useFilter: Boolean = false,
    val synthMode: Boolean = false,
    val synthRootNote: Int = 60,
    val sliceStart: Float = 0f,
    val sliceEnd: Float = 1f,
    val chokeGroup: Int = 0,
)

/**
 * Persisted per-pad state: the sample file path backing the pad, the display
 * name, and the cached parameter snapshot.
 *
 * `samplePath` is stored absolute so a reload finds the file again; empty
 * means an unloaded pad.
 */
@Serializable
data class PadSettings(
    val samplePath: String = "",
    val name: String = "",
    val params: PadParamValues = PadParamValues(),
    /** Name of the synth preset snapshot assigned to this pad, if any. */
    val synthPresetName: String = "",
)

/** Per-track mixer channel state. */
@Serializable
data class TrackState(
    val faderDb: Float = 0.0f,
    val pan: Float = 0.0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val arm: Boolean = false,
    val sendALevel: Float = 0.0f,
    val sendBLevel: Float = 0.0f,
    val insertFx: List<InsertFxSlot> = emptyList(),
)

/** Send/return bus mixer state (bus A or B). */
@Serializable
data class BusState(
    val faderDb: Float = 0.0f,
    val insertFx: List<InsertFxSlot> = emptyList(),
)

/**
 * Complete mixer state snapshot for project save/load.
 *
 * Contains per-track fader/pan/mute/solo/arm/send levels, bus states,
 * master fader, and insert FX chains.
 */
@Serializable
data class MixerState(
    val tracks: List<TrackState> = List(16) { TrackState() },
    val busA: BusState = BusState(),
    val busB: BusState = BusState(),
    val masterFaderDb: Float = 0.0f,
)

/**
 * Top-level project model serialised as JSON.
 *
 * ### Public API
 * - [Project] — the root container for all DAW project data.
 * - [MixerState] — 16-track mixer snapshot with buses and master fader.
 * - [TrackState] — per-channel fader/pan/mute/solo/arm/sends/inserts.
 * - [BusState] — send-return bus fader and inserts.
 * - [InsertFxSlot] — a single insert effect slot.
 *
 * ### Assumptions
 * - [Pattern], [Arrangement], [Clip], [AutomationPoint], [TimeSignature],
 *   and [MidiMapping] are already `@Serializable` in their respective files.
 * - All paths in [samplePaths] are relative to the project directory.
 * - [MidiMapping] is reused from `com.jujidaw.data.MidiMappingStore`.
 */
@Serializable
data class Project(
    val name: String,
    val bpm: Float = 120.0f,
    val timeSignature: TimeSignature = TimeSignature(),
    val patterns: List<Pattern> = emptyList(),
    val arrangement: Arrangement = Arrangement(),
    val mixerState: MixerState = MixerState(),
    val midiMappings: List<MidiMapping> = emptyList(),
    val samplePaths: List<String> = emptyList(),
    val automation: List<AutomationClip> = emptyList(),
    val trackSynthStates: Map<Int, SynthState> = emptyMap(),
    /** Per-pad state (32 entries, bank A then bank B). Added for pad autosave. */
    val pads: List<PadSettings> = emptyList(),
    /** Full independent synth snapshots for pads in synth mode, keyed 0..31. */
    val padSynthStates: Map<Int, SynthState> = emptyMap(),
)
