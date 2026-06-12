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
    state: SynthState, onParamChange: (SynthState) -> Unit, modifier: Modifier = Modifier,
    learnMode: Boolean = false, selectedParamId: Int? = null, onLearnSelect: ((Int) -> Unit)? = null
) {
    SynthPanel(title = "FILTER", modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Mode", color = TextSecondary, fontSize = 9.sp)
                Row {
                    listOf("LPF", "HPF", "BPF").forEachIndexed { i, label ->
                        val selected = i == state.filterMode
                        Box(modifier = Modifier.padding(1.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (selected) KnobCyan.copy(alpha = 0.3f) else BgPanel.copy(alpha = 0.2f))
                            .clickable { onParamChange(state.copy(filterMode = i)); SynthEngine.setParam(ParamIds.FILTER_MODE, i.toFloat()) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(label, color = if (selected) KnobCyan else TextSecondary, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            // Filter frequency response curve
            FilterResponseView(
                cutoff = state.filterCutoff,
                resonance = state.filterResonance,
                mode = state.filterMode,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            RealKnob(value = state.filterCutoff, onValueChange = { onParamChange(state.copy(filterCutoff = it)); SynthEngine.setParam(ParamIds.FILTER_CUTOFF, it) }, label = "Cutoff", valueDisplay = "%.0f".format(state.filterCutoff * 100), accentColor = KnobCyan, ledColor = LedCyan, size = 48.dp, learnMode = learnMode, isSelected = selectedParamId == ParamIds.FILTER_CUTOFF, onLearnSelect = { onLearnSelect?.invoke(ParamIds.FILTER_CUTOFF) })
            RealKnob(value = state.filterResonance, onValueChange = { onParamChange(state.copy(filterResonance = it)); SynthEngine.setParam(ParamIds.FILTER_RESONANCE, it) }, label = "Resonance", valueDisplay = "%.0f".format(state.filterResonance * 100), accentColor = KnobCyan, ledColor = LedCyan, size = 48.dp, learnMode = learnMode, isSelected = selectedParamId == ParamIds.FILTER_RESONANCE, onLearnSelect = { onLearnSelect?.invoke(ParamIds.FILTER_RESONANCE) })
            RealKnob(value = (state.filterEnvAmount + 1f) / 2f, onValueChange = { onParamChange(state.copy(filterEnvAmount = it * 2f - 1f)); SynthEngine.setParam(ParamIds.FILTER_ENV_AMOUNT, it * 2f - 1f) }, label = "Env Amt", valueDisplay = "%.0f".format(state.filterEnvAmount * 100), accentColor = KnobGreen, size = 48.dp, learnMode = learnMode, isSelected = selectedParamId == ParamIds.FILTER_ENV_AMOUNT, onLearnSelect = { onLearnSelect?.invoke(ParamIds.FILTER_ENV_AMOUNT) })
        }
    }
}
