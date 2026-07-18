package com.jujidaw.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jujidaw.ui.theme.OnSurface
import com.jujidaw.ui.theme.OnSurfaceVariant
import com.jujidaw.ui.theme.Outline
import com.jujidaw.ui.theme.Primary
import com.jujidaw.ui.theme.Secondary
import com.jujidaw.ui.theme.Spacing
import com.jujidaw.ui.theme.SurfaceContainer
import com.jujidaw.ui.theme.SurfaceContainerHigh
import com.jujidaw.ui.theme.SurfaceContainerLow
import com.jujidaw.ui.theme.TitleLarge
import com.jujidaw.ui.theme.BodyMedium
import com.jujidaw.ui.theme.LabelSmall

/** Global, offline workflow reference for the core music-making paths. */
@Composable
fun HelpScreen(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(SurfaceContainerLow)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("WORKFLOW GUIDE", color = Primary, style = LabelSmall, fontWeight = FontWeight.Bold)
                    Text("Make sound first. Arrange it second.", color = OnSurface, style = TitleLarge)
                }
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Outline, RoundedCornerShape(8.dp))
                            .clickable(onClick = onClose)
                            .padding(12.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close workflow help", tint = OnSurface)
                }
            }

            SignalFlow()

            HelpSection(
                index = "01",
                title = "Pads: samples or synth voices",
                accent = Secondary,
                text = "Import audio into a pad, then tap that pad to hear its sample. Turn on Synth for a pad to replace its sample voice with an independent synth. The Pad selector on the Synth screen edits that pad's synth voice.",
            )
            HelpSection(
                index = "02",
                title = "Patterns are sequencer content",
                accent = Primary,
                text = "Sequencer Pattern 1-16 chooses a pattern made in the Seq screen. Pattern 1 is not Pad 1: it plays every step or piano-roll note stored in Pattern 1. To make a beat, open Seq, select a pattern, activate pad rows, then place that pattern on the Timeline.",
            )
            HelpSection(
                index = "03",
                title = "Timeline: patterns versus one-shot pads",
                accent = Primary,
                text = "Tap an empty lane to place the selected Sequencer Pattern. Use One-shot Pad to place exactly one pad hit at the playhead. The hit marker is a single trigger, not a repeating clip; move it to change when the hit occurs.",
            )
            HelpSection(
                index = "04",
                title = "Punch and loop",
                accent = Secondary,
                text = "Punch limits recording, not playback. Turning it on creates a one-bar range from the current playhead if you have not set one yet. Loop repeats playback between its start and end points.",
            )
            HelpSection(
                index = "05",
                title = "No sound? Check this order",
                accent = Color(0xFFFC393D),
                text = "1. Confirm Play is moving. 2. For samples, load audio into the chosen pad. 3. For a synth pad, enable Synth on that pad. 4. Check mute/solo and master level. 5. In Keys, confirm the current target: Global Synth, sample bank, track, or selected synth pad.",
            )

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun SignalFlow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceContainer)
                .border(1.dp, Outline, RoundedCornerShape(10.dp))
                .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SignalNode("SOURCE", "Pads / Synth")
        Text("->", color = Primary, style = TitleLarge)
        SignalNode("WRITE", "Seq / Timeline")
        Text("->", color = Primary, style = TitleLarge)
        SignalNode("HEAR", "Transport")
    }
}

@Composable
private fun SignalNode(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = OnSurfaceVariant, style = LabelSmall)
        Text(value, color = OnSurface, style = BodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HelpSection(index: String, title: String, accent: Color, text: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceContainerHigh)
                .padding(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Text(index, color = accent, style = TitleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(Spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, color = OnSurface, style = BodyMedium, fontWeight = FontWeight.Bold)
            Text(text, color = OnSurfaceVariant, style = BodyMedium)
        }
    }
}
