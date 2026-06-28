package com.jujidaw.ui

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
import com.jujidaw.audio.SynthEngine
import com.jujidaw.model.ParamIds
import com.jujidaw.model.SynthState
import com.jujidaw.ui.theme.*

@Composable
fun OscillatorPanel(
    state: SynthState,
    onParamChange: (SynthState) -> Unit,
    modifier: Modifier = Modifier,
    learnMode: Boolean = false,
    selectedParamId: Int? = null,
    onLearnSelect: ((Int) -> Unit)? = null
) {
    SynthPanel(title = "OSCILLATORS", modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            // OSC1 section
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text("OSC1", color = KnobAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WaveformButton(0, state.osc1Waveform, { onParamChange(state.copy(osc1Waveform = 0)); SynthEngine.setParam(ParamIds.OSC1_WAVE, 0f) }, accentColor = KnobAmber)
                    WaveformButton(1, state.osc1Waveform, { onParamChange(state.copy(osc1Waveform = 1)); SynthEngine.setParam(ParamIds.OSC1_WAVE, 1f) }, accentColor = KnobAmber)
                    WaveformButton(2, state.osc1Waveform, { onParamChange(state.copy(osc1Waveform = 2)); SynthEngine.setParam(ParamIds.OSC1_WAVE, 2f) }, accentColor = KnobAmber)
                    WaveformButton(3, state.osc1Waveform, { onParamChange(state.copy(osc1Waveform = 3)); SynthEngine.setParam(ParamIds.OSC1_WAVE, 3f) }, accentColor = KnobAmber)
                }
                OscWaveformView(waveform = state.osc1Waveform, accentColor = KnobAmber, modifier = Modifier.padding(vertical = 2.dp))
                RealKnob(value = state.osc1Level, onValueChange = { onParamChange(state.copy(osc1Level = it)); SynthEngine.setParam(ParamIds.OSC1_LEVEL, it) }, label = "Level", valueDisplay = "%.0f".format(state.osc1Level * 100), accentColor = KnobAmber, ledColor = LedAmber, size = 48.dp, learnMode = learnMode, isSelected = selectedParamId == ParamIds.OSC1_LEVEL, onLearnSelect = { onLearnSelect?.invoke(ParamIds.OSC1_LEVEL) })
            }

            // Divider
            Box(modifier = Modifier.width(1.dp).height(200.dp).clip(RoundedCornerShape(1.dp)).background(BgPanel.copy(alpha = 0.3f)))

            // OSC2 section
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text("OSC2", color = KnobCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WaveformButton(0, state.osc2Waveform, { onParamChange(state.copy(osc2Waveform = 0)); SynthEngine.setParam(ParamIds.OSC2_WAVE, 0f) }, accentColor = KnobCyan)
                    WaveformButton(1, state.osc2Waveform, { onParamChange(state.copy(osc2Waveform = 1)); SynthEngine.setParam(ParamIds.OSC2_WAVE, 1f) }, accentColor = KnobCyan)
                    WaveformButton(2, state.osc2Waveform, { onParamChange(state.copy(osc2Waveform = 2)); SynthEngine.setParam(ParamIds.OSC2_WAVE, 2f) }, accentColor = KnobCyan)
                    WaveformButton(3, state.osc2Waveform, { onParamChange(state.copy(osc2Waveform = 3)); SynthEngine.setParam(ParamIds.OSC2_WAVE, 3f) }, accentColor = KnobCyan)
                }
                OscWaveformView(waveform = state.osc2Waveform, accentColor = KnobCyan, modifier = Modifier.padding(vertical = 2.dp))
                RealKnob(value = state.osc2Level, onValueChange = { onParamChange(state.copy(osc2Level = it)); SynthEngine.setParam(ParamIds.OSC2_LEVEL, it) }, label = "Level", valueDisplay = "%.0f".format(state.osc2Level * 100), accentColor = KnobCyan, size = 48.dp, learnMode = learnMode, isSelected = selectedParamId == ParamIds.OSC2_LEVEL, onLearnSelect = { onLearnSelect?.invoke(ParamIds.OSC2_LEVEL) })
            }
        }

        Spacer(Modifier.height(8.dp))

        // Bottom knobs row
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            RealKnob(value = state.subOscLevel, onValueChange = { onParamChange(state.copy(subOscLevel = it)); SynthEngine.setParam(ParamIds.SUB_OSC_LEVEL, it) }, label = "Sub", valueDisplay = "%.0f".format(state.subOscLevel * 100), accentColor = KnobPink, size = 48.dp, learnMode = learnMode, isSelected = selectedParamId == ParamIds.SUB_OSC_LEVEL, onLearnSelect = { onLearnSelect?.invoke(ParamIds.SUB_OSC_LEVEL) })
            RealKnob(value = state.noiseLevel, onValueChange = { onParamChange(state.copy(noiseLevel = it)); SynthEngine.setParam(ParamIds.NOISE_LEVEL, it) }, label = "Noise", valueDisplay = "%.0f".format(state.noiseLevel * 100), accentColor = Color.White, size = 48.dp, learnMode = learnMode, isSelected = selectedParamId == ParamIds.NOISE_LEVEL, onLearnSelect = { onLearnSelect?.invoke(ParamIds.NOISE_LEVEL) })
            RealKnob(value = (state.oscDetune + 1f) / 2f, onValueChange = { onParamChange(state.copy(oscDetune = it * 2f - 1f)); SynthEngine.setParam(ParamIds.OSC_DETUNE, it * 2f - 1f) }, label = "Detune", valueDisplay = "%.0f".format(state.oscDetune * 100), accentColor = KnobGreen, size = 48.dp, learnMode = learnMode, isSelected = selectedParamId == ParamIds.OSC_DETUNE, onLearnSelect = { onLearnSelect?.invoke(ParamIds.OSC_DETUNE) })
            RealKnob(value = state.oscMix, onValueChange = { onParamChange(state.copy(oscMix = it)); SynthEngine.setParam(ParamIds.OSC_MIX, it) }, label = "Mix", valueDisplay = "%.0f".format(state.oscMix * 100), accentColor = KnobOrange, size = 48.dp, learnMode = learnMode, isSelected = selectedParamId == ParamIds.OSC_MIX, onLearnSelect = { onLearnSelect?.invoke(ParamIds.OSC_MIX) })
        }
    }
}
