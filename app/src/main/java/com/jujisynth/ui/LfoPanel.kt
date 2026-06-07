package com.jujisynth.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujisynth.audio.SynthEngine
import com.jujisynth.model.ParamIds
import com.jujisynth.model.SynthState
import com.jujisynth.ui.theme.*

@Composable
fun LfoPanel(
    state: SynthState,
    onParamChange: (SynthState) -> Unit,
    modifier: Modifier = Modifier
) {
    SynthPanel(title = "LFO", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // ── LFO1 ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("LFO1", color = KnobPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Sin", "Sqr", "Saw", "Tri", "Rnd").forEachIndexed { i, label ->
                        val selected = i == state.lfo1Waveform
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) KnobPink.copy(alpha = 0.2f) else PurpleMid.copy(alpha = 0.15f))
                                .border(
                                    1.dp,
                                    if (selected) KnobPink else PurpleMid.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    onParamChange(state.copy(lfo1Waveform = i))
                                    SynthEngine.setParam(ParamIds.LFO1_WAVE, i.toFloat())
                                }
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) KnobPink else TextSecondary,
                                fontSize = 7.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SynthKnob(
                        value = state.lfo1Rate,
                        onValueChange = {
                            onParamChange(state.copy(lfo1Rate = it))
                            SynthEngine.setParam(ParamIds.LFO1_RATE, it)
                        },
                        label = "Rate",
                        valueDisplay = "%.1fHz".format(0.01f + 49.99f * state.lfo1Rate * state.lfo1Rate),
                        accentColor = KnobPink,
                        size = 60.dp
                    )
                    SynthKnob(
                        value = state.lfo1Depth,
                        onValueChange = {
                            onParamChange(state.copy(lfo1Depth = it))
                            SynthEngine.setParam(ParamIds.LFO1_DEPTH, it)
                        },
                        label = "Depth",
                        valueDisplay = "%.0f".format(state.lfo1Depth * 100),
                        accentColor = KnobPink,
                        size = 60.dp
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(PurpleMid.copy(alpha = 0.3f))
            )

            // ── LFO2 ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("LFO2", color = KnobOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Sin", "Sqr", "Saw", "Tri", "Rnd").forEachIndexed { i, label ->
                        val selected = i == state.lfo2Waveform
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) KnobOrange.copy(alpha = 0.2f) else PurpleMid.copy(alpha = 0.15f))
                                .border(
                                    1.dp,
                                    if (selected) KnobOrange else PurpleMid.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    onParamChange(state.copy(lfo2Waveform = i))
                                    SynthEngine.setParam(ParamIds.LFO2_WAVE, i.toFloat())
                                }
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) KnobOrange else TextSecondary,
                                fontSize = 7.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SynthKnob(
                        value = state.lfo2Rate,
                        onValueChange = {
                            onParamChange(state.copy(lfo2Rate = it))
                            SynthEngine.setParam(ParamIds.LFO2_RATE, it)
                        },
                        label = "Rate",
                        valueDisplay = "%.1fHz".format(0.01f + 49.99f * state.lfo2Rate * state.lfo2Rate),
                        accentColor = KnobOrange,
                        size = 60.dp
                    )
                    SynthKnob(
                        value = state.lfo2Depth,
                        onValueChange = {
                            onParamChange(state.copy(lfo2Depth = it))
                            SynthEngine.setParam(ParamIds.LFO2_DEPTH, it)
                        },
                        label = "Depth",
                        valueDisplay = "%.0f".format(state.lfo2Depth * 100),
                        accentColor = KnobOrange,
                        size = 60.dp
                    )
                }
            }
        }
    }
}
