package com.jujisynth.ui

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
import com.jujisynth.audio.SynthEngine
import com.jujisynth.model.ParamIds
import com.jujisynth.model.SynthState
import com.jujisynth.ui.theme.*

@Composable
fun EffectsPanel(
    state: SynthState,
    onParamChange: (SynthState) -> Unit,
    modifier: Modifier = Modifier
) {
    SynthPanel(title = "EFFECTS", modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // ── Reverb ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("Reverb", color = KnobCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                SynthKnob(
                    value = state.reverbMix,
                    onValueChange = {
                        onParamChange(state.copy(reverbMix = it))
                        SynthEngine.setParam(ParamIds.REVERB_MIX, it)
                    },
                    label = "Mix",
                    valueDisplay = "%.0f".format(state.reverbMix * 100),
                    accentColor = KnobCyan,
                    size = 48.dp
                )
                Spacer(Modifier.height(4.dp))
                SynthKnob(
                    value = state.reverbDecay,
                    onValueChange = {
                        onParamChange(state.copy(reverbDecay = it))
                        SynthEngine.setParam(ParamIds.REVERB_DECAY, it)
                    },
                    label = "Decay",
                    valueDisplay = "%.0f".format(state.reverbDecay * 100),
                    accentColor = KnobCyan,
                    size = 48.dp
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(PurpleMid.copy(alpha = 0.3f))
            )

            // ── Delay ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("Delay", color = KnobAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                SynthKnob(
                    value = state.delayMix,
                    onValueChange = {
                        onParamChange(state.copy(delayMix = it))
                        SynthEngine.setParam(ParamIds.DELAY_MIX, it)
                    },
                    label = "Mix",
                    valueDisplay = "%.0f".format(state.delayMix * 100),
                    accentColor = KnobAmber,
                    size = 48.dp
                )
                Spacer(Modifier.height(4.dp))
                SynthKnob(
                    value = state.delayTime,
                    onValueChange = {
                        onParamChange(state.copy(delayTime = it))
                        SynthEngine.setParam(ParamIds.DELAY_TIME, it)
                    },
                    label = "Time",
                    valueDisplay = "%.0fms".format(20 + 1980 * state.delayTime * state.delayTime),
                    accentColor = KnobAmber,
                    size = 48.dp
                )
                Spacer(Modifier.height(4.dp))
                SynthKnob(
                    value = state.delayFeedback,
                    onValueChange = {
                        onParamChange(state.copy(delayFeedback = it))
                        SynthEngine.setParam(ParamIds.DELAY_FEEDBACK, it)
                    },
                    label = "Fdbk",
                    valueDisplay = "%.0f".format(state.delayFeedback * 100),
                    accentColor = KnobAmber,
                    size = 48.dp
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(PurpleMid.copy(alpha = 0.3f))
            )

            // ── Distortion ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("Distortion", color = KnobRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                SynthKnob(
                    value = state.distortionDrive,
                    onValueChange = {
                        onParamChange(state.copy(distortionDrive = it))
                        SynthEngine.setParam(ParamIds.DIST_DRIVE, it)
                    },
                    label = "Drive",
                    valueDisplay = "%.1fx".format(1f + 19f * state.distortionDrive * state.distortionDrive),
                    accentColor = KnobRed,
                    size = 48.dp
                )
                Spacer(Modifier.height(4.dp))
                SynthKnob(
                    value = state.distortionMix,
                    onValueChange = {
                        onParamChange(state.copy(distortionMix = it))
                        SynthEngine.setParam(ParamIds.DIST_MIX, it)
                    },
                    label = "Mix",
                    valueDisplay = "%.0f".format(state.distortionMix * 100),
                    accentColor = KnobRed,
                    size = 48.dp
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Global bypass toggle
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
                disabledColor = PurpleMid.copy(alpha = 0.3f)
            )
        }
    }
}
