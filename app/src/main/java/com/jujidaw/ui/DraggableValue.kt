package com.jujidaw.ui

import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Shared controller for draggable value UI (faders, knobs).
 * Handles tap vs drag discrimination via touch-slop hysteresis.
 */
object DraggableValueController {
    /** Minimum drag distance before entering drag mode (prevents tap-jump on small drags). */
    val touchSlop = 8.dp

    /** Quantization step for dB values (0.1 dB steps). */
    const val dB_STEP = 0.1f

    /**
     * Quantize a dB value to the nearest step.
     */
    fun quantizeDb(
        value: Float,
        step: Float = dB_STEP,
    ): Float = (value / step).roundToInt() * step
}
