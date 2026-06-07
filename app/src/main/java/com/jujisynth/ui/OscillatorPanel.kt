package com.jujisynth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujisynth.audio.SynthEngine
import com.jujisynth.model.ParamIds
import com.jujisynth.model.SynthState
import com.jujisynth.ui.theme.*

@Composable
fun OscillatorPanel(
    state: SynthState,
    onParamChange: (SynthState) -> Unit,
    modifier: Modifier = Modifier
) {
    SynthPanel(title = "OSCILLATORS", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // ── OSC1 ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("OSC1", color = KnobAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WaveformButton(0, state.osc1Waveform, {
                        onParamChange(state.copy(osc1Waveform = 0))
                        SynthEngine.setParam(ParamIds.OSC1_WAVE, 0f)
                    }, accentColor = KnobAmber)
                    WaveformButton(1, state.osc1Waveform, {
                        onParamChange(state.copy(osc1Waveform = 1))
                        SynthEngine.setParam(ParamIds.OSC1_WAVE, 1f)
                    }, accentColor = KnobAmber)
                    WaveformButton(2, state.osc1Waveform, {
                        onParamChange(state.copy(osc1Waveform = 2))
                        SynthEngine.setParam(ParamIds.OSC1_WAVE, 2f)
                    }, accentColor = KnobAmber)
                    WaveformButton(3, state.osc1Waveform, {
                        onParamChange(state.copy(osc1Waveform = 3))
                        SynthEngine.setParam(ParamIds.OSC1_WAVE, 3f)
                    }, accentColor = KnobAmber)
                }
                Spacer(Modifier.height(6.dp))
                SynthKnob(
                    value = state.osc1Level,
                    onValueChange = {
                        onParamChange(state.copy(osc1Level = it))
                        SynthEngine.setParam(ParamIds.OSC1_LEVEL, it)
                    },
                    label = "Level",
                    valueDisplay = "%.0f".format(state.osc1Level * 100),
                    accentColor = KnobAmber,
                    size = 80.dp
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SynthKnob(
                        value = state.subOscLevel,
                        onValueChange = {
                            onParamChange(state.copy(subOscLevel = it))
                            SynthEngine.setParam(ParamIds.SUB_OSC_LEVEL, it)
                        },
                        label = "Sub",
                        valueDisplay = "%.0f".format(state.subOscLevel * 100),
                        accentColor = KnobPink,
                        size = 48.dp
                    )
                    SynthKnob(
                        value = state.noiseLevel,
                        onValueChange = {
                            onParamChange(state.copy(noiseLevel = it))
                            SynthEngine.setParam(ParamIds.NOISE_LEVEL, it)
                        },
                        label = "Noise",
                        valueDisplay = "%.0f".format(state.noiseLevel * 100),
                        accentColor = Color.White,
                        size = 48.dp
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(PurpleMid.copy(alpha = 0.3f))
            )

            // ── OSC2 ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("OSC2", color = KnobCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WaveformButton(0, state.osc2Waveform, {
                        onParamChange(state.copy(osc2Waveform = 0))
                        SynthEngine.setParam(ParamIds.OSC2_WAVE, 0f)
                    }, accentColor = KnobCyan)
                    WaveformButton(1, state.osc2Waveform, {
                        onParamChange(state.copy(osc2Waveform = 1))
                        SynthEngine.setParam(ParamIds.OSC2_WAVE, 1f)
                    }, accentColor = KnobCyan)
                    WaveformButton(2, state.osc2Waveform, {
                        onParamChange(state.copy(osc2Waveform = 2))
                        SynthEngine.setParam(ParamIds.OSC2_WAVE, 2f)
                    }, accentColor = KnobCyan)
                    WaveformButton(3, state.osc2Waveform, {
                        onParamChange(state.copy(osc2Waveform = 3))
                        SynthEngine.setParam(ParamIds.OSC2_WAVE, 3f)
                    }, accentColor = KnobCyan)
                }
                Spacer(Modifier.height(6.dp))
                SynthKnob(
                    value = state.osc2Level,
                    onValueChange = {
                        onParamChange(state.copy(osc2Level = it))
                        SynthEngine.setParam(ParamIds.OSC2_LEVEL, it)
                    },
                    label = "Level",
                    valueDisplay = "%.0f".format(state.osc2Level * 100),
                    accentColor = KnobCyan,
                    size = 80.dp
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SynthKnob(
                        value = (state.oscDetune + 1f) / 2f,
                        onValueChange = {
                            val newDetune = it * 2f - 1f
                            onParamChange(state.copy(oscDetune = newDetune))
                            SynthEngine.setParam(ParamIds.OSC_DETUNE, newDetune)
                        },
                        label = "Detune",
                        valueDisplay = "%.0f".format(state.oscDetune * 100),
                        accentColor = KnobGreen,
                        size = 48.dp
                    )
                    SynthKnob(
                        value = state.oscMix,
                        onValueChange = {
                            onParamChange(state.copy(oscMix = it))
                            SynthEngine.setParam(ParamIds.OSC_MIX, it)
                        },
                        label = "Mix",
                        valueDisplay = "%.0f".format(state.oscMix * 100),
                        accentColor = KnobOrange,
                        size = 48.dp
                    )
                }
            }
        }
    }
}
