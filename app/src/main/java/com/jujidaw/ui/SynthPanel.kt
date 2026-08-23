package com.jujidaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jujidaw.ui.theme.*

/**
 * Flat device section with one boundary and a compact signal-color marker.
 */
@Composable
fun SynthPanel(
    title: String,
    accentColor: Color = OnSurface,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(RadiusSm))
                .background(SurfaceContainer)
                .border(1.dp, OutlineVariant, RoundedCornerShape(RadiusSm))
                .padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(14.dp).background(accentColor))
            Spacer(Modifier.width(Spacing.sm))
            Text(text = title, color = OnSurface, style = LabelSmall)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
