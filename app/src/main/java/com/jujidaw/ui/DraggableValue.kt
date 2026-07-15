package com.jujidaw.ui

import androidx.compose.ui.unit.dp
import com.jujidaw.ui.theme.TouchTargetMin
import kotlin.math.roundToInt

/**
 * Shared controller for draggable value UI (faders, knobs).
 * Handles tap vs drag discrimination via touch-slop hysteresis and centralizes the
 * Material/ADA 44dp minimum interactive touch-target rule.
 */
object DraggableValueController {
    /** Minimum drag distance before entering drag mode (prevents tap-jump on small drags). */
    val touchSlop = 8.dp

    /** Minimum interactive touch-target size (Material/ADA). Nothing interactive below this. */
    val minTouchTarget = TouchTargetMin

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
