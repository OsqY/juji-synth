package com.jujisynth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujisynth.audio.SynthEngine
import com.jujisynth.model.ParamIds
import com.jujisynth.model.SynthState
import com.jujisynth.ui.theme.*

@Composable
fun FilterPanel(
    state: SynthState,
    onParamChange: (SynthState) -> Unit,
    modifier: Modifier = Modifier
) {
    SynthPanel(title = "FILTER", modifier = modifier) {
        // Mode selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mode", color = TextSecondary, fontSize = 9.sp)
            Spacer(Modifier.width(8.dp))
            listOf("LPF", "HPF", "BPF").forEachIndexed { i, label ->
                val selected = i == state.filterMode
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) KnobCyan.copy(alpha = 0.25f) else PurpleMid.copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            if (selected) KnobCyan else PurpleMid.copy(alpha = 0.3f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            onParamChange(state.copy(filterMode = i))
                            SynthEngine.setParam(ParamIds.FILTER_MODE, i.toFloat())
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) KnobCyan else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Large knobs row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SynthKnob(
                value = state.filterCutoff,
                onValueChange = {
                    onParamChange(state.copy(filterCutoff = it))
                    SynthEngine.setParam(ParamIds.FILTER_CUTOFF, it)
                },
                label = "Cutoff",
                valueDisplay = "%.0f".format(state.filterCutoff * 100),
                accentColor = KnobCyan,
                size = 80.dp
            )
            SynthKnob(
                value = state.filterResonance,
                onValueChange = {
                    onParamChange(state.copy(filterResonance = it))
                    SynthEngine.setParam(ParamIds.FILTER_RESONANCE, it)
                },
                label = "Resonance",
                valueDisplay = "%.0f".format(state.filterResonance * 100),
                accentColor = KnobCyan,
                size = 80.dp
            )
            SynthKnob(
                value = (state.filterEnvAmount + 1f) / 2f,
                onValueChange = {
                    val newAmt = it * 2f - 1f
                    onParamChange(state.copy(filterEnvAmount = newAmt))
                    SynthEngine.setParam(ParamIds.FILTER_ENV_AMOUNT, newAmt)
                },
                label = "Env Amt",
                valueDisplay = "%.0f".format(state.filterEnvAmount * 100),
                accentColor = KnobGreen,
                size = 80.dp
            )
        }

        Spacer(Modifier.height(8.dp))

        // Envelope curve visualization
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(BgKnobArea)
                .padding(6.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val pad = 4f

                // Envelope shape using filter envelope values
                val a = state.filterAttack.coerceIn(0.01f, 1f) * 0.2f
                val d = state.filterDecay.coerceIn(0.01f, 1f) * 0.2f
                val s = 1f - state.filterSustain * 0.4f
                val r = state.filterRelease.coerceIn(0.01f, 1f) * 0.2f

                val total = a + d + r + 0.3f
                val aX = w * (a / total)
                val dX = w * ((a + d) / total)
                val sY = h * s
                val rStart = w * ((a + d + 0.3f) / total)

                val path = Path().apply {
                    moveTo(0f, h - pad)
                    lineTo(aX, pad)
                    lineTo(dX, sY)
                    lineTo(rStart, sY)
                    lineTo(w - pad, h - pad)
                }
                drawPath(path, color = KnobGreen, style = Stroke(width = 2.5f))
            }
        }
    }
}
