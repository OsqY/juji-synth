package com.jujidaw.ui.timeline

import com.jujidaw.model.Clip
import com.jujidaw.model.AudioClip
import com.jujidaw.model.PadClip
import com.jujidaw.model.PatternClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEditHistoryTest {
    private data class ClipEditState(
        val clips: List<Clip>,
        val trash: List<Clip>,
        val selectedIds: Set<String>,
    )

    @Test
    fun undoAndRedoRoundTripAtomicStates() {
        val history = TimelineEditHistory<Int>()
        history.record(ArrangementEditCommand("first", 1, 2))
        history.record(ArrangementEditCommand("second", 2, 3))

        assertEquals(2, history.undo()?.before)
        assertEquals(1, history.undo()?.before)
        assertEquals(2, history.redo()?.after)
        assertEquals(3, history.redo()?.after)
    }

    @Test
    fun newEditClearsRedo() {
        val history = TimelineEditHistory<Int>()
        history.record(ArrangementEditCommand("edit", 1, 2))
        assertEquals(1, history.undo()?.before)
        assertTrue(history.canRedo)

        history.record(ArrangementEditCommand("new edit", 5, 6))

        assertFalse(history.canRedo)
        assertNull(history.redo())
    }

    @Test
    fun capacityDropsOldestState() {
        val history = TimelineEditHistory<Int>(capacity = 2)
        history.record(ArrangementEditCommand("one", 0, 1))
        history.record(ArrangementEditCommand("two", 1, 2))
        history.record(ArrangementEditCommand("three", 2, 3))

        assertEquals(2, history.undo()?.before)
        assertEquals(1, history.undo()?.before)
        assertNull(history.undo())
    }

    @Test
    fun oneCommandRepresentsOneAtomicTransaction() {
        val history = TimelineEditHistory<Int>()
        history.record(DeleteClipsCommand(setOf("a", "b", "c"), 10, 9))

        assertEquals(1, history.undoCount)
        assertEquals(10, history.undo()?.before)
        assertEquals(1, history.redoCount)
        assertEquals(9, history.redo()?.after)
        assertEquals(1, history.undoCount)
    }

    @Test
    fun mixedCommandsUndoAndRedoInTransactionOrder() {
        val history = TimelineEditHistory<Int>()
        history.record(MoveClipsCommand(setOf("clip"), 0, 1))
        history.record(ResizeClipCommand("clip", 0L, 120L, 0L, 240L, 1, 2))
        history.record(DeleteClipsCommand(setOf("clip"), 2, 3))

        assertEquals(2, history.undo()?.before)
        assertEquals(1, history.undo()?.before)
        assertEquals(0, history.undo()?.before)
        assertEquals(1, history.redo()?.after)
        assertEquals(2, history.redo()?.after)
        assertEquals(3, history.redo()?.after)
    }

    @Test
    fun multiDeleteRoundTripPreservesCompleteClipStateAsOneCommand() {
        val kept = PatternClip(
            id = "kept",
            trackIndex = 3,
            startTick = 960L,
            durationTicks = 480L,
            patternId = 2,
        )
        val deletedPad = PadClip(
            id = "pad",
            trackIndex = 1,
            startTick = 120L,
            durationTicks = 1L,
            padIndex = 7,
            mute = true,
        )
        val deletedPattern = PatternClip(
            id = "pattern",
            trackIndex = 2,
            startTick = 240L,
            durationTicks = 960L,
            patternId = 4,
            mute = true,
            contentOffsetTicks = 120L,
        )
        val deletedAudio = AudioClip(
            id = "audio",
            trackIndex = 4,
            startTick = 480L,
            durationTicks = 720L,
            audioFilePath = "samples/kick.wav",
            audioStartOffsetSamples = 240L,
            gain = 0.75f,
            fadeInSamples = 12,
            fadeOutSamples = 24,
        )
        val before = ClipEditState(
            clips = listOf(kept, deletedPad, deletedPattern, deletedAudio),
            trash = emptyList(),
            selectedIds = setOf(deletedPad.id, deletedPattern.id, deletedAudio.id),
        )
        val after = ClipEditState(
            clips = listOf(kept),
            trash = listOf(deletedPad, deletedPattern, deletedAudio),
            selectedIds = emptySet(),
        )
        val history = TimelineEditHistory<ClipEditState>()

        history.record(
            DeleteClipsCommand(
                setOf(deletedPad.id, deletedPattern.id, deletedAudio.id),
                before,
                after,
            ),
        )

        assertEquals(1, history.undoCount)
        assertEquals(before, history.undo()?.before)
        assertEquals(1, history.redoCount)
        assertEquals(after, history.redo()?.after)
    }

    @Test
    fun restoringTrashRoundTripKeepsClipIdentityAndMetadata() {
        val restored = PadClip(
            id = "restored",
            trackIndex = 5,
            startTick = 360L,
            durationTicks = 120L,
            padIndex = 31,
            velocity = 0.35f,
            mute = true,
        )
        val before = ClipEditState(
            clips = emptyList(),
            trash = listOf(restored),
            selectedIds = emptySet(),
        )
        val after = ClipEditState(
            clips = listOf(restored),
            trash = emptyList(),
            selectedIds = setOf(restored.id),
        )
        val history = TimelineEditHistory<ClipEditState>()

        history.record(RestoreTrashClipCommand(restored.id, before, after))

        assertEquals(before, history.undo()?.before)
        assertEquals(after, history.redo()?.after)
    }
}
