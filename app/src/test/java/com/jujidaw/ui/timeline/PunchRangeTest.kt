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

    @Test
    fun enablingPunchAtMaximumTickCreatesValidSaturatedRange() {
        val result =
            enablePunchArrangement(
                arrangement = Arrangement(),
                currentTick = Long.MAX_VALUE,
                oneBarTicks = Long.MAX_VALUE,
            )

        assertTrue(result.punchEnabled)
        assertEquals(Long.MAX_VALUE - 1L, result.punchInTick)
        assertEquals(Long.MAX_VALUE, result.punchOutTick)
    }

    @Test
    fun settingPunchInAtMaximumTickKeepsEnabledArrangementValid() {
        val result =
            setPunchInArrangement(
                arrangement =
                    Arrangement(
                        punchEnabled = true,
                        punchInTick = 0L,
                        punchOutTick = 1L,
                    ),
                currentTick = Long.MAX_VALUE,
                oneBarTicks = Long.MAX_VALUE,
            )

        assertTrue(result.punchEnabled)
        assertEquals(Long.MAX_VALUE - 1L, result.punchInTick)
        assertEquals(Long.MAX_VALUE, result.punchOutTick)
    }
}
