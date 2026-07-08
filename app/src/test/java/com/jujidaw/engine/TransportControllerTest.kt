package com.jujidaw.engine

import com.jujidaw.model.Arrangement
import com.jujidaw.model.AudioClip
import com.jujidaw.model.NoteEvent
import com.jujidaw.model.PPQ
import com.jujidaw.model.Pattern
import com.jujidaw.model.PatternClip
import com.jujidaw.model.TICKS_PER_STEP
import com.jujidaw.model.TimeSignature
import com.jujidaw.model.TransportPosition
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

        // Exactly 2 note-ons (one per rep) — no ghost third
        assertEquals(
            "Should schedule exactly 2 note-on events for 2 reps",
            2,
            fakeScheduler.noteOnEvents.size,
        )
        assertEquals(
            "Should schedule exactly 2 note-off events",
            2,
            fakeScheduler.noteOffEvents.size,
        )
        assertEquals("Note-on rep 0", 60, fakeScheduler.noteOnEvents[0].note)
        assertEquals("Note-on rep 1", 60, fakeScheduler.noteOnEvents[1].note)
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
        // Expected: 3 note-ons, 3 note-offs.
        assertEquals(
            "Expected 3 note-ons (2 full + 1 truncated)",
            3,
            fakeScheduler.noteOnEvents.size,
        )
        assertEquals(
            "Expected 3 note-offs",
            3,
            fakeScheduler.noteOffEvents.size,
        )

        assertEquals("Full rep note 60", 60, fakeScheduler.noteOnEvents[0].note)
        assertEquals("Full rep note 64", 64, fakeScheduler.noteOnEvents[1].note)
        assertEquals("Partial rep only note 60 fits", 60, fakeScheduler.noteOnEvents[2].note)
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

        // 2 notes in the pattern
        assertEquals(
            "Should schedule 2 note-ons",
            2,
            fakeScheduler.noteOnEvents.size,
        )
        assertEquals(
            "Should schedule 2 note-offs",
            2,
            fakeScheduler.noteOffEvents.size,
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

        // Note-ons from pattern 0 scheduled in first 2 blocks
        val noteOnsBeforeSwitch = fakeScheduler.noteOnEvents.size

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

        // A note-off should have been sent for the previously held note 60
        assertTrue(
            "At least one note-off should have been sent for held notes",
            fakeScheduler.noteOffEvents.any { it.trackIndex == 0 && it.note == 60 },
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
            )
        val windowEnd = controller.tickToSample(9600, 120f)
        controller.schedulePatternClip(clip, 0L, windowEnd, 120f)

        assertEquals(
            "Should schedule 1 pad trigger",
            1,
            fakeScheduler.padTriggers.size,
        )
        assertEquals(
            "Pad index should be 3",
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
    fun schedulePatternClip_legacyPadIndex_fallsBackToNoteOn() {
        // NoteEvent with padIndex = -1 (legacy) should use noteOn path
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

        assertEquals(
            "Should schedule 1 noteOn",
            1,
            fakeScheduler.noteOnEvents.size,
        )
        assertEquals(
            "Note should be 60",
            60,
            fakeScheduler.noteOnEvents[0].note,
        )
        assertEquals(
            "No pad triggers should be scheduled",
            0,
            fakeScheduler.padTriggers.size,
        )
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
