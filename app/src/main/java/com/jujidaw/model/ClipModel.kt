package com.jujidaw.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Automation interpolation curve. */
@Serializable
enum class AutomationCurve {
    LINEAR,
    EXPONENTIAL,
    STEP,
}

/** A single automation point on the arrangement timeline.
 *  `paramId` uses dotted paths such as "track.0.synth.filter.cutoff".
 */
@Serializable
data class AutomationPoint(
    val paramId: String,
    val tick: Long,
    val value: Float,
    val curve: AutomationCurve = AutomationCurve.LINEAR,
) {
    init {
        require(paramId.isNotBlank()) { "Automation paramId must not be blank" }
        require(tick >= 0) { "Automation tick must be non-negative" }
    }
}

/** Base type for arrangement timeline clips. */
@Serializable
sealed class Clip {
    abstract val id: String
    abstract val trackIndex: Int
    abstract val startTick: Long
    abstract val durationTicks: Long
    abstract val mute: Boolean
}

/** A clip that plays a pattern for its duration. */
@Serializable
@SerialName("pattern")
data class PatternClip(
    override val id: String,
    override val trackIndex: Int,
    override val startTick: Long,
    override val durationTicks: Long,
    override val mute: Boolean = false,
    val patternId: Int,
    val transpose: Int = 0,
    val padIndex: Int = -1, // global pad to trigger (-1 = use note's padIndex, then legacy noteOn)
) : Clip() {
    init {
        require(trackIndex in 0..15) { "Track index must be between 0 and 15" }
        require(startTick >= 0) { "Clip start tick must be non-negative" }
        require(durationTicks > 0) { "Clip duration must be positive" }
        require(padIndex in -1..31) { "Pad index must be between -1 and 31" }
    }
}

/** A clip that plays an audio file from disk/memory. */
@Serializable
@SerialName("audio")
data class AudioClip(
    override val id: String,
    override val trackIndex: Int,
    override val startTick: Long,
    override val durationTicks: Long,
    override val mute: Boolean = false,
    val audioFilePath: String, // relative to project dir
    val audioStartOffsetSamples: Long = 0,
    val gain: Float = 1.0f,
    val fadeInSamples: Int = 0,
    val fadeOutSamples: Int = 0,
) : Clip() {
    init {
        require(trackIndex in 0..15) { "Track index must be between 0 and 15" }
        require(startTick >= 0) { "Clip start tick must be non-negative" }
        require(durationTicks > 0) { "Clip duration must be positive" }
        require(audioFilePath.isNotBlank()) { "Audio file path must not be blank" }
        require(gain >= 0f) { "Gain must be non-negative" }
    }
}

/** The linear arrangement: clips, loop range, and punch-in range. */
@Serializable
data class Arrangement(
    val clips: List<Clip> = emptyList(),
    val automation: List<AutomationPoint> = emptyList(),
    val loopEnabled: Boolean = false,
    val loopStartTick: Long = 0,
    val loopEndTick: Long = PPQ * 4L,
    val punchEnabled: Boolean = false,
    val punchInTick: Long = 0,
    val punchOutTick: Long = 0,
) {
    init {
        if (loopEnabled) {
            require(loopStartTick < loopEndTick) { "Loop start must be before loop end" }
        }
        if (punchEnabled) {
            require(punchInTick < punchOutTick) { "Punch in must be before punch out" }
        }
    }

    /** Return clips whose playback window overlaps [startTick, endTick). */
    fun clipsInRange(
        startTick: Long,
        endTick: Long,
    ): List<Clip> =
        clips.filter { clip ->
            !clip.mute && clip.startTick < endTick &&
                (clip.startTick + clip.durationTicks) > startTick
        }
}
