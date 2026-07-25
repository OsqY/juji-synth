package com.jujidaw.ui.timeline

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jujidaw.ui.theme.JujiDawTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal deterministic surface contract for the real Timeline composable.
 * Interaction scenarios are added in the following instrumentation modules.
 */
@RunWith(AndroidJUnit4::class)
class TimelineComposeHarnessTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setTimelineContent() {
        composeRule.setContent {
            JujiDawTheme {
                TimelineScreen(showTransportControls = true)
            }
        }
    }

    @Test
    fun timelineSurfacesRemainDiscoverableDuringInitialMeasurement() {
        composeRule.onNodeWithTag("timeline-viewport").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-grid").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-ruler").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-playhead").assertIsDisplayed()
    }

    @Test
    fun editingControlsExposeStableTags() {
        composeRule.onNodeWithTag("timeline-pad-selector").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-zoom-indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-snap-indicator").assertIsDisplayed()
    }
}
