package com.jujisynth.ui

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jujisynth.model.ModulationRoute
import com.jujisynth.ui.theme.*
/**
 * Modulation matrix showing up to 8 routing slots.
 * Each slot: source → destination with amount slider.
 */
@Composable
fun ModulationMatrixPanel(
    routes: List<ModulationRoute>,
    onRouteChange: (Int, ModulationRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val sourceNames = listOf("LFO1", "LFO2", "ENV1", "ENV2", "Vel", "AT")
    val destNames = listOf("Pitch", "Filter", "Res", "Amp", "OscMix", "LFO Rt")
    SynthPanel(title = "MOD MATRIX", modifier = modifier) {
        Column(
            modifier = Modifier.heightIn(max = 120.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            routes.forEachIndexed { idx, route ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (route.active) PurpleMid.copy(alpha = 0.2f) else SurfaceDark)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Toggle
                    SynthToggle(
                        route.active,
                        { onRouteChange(idx, route.copy(active = it)) },
                        label = "",
                        enabledColor = PurplePrimary,
                        disabledColor = PurpleMid.copy(alpha = 0.3f),
                        modifier = Modifier.width(24.dp)
                    )
                    // Source
                    val srcIdx = route.source.coerceIn(0, sourceNames.size - 1)
                    Text(
                        sourceNames[srcIdx],
                        color = KnobAmber,
                        fontSize = 8.sp,
                        modifier = Modifier.width(28.dp)
                    )
                    Text("→", color = TextMuted, fontSize = 8.sp)
                    // Destination
                    val dstIdx = route.destination.coerceIn(0, destNames.size - 1)
                    Text(
                        destNames[dstIdx],
                        color = KnobCyan,
                        fontSize = 8.sp,
                        modifier = Modifier.width(30.dp)
                    )
                    // Amount knob
                    SynthKnob(
                        value = (route.amount + 1f) / 2f,
                        onValueChange = { onRouteChange(idx, route.copy(amount = it * 2f - 1f)) },
                        valueDisplay = "%.0f".format(route.amount * 100),
                        accentColor = KnobGreen,
                        size = 24.dp
                    )
                }
            }
        }
    }
}
