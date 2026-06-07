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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujisynth.ui.theme.*

enum class SynthTab(val label: String) {
    OSC("OSC"),
    FILTER("FILTER"),
    ENV("ENV"),
    LFO("LFO"),
    FX("FX"),
    SEQ("SEQ"),
    MOD("MOD")
}

@Composable
fun TabBar(
    selectedTab: SynthTab,
    onTabSelected: (SynthTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BgPanel)
            .border(1.dp, PurpleMid.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SynthTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) PurplePrimary.copy(alpha = 0.3f)
                        else PurpleMid.copy(alpha = 0.15f)
                    )
                    .border(
                        1.dp,
                        if (isSelected) PurplePrimary.copy(alpha = 0.6f)
                        else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) PurpleLight else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
