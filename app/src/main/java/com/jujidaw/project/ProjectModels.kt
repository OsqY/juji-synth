package com.jujidaw.project

import com.jujidaw.data.MidiMapping
import com.jujidaw.model.Arrangement
import com.jujidaw.model.Pattern
import com.jujidaw.model.TimeSignature
import kotlinx.serialization.Serializable

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
    val params: Map<Int, Float> = emptyMap()
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
    val insertFx: List<InsertFxSlot> = emptyList()
)

/** Send/return bus mixer state (bus A or B). */
@Serializable
data class BusState(
    val faderDb: Float = 0.0f,
    val insertFx: List<InsertFxSlot> = emptyList()
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
    val masterFaderDb: Float = 0.0f
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
    val samplePaths: List<String> = emptyList()
)
