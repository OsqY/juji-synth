package com.jujidaw.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineDeleteSessionTest {
    @Test
    fun oneEraseStrokeDeduplicatesRepeatedClipCrossings() {
        val session = TimelineDeleteSession()

        assertEquals(setOf("a", "b"), session.add(listOf("a", "b")))
        assertEquals(setOf("a", "b"), session.add(listOf("b", "a", "b")))
        assertEquals(setOf("a", "b", "c"), session.add(listOf("c", "a")))
    }

    @Test
    fun emptyEraseStrokeRemainsEmpty() {
        val session = TimelineDeleteSession()

        assertEquals(emptySet<String>(), session.add(emptyList()))
        assertEquals(emptySet<String>(), session.ids)
    }
}
