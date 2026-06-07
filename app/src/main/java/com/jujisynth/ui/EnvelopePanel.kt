package com.jujisynth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
fun EnvelopePanel(
    state: SynthState,
    onParamChange: (SynthState) -> Unit,
    modifier: Modifier = Modifier
) {
    SynthPanel(title = "ENVELOPES", modifier = modifier) {
        // ADSR curve visualization
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val pad = 6f

                // Normalize ADSR times to fit in view width
                val a = state.ampAttack.coerceIn(0.01f, 1f) * 0.15f
                val d = state.ampDecay.coerceIn(0.01f, 1f) * 0.25f
                val s = 1f - state.ampSustain * 0.35f
                val r = state.ampRelease.coerceIn(0.01f, 1f) * 0.25f
                val total = a + d + r + 0.25f

                val aX = w * (a / total)
                val dX = w * ((a + d) / total)
                val sY = h * s
                val rStart = w * ((a + d + 0.25f) / total)

                val envPath = Path().apply {
                    moveTo(pad, h - pad)
                    lineTo(aX, pad)
                    lineTo(dX, sY)
                    lineTo(rStart, sY)
                    lineTo(w - pad, h - pad)
                }
                drawPath(envPath, color = KnobAmber, style = Stroke(width = 3f))
            }
        }

        Spacer(Modifier.height(8.dp))

        // AMP ENV row
        Text("AMP ENV", color = KnobAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SynthKnob(
                value = state.ampAttack,
                onValueChange = {
                    onParamChange(state.copy(ampAttack = it))
                    SynthEngine.setParam(ParamIds.AMP_ATTACK, it)
                },
                label = "Attack",
                valueDisplay = "%.0fms".format(state.ampAttack * 10000),
                accentColor = KnobAmber,
                size = 60.dp
            )
            SynthKnob(
                value = state.ampDecay,
                onValueChange = {
                    onParamChange(state.copy(ampDecay = it))
                    SynthEngine.setParam(ParamIds.AMP_DECAY, it)
                },
                label = "Decay",
                valueDisplay = "%.0fms".format(state.ampDecay * 10000),
                accentColor = KnobAmber,
                size = 60.dp
            )
            SynthKnob(
                value = state.ampSustain,
                onValueChange = {
                    onParamChange(state.copy(ampSustain = it))
                    SynthEngine.setParam(ParamIds.AMP_SUSTAIN, it)
                },
                label = "Sustain",
                valueDisplay = "%.0f".format(state.ampSustain * 100),
                accentColor = KnobAmber,
                size = 60.dp
            )
            SynthKnob(
                value = state.ampRelease,
                onValueChange = {
                    onParamChange(state.copy(ampRelease = it))
                    SynthEngine.setParam(ParamIds.AMP_RELEASE, it)
                },
                label = "Release",
                valueDisplay = "%.0fms".format(state.ampRelease * 10000),
                accentColor = KnobAmber,
                size = 60.dp
            )
        }

        Spacer(Modifier.height(10.dp))

        // FILTER ENV row
        Text("FILTER ENV", color = KnobGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SynthKnob(
                value = state.filterAttack,
                onValueChange = {
                    onParamChange(state.copy(filterAttack = it))
                    SynthEngine.setParam(ParamIds.FILTER_ATTACK, it)
                },
                label = "Attack",
                valueDisplay = "%.0fms".format(state.filterAttack * 10000),
                accentColor = KnobGreen,
                size = 60.dp
            )
            SynthKnob(
                value = state.filterDecay,
                onValueChange = {
                    onParamChange(state.copy(filterDecay = it))
                    SynthEngine.setParam(ParamIds.FILTER_DECAY, it)
                },
                label = "Decay",
                valueDisplay = "%.0fms".format(state.filterDecay * 10000),
                accentColor = KnobGreen,
                size = 60.dp
            )
            SynthKnob(
                value = state.filterSustain,
                onValueChange = {
                    onParamChange(state.copy(filterSustain = it))
                    SynthEngine.setParam(ParamIds.FILTER_SUSTAIN, it)
                },
                label = "Sustain",
                valueDisplay = "%.0f".format(state.filterSustain * 100),
                accentColor = KnobGreen,
                size = 60.dp
            )
            SynthKnob(
                value = state.filterRelease,
                onValueChange = {
                    onParamChange(state.copy(filterRelease = it))
                    SynthEngine.setParam(ParamIds.FILTER_RELEASE, it)
                },
                label = "Release",
                valueDisplay = "%.0fms".format(state.filterRelease * 10000),
                accentColor = KnobGreen,
                size = 60.dp
            )
        }
    }
}
