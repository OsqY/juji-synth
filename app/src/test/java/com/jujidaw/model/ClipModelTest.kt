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
        assertEquals(PadGateMode.LEGACY_ONE_SHOT, clip.gateMode)
    }

    @Test
    fun legacyPadClipJsonDefaultsToOneShotGateMode() {
        val decoded =
            json.decodeFromString(
                Arrangement.serializer(),
                """{"clips":[{"type":"pad","id":"legacy","trackIndex":0,"startTick":0,"durationTicks":120,"padIndex":0}]}""",
            )

        assertEquals(PadGateMode.LEGACY_ONE_SHOT, (decoded.clips.single() as PadClip).gateMode)
    }

    @Test
    fun patternClipSerializesContentOffset() {
        val original = PatternClip(
            id = "phase",
            trackIndex = 0,
            startTick = 480,
            durationTicks = 960,
            patternId = 2,
            contentOffsetTicks = 120,
        )

        val decoded = json.decodeFromString<PatternClip>(json.encodeToString(original))

        assertEquals(120L, decoded.contentOffsetTicks)
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

    @Test(expected = IllegalArgumentException::class)
    fun audioClipRejectsNegativeStartOffset() {
        AudioClip("offset", 0, 0, PPQ.toLong(), audioFilePath = "samples/kick.wav", audioStartOffsetSamples = -1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun audioClipRejectsNegativeFades() {
        AudioClip("fade", 0, 0, PPQ.toLong(), audioFilePath = "samples/kick.wav", fadeInSamples = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun audioClipRejectsNegativeFadeOut() {
        AudioClip("fade-out", 0, 0, PPQ.toLong(), audioFilePath = "samples/kick.wav", fadeOutSamples = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun audioClipRejectsNonFiniteGain() {
        AudioClip("gain", 0, 0, PPQ.toLong(), audioFilePath = "samples/kick.wav", gain = Float.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun audioClipRejectsInfiniteGain() {
        AudioClip("infinite-gain", 0, 0, PPQ.toLong(), audioFilePath = "samples/kick.wav", gain = Float.POSITIVE_INFINITY)
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

    @Test
    fun clipsInRangeHandlesPlaybackWindowsAtMaximumTick() {
        val arrangement =
            Arrangement(
                clips =
                    listOf(
                        PatternClip(
                            id = "extreme",
                            trackIndex = 0,
                            startTick = Long.MAX_VALUE - 10L,
                            durationTicks = 120L,
                            patternId = 0,
                        ),
                    ),
            )

        val overlaps = arrangement.clipsInRange(Long.MAX_VALUE - 5L, Long.MAX_VALUE)

        assertEquals(listOf("extreme"), overlaps.map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun arrangementRejectsBlankClipIds() {
        Arrangement(clips = listOf(PatternClip("", 0, 0, PPQ.toLong(), patternId = 0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun arrangementRejectsDuplicateClipIds() {
        Arrangement(
            clips = listOf(
                PatternClip("duplicate", 0, 0, PPQ.toLong(), patternId = 0),
                PatternClip("duplicate", 1, PPQ.toLong(), PPQ.toLong(), patternId = 1),
            ),
        )
    }
}
