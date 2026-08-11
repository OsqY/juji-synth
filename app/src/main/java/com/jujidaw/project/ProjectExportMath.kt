package com.jujidaw.project

import com.jujidaw.model.Arrangement
import com.jujidaw.model.PPQ
import java.io.IOException

internal data class ProjectExportTiming(
    val maxEndTick: Long,
    val totalSamples: Long,
    val waitMillis: Long,
)

internal fun calculateProjectExportTiming(
    arrangement: Arrangement,
    bpm: Float,
    sampleRate: Long = 48_000L,
): Result<ProjectExportTiming> {
    if (!bpm.isFinite() || bpm <= 0f || sampleRate <= 0L) {
        return Result.failure(IOException("Invalid export timing parameters"))
    }

    val bpmUnits = bpm.toLong()
    if (bpmUnits <= 0L) {
        return Result.failure(IOException("Export tempo is too small"))
    }

    return try {
        val maxEndTick = arrangement.clips.fold(PPQ * 4L) { current, clip ->
            maxOf(current, Math.addExact(clip.startTick, clip.durationTicks))
        }
        val denominator = Math.multiplyExact(PPQ.toLong(), bpmUnits)
        val totalSamples =
            Math.multiplyExact(Math.multiplyExact(maxEndTick, sampleRate), 60L) / denominator
        val totalMillis = Math.multiplyExact(maxEndTick, 60_000L) / denominator
        val waitMillis = Math.addExact(totalMillis, 500L)
        if (totalSamples <= 0L || totalMillis < 0L) {
            return Result.failure(IOException("Export duration is invalid"))
        }
        Result.success(ProjectExportTiming(maxEndTick, totalSamples, waitMillis))
    } catch (_: ArithmeticException) {
        Result.failure(IOException("Export duration exceeds supported range"))
    }
}
