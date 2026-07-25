package com.jujidaw.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEditHistoryTest {
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
}
