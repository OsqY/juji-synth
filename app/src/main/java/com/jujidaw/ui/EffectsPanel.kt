package com.jujidaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.audio.SynthEngine
import com.jujidaw.model.ParamIds
import com.jujidaw.model.SynthState
import com.jujidaw.ui.theme.*

@Composable
fun EffectsPanel(
    state: SynthState,
    onParamChange: (SynthState) -> Unit,
    modifier: Modifier = Modifier,
    learnMode: Boolean = false,
    selectedParamId: Int? = null,
    onLearnSelect: ((Int) -> Unit)? = null
) {
    SynthPanel(title = "EFFECTS", modifier = modifier) {
        // Bypass toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SynthToggle(
                checked = state.effectsBypass,
                onCheckedChange = {
                    onParamChange(state.copy(effectsBypass = it))
                    SynthEngine.setParam(ParamIds.EFFECTS_BYPASS, if (it) 1f else 0f)
                },
                label = "Bypass All",
                enabledColor = KnobRed,
                disabledColor = BgPanel.copy(alpha = 0.3f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Reverb
        Text("REVERB", color = KnobCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RealKnob(
                value = state.reverbMix,
                onValueChange = {
                    onParamChange(state.copy(reverbMix = it))
                    SynthEngine.setParam(ParamIds.REVERB_MIX, it)
                },
                label = "Reverb Mix",
                valueDisplay = "%.0f".format(state.reverbMix * 100),
                accentColor = KnobCyan, ledColor = LedCyan,
                size = 48.dp,
                learnMode = learnMode,
                isSelected = selectedParamId == ParamIds.REVERB_MIX,
                onLearnSelect = { onLearnSelect?.invoke(ParamIds.REVERB_MIX) }
            )
            RealKnob(
                value = state.reverbDecay,
                onValueChange = {
                    onParamChange(state.copy(reverbDecay = it))
                    SynthEngine.setParam(ParamIds.REVERB_DECAY, it)
                },
                label = "Reverb Decay",
                valueDisplay = "%.0f".format(state.reverbDecay * 100),
                accentColor = KnobCyan, ledColor = LedCyan,
                size = 48.dp,
                learnMode = learnMode,
                isSelected = selectedParamId == ParamIds.REVERB_DECAY,
                onLearnSelect = { onLearnSelect?.invoke(ParamIds.REVERB_DECAY) }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Delay
        Text("DELAY", color = KnobOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RealKnob(
                value = state.delayMix,
                onValueChange = {
                    onParamChange(state.copy(delayMix = it))
                    SynthEngine.setParam(ParamIds.DELAY_MIX, it)
                },
                label = "Delay Mix",
                valueDisplay = "%.0f".format(state.delayMix * 100),
                accentColor = KnobOrange, ledColor = LedOrange,
                size = 48.dp,
                learnMode = learnMode,
                isSelected = selectedParamId == ParamIds.DELAY_MIX,
                onLearnSelect = { onLearnSelect?.invoke(ParamIds.DELAY_MIX) }
            )
            RealKnob(
                value = state.delayTime,
                onValueChange = {
                    onParamChange(state.copy(delayTime = it))
                    SynthEngine.setParam(ParamIds.DELAY_TIME, it)
                },
                label = "Delay Time",
                valueDisplay = "%.0fms".format(state.delayTime * 2000),
                accentColor = KnobOrange, ledColor = LedOrange,
                size = 48.dp,
                learnMode = learnMode,
                isSelected = selectedParamId == ParamIds.DELAY_TIME,
                onLearnSelect = { onLearnSelect?.invoke(ParamIds.DELAY_TIME) }
            )
            RealKnob(
                value = state.delayFeedback,
                onValueChange = {
                    onParamChange(state.copy(delayFeedback = it))
                    SynthEngine.setParam(ParamIds.DELAY_FEEDBACK, it)
                },
                label = "Delay Fdbk",
                valueDisplay = "%.0f".format(state.delayFeedback * 100),
                accentColor = KnobOrange, ledColor = LedOrange,
                size = 48.dp,
                learnMode = learnMode,
                isSelected = selectedParamId == ParamIds.DELAY_FEEDBACK,
                onLearnSelect = { onLearnSelect?.invoke(ParamIds.DELAY_FEEDBACK) }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Distortion
        Text("DISTORTION", color = KnobRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RealKnob(
                value = state.distortionDrive,
                onValueChange = {
                    onParamChange(state.copy(distortionDrive = it))
                    SynthEngine.setParam(ParamIds.DIST_DRIVE, it)
                },
                label = "Dist Drive",
                valueDisplay = "%.0f".format(state.distortionDrive * 100),
                accentColor = KnobRed, ledColor = LedRed,
                size = 48.dp,
                learnMode = learnMode,
                isSelected = selectedParamId == ParamIds.DIST_DRIVE,
                onLearnSelect = { onLearnSelect?.invoke(ParamIds.DIST_DRIVE) }
            )
            RealKnob(
                value = state.distortionMix,
                onValueChange = {
                    onParamChange(state.copy(distortionMix = it))
                    SynthEngine.setParam(ParamIds.DIST_MIX, it)
                },
                label = "Dist Mix",
                valueDisplay = "%.0f".format(state.distortionMix * 100),
                accentColor = KnobRed, ledColor = LedRed,
                size = 48.dp,
                learnMode = learnMode,
                isSelected = selectedParamId == ParamIds.DIST_MIX,
                onLearnSelect = { onLearnSelect?.invoke(ParamIds.DIST_MIX) }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Chorus
        Text("CHORUS", color = KnobGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RealKnob(
                value = state.chorusRate,
                onValueChange = {
                    onParamChange(state.copy(chorusRate = it))
                    SynthEngine.setParam(ParamIds.CHORUS_RATE, it)
                },
                label = "Chorus Rate",
                valueDisplay = "%.1fHz".format(0.01f + 19.99f * state.chorusRate),
                accentColor = KnobGreen, ledColor = LedGreen,
                size = 48.dp,
                learnMode = learnMode,
                isSelected = selectedParamId == ParamIds.CHORUS_RATE,
                onLearnSelect = { onLearnSelect?.invoke(ParamIds.CHORUS_RATE) }
            )
            RealKnob(
                value = state.chorusDepth,
                onValueChange = {
                    onParamChange(state.copy(chorusDepth = it))
                    SynthEngine.setParam(ParamIds.CHORUS_DEPTH, it)
                },
                label = "Chorus Depth",
                valueDisplay = "%.0f".format(state.chorusDepth * 100),
                accentColor = KnobGreen, ledColor = LedGreen,
                size = 48.dp,
                learnMode = learnMode,
                isSelected = selectedParamId == ParamIds.CHORUS_DEPTH,
                onLearnSelect = { onLearnSelect?.invoke(ParamIds.CHORUS_DEPTH) }
            )
            RealKnob(
                value = state.chorusMix,
                onValueChange = {
                    onParamChange(state.copy(chorusMix = it))
                    SynthEngine.setParam(ParamIds.CHORUS_MIX, it)
                },
                label = "Chorus Mix",
                valueDisplay = "%.0f".format(state.chorusMix * 100),
                accentColor = KnobGreen, ledColor = LedGreen,
                size = 48.dp,
                learnMode = learnMode,
                isSelected = selectedParamId == ParamIds.CHORUS_MIX,
                onLearnSelect = { onLearnSelect?.invoke(ParamIds.CHORUS_MIX) }
            )
        }
    }
}
