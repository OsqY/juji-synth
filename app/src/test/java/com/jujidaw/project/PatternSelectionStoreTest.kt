package com.jujidaw.project

import org.junit.Assert.assertEquals
import org.junit.Test

class PatternSelectionStoreTest {
    @Test
    fun selectsOnlyValidPatternIds() {
        PatternSelectionStore.select(6)
        PatternSelectionStore.select(16)

        assertEquals(6, PatternSelectionStore.selectedPattern.value)
    }
}
