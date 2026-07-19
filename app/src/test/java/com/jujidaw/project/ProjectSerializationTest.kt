package com.jujidaw.project

import com.jujidaw.data.MidiMapping
import com.jujidaw.model.Arrangement
import com.jujidaw.model.AudioClip
import com.jujidaw.model.NoteEvent
import com.jujidaw.model.PPQ
import com.jujidaw.model.Pattern
import com.jujidaw.model.PatternClip
import com.jujidaw.model.SynthState
import com.jujidaw.model.TimeSignature
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip serialization tests for [Project]. Proves the four autosave
 * components — transport settings (BPM/time signature), patterns, arrangement
 * (timeline clips), and per-pad state — all survive encode → decode through the
 * exact [Json] configuration used by [ProjectRepository].
 */
class ProjectSerializationTest {
    // Mirrors ProjectRepository's serializer so this exercises the real path.
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "type"
        }

    private fun sampleProject(): Project =
        Project(
            name = "autosave",
            bpm = 137f,
            timeSignature = TimeSignature(numerator = 6, denominator = 8),
            swing = 0.35f,
            patterns =
                listOf(
                    Pattern(
                        id = 0,
                        notes =
                            listOf(
                                NoteEvent(
                                    note = 60,
                                    velocity = 0.9f,
                                    startTick = 0L,
                                    durationTicks = PPQ / 4L,
                                    trackIndex = 1,
                                    padIndex = 3,
                                ),
                            ),
                    ),
                    Pattern(id = 1),
                ),
            arrangement =
                Arrangement(
                    clips =
                        listOf(
                            PatternClip(
                                id = "clip-a",
                                trackIndex = 1,
                                startTick = 0L,
                                durationTicks = PPQ * 4L,
                                patternId = 0,
                                padIndex = 2,
                            ),
                            AudioClip(
                                id = "clip-b",
                                trackIndex = 2,
                                startTick = PPQ * 4L,
                                durationTicks = PPQ * 2L,
                                audioFilePath = "samples/kick.wav",
                                gain = 0.8f,
                            ),
                        ),
                    loopEnabled = true,
                    loopStartTick = 0L,
                    loopEndTick = PPQ * 8L,
                ),
            mixerState =
                MixerState(
                    tracks =
                        List(16) { i ->
                            if (i == 1) TrackState(faderDb = -3.5f, mute = true) else TrackState()
                        },
                    busA = BusState(faderDb = -6f),
                    masterFaderDb = -1.2f,
                ),
            pads =
                listOf(
                    PadSettings(
                        samplePath = "/sdcard/pad/kick.wav",
                        name = "Kick",
                        params =
                            PadParamValues(
                                synthMode = true,
                                synthRootNote = 48,
                                volume = 0.9f,
                            ),
                    ),
                    PadSettings(samplePath = "/sdcard/pad/snare.wav", name = "Snare"),
                ) + List(14) { PadSettings() } +
                    PadSettings(
                        name = "Bank B Lead",
                        params = PadParamValues(synthMode = true, synthRootNote = 72),
                    ) +
                    List(15) { PadSettings() },
            automation =
                listOf(
                    AutomationClip(
                        trackIndex = 0,
                        paramIndex = 5,
                        points =
                            listOf(
                                AutomationPoint(position = 0L, value = 0.0f),
                                AutomationPoint(position = PPQ * 2L, value = 1.0f),
                            ),
                    ),
                ),
            midiMappings = listOf(MidiMapping(ccNumber = 74)),
            trackSynthStates = mapOf(0 to SynthState(filterCutoff = 0.5f)),
            padSynthStates = mapOf(16 to SynthState(osc1Level = 0.42f, filterCutoff = 0.31f)),
        )

    @Test
    fun projectRoundTripsThroughRepositoryJsonConfig() {
        val original = sampleProject()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Project>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun allAutosaveComponentsSurviveSerialization() {
        val original = sampleProject()
        val decoded = json.decodeFromString<Project>(json.encodeToString(original))

        // 1. Transport settings (BPM + time signature).
        assertEquals(137f, decoded.bpm)
        assertEquals(TimeSignature(6, 8), decoded.timeSignature)
        assertEquals(0.35f, decoded.swing)

        // 2. Patterns, including per-note pad routing.
        assertEquals(2, decoded.patterns.size)
        val note = decoded.patterns[0].notes.single()
        assertEquals(60, note.note)
        assertEquals(3, note.padIndex)
        assertEquals(1, note.trackIndex)

        // 3. Arrangement / timeline clips (polymorphic Clip subtypes).
        val clips = decoded.arrangement.clips
        assertEquals(2, clips.size)
        assertTrue(clips.any { it is PatternClip && it.padIndex == 2 })
        val audio = clips.filterIsInstance<AudioClip>().single()
        assertEquals("samples/kick.wav", audio.audioFilePath)
        assertTrue(decoded.arrangement.loopEnabled)

        // 4. Pad state (sample path + cached params incl. synth mode).
        assertEquals(32, decoded.pads.size)
        val pad0 = decoded.pads[0]
        assertEquals("/sdcard/pad/kick.wav", pad0.samplePath)
        assertTrue(pad0.params.synthMode)
        assertEquals(48, pad0.params.synthRootNote)

        // 5. The Bank B pad retains independent synth-mode configuration.
        val bankBPad = decoded.pads[16]
        assertEquals("Bank B Lead", bankBPad.name)
        assertTrue(bankBPad.params.synthMode)
        assertEquals(72, bankBPad.params.synthRootNote)

        // 6. Full Bank B synth state remains independent of the global synth.
        val bankBSynth = decoded.padSynthStates.getValue(16)
        assertEquals(0.42f, bankBSynth.osc1Level)
        assertEquals(0.31f, bankBSynth.filterCutoff)
    }
}
