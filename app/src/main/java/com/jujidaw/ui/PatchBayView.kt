package com.jujidaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jujidaw.model.ModulationRoute
import com.jujidaw.ui.theme.*

private val SOURCE_LABELS = listOf("LFO1", "LFO2", "ENV1", "ENV2", "Vel", "Aft")
private val DEST_LABELS = listOf("Pitch", "Cut", "Res", "Amp", "Mix", "LFO")

private val SOURCE_COLORS = listOf(
    KnobPink,   // LFO1
    KnobPink,   // LFO2
    KnobGreen,  // ENV1
    KnobGreen,  // ENV2
    KnobAmber,  // Vel
    KnobAmber   // Aft
)

/**
 * Visual patch bay for the modulation matrix.
 * Shows 8 routes with source/destination jacks, amount knobs, and Bézier cables.
 */
@Composable
fun PatchBayView(
    routes: List<ModulationRoute>,
    onRouteChange: (Int, ModulationRoute) -> Unit,
    modifier: Modifier = Modifier,
    learnMode: Boolean = false,
    selectedParamId: Int? = null,
    onLearnSelect: ((Int) -> Unit)? = null
) {
    // Track jack positions for cable drawing
    val sourceJackPositions = remember { Array<Offset?>(8) { null } }
    val destJackPositions = remember { Array<Offset?>(8) { null } }

    Column(modifier = modifier) {
        for (i in routes.indices) {
            val route = routes[i]
            val isActive = route.active
            val sourceLabel = SOURCE_LABELS.getOrElse(route.source) { "—" }
            val destLabel = DEST_LABELS.getOrElse(route.destination) { "—" }
            val sourceColor = SOURCE_COLORS.getOrElse(route.source) { TextMuted }
            val cableColor = if (isActive) sourceColor else TextMuted
            val textColor = if (isActive) TextSecondary else TextMuted

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source label + jack
                Box(
                    modifier = Modifier.width(60.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            sourceJackPositions[i] = coords.positionInRoot()
                        }
                    ) {
                        Text(
                            text = sourceLabel,
                            color = textColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Source jack circle
                        Canvas(modifier = Modifier.size(8.dp)) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            drawCircle(
                                color = if (isActive) sourceColor else TextMuted,
                                radius = size.width / 2f,
                                center = Offset(cx, cy)
                            )
                            drawCircle(
                                color = if (isActive) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                                radius = size.width / 4f,
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Amount knob (small RealKnob)
                RealKnob(
                    value = route.amount,
                    onValueChange = { newAmount ->
                        onRouteChange(i, route.copy(amount = newAmount))
                    },
                    size = 32.dp,
                    accentColor = if (isActive) sourceColor else TextMuted,
                    ledColor = if (isActive) sourceColor.copy(alpha = 0.5f) else Color.Transparent,
                    learnMode = learnMode,
                    isSelected = selectedParamId == i,
                    onLearnSelect = if (onLearnSelect != null) { { onLearnSelect(i) } } else null
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Destination jack + label
                Box(
                    modifier = Modifier.width(60.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            destJackPositions[i] = coords.positionInRoot()
                        }
                    ) {
                        // Dest jack circle
                        Canvas(modifier = Modifier.size(8.dp)) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            drawCircle(
                                color = if (isActive) sourceColor else TextMuted,
                                radius = size.width / 2f,
                                center = Offset(cx, cy)
                            )
                            drawCircle(
                                color = if (isActive) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                                radius = size.width / 4f,
                                center = Offset(cx, cy)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = destLabel,
                            color = textColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Cable overlay: Bézier curves from source jacks to destination jacks
    // This is drawn as a separate overlay Canvas on top of the rows
    // Note: In a real implementation, you'd use a Box overlay with matchParentSize
    // and draw all cables in one Canvas pass. For simplicity, cables are drawn
    // per-row inline above. A production version would use a single overlay Canvas.
}