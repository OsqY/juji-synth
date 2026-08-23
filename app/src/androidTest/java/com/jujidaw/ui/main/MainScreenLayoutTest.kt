package com.jujidaw.ui.main

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jujidaw.ui.theme.JujiDawTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainScreenLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun landscapeTimelineStartsWithInlineControlsCollapsed() {
        val landscape = Configuration(composeRule.activity.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides landscape) {
                JujiDawTheme { MainScreen() }
            }
        }

        composeRule.onNodeWithTag("timeline-controls-toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("timeline-zoom-indicator").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Play").assertCountEquals(1)
    }

    @Test
    fun everyDestinationFitsAndRetainsOneTransportInLandscape() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitUntil(5_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.setContent { JujiDawTheme { MainScreen() } }

        listOf(
            "timeline" to "timeline-viewport",
            "mixer" to "mixer-root",
            "synth" to "synth-root",
            "pads" to "pads-root",
            "keyboard" to "keyboard-root",
            "sequencer" to "sequencer-root",
            "project" to "project-root",
        ).forEach { (destination, rootTag) ->
            composeRule.onNodeWithTag("main-destination-$destination").performClick().assertIsSelected()
            val bounds = composeRule.onNodeWithTag(rootTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val screenWidth = composeRule.activity.resources.displayMetrics.widthPixels.toFloat()
            assertTrue("$destination extends past the screen: $bounds", bounds.right <= screenWidth)
            composeRule.onAllNodesWithContentDescription("Play").assertCountEquals(1)
        }
    }
}
