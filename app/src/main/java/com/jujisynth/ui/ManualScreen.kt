package com.jujisynth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujisynth.ui.theme.*

@Composable
fun ManualScreen(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var selectedSection by remember { mutableStateOf(0) }

    Box(modifier = modifier.fillMaxWidth().fillMaxHeight(0.8f).clip(RoundedCornerShape(12.dp)).background(BgPanel).padding(12.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("JUJI SYNTH MANUAL", color = PurpleLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                TextButton(onClick = onDismiss) { Text("✕", color = TextSecondary, fontSize = 16.sp) }
            }
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.weight(1f)) {
                // TOC
                Column(modifier = Modifier.width(120.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(SurfaceDark).padding(4.dp)) {
                    manualSections.forEachIndexed { idx, section ->
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                            .background(if (idx == selectedSection) PurplePrimary.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedSection = idx }.padding(horizontal = 6.dp, vertical = 5.dp)) {
                            Text(section.title, color = if (idx == selectedSection) PurpleLight else TextSecondary, fontSize = 9.sp,
                                fontWeight = if (idx == selectedSection) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))

                // Content
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(SurfaceDark).padding(8.dp)) {
                    val section = manualSections[selectedSection]
                    LazyColumn {
                        item {
                            Text(section.title.uppercase(), color = PurpleLight, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(section.content, color = TextSecondary, fontSize = 10.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

private data class ManualSection(val title: String, val content: String)

private val manualSections = listOf(
    ManualSection("About This Synth",
        "Juji Synth is a 4-voice subtractive synthesizer inspired by classic analog " +
        "synthesizers like the Korg Minilogue. It generates sound using oscillators " +
        "whose timbre is shaped by a filter, envelopes, and LFOs.\n\n" +
        "This app is entirely free and works offline. No internet connection required."),
    ManualSection("Subtractive Synthesis",
        "Subtractive synthesis starts with harmonically rich sound sources (oscillators) " +
        "and 'subtracts' frequencies using a filter to shape the timbre.\n\n" +
        "Signal flow: Oscillators → Filter → Amplifier → Effects\n\n" +
        "Envelopes and LFOs modulate these stages to create movement over time."),
    ManualSection("Oscillators",
        "OSC1 and OSC2 generate the raw waveforms:\n\n" +
        "- Saw: Bright, buzzy sound with all harmonics\n" +
        "- Square: Hollow, reed-like with odd harmonics\n" +
        "- Triangle: Soft, flute-like with few harmonics\n" +
        "- Sine: Pure fundamental — no harmonics\n\n" +
        "Use Detune to slightly pitch OSC2 against OSC1 for a thick, chorused effect. " +
        "Mix blends between the two. Sub adds a bass tone one octave below."),
    ManualSection("Filter",
        "The filter shapes the sound by removing frequencies:\n\n" +
        "- LPF (Low-Pass): Passes low frequencies, cuts highs\n" +
        "- HPF (High-Pass): Passes high frequencies, cuts lows\n" +
        "- BPF (Band-Pass): Passes a band of frequencies\n\n" +
        "Cutoff sets the frequency point. Resonance boosts frequencies at the cutoff. " +
        "High resonance causes self-oscillation."),
    ManualSection("Envelopes",
        "ADSR envelopes shape how a sound evolves over time:\n\n" +
        "- Attack: Time to reach peak level (fast = punchy, slow = swelling)\n" +
        "- Decay: Time to fall to sustain level\n" +
        "- Sustain: Level held while key is held\n" +
        "- Release: Time to fade to silence after key release\n\n" +
        "AMP ENV controls volume. FILTER ENV controls filter cutoff."),
    ManualSection("LFO",
        "Low Frequency Oscillators create cyclic modulation effects:\n\n" +
        "- Sine: Smooth, watery modulation\n" +
        "- Square: Abrupt on/off effects (tremolo)\n" +
        "- Saw: Ramp effects\n" +
        "- Triangle: Gentle up/down sweep\n" +
        "- Random: Sample & hold — stepped random values\n\n" +
        "Route LFOs to pitch (vibrato), filter (wobble), or amplitude (tremolo)."),
    ManualSection("Effects",
        "Three built-in effects add space and character:\n\n" +
        "Reverb: Simulates room/hall ambience. Mix blends dry/wet.\n\n" +
        "Delay: Echo effect. Time controls spacing. Feedback controls repeats.\n\n" +
        "Distortion: Adds harmonic saturation. Soft-clip for tube-like warmth.\n\n" +
        "Bypass removes all effects for a clean signal."),
    ManualSection("Sequencer",
        "The 16-step sequencer lets you program note patterns:\n\n" +
        "- Tap a step to toggle a note on/off\n" +
        "- Each step stores a note and velocity\n" +
        "- Gate length controls note duration\n" +
        "- Tempo adjusts playback speed (30-300 BPM)\n\n" +
        "Use the sequencer to create basslines, arpeggios, or rhythmic patterns."),
    ManualSection("Presets",
        "Presets save the complete state of all parameters:\n\n" +
        "- Factory presets: 50+ curated sounds across categories\n" +
        "- User presets: Save your own creations\n" +
        "- Categories: Leads, Pads, Bass, FX, Ambient\n\n" +
        "Use presets as learning tools — study how each sound is built."),
    ManualSection("USB MIDI",
        "Connect a USB MIDI keyboard or controller via USB-OTG:\n\n" +
        "- The synth automatically detects connected devices\n" +
        "- Play notes via the MIDI keyboard\n" +
        "- Use mod wheel and pitch bend wheels\n" +
        "- MIDI CC messages can control parameters\n\n" +
        "Standard CC mapping: CC1 = Mod Wheel, CC64 = Sustain Pedal"),
    ManualSection("Troubleshooting",
        "No sound? Check:\n\n" +
        "- Master volume is turned up\n" +
        "- At least one oscillator is active (not at 0 level)\n" +
        "- Effects bypass is not enabled\n" +
        "- Keyboard or MIDI note is actually playing\n\n" +
        "Audio glitches? Try lowering sample rate.\n\n" +
        "MIDI not working? Ensure USB-OTG cable is supported.")
)
