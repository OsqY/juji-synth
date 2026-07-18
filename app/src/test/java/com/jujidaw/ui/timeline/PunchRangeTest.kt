package com.jujidaw.ui.timeline

import com.jujidaw.model.Arrangement
import com.jujidaw.model.PPQ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PunchRangeTest {
    @Test
    fun enablingPunchWithoutRangeCreatesOneBarAtPlayhead() {
        val result = enablePunchArrangement(Arrangement(), currentTick = PPQ.toLong(), oneBarTicks = PPQ * 4L)

        assertTrue(result.punchEnabled)
        assertEquals(PPQ.toLong(), result.punchInTick)
        assertEquals(PPQ * 5L, result.punchOutTick)
    }

    @Test
    fun enablingPunchPreservesExistingValidRange() {
        val result =
            enablePunchArrangement(
                Arrangement(punchInTick = 120L, punchOutTick = 840L),
                currentTick = 960L,
                oneBarTicks = PPQ * 4L,
            )

        assertTrue(result.punchEnabled)
        assertEquals(120L, result.punchInTick)
        assertEquals(840L, result.punchOutTick)
    }
}
