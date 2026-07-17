package com.jujidaw.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipModelTest {

    @Test
    fun patternClipAcceptsGlobalBankBPadIndex() {
        val clip =
            PatternClip(
                id = "bank-b-pad",
                trackIndex = 0,
                startTick = 0,
                durationTicks = PPQ.toLong(),
                patternId = 1000,
                padIndex = 31,
            )

        assertEquals(31, clip.padIndex)
    }

    @Test
    fun padClipSerializesAsOneShotPolymorphicClip() {
        val arrangement = Arrangement(
            clips = listOf(PadClip("pad", 1, PPQ.toLong(), TICKS_PER_STEP.toLong(), padIndex = 31)),
        )
        val decoded = json.decodeFromString(
            Arrangement.serializer(),
            json.encodeToString(arrangement),
        )
        val clip = decoded.clips.single() as PadClip
        assertEquals(31, clip.padIndex)
        assertEquals(TICKS_PER_STEP.toLong(), clip.durationTicks)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    @Test
    fun arrangementWithPatternAndAudioClipsSerializesPolymorphically() {
        val arrangement = Arrangement(
            clips = listOf(
                PatternClip(
                    id = "clip-1",
                    trackIndex = 1,
                    startTick = 0,
                    durationTicks = PPQ * 4L,
                    patternId = 0
                ),
                AudioClip(
                    id = "clip-2",
                    trackIndex = 2,
                    startTick = PPQ * 4L,
                    durationTicks = PPQ * 2L,
                    audioFilePath = "samples/kick.wav"
                )
            ),
            loopEnabled = true,
            loopStartTick = 0,
            loopEndTick = PPQ * 8L
        )

        val encoded = json.encodeToString(arrangement)
        val decoded = json.decodeFromString(Arrangement.serializer(), encoded)

        assertEquals(2, decoded.clips.size)
        assertTrue(decoded.clips[0] is PatternClip)
        assertTrue(decoded.clips[1] is AudioClip)
        assertEquals("samples/kick.wav", (decoded.clips[1] as AudioClip).audioFilePath)
    }

    @Test
    fun clipsInRangeReturnsOverlappingClipsOnly() {
        val arrangement = Arrangement(
            clips = listOf(
                PatternClip("a", 0, 0, PPQ * 4L, patternId = 0),
                PatternClip("b", 0, PPQ * 4L, PPQ * 4L, patternId = 1),
                PatternClip("c", 0, PPQ * 10L, PPQ * 4L, patternId = 2)
            )
        )
        val overlaps = arrangement.clipsInRange(PPQ * 3L, PPQ * 6L)
        assertEquals(2, overlaps.size)
        assertTrue(overlaps.any { it.id == "a" })
        assertTrue(overlaps.any { it.id == "b" })
    }
}
