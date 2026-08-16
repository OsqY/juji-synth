package com.jujidaw.engine

import com.jujidaw.model.Arrangement
import com.jujidaw.model.AutomationPoint
import com.jujidaw.model.AudioClip
import com.jujidaw.model.NoteEvent
import com.jujidaw.model.PPQ
import com.jujidaw.model.Pattern
import com.jujidaw.model.PatternClip
import com.jujidaw.model.PadClip
import com.jujidaw.model.PadGateMode
import com.jujidaw.model.TICKS_PER_STEP
import com.jujidaw.model.TimeSignature
import com.jujidaw.model.TransportPosition
import com.jujidaw.project.AutomationClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransportControllerTest {
    // ---- shared test fixtures ----

    private lateinit var fakeScheduler: FakeSynthEngineScheduler
    private lateinit var controller: TransportController

    @Before
    fun setUp() {
        fakeScheduler = FakeSynthEngineScheduler()
        // cancelled scope prevents the scheduler coroutine from ever running
        controller =
            TransportController(
                sampleRate = 48000,
                lookaheadMs = 5000, // 5 s lookahead so one tick covers a full clip
                coroutineScope = CoroutineScope(Job().also { it.cancel() }),
                scheduler = fakeScheduler,
            )
    }

    // ================================================================
    //  Original tests (must remain)
    // ================================================================

    @Test
    fun tickToSampleConversionAt120Bpm48kHz() {
        // This test uses its own controller without the fake, which exercises
        // the default NativeSynthEngineScheduler path (safe as long as no
        // scheduler methods are called during tickToSample).
        val ctrl = TransportController(sampleRate = 48000)
        assertEquals(24000L, ctrl.tickToSample(PPQ.toLong(), 120f))
    }

    @Test
    fun updateSampleRate_changesTransportTimeConversions() {
        controller.updateSampleRate(44100)

        assertEquals(22050L, controller.tickToSample(PPQ.toLong(), 120f))
        assertEquals(PPQ.toLong(), controller.sampleToTick(22050L, 120f))
    }

    @Test
    fun oneBarEqualsFourBeatsInTicks() {
        val ts = TimeSignature(4, 4)
        val bar = TransportPosition(1, 0, 0)
        assertEquals(PPQ * 4L, bar.toTicks(ts))
    }

    @Test
    fun transportPositionRoundTripsThroughTicks() {
        val ts = TimeSignature(3, 4)
        val original = TransportPosition(2, 1, 60)
        val ticks = original.toTicks(ts)
        val restored = TransportPosition.fromTicks(ticks, ts)
        assertEquals(original, restored)
    }

    @Test
    fun loadArrangementPublishesPersistedLoopAndPunchStateToTransport() {
        controller.loadArrangement(
            Arrangement(
                loopEnabled = true,
                loopStartTick = PPQ.toLong(),
                loopEndTick = PPQ * 5L,
                punchEnabled = true,
                punchInTick = PPQ * 2L,
                punchOutTick = PPQ * 3L,
            ),
        )

        val state = controller.transportState
        assertTrue(state.loopEnabled)
        assertEquals(PPQ.toLong(), state.loopStart.toTicks())
        assertEquals(PPQ * 5L, state.loopEnd.toTicks())
        assertTrue(state.punchEnabled)
        assertEquals(PPQ * 2L, state.punchIn.toTicks())
        assertEquals(PPQ * 3L, state.punchOut.toTicks())
    }

    @Test
    fun arrangementAutomationUsesNativeParameterIndices() {
        controller.loadArrangement(
            Arrangement(
                automation = listOf(
                    AutomationPoint(
                        paramId = "track.2.synth.filter.cutoff",
                        tick = PPQ.toLong(),
                        value = 0.75f,
                    ),
                    AutomationPoint(
                        paramId = "track.2.synth.lfo1.rate",
                        tick = PPQ.toLong(),
                        value = 0.5f,
                    ),
                    AutomationPoint(
                        paramId = "track.2.synth.master.volume",
                        tick = PPQ.toLong(),
                        value = 0.25f,
                    ),
                ),
            ),
        )

        controller.scheduleAutomationEvents(0L, controller.tickToSample(PPQ * 2L, 120f), 120f)

        assertEquals(listOf(9, 21, 38), fakeScheduler.scheduledAutomation.map { it.paramIndex })
        assertTrue(fakeScheduler.scheduledAutomation.all { it.trackIndex == 2 })
    }

    @Test
    fun canonicalAndLegacyAutomationBothRemainSchedulable() {
        controller.loadArrangement(
            Arrangement(
                automation = listOf(
                    AutomationPoint(
                        paramId = "track.0.synth.filter.cutoff",
                        tick = PPQ.toLong(),
                        value = 0.75f,
                    ),
                ),
            ),
        )
        controller.loadAutomation(
            listOf(
                AutomationClip(
                    trackIndex = 1,
                    paramIndex = 5,
                    points = listOf(com.jujidaw.project.AutomationPoint(PPQ * 2L, 0.25f)),
                ),
            ),
        )

        controller.scheduleAutomationEvents(0L, controller.tickToSample(PPQ * 3L, 120f), 120f)

        assertEquals(2, fakeScheduler.scheduledAutomation.size)
    }

    // ================================================================
    //  1.  schedulePatternClip — exact-duration bug
    // ================================================================

    @Test
    fun schedulePatternClip_exactMultiple_doesNotScheduleGhostRepetition() {
        // Pattern is 2 beats (960 ticks). Clip is 4 beats (1920 ticks) = exactly 2 reps.
        val pattern = simplePattern(id = 0, lengthTicks = 960L, noteStart = 60, noteCount = 1)
        controller.loadPatterns(listOf(pattern))

        val clip =
            PatternClip(
                id = "pc-1",
                trackIndex = 0,
                startTick = 0L,
                durationTicks = 1920L,
                patternId = 0,
            )
        val windowEnd = controller.tickToSample(9600, 120f) // far ahead

        controller.schedulePatternClip(clip, 0L, windowEnd, 120f)

        // Exactly 2 pad triggers (one per rep) — no ghost third
        assertEquals(
            "Should schedule exactly 2 pad triggers for 2 reps",
            2,
            fakeScheduler.padTriggers.size,
        )
        assertEquals(
            "Should schedule exactly 2 pad releases",
            2,
            fakeScheduler.padReleases.size,
        )
        assertEquals("Pad-trigger rep 0", 12, fakeScheduler.padTriggers[0].padIndex)
        assertEquals("Pad-trigger rep 1", 12, fakeScheduler.padTriggers[1].padIndex)
    }

    // ================================================================
    //  2.  schedulePatternClip — partial tail
    // ================================================================

    @Test
    fun schedulePatternClip_partialTail_truncatesLastRepNotes() {
        // Pattern: 2 beats (960 ticks) with note at tick 0 (dur 480) and note at tick 480 (dur 480).
        // Clip duration is 1440 ticks = 1 full rep + 480 tick tail.
        val pattern =
            Pattern(
                id = 0,
                lengthSteps = 16,
                lengthTicks = 960L,
                notes =
                    listOf(
                        NoteEvent(note = 60, velocity = 0.8f, startTick = 0L, durationTicks = 480L, trackIndex = 0),
                        NoteEvent(note = 64, velocity = 0.8f, startTick = 480L, durationTicks = 480L, trackIndex = 0),
                    ),
            )
        controller.loadPatterns(listOf(pattern))

        val clip =
            PatternClip(
                id = "pc-2",
                trackIndex = 0,
                startTick = 0L,
                durationTicks = 1440L,
                patternId = 0,
            )
        val windowEnd = controller.tickToSample(9600, 120f)

        controller.schedulePatternClip(clip, 0L, windowEnd, 120f)

        // Rep 0: both notes (60 and 64) play fully.
        // Rep 1 (partial 480 ticks): note 60 at local tick 0 (global 960) fits within 1440 (end=1440>960),
        //   note 64 at local tick 480 (global 1440) has maxEndTick=1440 => end=1440 => noteEndTick==startTick => skipped.
        // Expected: 3 pad triggers, 3 pad releases.
        assertEquals(
            "Expected 3 pad triggers (2 full + 1 truncated)",
            3,
            fakeScheduler.padTriggers.size,
        )
        assertEquals(
            "Expected 3 pad releases",
            3,
            fakeScheduler.padReleases.size,
        )

        assertEquals("Full rep note 60 maps to pad 12", 12, fakeScheduler.padTriggers[0].padIndex)
        assertEquals("Full rep note 64 maps to pad 0", 0, fakeScheduler.padTriggers[1].padIndex)
        assertEquals("Partial rep note 60 maps to pad 12", 12, fakeScheduler.padTriggers[2].padIndex)
    }

    // ================================================================
    //  3.  Pattern playback in sequencer mode
    // ================================================================

    @Test
    fun scheduleNextBlock_schedulesActivePatternNotes() {
        val pattern = simplePattern(id = 0, lengthTicks = 960L, noteStart = 0, noteCount = 2)
        controller.loadPatterns(listOf(pattern))
        controller.isSequencerMode = true

        // Queue pattern 0 so it becomes active on the next block
        controller.queuePattern(0)
        controller.scheduleNextBlock()

        assertTrue("Pattern should be activated", controller.activePatternId >= 0)
        assertEquals("Active pattern should be 0", 0, controller.activePatternId)

        // 2 pad triggers in the pattern
        assertEquals(
            "Should schedule 2 pad triggers",
            2,
            fakeScheduler.padTriggers.size,
        )
        assertEquals(
            "Should schedule 2 pad releases",
            2,
            fakeScheduler.padReleases.size,
        )
    }

    // ================================================================
    //  4.  Pattern chaining on bar boundary
    // ================================================================

    @Test
    fun scheduleNextBlock_queuedPattern_switchesAtBarBoundary() {
        val pattern0 = simplePattern(id = 0, lengthTicks = 960L, noteStart = 60, noteCount = 1)
        val pattern1 = simplePattern(id = 1, lengthTicks = 960L, noteStart = 72, noteCount = 1)
        controller.loadPatterns(listOf(pattern0, pattern1))

        // Activate pattern 0 first
        controller.queuePattern(0)
        controller.scheduleNextBlock()
        assertEquals("Pattern 0 active after first block", 0, controller.activePatternId)

        // Queue pattern 1; the switch happens at the next bar boundary.
        // At 120 BPM, 4/4, one bar = 1920 ticks = 96000 samples.
        // Set playhead to just before the bar boundary.
        fakeScheduler.controlledPlayheadSample = 95999
        controller.queuePattern(1)
        controller.scheduleNextBlock()

        // The bar-boundary pendingSwitchSample should have been resolved.
        assertTrue(
            "Pending switch sample should be resolved",
            controller.pendingSwitchSample > 0,
        )
        assertEquals(
            "Pattern 0 still active before boundary",
            0,
            controller.activePatternId,
        )

        // Advance playhead past the bar boundary to trigger the switch
        fakeScheduler.controlledPlayheadSample = 96000
        controller.scheduleNextBlock()

        assertEquals("Pattern 1 should be active after switch", 1, controller.activePatternId)
        assertEquals("Queued pattern consumed", -1, controller.queuedPatternId)
        assertEquals("Pending switch sample reset", -1L, controller.pendingSwitchSample)
        assertEquals(
            "Transport position reset to bar 0",
            0,
            controller.transportState.position.bar,
        )

        // A pad release should have been scheduled for the previously triggered note 60.
        assertTrue(
            "At least one pad release should have been scheduled",
            fakeScheduler.padReleases.any { it.trackIndex == 0 && it.padIndex == 12 },
        )
    }

    // ================================================================
    //  5.  Loop wrap
    // ================================================================

    @Test
    fun scheduleNextBlock_loopWrap_resetsPlayheadAndClearsEvents() {
        val pattern = simplePattern(id = 0, lengthTicks = 960L, noteStart = 60, noteCount = 1)
        controller.loadPatterns(listOf(pattern))
        controller.loadArrangement(
            Arrangement(
                loopEnabled = true,
                loopStartTick = 0L,
                loopEndTick = 960L, // 2 beats
            ),
        )

        controller.queuePattern(0)

        // Put the playhead past the loop end
        val loopEndSample = controller.tickToSample(960L, 120f) // 48000
        fakeScheduler.controlledPlayheadSample = loopEndSample + 1 // just past end

        controller.scheduleNextBlock()

        // Loop wrap should have fired: clear events, set playhead to loop start
        assertTrue(
            "clearScheduledEvents should have been called",
            fakeScheduler.clearScheduledEventsCount >= 1,
        )
        assertEquals(
            "Playhead should be reset to loop start",
            controller.tickToSample(0L, 120f),
            fakeScheduler.lastSetPlayheadSample,
        )
        assertEquals(
            "Playhead sample should be loop start",
            0L,
            fakeScheduler.controlledPlayheadSample,
        )
        assertEquals(
            "Transport position should be reset to bar 0",
            0,
            controller.transportState.position.bar,
        )
    }

    // ================================================================
    //  6.  Held note cleanup on stop
    // ================================================================

    @Test
    fun stop_sendsNoteOffsForHeldNotes() {
        // Schedule some notes via the internal scheduling path
        controller.scheduleNoteOn(track = 0, note = 60, velocity = 0.8f)
        controller.scheduleNoteOn(track = 0, note = 64, velocity = 0.8f)
        controller.scheduleNoteOn(track = 1, note = 67, velocity = 0.8f)

        // Verify heldNotes is tracking them
        assertEquals(
            "Should have 2 held notes on track 0",
            setOf(60, 64),
            controller.heldNotes[0],
        )
        assertEquals(
            "Should have 1 held note on track 1",
            setOf(67),
            controller.heldNotes[1],
        )

        // Count note-off events so far (none yet — we only did note-ons)
        // scheduleNoteOn calls scheduler.scheduleNoteOn() which records in fake
        assertEquals("3 note-ons recorded", 3, fakeScheduler.noteOnEvents.size)
        assertEquals(
            "0 note-offs recorded before stop",
            0,
            fakeScheduler.noteOffEvents.size,
        )

        // Now stop — this should send note-offs for all held notes
        controller.stop()

        // Expected: 3 additional note-offs (one per held note)
        assertEquals(
            "Should have 3 note-offs after stop",
            3,
            fakeScheduler.noteOffEvents.size,
        )

        // Verify the actual note-off events
        assertTrue(
            "Note-off for note 60 track 0",
            fakeScheduler.noteOffEvents.any {
                it.trackIndex == 0 && it.note == 60
            },
        )
        assertTrue(
            "Note-off for note 64 track 0",
            fakeScheduler.noteOffEvents.any {
                it.trackIndex == 0 && it.note == 64
            },
        )
        assertTrue(
            "Note-off for note 67 track 1",
            fakeScheduler.noteOffEvents.any {
                it.trackIndex == 1 && it.note == 67
            },
        )

        // heldNotes should be cleared
        assertTrue(
            "heldNotes should be empty after stop",
            controller.heldNotes.isEmpty(),
        )
    }

    // ================================================================
    //  7.  Audio clip scheduling
    // ================================================================

    @Test
    fun scheduleNextBlock_startsAudioClipAtCorrectOffset() {
        val audioClip =
            AudioClip(
                id = "audio-1",
                trackIndex = 2,
                startTick = 480L, // starts at beat 1
                durationTicks = 960L,
                audioFilePath = "samples/kick.wav",
            )
        controller.loadArrangement(Arrangement(clips = listOf(audioClip)))

        // Playhead at tick 0
        fakeScheduler.controlledPlayheadSample = 0L
        controller.scheduleNextBlock()

        // Audio clip start should be triggered
        assertEquals("Audio clip should be started", 1, fakeScheduler.audioClipStarts.size)
        val startEvent = fakeScheduler.audioClipStarts[0]
        assertEquals("audio-1", startEvent.clipId)
        assertEquals(2, startEvent.trackIndex)
        // offsetInBuffer = clipStartSample - currentSample
        val clipStartSample = controller.tickToSample(480L, 120f) // 24000
        assertEquals(
            "Offset should be clip start sample",
            clipStartSample.toInt(),
            startEvent.startOffsetInBuffer,
        )
    }

    @Test
    fun scheduleNextBlock_doesNotStartAudioClipTwice() {
        val audioClip =
            AudioClip(
                id = "audio-1",
                trackIndex = 2,
                startTick = 0L,
                durationTicks = 960L,
                audioFilePath = "samples/kick.wav",
            )
        controller.loadArrangement(Arrangement(clips = listOf(audioClip)))

        // First schedule — clip starts
        fakeScheduler.controlledPlayheadSample = 0L
        controller.scheduleNextBlock()
        assertEquals(
            "Clip started on first tick",
            1,
            fakeScheduler.audioClipStarts.size,
        )

        // Second schedule — clip already in startedClips, should not re-start
        fakeScheduler.controlledPlayheadSample = 100L
        controller.scheduleNextBlock()
        assertEquals(
            "Clip should not be started again",
            1,
            fakeScheduler.audioClipStarts.size,
        )
    }

    // ================================================================
    //  8.  Recording punch range
    // ================================================================

    @Test
    fun setRecording_enablesPunchRangeFromArrangement() {
        controller.loadArrangement(
            Arrangement(
                punchEnabled = true,
                punchInTick = 480L,
                punchOutTick = 1440L,
            ),
        )

        controller.setRecording(true)

        assertTrue("Punch should be enabled", fakeScheduler.lastPunchEnabled)
        val expectedIn = controller.tickToSample(480L, 120f)
        val expectedOut = controller.tickToSample(1440L, 120f)
        assertEquals("Punch in sample", expectedIn, fakeScheduler.lastPunchInSample)
        assertEquals("Punch out sample", expectedOut, fakeScheduler.lastPunchOutSample)
    }

    @Test
    fun setRecording_disabled_disablesPunchRange() {
        controller.loadArrangement(
            Arrangement(
                punchEnabled = true,
                punchInTick = 480L,
                punchOutTick = 1440L,
            ),
        )

        controller.setRecording(true) // punch is on
        assertTrue("Punch should be enabled initially", fakeScheduler.lastPunchEnabled)

        controller.setRecording(false) // punch should be disabled
        assertFalse("Punch should be disabled", fakeScheduler.lastPunchEnabled)
    }

    // ================================================================
    //  Additional edge-case tests
    // ================================================================

    @Test
    fun schedulePatternClip_clipEntirelyBehindPlayhead_skips() {
        val pattern = simplePattern(id = 0, lengthTicks = 960L)
        controller.loadPatterns(listOf(pattern))

        val clip =
            PatternClip(
                id = "pc-behind",
                trackIndex = 0,
                startTick = 0L,
                durationTicks = 960L,
                patternId = 0,
            )
        // Playhead already past the clip end
        val clipEndSample = controller.tickToSample(960L, 120f)
        val windowEnd = clipEndSample + controller.tickToSample(960L, 120f)

        controller.schedulePatternClip(clip, clipEndSample + 1, windowEnd, 120f)

        assertEquals(
            "No notes should be scheduled for a finished clip",
            0,
            fakeScheduler.noteOnEvents.size,
        )
    }

    @Test
    fun schedulePatternClip_clipEntirelyAheadOfWindow_skips() {
        val pattern = simplePattern(id = 0, lengthTicks = 960L)
        controller.loadPatterns(listOf(pattern))

        val clip =
            PatternClip(
                id = "pc-ahead",
                trackIndex = 0,
                startTick = 9600L,
                durationTicks = 960L,
                patternId = 0,
            )
        controller.schedulePatternClip(clip, 0L, controller.tickToSample(4800L, 120f), 120f)

        assertEquals(
            "No notes should be scheduled for a clip ahead of the window",
            0,
            fakeScheduler.noteOnEvents.size,
        )
    }

    @Test
    fun scheduleNextBlock_noActivePattern_doesNothing() {
        // No pattern queued, no active pattern
        controller.scheduleNextBlock()

        assertEquals(
            "Active pattern should remain -1",
            -1,
            controller.activePatternId,
        )
        assertEquals(
            "No notes should be scheduled",
            0,
            fakeScheduler.noteOnEvents.size,
        )
    }

    @Test
    fun checkLoopWrap_playheadNotAtLoopEnd_doesNothing() {
        controller.loadArrangement(
            Arrangement(
                loopEnabled = true,
                loopStartTick = 0L,
                loopEndTick = 960L,
            ),
        )

        fakeScheduler.controlledPlayheadSample = 100L // inside loop range
        controller.checkLoopWrap(100L, 200000L, 120f, TimeSignature())

        assertEquals(
            "Playhead should not be reset",
            100L,
            fakeScheduler.controlledPlayheadSample,
        )
        assertEquals(
            "Events should not be cleared",
            0,
            fakeScheduler.clearScheduledEventsCount,
        )
    }

    // ================================================================
    //  9.  Pad-triggered transport (slice B)
    // ================================================================

    @Test
    fun schedulePatternClip_withPadIndex_callsSchedulePadTrigger() {
        val pattern =
            Pattern(
                id = 0,
                lengthSteps = 16,
                lengthTicks = 960L,
                notes =
                    listOf(
                        NoteEvent(note = 60, velocity = 0.8f, startTick = 0L, durationTicks = TICKS_PER_STEP.toLong(), padIndex = 3),
                    ),
            )
        controller.loadPatterns(listOf(pattern))

        val clip =
            PatternClip(
                id = "pc-pad",
                trackIndex = 0,
                startTick = 0L,
                durationTicks = 960L,
                patternId = 0,
                padIndex = 5,
            )
        val windowEnd = controller.tickToSample(9600, 120f)
        controller.schedulePatternClip(clip, 0L, windowEnd, 120f)

        assertEquals(
            "Should schedule 1 pad trigger",
            1,
            fakeScheduler.padTriggers.size,
        )
        assertEquals(
            "Note pad index should override clip pad index",
            3,
            fakeScheduler.padTriggers[0].padIndex,
        )
        assertEquals(
            "Velocity should match",
            0.8f,
            fakeScheduler.padTriggers[0].velocity,
            0.001f,
        )
        assertEquals(
            "No noteOn events should be scheduled",
            0,
            fakeScheduler.noteOnEvents.size,
        )
    }

    @Test
    fun schedulePatternClip_legacyPadIndex_migratesToNoteModuloPadTrigger() {
        // NoteEvent with padIndex = -1 (legacy) maps to note % 16.
        val pattern =
            Pattern(
                id = 0,
                lengthSteps = 16,
                lengthTicks = 960L,
                notes =
                    listOf(
                        NoteEvent(note = 60, velocity = 0.8f, startTick = 0L, durationTicks = TICKS_PER_STEP.toLong(), padIndex = -1),
                    ),
            )
        controller.loadPatterns(listOf(pattern))

        val clip =
            PatternClip(
                id = "pc-legacy",
                trackIndex = 0,
                startTick = 0L,
                durationTicks = 960L,
                patternId = 0,
            )
        val windowEnd = controller.tickToSample(9600, 120f)
        controller.schedulePatternClip(clip, 0L, windowEnd, 120f)

        assertEquals("Should schedule 1 pad trigger", 1, fakeScheduler.padTriggers.size)
        assertEquals(
            "Legacy note 60 should map to pad 12",
            12,
            fakeScheduler.padTriggers[0].padIndex,
        )
        assertEquals("No noteOn events should be scheduled", 0, fakeScheduler.noteOnEvents.size)
    }

    @Test
    fun schedulePatternClip_clipPadIndex_overridesNotePadIndex() {
        // Note.padIndex = -1 (legacy), but clip.padIndex = 5
        // The clip's padIndex should be used as the effective padIndex
        val pattern =
            Pattern(
                id = 0,
                lengthSteps = 16,
                lengthTicks = 960L,
                notes =
                    listOf(
                        NoteEvent(note = 60, velocity = 0.8f, startTick = 0L, durationTicks = TICKS_PER_STEP.toLong(), padIndex = -1),
                    ),
            )
        controller.loadPatterns(listOf(pattern))

        val clip =
            PatternClip(
                id = "pc-override",
                trackIndex = 0,
                startTick = 0L,
                durationTicks = 960L,
                patternId = 0,
                padIndex = 5,
            )
        val windowEnd = controller.tickToSample(9600, 120f)
        controller.schedulePatternClip(clip, 0L, windowEnd, 120f)

        assertEquals(
            "Should schedule 1 pad trigger",
            1,
            fakeScheduler.padTriggers.size,
        )
        assertEquals(
            "Pad index should be 5 (from clip)",
            5,
            fakeScheduler.padTriggers[0].padIndex,
        )
        assertEquals(
            "No noteOn events should be scheduled",
            0,
            fakeScheduler.noteOnEvents.size,
        )
    }

    @Test
    fun schedulePatternClip_clipAtNonZeroStartTick_firesPadTriggerWhenPlayheadReachesIt() {
        // Regression: a pad clip placed anywhere past tick 0 used to resolve
        // its notes to samples near 0 (ignoring clip.startTick), so the
        // lookahead window check dropped the trigger and no sound played.
        val pattern =
            Pattern(
                id = 0,
                lengthSteps = 16,
                lengthTicks = 960L,
                notes =
                    listOf(
                        NoteEvent(
                            note = 60,
                            velocity = 0.8f,
                            startTick = 0L,
                            durationTicks = TICKS_PER_STEP.toLong(),
                            padIndex = 3,
                        ),
                    ),
            )
        controller.loadPatterns(listOf(pattern))

        val clipStartTick = 9600L // clip placed far down the timeline
        val clip =
            PatternClip(
                id = "pc-offset",
                trackIndex = 0,
                startTick = clipStartTick,
                durationTicks = 960L,
                patternId = 0,
                padIndex = 3,
            )
        val clipStartSample = controller.tickToSample(clipStartTick, 120f)
        val lookahead = clipStartSample / 2L // window straddles the clip start
        val windowEnd = clipStartSample + lookahead

        // Playhead is just before the clip start, within the lookahead window.
        controller.schedulePatternClip(clip, clipStartSample - 1, windowEnd, 120f)

        assertEquals(
            "Pad trigger should fire when playhead reaches a clip past tick 0",
            1,
            fakeScheduler.padTriggers.size,
        )
        assertEquals(
            "Pad index should be 3",
            3,
            fakeScheduler.padTriggers[0].padIndex,
        )
        assertEquals(
            "Pad trigger must carry the sample-accurate start sample so the C++ " +
                "EventQueue fires it when the playhead reaches it (not immediately)",
            clipStartSample,
            fakeScheduler.padTriggers[0].targetSample,
        )
    }

    // ================================================================
    //  9.  Sequencer multi-trigger / de-duplication regression
    // ================================================================

    @Test
    fun schedulePatternNotes_repeatedTicks_doesNotRetriggerPadOrNote() {
        // Regression: with a ~100ms lookahead and ~50ms tick, a note whose
        // start sample sits inside the window was re-pushed on every overlapping
        // scheduler tick, so a single step fired twice (or three times when the
        // launcher and an arrangement clip both referenced the same pattern).
        val pattern =
            Pattern(
                id = 0,
                lengthSteps = 16,
                lengthTicks = 960L,
                notes =
                    listOf(
                        NoteEvent(
                            note = 60,
                            velocity = 0.8f,
                            startTick = 0L,
                            durationTicks = TICKS_PER_STEP.toLong(),
                            padIndex = 2,
                        ),
                        NoteEvent(
                            note = 64,
                            velocity = 0.8f,
                            startTick = 0L,
                            durationTicks = TICKS_PER_STEP.toLong(),
                            padIndex = -1,
                        ),
                    ),
            )
        controller.loadPatterns(listOf(pattern))

        val startSample = controller.tickToSample(0L, 120f)
        val windowEnd = startSample + 24000L // 500ms lookahead at 48kHz

        // First tick schedules two pad triggers.
        controller.schedulePatternNotes(pattern, startSample, windowEnd, 120f)
        assertEquals("First tick schedules 2 pad triggers", 2, fakeScheduler.padTriggers.size)
        assertEquals("First tick schedules 2 pad releases", 2, fakeScheduler.padReleases.size)
        assertTrue(fakeScheduler.padTriggers.any { it.padIndex == 2 })
        assertTrue(fakeScheduler.padTriggers.any { it.padIndex == 0 })

        // Second tick, same overlapping window — must NOT re-trigger anything.
        controller.schedulePatternNotes(pattern, startSample, windowEnd, 120f)
        assertEquals("Second tick must not re-trigger pads", 2, fakeScheduler.padTriggers.size)
        assertEquals("Second tick must not re-trigger releases", 2, fakeScheduler.padReleases.size)

        // After a stop() (which clears the de-dup set alongside the engine
        // queue), the same note may legitimately fire again.
        controller.stop()
        controller.schedulePatternNotes(pattern, startSample, windowEnd, 120f)
        assertEquals("After clear, pads fire again", 4, fakeScheduler.padTriggers.size)
        assertEquals("After clear, releases fire again", 4, fakeScheduler.padReleases.size)
    }

    @Test
    fun scheduleNextBlock_sequencerMode_skipsArrangementClips() {
        // In sequencer mode the launcher owns playback; an arrangement clip
        // referencing the same pattern must NOT double-fire the same notes in
        // the same tick (was the other half of the triple-trigger).
        val pattern = simplePattern(id = 0, lengthTicks = 960L, noteStart = 60, noteCount = 1)
        controller.loadPatterns(listOf(pattern))

        val clip =
            PatternClip(
                id = "pc-dup",
                trackIndex = 0,
                startTick = 0L,
                durationTicks = 960L,
                patternId = 0,
            )
        controller.loadArrangement(Arrangement(clips = listOf(clip)))
        controller.isSequencerMode = true
        controller.queuePattern(0)

        controller.scheduleNextBlock()

        // Only the launcher's single pad trigger should fire; the arrangement clip
        // referencing the same pattern is suppressed in sequencer mode.
        assertEquals(
            "Sequencer mode must not double-fire arrangement clips",
            1,
            fakeScheduler.padTriggers.size,
        )
    }

    @Test
    fun loadArrangementMigratesLegacyPadPatternClipToOneShotPadClip() {
        controller.loadArrangement(
            Arrangement(
                clips = listOf(
                    PatternClip(
                        id = "legacy-pad",
                        trackIndex = 1,
                        startTick = 0L,
                        durationTicks = PPQ * 4L,
                        patternId = 1007,
                        padIndex = 7,
                    ),
                ),
            ),
        )

        val clip = controller.arrangement.clips.single()
        assertTrue(clip is PadClip)
        assertEquals(7, (clip as PadClip).padIndex)
    }

    @Test
    fun play_enablesNativeTransportAndDisablesInternalSequencer() {
        controller.play()

        assertTrue(controller.transportState.playing)
        assertTrue(fakeScheduler.lastTransportPlaying)
        assertFalse(fakeScheduler.lastSequencerEnabled)
        assertEquals(120f, fakeScheduler.lastTransportTempoBpm, 0.001f)
    }

    @Test
    fun scheduleNextBlock_padClipSchedulesGlobalPadAndReleaseAtClipEnd() {
        val clip =
            PadClip(
                id = "pad-31",
                trackIndex = 12,
                startTick = 0L,
                durationTicks = TICKS_PER_STEP.toLong(),
                padIndex = 31,
                velocity = 0.75f,
                gateMode = PadGateMode.TIMELINE_GATE,
            )
        controller.loadArrangement(Arrangement(clips = listOf(clip)))

        controller.scheduleNextBlock()

        assertEquals(1, fakeScheduler.padTriggers.size)
        assertEquals(12, fakeScheduler.padTriggers.single().trackIndex)
        assertEquals(31, fakeScheduler.padTriggers.single().padIndex)
        assertEquals(0.75f, fakeScheduler.padTriggers.single().velocity, 0.001f)
        assertEquals(1, fakeScheduler.padReleases.size)
        assertEquals(31, fakeScheduler.padReleases.single().padIndex)
        assertEquals(
            controller.tickToSample(TICKS_PER_STEP.toLong(), 120f),
            fakeScheduler.padReleases.single().targetSample,
        )
    }

    @Test
    fun scheduleNextBlock_overlappingPadClipsKeepIndependentGateIdentities() {
        controller.loadArrangement(
            Arrangement(
                clips = listOf(
                    PadClip(
                        id = "first", trackIndex = 3, startTick = 0L,
                        durationTicks = TICKS_PER_STEP.toLong() * 2, padIndex = 7,
                        gateMode = PadGateMode.TIMELINE_GATE,
                    ),
                    PadClip(
                        id = "second", trackIndex = 3, startTick = TICKS_PER_STEP.toLong(),
                        durationTicks = TICKS_PER_STEP.toLong() * 2, padIndex = 7,
                        gateMode = PadGateMode.TIMELINE_GATE,
                    ),
                ),
            ),
        )

        controller.scheduleNextBlock()

        assertEquals(2, fakeScheduler.padTriggers.size)
        assertEquals(2, fakeScheduler.padReleases.size)
        val triggerIds = fakeScheduler.padTriggers.map { it.triggerId }.toSet()
        assertEquals(2, triggerIds.size)
        assertEquals(triggerIds, fakeScheduler.padReleases.map { it.triggerId }.toSet())
    }

    @Test
    fun scheduleNextBlock_legacyPadClipDoesNotForceRelease() {
        controller.loadArrangement(
            Arrangement(
                clips = listOf(
                    PadClip(
                        id = "legacy-tail",
                        trackIndex = 0,
                        startTick = 0L,
                        durationTicks = TICKS_PER_STEP.toLong(),
                        padIndex = 0,
                    ),
                ),
            ),
        )

        controller.scheduleNextBlock()

        assertEquals(1, fakeScheduler.padTriggers.size)
        assertTrue(fakeScheduler.padReleases.isEmpty())
    }

    @Test
    fun swingDelaysOddSixteenthAndPreservesNoteDuration() {
        controller.setSwing(0.5f)
        val pattern =
            Pattern(
                id = 0,
                lengthTicks = PPQ * 2L,
                notes = listOf(NoteEvent(note = 60, velocity = 1f, startTick = 120L, durationTicks = 120L)),
            )

        controller.schedulePatternNotes(pattern, 0L, controller.tickToSample(960L, 120f), 120f)

        assertEquals(controller.tickToSample(150L, 120f), fakeScheduler.padTriggers.single().targetSample)
        assertEquals(controller.tickToSample(270L, 120f), fakeScheduler.padReleases.single().targetSample)
    }

    @Test
    fun swingLeavesOffGridNotesUnchanged() {
        controller.setSwing(1f)
        val pattern =
            Pattern(
                id = 0,
                lengthTicks = PPQ * 2L,
                notes = listOf(NoteEvent(note = 60, velocity = 1f, startTick = 121L, durationTicks = 120L)),
            )

        controller.schedulePatternNotes(pattern, 0L, controller.tickToSample(960L, 120f), 120f)

        assertEquals(controller.tickToSample(121L, 120f), fakeScheduler.padTriggers.single().targetSample)
    }

    @Test
    fun swingDelaysTimelinePadAndItsGateByTheSameAmount() {
        controller.setSwing(0.5f)
        controller.loadArrangement(
            Arrangement(
                clips = listOf(
                    PadClip(
                        id = "swung-pad",
                        trackIndex = 2,
                        startTick = 120L,
                        durationTicks = 120L,
                        padIndex = 9,
                        gateMode = PadGateMode.TIMELINE_GATE,
                    ),
                ),
            ),
        )

        controller.scheduleNextBlock()

        assertEquals(controller.tickToSample(150L, 120f), fakeScheduler.padTriggers.single().targetSample)
        assertEquals(controller.tickToSample(270L, 120f), fakeScheduler.padReleases.single().targetSample)
    }

    @Test
    fun patternClipContentOffsetStartsAtTheExpectedPatternPhase() {
        controller.loadPatterns(
            listOf(
                Pattern(
                    id = 0,
                    lengthTicks = 960L,
                    notes = listOf(
                        NoteEvent(note = 60, velocity = 1f, startTick = 0L, durationTicks = 120L),
                        NoteEvent(note = 64, velocity = 1f, startTick = 480L, durationTicks = 120L),
                    ),
                ),
            ),
        )
        val clip =
            PatternClip(
                id = "phase",
                trackIndex = 3,
                startTick = 1000L,
                durationTicks = 480L,
                patternId = 0,
                contentOffsetTicks = 480L,
            )

        controller.schedulePatternClip(clip, 0L, controller.tickToSample(2000L, 120f), 120f)

        assertEquals(1, fakeScheduler.padTriggers.size)
        assertEquals(0, fakeScheduler.padTriggers.single().padIndex)
        assertEquals(controller.tickToSample(1000L, 120f), fakeScheduler.padTriggers.single().targetSample)
    }

    // ================================================================
    //  Helpers
    // ================================================================

    /**
     * Build a simple pattern with [noteCount] notes starting at [noteStart]
     * and spaced one step apart.
     */
    private fun simplePattern(
        id: Int,
        lengthTicks: Long = 960L,
        noteStart: Int = 60,
        noteCount: Int = 1,
        trackIndex: Int = 0,
    ): Pattern {
        val notes =
            (0 until noteCount).map { i ->
                NoteEvent(
                    note = noteStart + i,
                    velocity = 0.8f,
                    startTick = (i * TICKS_PER_STEP).toLong(),
                    durationTicks = TICKS_PER_STEP.toLong(),
                    trackIndex = trackIndex,
                )
            }
        return Pattern(
            id = id,
            lengthSteps = (lengthTicks / TICKS_PER_STEP).toInt().coerceIn(1, 64),
            lengthTicks = lengthTicks,
            notes = notes,
        )
    }
}
