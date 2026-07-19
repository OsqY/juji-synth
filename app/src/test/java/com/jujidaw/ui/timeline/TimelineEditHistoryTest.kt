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
        history.record(1)
        history.record(2)

        assertEquals(2, history.undo(3))
        assertEquals(1, history.undo(2))
        assertEquals(2, history.redo(1))
        assertEquals(3, history.redo(2))
    }

    @Test
    fun newEditClearsRedo() {
        val history = TimelineEditHistory<Int>()
        history.record(1)
        assertEquals(1, history.undo(2))
        assertTrue(history.canRedo)

        history.record(5)

        assertFalse(history.canRedo)
        assertNull(history.redo(6))
    }

    @Test
    fun capacityDropsOldestState() {
        val history = TimelineEditHistory<Int>(capacity = 2)
        history.record(1)
        history.record(2)
        history.record(3)

        assertEquals(3, history.undo(4))
        assertEquals(2, history.undo(3))
        assertNull(history.undo(2))
    }
}
