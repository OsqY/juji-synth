package com.jujidaw.project

import com.jujidaw.model.Arrangement
import com.jujidaw.model.PPQ
import com.jujidaw.model.PatternClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectExportMathTest {
    @Test
    fun rejectsClipEndOverflow() {
        val arrangement = Arrangement(
            clips = listOf(
                PatternClip("overflow", 0, Long.MAX_VALUE - 1L, 2L, patternId = 0),
            ),
        )

        assertTrue(calculateProjectExportTiming(arrangement, bpm = 120f).isFailure)
    }

    @Test
    fun rejectsExportDurationOverflow() {
        val arrangement = Arrangement(
            clips = listOf(
                PatternClip("duration-overflow", 0, Long.MAX_VALUE / 2L, 1L, patternId = 0),
            ),
        )

        assertTrue(calculateProjectExportTiming(arrangement, bpm = 120f).isFailure)
    }

    @Test
    fun computesSafeTimingForNormalArrangement() {
        val timing = calculateProjectExportTiming(Arrangement(), bpm = 120f).getOrThrow()

        assertEquals(PPQ * 4L, timing.maxEndTick)
        assertEquals(96_000L, timing.totalSamples)
        assertEquals(2_500L, timing.waitMillis)
    }

    @Test
    fun rejectsInvalidTempo() {
        assertTrue(calculateProjectExportTiming(Arrangement(), bpm = 0f).isFailure)
        assertTrue(calculateProjectExportTiming(Arrangement(), bpm = Float.NaN).isFailure)
    }
}
