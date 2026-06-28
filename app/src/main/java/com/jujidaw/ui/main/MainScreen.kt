package com.jujidaw.ui.main

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.JujiDawApp
import com.jujidaw.R
import com.jujidaw.model.TICKS_PER_STEP
import com.jujidaw.ui.synth.SynthScreen
import com.jujidaw.ui.keyboard.KeyboardScreen
import com.jujidaw.ui.mixer.MixerScreen
import com.jujidaw.ui.pads.PadsScreen
import com.jujidaw.ui.project.ProjectScreen
import com.jujidaw.ui.sequencer.SequencerScreen
import com.jujidaw.ui.theme.*
import com.jujidaw.ui.timeline.TimelineScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class MainTab(
    val label: String,
    val icon: ImageVector
) {
    TIMELINE("Timeline", Icons.AutoMirrored.Filled.ViewList),
    MIXER("Mixer", Icons.Filled.Equalizer),
    SYNTH("Synth", Icons.Filled.Tune),
    PADS("Pads", Icons.Filled.Dashboard),
    KEYBOARD("Keys", Icons.Filled.MusicNote),
    SEQUENCER("Seq", Icons.Filled.ViewModule),
    PROJECT("Project", Icons.Filled.Folder);
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = MainTab.entries.toTypedArray()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        modifier = modifier.background(BgGunmetal),
        containerColor = BgGunmetal,
        bottomBar = {
            if (!isLandscape) {
                Column {
                    PersistentTransportBar(modifier = Modifier.fillMaxWidth())
                    NavigationBar(
                        containerColor = BgPanel,
                        contentColor = TextPrimary,
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = KnobAmber,
                                    indicatorColor = KnobAmber,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgGunmetal)
        ) {    
            if (isLandscape) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(80.dp)
                        .background(BgPanel)
                        .verticalScroll(rememberScrollState())
                ) {
                    PersistentTransportBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    tabs.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = KnobAmber,
                                indicatorColor = KnobAmber,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (tabs[selectedTab]) {
                    MainTab.TIMELINE -> TimelineScreen()
                    MainTab.MIXER -> MixerScreen()
                    MainTab.SYNTH -> SynthScreen()
                    MainTab.PADS -> PadsScreen()
                    MainTab.KEYBOARD -> KeyboardScreen()
                    MainTab.SEQUENCER -> SequencerScreen()
                    MainTab.PROJECT -> ProjectScreen()
                }
            }
        }
    }
}

@Composable
private fun PersistentTransportBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val transportController = remember {
        (context.applicationContext as JujiDawApp).transportController
    }

    var transportState by remember {
        mutableStateOf(transportController.transportState)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            transportState = transportController.transportState
            delay(50)
        }
    }

    Row(
        modifier = modifier
            .height(44.dp)
            .background(BgPanel)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Play / Stop
        TransportMiniButton(
            label = if (transportState.playing) "\u25A0" else "\u25B6",
            active = transportState.playing,
            activeColor = TransportGreen,
            onClick = {
                if (transportState.playing) {
                    transportController.stop()
                } else {
                    transportController.play()
                }
            }
        )

        // Record arm
        TransportMiniButton(
            label = "\u25CF",
            active = transportState.recording,
            activeColor = TransportRed,
            onClick = {
                transportController.setRecording(!transportState.recording)
            }
        )

        // Reset
        TransportMiniButton(
            label = "\u21BA",
            active = false,
            activeColor = TransportRed,
            onClick = {
                transportController.stop()
                transportController.seek(com.jujidaw.model.TransportPosition())
            }
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Time LCD
        val step = transportState.position.tick / TICKS_PER_STEP
        Text(
            text = "${transportState.position.bar + 1}|${transportState.position.beat + 1}|${step + 1}",
            color = KnobAmber,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )

        Spacer(modifier = Modifier.weight(1f))

        // BPM
        Text(
            text = "%.1f".format(transportState.tempoBpm) + " BPM",
            color = KnobGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
private fun TransportMiniButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) activeColor else BgGunmetal)
            .border(
                1.dp,
                if (active) activeColor else PanelHighlight.copy(alpha = 0.4f),
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color.White else TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlaceholderScreen(title: String, message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgGunmetal)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = TextMuted,
                fontSize = 14.sp
            )
        }
    }
}
