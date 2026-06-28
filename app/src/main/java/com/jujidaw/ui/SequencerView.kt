package com.jujidaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.jujidaw.model.SequencerStep
import com.jujidaw.ui.theme.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * 16-step sequencer with play/stop/reset and tempo.
 */
@Composable
fun SequencerView(
    steps: List<SequencerStep>,
    onStepChange: (Int, SequencerStep) -> Unit,
    currentStep: Int,
    playing: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    tempo: Float,
    onTempoChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    learnMode: Boolean = false,
    selectedParamId: Int? = null,
    onLearnSelect: ((Int) -> Unit)? = null
) {
    SynthPanel(title = "SEQUENCER", modifier = modifier) {
        // Transport
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Play/Pause
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (playing) KnobGreen else BgPanel)
                    .clickable { onPlayingChange(!playing) },
                contentAlignment = Alignment.Center
            ) {
                Text(if (playing) "■" else "▶", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            // Reset
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                    .background(BgPanel).clickable { onPlayingChange(false) },
                contentAlignment = Alignment.Center
            ) {
                Text("↺", color = Color.White, fontSize = 12.sp)
            }
            // Tempo
            SynthKnob(
                value = tempo / 300f,
                onValueChange = { val bpm = it * 300f; onTempoChange(bpm); SynthEngine.setParam(60, bpm) },
                label = "Tempo", valueDisplay = "%.0f".format(tempo),
                accentColor = KnobAmber, size = 28.dp
            )
        }

        Spacer(Modifier.height(4.dp))

        // 16-step grid
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (row in 0 until 2) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    for (col in 0 until 8) {
                        val stepIdx = row * 8 + col
                        val step = steps.getOrNull(stepIdx) ?: return@Column
                        val isCurrent = stepIdx == currentStep
                        val hasNote = step.note >= 0

                        Box(
                            modifier = Modifier
                                .size(width = 28.dp, height = 22.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(when {
                                    isCurrent && hasNote -> SeqStepCurrent
                                    isCurrent -> SeqStepCurrent.copy(alpha = 0.5f)
                                    hasNote -> SeqStepActive
                                    else -> SeqStepInactive
                                })
                                .clickable {
                                    val newStep = if (hasNote) step.copy(note = -1)
                                    else step.copy(note = 60 + stepIdx)
                                    onStepChange(stepIdx, newStep)
                                    // Preview: play the note briefly
                                    if (newStep.note >= 0 && !playing) {
                                        SynthEngine.noteOn(newStep.note, 100)
                                        GlobalScope.launch {
                                            kotlinx.coroutines.delay(100)
                                            SynthEngine.noteOff(newStep.note)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasNote) {
                                val noteName = midiNoteToName(step.note)
                                Text(noteName, color = Color.White, fontSize = 7.sp)
                            }
                        }
                    }
                }
                if (row == 0) Spacer(Modifier.width(4.dp))
            }
        }
    }
}

private fun midiNoteToName(note: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    if (note < 0) return "--"
    val octave = (note / 12) - 1
    val name = names[note % 12]
    return "$name$octave"
}
